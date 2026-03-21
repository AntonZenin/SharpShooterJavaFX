package org.example.java_lab1.network;

public enum MessageType {
    // Клиент → Сервер
    JOIN,    // подключиться (data: имя игрока)
    READY,   // готов начать / снять паузу
    SHOOT,   // выстрел
    PAUSE,   // пауза

    // Сервер → Клиент
    JOIN_OK,     // подключение принято
    JOIN_FAIL,   // отказ (data: причина — строка)
    GAME_STATE,  // состояние игры (data: NetworkGameState)
    GAME_OVER    // игра завершена (data: имя победителя — строка)
}