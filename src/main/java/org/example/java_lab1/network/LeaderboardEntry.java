package org.example.java_lab1.network;

// простой контейнер для передачи одной строки таблицы лидеров по сети.

public class LeaderboardEntry {
    public String name;
    public int    wins;

    public LeaderboardEntry(String name, int wins) {
        this.name = name;
        this.wins = wins;
    }
}

// БД → Player (Hibernate сущность) → LeaderboardEntry (для сети) → клиент
