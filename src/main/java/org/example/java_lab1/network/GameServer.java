package org.example.java_lab1.network;

import com.google.gson.Gson;
import org.example.java_lab1.Arrow;
import org.example.java_lab1.Target;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {

    private static final int PORT         = 12345; //порт слушанья сервера
    private static final int MAX_PLAYERS  = 4;
    private static final int WIN_SCORE    = 6;
    private static final double FIELD_W   = 650;
    private static final double FIELD_H   = 460;

    // Gson — один экземпляр на всё, он потокобезопасный
    private final Gson gson = new Gson();

    // Список подключённых клиентов, синхронизированный список потокобезопасен
    private final List<ClientHandler> clients =
            Collections.synchronizedList(new ArrayList<>());

    private final Map<String, Double> playerYPositions = new HashMap<>();

    // Игровые объекты — трогает только игровой поток
    private Target nearTarget;
    private Target farTarget;

    // Стрела каждого игрока: имя → Arrow
    private final Map<String, Arrow> arrows = new HashMap<>();

    private volatile boolean gameRunning = false;
    private Thread gameThread;

    private volatile boolean gamePaused = false;
    private final Object pauseLock = new Object(); //для wait/notify()
    private String pausedBy = null; // кто поставил паузу

    // -------------------------------------------------------
    // Запуск сервера
    // -------------------------------------------------------
    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT); // слушает порт и ждёт входящих подключений
        System.out.println("Сервер запущен на порту " + PORT);

        // Принимаем подключения в отдельном потоке чтобы не блокировать main
        Thread acceptThread = new Thread(() -> {
            while (true) {
                try {
                    Socket socket = serverSocket.accept(); // блокирующий вызов — ждём подключения
                    if (clients.size() >= MAX_PLAYERS) {
                        // Уже максимум игроков — сразу отказываем
                        rejectConnection(socket);
                    } else {
                        ClientHandler handler = new ClientHandler(socket); //игрок подключился
                        handler.start(); // запускаем поток для нового клиента
                    }
                } catch (IOException e) {
                    System.out.println("Ошибка приёма подключения: " + e.getMessage());
                }
            }
        });
        acceptThread.setDaemon(true); // поток-демон — завершится когда закроется приложение
        acceptThread.start();
    }

    // Отказ когда сервер уже полон
    private void rejectConnection(Socket socket) {
        try {
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()), true);
            Message msg = new Message(MessageType.JOIN_FAIL, "Сервер полон (максимум 4 игрока)");
            out.println(gson.toJson(msg));
            socket.close();
        } catch (IOException e) {
            System.out.println("Ошибка отказа подключения: " + e.getMessage());
        }
    }

    // -------------------------------------------------------
    // Проверяем: все игроки готовы?
    // -------------------------------------------------------
    private synchronized void checkAllReady() {
        if (clients.isEmpty()) return;
        boolean allReady = clients.stream().allMatch(c -> c.ready);
        if (allReady && !gameRunning) {
            startGame();
        }
    }

    //на примере стрелы:
    //startGame() создаёт стрелы,
    // SHOOT активирует стрелу (arrow.shoot()),
    // buildState() читает позицию летящей стрелы и передаёт клиенту.

    // -------------------------------------------------------
    // Запуск игры
    // -------------------------------------------------------
    private void startGame() {
        // Сбрасываем очки и выстрелы всех игроков
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.score = 0; // сброс очков
                c.shots = 0; // сброс выстрелов

            }
        }

        // Создаём мишени
        nearTarget = new Target(FIELD_W * 0.6,  FIELD_H / 2, 2, 25);
        farTarget  = new Target(FIELD_W * 0.85, FIELD_H / 2, 4, 12);

        // Считаем Y позицию для каждого игрока — равномерно по высоте поля
        arrows.clear();
        playerYPositions.clear();
        synchronized (clients) {
            int count = clients.size();
            double step = FIELD_H / (count + 1); //шаг между игроками
            for (int i = 0; i < count; i++) { //распредление игроков по полу слева
                ClientHandler c = clients.get(i);
                double playerY = step * (i + 1);
                playerYPositions.put(c.name, playerY);
                arrows.put(c.name, new Arrow()); //создание стрелы для игрока
            }
        }

        gameRunning = true;
        gamePaused  = false;
        System.out.println("Игра началась!");

        gameThread = new Thread(() -> {
            while (gameRunning) {
                tick(); //один шаг игры

                // Пауза через wait/notify — поток спит, не тратит ресурсы
                synchronized (pauseLock) {
                    if (gamePaused) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                        gamePaused = false;
                    }
                }

                try { Thread.sleep(10); }
                catch (InterruptedException e) { return; }
            }
        });
        gameThread.setDaemon(true);
        gameThread.start();
    }

    // -------------------------------------------------------
    // Один шаг игры
    // -------------------------------------------------------
    private void tick() {
        // Двигаем мишени
        nearTarget.next(FIELD_H);
        farTarget.next(FIELD_H);

        // Двигаем стрелы и проверяем попадания
        synchronized (clients) {
            for (ClientHandler c : clients) {
                Arrow arrow = arrows.get(c.name);
                if (arrow == null) continue;
                arrow.next(FIELD_W);
                checkHit(c, arrow); //проверка попадания
            }
        }


        NetworkGameState state = buildState(); // создаем состояние для рассылки
        broadcast(new Message(MessageType.GAME_STATE, state)); // Рассылаем состояние всем клиентам

        // Проверяем победителя
        checkWinner();
    } //Сервер — единственный кто двигает объекты, клиенты только получают готовые координаты и рисуют их.

    // -------------------------------------------------------
    // Проверка попадания стрелы конкретного игрока
    // -------------------------------------------------------
    private void checkHit(ClientHandler player, Arrow arrow) {
        if (!arrow.isActive()) return;

        double dNear = dist(arrow.getX(), arrow.getY(), // Расстояние по теореме Пифагора
                nearTarget.getX(), nearTarget.getY());
        double dFar  = dist(arrow.getX(), arrow.getY(),
                farTarget.getX(),  farTarget.getY());

        if (dNear < nearTarget.getRadius()) {
            arrow.setActive(false);
            player.score += 1;
            System.out.println(player.name + " попал в ближнюю мишень (+1)");
        } else if (dFar < farTarget.getRadius()) {
            arrow.setActive(false);
            player.score += 2;
            System.out.println(player.name + " попал в дальнюю мишень (+2)");
        }
    }

    private double dist(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    // -------------------------------------------------------
    // Проверка победителя
    // -------------------------------------------------------
    private void checkWinner() {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c.score >= WIN_SCORE) {
                    gameRunning = false;
                    System.out.println("Победитель: " + c.name);

                    for (ClientHandler p : clients) {
                        p.ready = false;
                    }
                    broadcast(new Message(MessageType.GAME_STATE, buildState()));

                    broadcast(new Message(MessageType.GAME_OVER, c.name));

                    return;
                }
            }
        }
    }

    // -------------------------------------------------------
    // Построение NetworkGameState для рассылки
    // -------------------------------------------------------
    private NetworkGameState buildState() {
        NetworkGameState state = new NetworkGameState();
        state.nearX = nearTarget.getX();
        state.nearY = nearTarget.getY();
        state.farX  = farTarget.getX();
        state.farY  = farTarget.getY();
        state.gameRunning = gameRunning && !gamePaused;

        // Стрелы
        state.arrows = new ArrayList<>();
        synchronized (clients) {
            for (ClientHandler c : clients) {
                Arrow a = arrows.get(c.name);
                if (a != null && a.isActive()) {
                    state.arrows.add(
                            new NetworkGameState.ArrowState(c.name, a.getX(), a.getY()));
                }
            }

            // Игроки
            state.players = new ArrayList<>();
            for (ClientHandler c : clients) {
                state.players.add(
                        new NetworkGameState.PlayerInfo(c.name, c.score, c.shots, c.ready));
            }
        }
        return state;
    }

    // -------------------------------------------------------
    // Рассылка сообщения всем клиентам
    // -------------------------------------------------------
    private void broadcast(Message message) {
        String json = gson.toJson(message);
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.send(json);
            }
        }
    }

    // -------------------------------------------------------
    // Обработчик одного клиента — каждый в своём потоке
    // -------------------------------------------------------
    // Каждый подключившийся игрок — это отдельный ClientHandler в отдельном потоке.
    // Он хранит всю информацию об игроке и читает сообщения от него в бесконечном цикле.
    private class ClientHandler extends Thread {
        private final Socket socket;
        private PrintWriter  out;

        String  name  = "";
        int     score = 0;
        int     shots = 0;
        boolean ready = false;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        //run - метод, который вызывается, когда поток стартует через start()
        // Внутри него бесконечный цикл который читает сообщения от клиента:

        @Override
        public void run() {
            // Открываем потоки чтения и записи для сокета
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {

                out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream()), true);

                String line;
                while ((line = in.readLine()) != null) { //чтение строк от клиента
                    handleMessage(line);                // обработка сообщений
                }

            } catch (IOException e) {
                System.out.println("Клиент отключился: " + name);
            } finally {
                clients.remove(this);
                System.out.println("Игрок удалён: " + name);
            }
        }

        // Разбираем входящее сообщение от клиента
        private void handleMessage(String json) {
            Message msg = gson.fromJson(json, Message.class);

            switch (msg.getType()) {

                case JOIN -> {
                    String requestedName = (String) msg.getData();

                    // Проверяем уникальность имени
                    boolean nameTaken = clients.stream()
                            .anyMatch(c -> c.name.equals(requestedName));

                    if (nameTaken) {
                        send(gson.toJson(new Message(MessageType.JOIN_FAIL,
                                "Имя уже занято")));
                    } else {
                        this.name = requestedName;
                        clients.add(this);
                        send(gson.toJson(new Message(MessageType.JOIN_OK, name)));
                        System.out.println("Игрок подключился: " + name);
                    }
                }

                case READY -> {
                    ready = true;
                    System.out.println(name + " готов");

                    if (gamePaused) {
                        // Снять паузу можно только если ВСЕ игроки готовы
                        boolean allReady = clients.stream().allMatch(c -> c.ready);
                        if (allReady) {
                            pausedBy = null;
                            gamePaused = false;
                            synchronized (pauseLock) {
                                pauseLock.notifyAll();  // будим спящий игровой поток
                            }
                        }
                        broadcast(new Message(MessageType.GAME_STATE, buildState()));
                    } else {
                        checkAllReady();
                    }
                }

                case SHOOT -> {
                    if (!gameRunning || gamePaused) return;
                    Arrow arrow = arrows.get(name);
                    if (arrow != null && !arrow.isActive()) {
                        shots++;
                        double playerY = playerYPositions.getOrDefault(name, FIELD_H / 2);
                        arrow.shoot(65, playerY);  // стреляем с высоты своего треугольника
                        System.out.println(name + " выстрелил");
                    }
                }

                case PAUSE -> {
                    if (!gameRunning || gamePaused) return;
                    gamePaused = true;
                    pausedBy = name;
                    ready = false; // сбрасываем готовность только тому кто нажал паузу
                    System.out.println(name + " поставил паузу");
                    broadcast(new Message(MessageType.GAME_STATE, buildState()));
                }
            }
        }

        void send(String json) {
            if (out != null) out.println(json);
        }
    }
}
//один игровой поток управляет игрой, каждый клиент имеет свой поток для чтения сообщений
// общение между ними через synchronized блоки и volatile флаги