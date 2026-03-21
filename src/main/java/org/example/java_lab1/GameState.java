package org.example.java_lab1;

// Класс хранит текущее состояние всех игровых объектов
// упаковать все координаты в один объект и передавать между потоками атомарно
// чтобы несколько потоков не работали с одним объектом
public class GameState {
    // Ближняя мишень (координаты)
    public double nearX;
    public double nearY;

    // Дальняя мишень (координаты)
    public double farX;
    public double farY;

    // Стрела (координаты и флаг летит ли она)
    public double arrowX;
    public double arrowY;
    public boolean arrowActive;
    // AnimationTimer смотрит на arrowActive чтобы решить показывать стрелу или нет

    public GameState(double nearX, double nearY,
                     double farX, double farY,
                     double arrowX, double arrowY,
                     boolean arrowActive) {
        this.nearX = nearX;
        this.nearY = nearY;
        this.farX = farX;
        this.farY = farY;
        this.arrowX = arrowX;
        this.arrowY = arrowY;
        this.arrowActive = arrowActive;
    }
}

//GameEngine (игровой поток):
//next() считает позиции → записывает в GameState → AtomicReference.set()
//
//AnimationTimer (JavaFX поток):
//AtomicReference.get() → читает GameState → двигает кружки на экране
