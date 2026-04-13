package org.example.java_lab1.network;

public class ServerLauncher {
    public static void main(String[] args) throws Exception {
        GameServer server = new GameServer();
        server.start();
        System.out.println("Сервер работает. Нажмите Enter для остановки.");
        System.in.read();
    }
}