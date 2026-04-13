/*package org.example.java_lab1;

import javafx.animation.AnimationTimer;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;

import java.util.concurrent.atomic.AtomicReference;

public class GameEngine {
    // сердце игры - её движок

    // Размеры поля
    private final double fieldWidth  = 650;
    private final double fieldHeight = 460;

    // Игровые объекты (логика) - их трогает только игровой поток
    private final Target nearTarget;
    private final Target farTarget;
    private final Arrow  arrow;

    // Игровое поле из FXML
    private final Pane gameField;

    // Визуальные объекты - то что рисуется на экране, их трогает AnimationTimer
    private Circle  nearCircle;
    private Circle  farCircle;
    private Polygon arrowShape;

    // Атомарное состояние — игровой поток пишет, AnimationTimer читает
    private final AtomicReference<GameState> currentState;

    // Поток и флаги
    private Thread  thread;
    volatile boolean isRun;
    volatile boolean isPause;
    //volatile гарантирует что оба потока видят актуальное значение флага, а не устаревшую копию из кэша процессора

    // Колбэк для очков
    private final ScoreCallback scoreCallback;

    public interface ScoreCallback {
        void onScore(int points);
    }

    // создаем логические объекты и нач. состояние, визуальные объекты в initVisuals
    public GameEngine(Pane gameField, ScoreCallback scoreCallback) {
        this.gameField      = gameField;
        this.scoreCallback  = scoreCallback; // ScoreCallback — интерфейс с одним методом onScore,
        //GameEngine просто вызывает метод когда нужно сообщить об очках

        nearTarget = new Target(fieldWidth * 0.6,  fieldHeight / 2, 2, 25);
        farTarget  = new Target(fieldWidth * 0.85, fieldHeight / 2, 4, 12);
        arrow      = new Arrow();

        // Начальное состояние
        currentState = new AtomicReference<>(new GameState(
                nearTarget.getX(), nearTarget.getY(),
                farTarget.getX(),  farTarget.getY(),
                0, 0, false
        ));
    }

    // Создаём визуальные объекты и запускаем AnimationTimer
    private void initVisuals() {
        nearCircle = new Circle(nearTarget.getX(), nearTarget.getY(),
                nearTarget.getRadius(), Color.RED);
        nearCircle.setStroke(Color.DARKRED);

        farCircle  = new Circle(farTarget.getX(), farTarget.getY(),
                farTarget.getRadius(), Color.SALMON);
        farCircle.setStroke(Color.DARKRED);

        arrowShape = new Polygon(0.0, -6.0, 20.0, 0.0, 0.0, 6.0);
        arrowShape.setFill(Color.BLUE);
        arrowShape.setVisible(false);

        gameField.getChildren().addAll(nearCircle, farCircle, arrowShape);

        // AnimationTimer — читает currentState и обновляет UI (60 раз/сек)
        // Работает уже в JavaFX потоке — Platform.runLater() не нужен
        AnimationTimer renderer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                GameState s = currentState.get(); //читаем атомарно

                nearCircle.setCenterX(s.nearX); //обновление интерфейса
                nearCircle.setCenterY(s.nearY);

                farCircle.setCenterX(s.farX);
                farCircle.setCenterY(s.farY);

                if (s.arrowActive) {
                    arrowShape.setVisible(true);
                    arrowShape.setLayoutX(s.arrowX);
                    arrowShape.setLayoutY(s.arrowY);
                } else {
                    arrowShape.setVisible(false);
                }
            }
        };
        renderer.start();
    }

    // next() — только считает позиции и пишет в AtomicReference
    // не трогаем UI, только данные
    private void next() {
        nearTarget.next(fieldHeight); // двигаем объекты
        farTarget.next(fieldHeight);
        arrow.next(fieldWidth);

        checkHit();

        // Атомарно обновляем состояние
        currentState.getAndUpdate(s -> {
            s.nearX = nearTarget.getX();
            s.nearY = nearTarget.getY();
            s.farX  = farTarget.getX();
            s.farY  = farTarget.getY();
            s.arrowX      = arrow.getX();
            s.arrowY      = arrow.getY();
            s.arrowActive = arrow.isActive();
            return s;
        });
    }

    private void checkHit() {
        if (!arrow.isActive()) return;

        // Расстояние между стрелой и центром мишени по теореме Пифагора
        // Если расстояние меньше радиуса мишени — попадание

        double dNear = Math.sqrt(
                Math.pow(arrow.getX() - nearTarget.getX(), 2) +
                        Math.pow(arrow.getY() - nearTarget.getY(), 2)
        );
        double dFar = Math.sqrt(
                Math.pow(arrow.getX() - farTarget.getX(), 2) +
                        Math.pow(arrow.getY() - farTarget.getY(), 2)
        );

        // начисление очков за попадание

        if (dNear < nearTarget.getRadius()) {
            arrow.setActive(false);
            scoreCallback.onScore(1);
        } else if (dFar < farTarget.getRadius()) {
            arrow.setActive(false);
            scoreCallback.onScore(2);
        }
    }

    public void start() {
        if (thread != null) return;

        // Добавляем визуал на поле (мы в JavaFX потоке — можно напрямую)
        initVisuals();

        thread = new Thread(() -> {
            isRun   = true;
            isPause = false;

            while (isRun) {
                next(); // считаем позиции

                // Пауза через wait/notify — поток спит, не тратит ресурсы
                // wait()/notifyAll() — пауза через засыпание потока.
                // Когда isPause = true поток вызывает wait() и засыпает — не крутится вхолостую
                // Просыпается только когда resume() вызывает notifyAll()
                synchronized (thread) {
                    if (isPause) {
                        try {
                            thread.wait(); // засыпаем здесь
                        } catch (InterruptedException e) {
                            return;
                        }
                        isPause = false;
                    }
                }

                try {
                    Thread.sleep(10); // 100 обновлений/сек — логика
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        thread.setDaemon(true);
        // поток-демон автоматически завершается когда закрывается приложение
        thread.start();
    }

    public void stop() {
        isRun = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        gameField.getChildren().removeAll(nearCircle, farCircle, arrowShape);
    }

    public void pause() {
        isPause = true;
    }

    public void resume() {
        if (thread == null) return;
        synchronized (thread) {
            thread.notifyAll(); // будим поток
        }
    }

    public void shoot() {
        if (!isRun || isPause) return;
        if (!arrow.isActive()) {
            arrow.shoot(65, fieldHeight / 2);
        }
    }

    public boolean isRunning()     { return isRun; }
    public boolean isPaused()      { return isPause; }
    public boolean isArrowActive() { return arrow.isActive(); }
}*/
