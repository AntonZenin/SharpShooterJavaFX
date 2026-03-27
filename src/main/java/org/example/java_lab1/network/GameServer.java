package org.example.java_lab1.network;

import com.google.gson.Gson;
import org.example.java_lab1.Arrow;
import org.example.java_lab1.Target;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {

    private static final int PORT         = 12345;
    private static final int MAX_PLAYERS  = 4;
    private static final int WIN_SCORE    = 6;
    private static final double FIELD_W   = 650;
    private static final double FIELD_H   = 460;

    // Gson — один экземпляр на всё, он thread-safe
    private final Gson gson = new Gson();

    // Список подключённых клиентов, синхронизированный список потокобезопасен
    private final List<ClientHandler> clients =
            Collections.synchronizedList(new ArrayList<>());

    // Игровые объекты — трогает только игровой поток
    private Target nearTarget;
    private Target farTarget;

    // Стрела каждого игрока: имя → Arrow
    private final Map<String, Arrow> arrows = new HashMap<>();

    private volatile boolean gameRunning = false;
    private Thread gameThread;

    private volatile boolean gamePaused = false;
    private final Object pauseLock = new Object();

    // -------------------------------------------------------
    // Запуск сервера
    // -------------------------------------------------------
    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Сервер запущен на порту " + PORT);

        // Принимаем подключения в отдельном потоке чтобы не блокировать main
        Thread acceptThread = new Thread(() -> {
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    if (clients.size() >= MAX_PLAYERS) {
                        // Уже максимум игроков — сразу отказываем
                        rejectConnection(socket);
                    } else {
                        ClientHandler handler = new ClientHandler(socket);
                        handler.start();
                    }
                } catch (IOException e) {
                    System.out.println("Ошибка приёма подключения: " + e.getMessage());
                }
            }
        });
        acceptThread.setDaemon(true);
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

    // -------------------------------------------------------
    // Запуск игры
    // -------------------------------------------------------
    private void startGame() {
        // Сбрасываем очки и выстрелы всех игроков
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.score = 0;
                c.shots = 0;

            }
        }

        // Создаём мишени
        nearTarget = new Target(FIELD_W * 0.6,  FIELD_H / 2, 2, 25);
        farTarget  = new Target(FIELD_W * 0.85, FIELD_H / 2, 4, 12);

        // Создаём стрелы для каждого игрока
        arrows.clear();
        synchronized (clients) {
            for (ClientHandler c : clients) {
                arrows.put(c.name, new Arrow());
            }
        }

        gameRunning = true;
        gamePaused  = false;
        System.out.println("Игра началась!");

        gameThread = new Thread(() -> {
            while (gameRunning) {
                tick();

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
                checkHit(c, arrow);
            }
        }

        // Рассылаем состояние всем клиентам
        NetworkGameState state = buildState();
        broadcast(new Message(MessageType.GAME_STATE, state));

        // Проверяем победителя
        checkWinner();
    }

    // -------------------------------------------------------
    // Проверка попадания стрелы конкретного игрока
    // -------------------------------------------------------
    private void checkHit(ClientHandler player, Arrow arrow) {
        if (!arrow.isActive()) return;

        double dNear = dist(arrow.getX(), arrow.getY(),
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
                    broadcast(new Message(MessageType.GAME_OVER, c.name));
                    for (ClientHandler p : clients) {
                        p.ready = false;
                    }
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

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {

                out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream()), true);

                String line;
                while ((line = in.readLine()) != null) {
                    handleMessage(line);
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
                        // Снятие паузы — будим спящий поток
                        gamePaused = false;

                        synchronized (pauseLock) {
                            pauseLock.notifyAll();
                        }
                    } else {
                        checkAllReady();
                    }
                }

                case SHOOT -> {
                    if (!gameRunning) return;
                    Arrow arrow = arrows.get(name);
                    if (arrow != null && !arrow.isActive()) {
                        shots++;
                        arrow.shoot(65, FIELD_H / 2);
                        System.out.println(name + " выстрелил");
                    }
                }

                case PAUSE -> {
                    if (!gameRunning) return;

                    gamePaused = true;
                    ready = false; // чтобы снять паузу нужно снова нажать READY
                    System.out.println(name + " поставил паузу");
                    // Рассылаем текущее состояние с gameRunning=false
                    broadcast(new Message(MessageType.GAME_STATE, buildState()));
                }
            }
        }

        void send(String json) {
            if (out != null) out.println(json);
        }
    }
}
