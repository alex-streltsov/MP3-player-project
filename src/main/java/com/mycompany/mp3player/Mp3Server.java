package com.mycompany.mp3player;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

// Серверная часть приложения. Управляет подключениями, хранит музыку и обрабатывает запросы
public class Mp3Server extends Application {
    private static final int DEFAULT_PORT = 8080;
    private static final String MUSIC_DIR = "./server_music";

    private Button startStopButton;
    private Button uploadLocalBtn;
    private Button deleteLocalBtn;
    private Label statusLabel;
    private Label portLabel;
    private Label clientsLabel;
    private Label filesLabel;
    private ListView<String> clientsListView;
    private ListView<String> serverFilesView;
    private TextArea logArea;

    private ServerSocket serverSocket;
    private volatile boolean serverRunning = false;
    private ExecutorService pool;
    private List<String> connectedClients = new ArrayList<>();

    public static void main(String[] args) { launch(args); }

    // Инициализация интерфейса сервера
    @Override
    public void start(Stage primaryStage) {
        try {
            Files.createDirectories(Paths.get(MUSIC_DIR));
            primaryStage.setTitle("MP3 Server");
            primaryStage.setMinWidth(700);
            primaryStage.setMinHeight(650);

            BorderPane root = new BorderPane();
            root.setPadding(new Insets(10));
            root.setStyle("-fx-background-color: #24192E;");
            root.setTop(createTopPanel());
            root.setCenter(createCenterPanel());
            root.setBottom(createBottomPanel());

            Scene scene = new Scene(root, 700, 650);
            primaryStage.setScene(scene);
            primaryStage.show();

            primaryStage.setOnCloseRequest(e -> { stopServer(); Platform.exit(); });
            refreshServerFilesView();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Верхняя панель с кнопками запуска и статусом
    private VBox createTopPanel() {
        VBox panel = new VBox(10);
        panel.setStyle("-fx-background-color: #12071D; -fx-padding: 10; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        Label title = new Label("MP3 Network Server");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #7B688E;");
        title.setAlignment(Pos.CENTER);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        startStopButton = new Button("Запустить сервер");
        startStopButton.setStyle(getButtonStyle("#7B688E"));
        startStopButton.setPrefWidth(200);
        startStopButton.setPrefHeight(40);
        startStopButton.setOnAction(e -> toggleServer());
        buttonBox.getChildren().add(startStopButton);

        statusLabel = new Label("Остановлен");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7B688E; -fx-font-weight: bold;");
        portLabel = new Label("Порт: " + DEFAULT_PORT);
        portLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #95a5a6;");
        portLabel.setAlignment(Pos.CENTER);

        panel.getChildren().addAll(title, buttonBox, statusLabel, portLabel);
        return panel;
    }

    // Центральная панель со списками клиентов и файлов
    private HBox createCenterPanel() {
        HBox mainBox = new HBox(10);
        mainBox.setStyle("-fx-background-color: #12071D; -fx-padding: 10; -fx-border-radius: 5; -fx-background-radius: 5;");

        VBox clientBox = new VBox(5);
        clientsLabel = new Label("Подключенные клиенты (0)");
        clientsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #7B688E;");
        clientsListView = new ListView<>();
        clientsListView.setStyle(getListStyle());
        clientsListView.setPrefHeight(200);
        clientsListView.setCellFactory(lv -> createDarkCell());
        VBox.setVgrow(clientBox, Priority.ALWAYS);
        clientBox.getChildren().addAll(clientsLabel, clientsListView);

        VBox fileBox = new VBox(5);
        filesLabel = new Label("Файлы на сервере (0)");
        filesLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #7B688E;");
        serverFilesView = new ListView<>();
        serverFilesView.setStyle(getListStyle());
        serverFilesView.setPrefHeight(150);
        serverFilesView.setCellFactory(lv -> createDarkCell());

        uploadLocalBtn = createSmallButton("+", "Загрузить файл с ПК");
        uploadLocalBtn.setStyle(getButtonStyle("#7B688E"));
        uploadLocalBtn.setOnAction(e -> uploadLocalFile());

        deleteLocalBtn = createSmallButton("X", "Удалить выбранный файл");
        deleteLocalBtn.setStyle(getButtonStyle("#7B688E"));
        deleteLocalBtn.setDisable(true);
        deleteLocalBtn.setOnAction(e -> deleteLocalFile());

        serverFilesView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> 
            deleteLocalBtn.setDisable(newVal == null));

        HBox fileActions = new HBox(5, uploadLocalBtn, deleteLocalBtn);
        fileBox.getChildren().addAll(filesLabel, serverFilesView, fileActions);
        VBox.setVgrow(fileBox, Priority.ALWAYS);

        mainBox.getChildren().addAll(clientBox, fileBox);
        return mainBox;
    }

    // Стиль для темных ячеек списка
    private ListCell<String> createDarkCell() {
        return new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: #24192E;");
                } else {
                    setText(item);
                    setStyle("-fx-background-color: #24192E; -fx-text-fill: #FFFFFF;");
                }
            }
        };
    }

    // Нижняя панель с логом
    private VBox createBottomPanel() {
        VBox panel = new VBox(5);
        Label logLabel = new Label("Лог событий");
        logLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #7B688E;");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setStyle(getListStyle());
        logArea.setPrefHeight(120);
        logArea.setWrapText(true);
        panel.getChildren().addAll(logLabel, logArea);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        return panel;
    }

    private String getButtonStyle(String color) {
        return "-fx-background-color: " + color + "; -fx-text-fill: white; " +
               "-fx-font-size: 14px; -fx-font-weight: bold; -fx-border-radius: 5; " +
               "-fx-background-radius: 5; -fx-cursor: hand;";
    }

    private String getListStyle() {
        return "-fx-background-color: #24192E; -fx-control-inner-background: #24192E; " +
               "-fx-text-fill: #FFFFFF; -fx-border-color: #4A235A; -fx-border-width: 1;";
    }

    private Button createSmallButton(String text, String tooltip) {
        Button btn = new Button(text);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setStyle("-fx-background-color: #4A235A; -fx-text-fill: white; -fx-font-weight: bold;");
        return btn;
    }

    private void toggleServer() {
        if (serverRunning) stopServer();
        else startServer();
    }

    // Запуск сервера и слушателя подключений
    private void startServer() {
        try {
            serverSocket = new ServerSocket(DEFAULT_PORT);
            serverRunning = true;
            pool = Executors.newCachedThreadPool();
            Platform.runLater(() -> {
                startStopButton.setText("Остановить сервер");
                startStopButton.setStyle(getButtonStyle("#500a91"));
                statusLabel.setText("Работает");
                statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #500a91; -fx-font-weight: bold;");
                log("Сервер запущен на порту " + DEFAULT_PORT);
            });
            new Thread(this::acceptConnections).start();
        } catch (IOException e) {
            Platform.runLater(() -> log("Ошибка запуска сервера: " + e.getMessage()));
        }
    }

    // Остановка сервера и очистка ресурсов
    private void stopServer() {
        serverRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            if (pool != null) pool.shutdown();
        } catch (IOException e) { e.printStackTrace(); }

        Platform.runLater(() -> {
            connectedClients.clear();
            clientsListView.getItems().clear();
            updateClientsCount();
            startStopButton.setText("Запустить сервер");
            startStopButton.setStyle(getButtonStyle("#7B688E"));
            statusLabel.setText("Остановлен");
            statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7B688E; -fx-font-weight: bold;");
            log("Сервер остановлен");
        });
    }

    // Цикл принятия подключений
    private void acceptConnections() {
        while (serverRunning) {
            try {
                Socket client = serverSocket.accept();
                log("Клиент подключён: " + client.getInetAddress());
                String clientInfo = client.getInetAddress().getHostAddress() + ":" + client.getPort();
                Platform.runLater(() -> {
                    connectedClients.add(clientInfo);
                    clientsListView.getItems().add(clientInfo);
                    updateClientsCount();
                });
                pool.submit(() -> handleClient(client, clientInfo));
            } catch (IOException e) {
                if (serverRunning) log("Ошибка принятия подключения: " + e.getMessage());
            }
        }
    }

    // Обработка запросов клиента
    private void handleClient(Socket client, String clientInfo) {
        FileOutputStream uploadStream = null;
        String uploadFileName = null;
        try (DataInputStream in = new DataInputStream(client.getInputStream());
             DataOutputStream out = new DataOutputStream(client.getOutputStream())) {
            
            while (client.isConnected() && !client.isClosed() && serverRunning) {
                byte type = in.readByte();
                int len = in.readInt();
                if (len < 0 || len > 10 * 1024 * 1024) break;
                byte[] payload = new byte[len];
                in.readFully(payload);

                if (type == 1) {
                    String cmd = new String(payload, StandardCharsets.UTF_8);
                    processCommand(cmd, out, clientInfo, uploadStream);
                } else if (type == 2 && uploadStream != null) {
                    uploadStream.write(payload, 0, len);
                    uploadStream.flush();
                }
            }
        } catch (Exception e) {
            log("Клиент отключился: " + clientInfo);
        } finally {
            if (uploadStream != null) { try { uploadStream.close(); } catch (IOException ignored) {} }
            try { client.close(); } catch (IOException ignored) {}
            Platform.runLater(() -> {
                connectedClients.remove(clientInfo);
                clientsListView.getItems().remove(clientInfo);
                updateClientsCount();
            });
        }
    }

    // Обработка текстовых команд
    private void processCommand(String cmd, DataOutputStream out, String clientInfo, FileOutputStream uploadStream) throws IOException {
        if (cmd.equals("LIST")) {
            sendResponse(out, listFiles());
        } else if (cmd.startsWith("DOWNLOAD:")) {
            String file = cmd.substring(9);
            File target = new File(MUSIC_DIR, file);
            if (target.exists()) {
                sendResponse(out, "START_DOWNLOAD:" + file);
                streamFile(out, target);
                sendResponse(out, "END_DOWNLOAD");
                log("Отправлен файл " + file + " клиенту " + clientInfo);
            } else {
                sendResponse(out, "ERROR:File not found");
            }
        } else if (cmd.startsWith("DELETE:")) {
            String fileName = cmd.substring(7);
            File target = new File(MUSIC_DIR, fileName);
            if (target.exists()) {
                if (target.delete()) {
                    sendResponse(out, "OK");
                    log("Удалён файл: " + fileName);
                    refreshServerFilesView();
                } else {
                    sendResponse(out, "ERROR:Deletion failed");
                }
            } else {
                sendResponse(out, "ERROR:File not found");
            }
        } else if (cmd.startsWith("UPLOAD_START:")) {
            String fileName = cmd.substring(13);
            uploadStream = new FileOutputStream(new File(MUSIC_DIR, fileName));
            log("Начало загрузки от " + clientInfo + ": " + fileName);
            sendResponse(out, "OK");
        } else if (cmd.equals("UPLOAD_END")) {
            if (uploadStream != null) { uploadStream.close(); }
            log("Загрузка завершена");
            sendResponse(out, "OK");
            refreshServerFilesView();
        }
    }

    private String listFiles() {
        File dir = new File(MUSIC_DIR);
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".mp3"));
        StringBuilder sb = new StringBuilder("FILES:");
        if (files != null) Arrays.stream(files).sorted().forEach(f -> sb.append(f.getName()).append("\n"));
        return sb.toString();
    }

    private void streamFile(DataOutputStream out, File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                out.writeByte(2);
                out.writeInt(n);
                out.write(buf, 0, n);
                out.flush();
            }
        }
    }

    private void sendResponse(DataOutputStream out, String msg) throws IOException {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        out.writeByte(3);
        out.writeInt(b.length);
        out.write(b);
        out.flush();
    }

    private void updateClientsCount() {
        clientsLabel.setText("Подключенные клиенты (" + connectedClients.size() + ")");
    }

    private void log(String message) {
        Platform.runLater(() -> logArea.appendText("[" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + message + "\n"));
    }

    private void refreshServerFilesView() {
        Platform.runLater(() -> {
            serverFilesView.getItems().clear();
            File dir = new File(MUSIC_DIR);
            File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".mp3"));
            if (files != null) Arrays.stream(files).sorted().forEach(f -> serverFilesView.getItems().add(f.getName()));
            filesLabel.setText("Файлы на сервере (" + serverFilesView.getItems().size() + ")");
        });
    }

    private void uploadLocalFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите MP3 файл");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP3 Files", "*.mp3"));
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            try {
                File destFile = new File(MUSIC_DIR, selectedFile.getName());
                Files.copy(selectedFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log("Локально загружен: " + selectedFile.getName());
                refreshServerFilesView();
                showAlert("Успех", "Файл загружен на сервер");
            } catch (IOException e) {
                log("Ошибка загрузки: " + e.getMessage());
            }
        }
    }

    private void deleteLocalFile() {
        String selectedFile = serverFilesView.getSelectionModel().getSelectedItem();
        if (selectedFile != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setContentText("Удалить файл " + selectedFile + "?");
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    File fileToDelete = new File(MUSIC_DIR, selectedFile);
                    if (fileToDelete.delete()) {
                        log("Локально удалён: " + selectedFile);
                        refreshServerFilesView();
                    }
                }
            });
        }
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}