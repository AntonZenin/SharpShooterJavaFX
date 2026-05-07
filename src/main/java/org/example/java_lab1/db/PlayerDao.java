package org.example.java_lab1.db;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.util.List;

// DAO — единственное место где работаем с БД
// GameServer использует только этот класс и вызывает методы PlayerDao
public class PlayerDao {

    private final SessionFactory sessionFactory;

    public PlayerDao() {
        // Читаем hibernate.cfg.xml и создаём фабрику сессий (подключений к БД)
        sessionFactory = new Configuration() //при старте сервера
                .configure("hibernate.cfg.xml")
                .addAnnotatedClass(Player.class) // говорим какие сущности исп-ть
                .buildSessionFactory(); //создание фабрики
    }

    // добавить победу игроку
    // если игрок ещё не в БД — создаём запись.
    // если уже есть — увеличиваем wins на 1.
    public void addWin(String playerName) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction(); // начало транзакции

            // поиск игрока по имени
            Player player = session.createQuery(
                            "FROM Player WHERE name = :name", Player.class)
                    .setParameter("name", playerName)
                    .uniqueResult(); // ожидаем один результат

            if (player == null) {
                // Игрок новый — создаём запись
                player = new Player(playerName);
                player.setWins(1);
                session.persist(player); // добавляем в базу данных (INSERT)
            } else {
                // Игрок уже есть — увеличиваем wins
                player.setWins(player.getWins() + 1);
                session.merge(player); // обновляем базу данных (UPDATE)
            }

            session.getTransaction().commit(); // фиксируем изменения в БД
            System.out.println("Победа сохранена для: " + playerName);
        } catch (Exception e) {
            System.out.println("Ошибка сохранения победы: " + e.getMessage());
        }
    }

    // Получаем таблицу лидеров — все игроки отсортированные
    // по числу побед от большего к меньшему
    // транзакция не нужна, потому что нет изменений - только читаем
    public List<Player> getLeaderboard() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Player ORDER BY wins DESC", Player.class)
                    .list();
        } catch (Exception e) {
            System.out.println("Ошибка получения таблицы лидеров: " + e.getMessage());
            return List.of();
        }
    }

    // Закрываем фабрику сессий, когда сервер завершается
    public void close() {
        if (sessionFactory != null && sessionFactory.isOpen()) {
            sessionFactory.close();
        }
    }
}