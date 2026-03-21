package org.example.java_lab1;

// Класс стрелы игрока
public class Arrow {
    private double x;      // позиция по горизонтали
    private double y;      // позиция по вертикали (фиксирована при выстреле)
    private double speed;  // скорость движения вправо
    private boolean active; // летит ли стрела сейчас

    public Arrow() { //конструктор, создает стрелу в неактивном состоянии
        this.speed = 7;
        this.active = false;
    }

    // Выстрел — задаём начальную позицию стрелы
    public void shoot(double startX, double startY) {
        this.x = startX;
        this.y = startY;
        this.active = true;
    }

    // next() — двигаем стрелу вправо, вызывается каждые 10 с. из игрового потока
    public void next(double fieldWidth) {
        if (!active) return;
        x += speed;
        // Если стрела вылетела за правый край — деактивируем
        if (x > fieldWidth) {
            active = false;
        }
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
