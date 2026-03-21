package org.example.java_lab1.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class ServerConnection {

    private static final int PORT = 12345;
    private final Gson gson = new Gson();

    private Socket socket;
    private PrintWriter out;

    // Колбэки — вызываются когда приходит сообщение от сервера.
    // HelloController подпишется на них чтобы обновлять UI.
    private Consumer<NetworkGameState> onGameState;  // новое состояние игры
    private Consumer<String>           onGameOver;   // имя победителя
    private Consumer<String>           onJoinFail;   // причина отказа
    private Runnable                   onJoinOk;     // успешное подключение

    // -------------------------------------------------------
    // Установка колбэков (вызывается из HelloController)
    // -------------------------------------------------------
    public void setOnGameState(Consumer<NetworkGameState> cb) { onGameState = cb; }
    public void setOnGameOver(Consumer<String> cb)            { onGameOver  = cb; }
    public void setOnJoinFail(Consumer<String> cb)            { onJoinFail  = cb; }
    public void setOnJoinOk(Runnable cb)                      { onJoinOk    = cb; }

    // -------------------------------------------------------
    // Подключение к серверу и запуск потока чтения
    // -------------------------------------------------------
    public void connect(String host, String playerName) throws IOException {
        socket = new Socket(host, PORT);

        out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream()), true);

        // Поток чтения — слушаем сервер постоянно
        Thread readerThread = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {

                String line;
                while ((line = in.readLine()) != null) {
                    handleMessage(line);
                }

            } catch (IOException e) {
                System.out.println("Соединение с сервером разорвано: " + e.getMessage());
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        // Первое сообщение — представляемся серверу
        send(new Message(MessageType.JOIN, playerName));
    }

    // -------------------------------------------------------
    // Разбор входящего сообщения от сервера
    // -------------------------------------------------------
    private void handleMessage(String json) {
        // Сначала читаем только поле "type" чтобы понять что внутри "data"
        JsonObject obj  = JsonParser.parseString(json).getAsJsonObject();
        String typeStr  = obj.get("type").getAsString();
        MessageType type = MessageType.valueOf(typeStr);

        switch (type) {

            case JOIN_OK -> {
                if (onJoinOk != null) onJoinOk.run();
            }

            case JOIN_FAIL -> {
                String reason = obj.has("data")
                        ? obj.get("data").getAsString()
                        : "Отказ сервера";
                if (onJoinFail != null) onJoinFail.accept(reason);
            }

            case GAME_STATE -> {
                // "data" — это объект NetworkGameState
                NetworkGameState state = gson.fromJson(
                        obj.get("data"), NetworkGameState.class);
                if (onGameState != null) onGameState.accept(state);
            }

            case GAME_OVER -> {
                String winner = obj.has("data")
                        ? obj.get("data").getAsString()
                        : "Неизвестный";
                if (onGameOver != null) onGameOver.accept(winner);
            }
        }
    }

    // -------------------------------------------------------
    // Отправка сообщений серверу (публичные методы для Controller)
    // -------------------------------------------------------
    public void sendReady() {
        send(new Message(MessageType.READY));
    }

    public void sendShoot() {
        send(new Message(MessageType.SHOOT));
    }

    public void sendPause() {
        send(new Message(MessageType.PAUSE));
    }

    // -------------------------------------------------------
    // Внутренний метод отправки
    // -------------------------------------------------------
    private void send(Message message) {
        if (out != null) {
            out.println(gson.toJson(message));
        }
    }

    // -------------------------------------------------------
    // Отключение
    // -------------------------------------------------------
    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Ошибка при отключении: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}
