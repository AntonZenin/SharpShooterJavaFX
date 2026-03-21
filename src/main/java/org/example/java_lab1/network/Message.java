package org.example.java_lab1.network;

// Универсальный контейнер для любого сообщения.
// По сокету передаётся как JSON-строка, например:
// {"type":"JOIN","data":"Anton"}
// {"type":"SHOOT"}
// {"type":"GAME_STATE","data":{"nearX":390,...}}
public class Message {
    private final MessageType type;
    private final Object data;

    public Message(MessageType type, Object data) {
        this.type = type;
        this.data = data;
    }

    // Если данные не нужны (SHOOT, READY, PAUSE)
    public Message(MessageType type) {
        this(type, null);
    }

    public MessageType getType() { return type; }
    public Object getData()      { return data; }
}