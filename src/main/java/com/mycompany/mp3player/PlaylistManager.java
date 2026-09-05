package com.mycompany.mp3player;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.io.*;
import java.nio.file.*;
import java.util.*;

// Управление состоянием плейлиста. Хранит порядок, индексы и настройки воспроизведения
public class PlaylistManager {
    private final ObservableList<String> playlist;
    private final List<String> originalPlaylist;
    private int currentIndex;
    private boolean isShuffle;
    private boolean isRepeat;
    private boolean isNetworkPlaylist;
    private NetworkClient networkClient;

    public PlaylistManager() {
        this.playlist = FXCollections.observableArrayList();
        this.originalPlaylist = new ArrayList<>();
        this.currentIndex = 0;
        this.isShuffle = false;
        this.isRepeat = false;
        this.isNetworkPlaylist = false;
    }

    public void setNetworkClient(NetworkClient c) { this.networkClient = c; }
    public boolean isNetworkPlaylist() { return isNetworkPlaylist; }
    public ObservableList<String> getPlaylist() { return FXCollections.unmodifiableObservableList(playlist); }
    public int getCurrentIndex() { return currentIndex; }
    public void setCurrentIndex(int i) { this.currentIndex = i; }
    public boolean isShuffle() { return isShuffle; }
    public void setShuffle(boolean s) { this.isShuffle = s; }
    public boolean isRepeat() { return isRepeat; }
    public void setRepeat(boolean r) { this.isRepeat = r; }
    public int size() { return playlist.size(); }

    public String getCurrentFilePath() {
        if (currentIndex >= 0 && currentIndex < originalPlaylist.size()) return originalPlaylist.get(currentIndex);
        return null;
    }

    public String getFilePath(int i) {
        if (i >= 0 && i < originalPlaylist.size()) return originalPlaylist.get(i);
        return null;
    }

    // Добавление файла с проверкой на дубликаты по имени
    public synchronized void addFile(String name, String path) {
        if (name != null && path != null && !playlist.contains(name)) {
            playlist.add(name);
            originalPlaylist.add(path);
        }
    }

    // Удаление файла по индексу. Не работает для сетевого режима
    public synchronized boolean removeFile(int i) {
        if (isNetworkPlaylist || i < 0 || i >= playlist.size()) return false;
        boolean was = (i == currentIndex);
        playlist.remove(i);
        originalPlaylist.remove(i);
        if (currentIndex == i) currentIndex = 0;
        else if (currentIndex > i) currentIndex--;
        return was;
    }

    // Переименование файла и обновление пути
    public synchronized void renameFile(int i, String newName) {
        if (isNetworkPlaylist || i < 0 || i >= originalPlaylist.size()) return;
        String old = originalPlaylist.get(i);
        if (old.startsWith("network://")) return;
        File of = new File(old);
        File nf = new File(of.getParent(), newName);
        try {
            if (of.renameTo(nf)) {
                originalPlaylist.set(i, nf.getAbsolutePath());
                playlist.set(i, newName);
            }
        } catch (Exception e) {
            System.err.println("Ошибка переименования: " + e.getMessage());
        }
    }

    // Сохранение плейлиста и копирование файлов в новую папку
    public String savePlaylistWithFiles(String name, String baseDir) throws IOException {
        if (isNetworkPlaylist) throw new IOException("Сетевой плейлист не сохраняется локально");
        if (name == null || baseDir == null) throw new IllegalArgumentException("Параметры пустые");
        File folder = new File(baseDir, name);
        if (!folder.exists()) folder.mkdirs();
        for (int i = 0; i < originalPlaylist.size(); i++) {
            String p = originalPlaylist.get(i);
            if (p.startsWith("network://")) continue;
            File src = new File(p);
            File dst = new File(folder, playlist.get(i));
            if (src.exists()) Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return folder.getAbsolutePath();
    }

    public int getNextIndex() {
        if (playlist.isEmpty()) return -1;
        return isShuffle ? (int)(Math.random() * playlist.size()) : (currentIndex + 1) % playlist.size();
    }

    public int getPreviousIndex() {
        if (playlist.isEmpty()) return -1;
        return currentIndex > 0 ? currentIndex - 1 : playlist.size() - 1;
    }

    public synchronized void clear() {
        playlist.clear();
        originalPlaylist.clear();
        currentIndex = 0;
        isNetworkPlaylist = false;
    }
}