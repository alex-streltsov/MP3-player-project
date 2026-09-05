package com.mycompany.mp3player;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Сетевой клиент. Отвечает за подключение, отправку команд и прием данных
public class NetworkClient {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean connected = false;
    private final String cacheDir = "./cache";

    private Consumer<List<String>> onListUpdated;
    private Consumer<String[]> onFileDownloaded;
    private Consumer<Boolean> onConnectionChanged;

    private final Object streamLock = new Object();

    // Подключение к серверу
    public boolean connect(String host, int port) {
        try {
            Files.createDirectories(Paths.get(cacheDir));
            socket = new Socket(host, port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            connected = true;
            if (onConnectionChanged != null) onConnectionChanged.accept(true);
            new Thread(this::listenLoop).start();
            return true;
        } catch (IOException e) { return false; }
    }

    // Отключение от сервера
    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        if (onConnectionChanged != null) onConnectionChanged.accept(false);
    }

    public boolean isConnected() { return connected; }

    // Установка обработчиков событий
    public void setOnListUpdated(Consumer<List<String>> c) { onListUpdated = c; }
    public void setOnFileDownloaded(Consumer<String[]> c) { onFileDownloaded = c; }
    public void setOnConnectionChanged(Consumer<Boolean> c) { onConnectionChanged = c; }

    public void requestFileList() { sendCommandFrame("LIST"); }
    public void downloadFile(String filename) { sendCommandFrame("DOWNLOAD:" + filename); }
    public void deleteFile(String filename) {
        sendCommandFrame("DELETE:" + filename);
        new Thread(() -> { try { Thread.sleep(500); } catch (InterruptedException e) {} requestFileList(); }).start();
    }

    // Загрузка файла на сервер
    public void uploadFile(File file) {
        if (!connected || !file.exists()) return;
        new Thread(() -> {
            try {
                synchronized (streamLock) {
                    sendCommandFrame("UPLOAD_START:" + file.getName());
                    byte[] buf = new byte[8192];
                    try (FileInputStream fis = new FileInputStream(file)) {
                        int n; while ((n = fis.read(buf)) != -1) sendAudioFrame(buf, n);
                    }
                    sendCommandFrame("UPLOAD_END");
                }
                requestFileList();
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private FileOutputStream downloadStream = null;
    private String downloadingFile = null;

    // Цикл прослушивания входящих данных
    private void listenLoop() {
        try {
            while (connected && !socket.isClosed()) {
                byte type = in.readByte();
                int len = in.readInt();
                if (len < 0 || len > 10 * 1024 * 1024) break;
                byte[] payload = new byte[len];
                in.readFully(payload);

                if (type == 3) {
                    String resp = new String(payload, StandardCharsets.UTF_8);
                    if (resp.startsWith("FILES:")) {
                        List<String> list = new ArrayList<>();
                        for (String l : resp.substring(6).split("\n")) if (!l.isEmpty()) list.add(l);
                        if (onListUpdated != null) onListUpdated.accept(list);
                    } else if (resp.startsWith("START_DOWNLOAD:")) {
                        downloadingFile = resp.substring(15);
                        downloadStream = new FileOutputStream(new File(cacheDir, downloadingFile));
                    } else if (resp.equals("END_DOWNLOAD")) {
                        if (downloadStream != null) { downloadStream.close(); downloadStream = null; }
                        if (downloadingFile != null && onFileDownloaded != null) {
                            String path = new File(cacheDir, downloadingFile).getAbsolutePath();
                            onFileDownloaded.accept(new String[]{path, downloadingFile});
                        }
                        downloadingFile = null;
                    }
                } else if (type == 2 && downloadStream != null) {
                    downloadStream.write(payload, 0, len);
                }
            }
        } catch (IOException e) {
            if (connected) System.err.println("Сеть разорвана: " + e.getMessage());
            if (downloadStream != null) { try { downloadStream.close(); } catch (IOException ignored) {} }
            disconnect();
        }
    }

    // Отправка текстовой команды
    private void sendCommandFrame(String command) {
        try {
            synchronized (streamLock) {
                if (out == null) return;
                byte[] data = command.getBytes(StandardCharsets.UTF_8);
                out.writeByte(1); out.writeInt(data.length); out.write(data); out.flush();
            }
        } catch (IOException e) { System.err.println("Ошибка отправки: " + e.getMessage()); }
    }

    // Отправка бинарных данных
    private void sendAudioFrame(byte[] data, int len) {
        try {
            out.writeByte(2); out.writeInt(len); out.write(data, 0, len); out.flush();
        } catch (IOException e) { System.err.println("Ошибка данных: " + e.getMessage()); }
    }
}