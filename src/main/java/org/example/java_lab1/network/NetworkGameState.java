package org.example.java_lab1.network;

import java.util.List;

// Полное состояние игры которое сервер рассылает всем клиентам.
// Клиент получает этот объект и просто рисует то что в нём.
public class NetworkGameState {

    // Позиции мишеней
    public double nearX, nearY;
    public double farX,  farY;

    // Летящие стрелы всех игроков
    public List<ArrowState> arrows;

    // Список всех игроков
    public List<PlayerInfo> players;

    // Идёт ли игра (false = ждём готовности)
    public boolean gameRunning;

    // Одна летящая стрела
    public static class ArrowState {
        public String playerName; // чья стрела
        public double x, y;

        public ArrowState(String playerName, double x, double y) {
            this.playerName = playerName;
            this.x = x;
            this.y = y;
        }
    }

    // Информация об одном игроке
    public static class PlayerInfo {
        public String  name;
        public int     score;
        public int     shots;
        public boolean ready; // подтвердил ли готовность

        public PlayerInfo(String name, int score, int shots, boolean ready) {
            this.name  = name;
            this.score = score;
            this.shots = shots;
            this.ready = ready;
        }
    }
}

