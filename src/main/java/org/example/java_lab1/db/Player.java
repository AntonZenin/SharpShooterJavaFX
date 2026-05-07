package org.example.java_lab1.db;

import jakarta.persistence.*;

// сущность игрока, отображается на таблицу в базе данных
@Entity
@Table(name = "players") // название таблицы в базе данных
public class Player {

    @Id // первичный ключ таблицы
    @GeneratedValue(strategy = GenerationType.IDENTITY) // значение генерируется автоматически
    private Long id; // SQLite сам присваивает 1,2,3

    // Имя игрока — уникальное, не может быть null
    @Column(name = "name", unique = true, nullable = false) // колонка
    private String name;

    // Число побед
    @Column(name = "wins", nullable = false)
    private int wins = 0;

    public Player() {} // обязателен для Hibernate — он создаёт объекты через рефлексию

    public Player(String name) {
        this.name = name;
        this.wins = 0;
    }

    public Long getId()       { return id; }
    public String getName()   { return name; }
    public int getWins()      { return wins; }
    public void setWins(int wins) { this.wins = wins; }
}