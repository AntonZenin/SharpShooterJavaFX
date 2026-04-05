package org.example.java_lab1;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import org.example.java_lab1.network.NetworkGameState;
import org.example.java_lab1.network.ServerConnection;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HelloController {

    // ── FXML элементы ──────────────────────────────────────
    @FXML private VBox connectScreen;
    @FXML private HBox      gameScreen;
    @FXML private TextField nameField;
    @FXML private Label     connectErrorLabel;
    @FXML private Pane      gameField;
    @FXML private Button    pauseButton;
    @FXML private VBox      playersList;   // панель справа

    // ── Сетевой слой ───────────────────────────────────────
    private ServerConnection connection;
    private String myName;

    // ── Визуальные объекты на игровом поле ────────────────
    private Circle  nearCircle;
    private Circle  farCircle;
    // Стрелы: имя игрока → его стрела на экране
    private final Map<String, Polygon> arrowShapes = new HashMap<>();

    private boolean gameRunning = false;
    private boolean paused = false;
    private boolean readySent = false;



    // ──────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        // Показываем экран подключения, игровое поле скрыто
        connectScreen.setVisible(true);
        gameScreen.setVisible(false);
    }

    // ══════════════════════════════════════════════════════
    // ЭКРАН ПОДКЛЮЧЕНИЯ
    // ══════════════════════════════════════════════════════

    @FXML
    public void onConnect() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            connectErrorLabel.setText("Введите имя!");
            return;
        }

        myName = name;
        connection = new ServerConnection();

        // Подписываемся на события от сервера
        connection.setOnJoinOk(this::onJoinOk);
        connection.setOnJoinFail(this::onJoinFail);
        connection.setOnGameState(this::onGameState);
        connection.setOnGameOver(this::onGameOver);

        try {
            connection.connect("localhost", myName);
            connectErrorLabel.setText("Подключаемся...");
        } catch (IOException e) {
            connectErrorLabel.setText("Не удалось подключиться к серверу");
        }
    }

    // Сервер принял нас
    private void onJoinOk() {
        Platform.runLater(() -> {
            // Переключаемся на игровой экран
            connectScreen.setVisible(false);
            gameScreen.setVisible(true);
            initVisuals(); // создаём кружки и стрелы на поле
        });
    }

    // Сервер отказал
    private void onJoinFail(String reason) {
        Platform.runLater(() ->
                connectErrorLabel.setText("Отказ: " + reason));
    }

    // ══════════════════════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ ВИЗУАЛА
    // ══════════════════════════════════════════════════════

    private void initVisuals() {
        // Мишени
        nearCircle = new Circle(390, 230, 25, Color.RED);
        nearCircle.setStroke(Color.DARKRED);

        farCircle = new Circle(552, 230, 12, Color.SALMON);
        farCircle.setStroke(Color.DARKRED);

        gameField.getChildren().addAll(nearCircle, farCircle);
    }

    // Получаем или создаём стрелу для игрока
    private Polygon getOrCreateArrow(String playerName) {
        return arrowShapes.computeIfAbsent(playerName, name -> {
            Polygon arrow = new Polygon(0.0, -6.0, 20.0, 0.0, 0.0, 6.0);
            // Своя стрела — синяя, чужие — оранжевые
            arrow.setFill(name.equals(myName) ? Color.BLUE : Color.ORANGE);
            arrow.setVisible(false);
            gameField.getChildren().add(arrow);
            return arrow;
        });
    }

    // ══════════════════════════════════════════════════════
    // ОБНОВЛЕНИЕ СОСТОЯНИЯ ИГРЫ (приходит от сервера ~100 раз/сек)
    // ══════════════════════════════════════════════════════

    private void onGameState(NetworkGameState state) {
        // Всё обновление UI — только в JavaFX потоке
        Platform.runLater(() -> {

            // Двигаем мишени
            nearCircle.setCenterX(state.nearX);
            nearCircle.setCenterY(state.nearY);
            farCircle.setCenterX(state.farX);
            farCircle.setCenterY(state.farY);

            // Прячем все стрелы, потом покажем только активные
            arrowShapes.values().forEach(a -> a.setVisible(false));

            if (state.arrows != null) {
                for (NetworkGameState.ArrowState as : state.arrows) {
                    Polygon arrow = getOrCreateArrow(as.playerName);
                    arrow.setVisible(true);
                    arrow.setLayoutX(as.x);
                    arrow.setLayoutY(as.y);
                }
            }

            if (state.gameRunning && !gameRunning) {
                readySent = false;
            }

            // paused = true только если игра уже была запущена и вдруг остановилась
            // (а не просто ещё не началась)
            if (!state.gameRunning && gameRunning) {
                paused = true;  // игра шла и встала — это пауза
            } else if (state.gameRunning) {
                paused = false; // игра идёт — паузы нет
            }

            gameRunning = state.gameRunning;

            // Обновляем панель игроков справа
            updatePlayersPanel(state);

        });
    }

    // ══════════════════════════════════════════════════════
    // ПАНЕЛЬ ИГРОКОВ СПРАВА
    // ══════════════════════════════════════════════════════

    private static final String CARD_STYLE_ME =
            "-fx-padding: 8; -fx-background-color: #d0e8ff; " +
                    "-fx-border-color: #3399ff; -fx-border-radius: 4; -fx-background-radius: 4;";

    private static final String CARD_STYLE_OTHER =
            "-fx-padding: 8; -fx-background-color: white; " +
                    "-fx-border-color: #cccccc; -fx-border-radius: 4; -fx-background-radius: 4;";

    private void updatePlayersPanel(NetworkGameState state) {
        if (state.players == null) return;
        playersList.getChildren().clear();
        state.players.forEach(p -> playersList.getChildren().add(buildPlayerCard(p)));
    }

    private VBox buildPlayerCard(NetworkGameState.PlayerInfo p) {
        boolean isMe = p.name.equals(myName);
        VBox card = new VBox(3);
        card.setStyle(isMe ? CARD_STYLE_ME : CARD_STYLE_OTHER);

        Label name  = new Label((isMe ? "👤 " : "") + p.name + (isMe ? " (вы)" : ""));
        name.setStyle("-fx-font-weight: bold;");

        card.getChildren().addAll(
                name,
                new Label("Счёт: "      + p.score),
                new Label("Выстрелов: " + p.shots),
                new Label(p.ready       ? "✅ Готов" : "⏳ Ожидает")
        );
        return card;
    }

    // ══════════════════════════════════════════════════════
    // КОНЕЦ ИГРЫ
    // ══════════════════════════════════════════════════════

    private void onGameOver(String winner) {
        Platform.runLater(() -> {
            readySent = false;
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Игра завершена");
            alert.setHeaderText("Победитель: " + winner);
            alert.setContentText("Нажми 'готов', чтобы сыграть снова.");
            alert.showAndWait();

            gameField.lookupAll(".button").forEach(node -> {
                Button btn = (Button) node;
                if (btn.getText().equals("Готов")) {
                    btn.setStyle("-fx-background-color: #3399ff; -fx-text-fill: white;");
                }
            });
        });
    }

    // ══════════════════════════════════════════════════════
    // КНОПКИ УПРАВЛЕНИЯ
    // ══════════════════════════════════════════════════════

    @FXML
    public void onReady() {
        if (connection == null || !connection.isConnected()) return;
        if (gameRunning && !paused) return;
        if (readySent) return;

        readySent = true;
        connection.sendReady();
        gameField.lookupAll(".button").forEach(node -> {
            Button btn = (Button) node;
            if (btn.getText().equals("Готов")) {
                btn.setStyle("");
            }
        });
    }

    @FXML
    public void onStopGame() {
        if (connection != null) {
            connection.disconnect();
        }
    }

    @FXML
    public void onPauseGame() {
        if (connection == null || !connection.isConnected()) return;
        connection.sendPause();
    }

    @FXML
    public void onShoot() {
        if (connection != null && connection.isConnected()) {
            connection.sendShoot();
        }
    }
}