package org.example.java_lab1.db;

import jakarta.persistence.*;

// Сущность — Java класс который映射ся на таблицу в БД
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Имя игрока — уникальное, не может быть null
    @Column(name = "name", unique = true, nullable = false)
    private String name;

    // Число побед
    @Column(name = "wins", nullable = false)
    private int wins = 0;

    public Player() {}

    public Player(String name) {
        this.name = name;
        this.wins = 0;
    }

    public Long getId()       { return id; }
    public String getName()   { return name; }
    public int getWins()      { return wins; }
    public void setWins(int wins) { this.wins = wins; }
}