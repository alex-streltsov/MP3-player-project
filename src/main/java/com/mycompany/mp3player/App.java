package com.mycompany.mp3player;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

// Главный класс приложения. Инициализирует все компоненты и запускает JavaFX интерфейс
public class App extends Application {
    private PlaylistManager playlistManager;
    private AudioPlayer audioPlayer;
    private UIComponents uiComponents;
    private NetworkClient networkClient;

    // Метод запуска приложения. Вызывается автоматически при старте JavaFX
    @Override
    public void start(Stage primaryStage) {
        // Создание менеджера плейлиста и аудиоплеера
        playlistManager = new PlaylistManager();
        audioPlayer = new AudioPlayer(playlistManager);

        // Инициализация сетевого клиента для взаимодействия с сервером
        networkClient = new NetworkClient();

        // Создание интерфейса с передачей всех зависимостей
        uiComponents = new UIComponents(playlistManager, audioPlayer, primaryStage, networkClient);

        // Настройка основного окна
        primaryStage.setTitle("MP3 Player (Client)");
        Scene scene = new Scene(uiComponents.getRootPane(), 1000, 700);
        scene.getRoot().setStyle("-fx-background-color: " + UIComponents.COLOR_BG_MAIN + ";");

        // Настройка масштабирования интерфейса
        uiComponents.setupScaling(scene, primaryStage);

        primaryStage.setScene(scene);
        primaryStage.show();

        // Фоновая попытка подключения к локальному серверу
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            if (networkClient.connect("127.0.0.1", 8080)) {
                System.out.println("Подключено к серверу");
                networkClient.requestFileList();
            } else {
                System.out.println("Сервер не найден. Запустите Mp3Server отдельно.");
            }
        }).start();
    }

    // Точка входа в приложение
    public static void main(String[] args) {
        launch(args);
    }
}