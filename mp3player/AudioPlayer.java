package com.mycompany.mp3player;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import java.io.File;
import java.util.function.Consumer;

// Управление воспроизведением аудио. Отвечает за запуск, паузу, перемотку и радиорежим
public class AudioPlayer {
    private MediaPlayer mediaPlayer;
    private final PlaylistManager playlistManager;
    private Runnable onEndOfMedia;
    private Consumer<MediaPlayer> onPlayerReady;
    private boolean isPaused;
    private boolean isRadioMode;

    public AudioPlayer(PlaylistManager playlistManager) {
        this.playlistManager = playlistManager;
        this.isPaused = false;
        this.isRadioMode = false;
    }

    // Установка обработчика готовности плеера
    public void setOnPlayerReady(Consumer<MediaPlayer> handler) {
        this.onPlayerReady = handler;
    }

    // Установка обработчика завершения трека
    public void setOnEndOfMedia(Runnable handler) {
        this.onEndOfMedia = handler;
    }

    // Проверка текущего режима воспроизведения
    public boolean isRadioMode() {
        return isRadioMode;
    }

    // Запуск текущего трека из плейлиста
    public void playCurrentSong() {
        isRadioMode = false;
        String filePath = playlistManager.getCurrentFilePath();
        if (filePath == null) return;

        File currentFile = new File(filePath);
        if (!currentFile.exists()) {
            System.err.println("Файл не найден: " + filePath);
            return;
        }

        try {
            stopCurrentPlayback();
            Media media = new Media(currentFile.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            setupMediaPlayerHandlers();
            mediaPlayer.play();
            isPaused = false;
        } catch (Exception e) {
            System.err.println("Ошибка при запуске файла: " + e.getMessage());
        }
    }

    // Запуск интернет-радио по прямой ссылке на поток
    public void playRadioStream(String streamUrl) {
        isRadioMode = true;
        try {
            stopCurrentPlayback();
            Media streamMedia = new Media(streamUrl);
            mediaPlayer = new MediaPlayer(streamMedia);

            // Обработка ошибок потока
            mediaPlayer.setOnError(() ->
                System.err.println("Ошибка потока радио: проверьте URL или подключение")
            );

            if (onPlayerReady != null) {
                onPlayerReady.accept(mediaPlayer);
            }
            mediaPlayer.play();
            isPaused = false;
        } catch (Exception e) {
            System.err.println("Ошибка запуска радио: " + e.getMessage());
        }
    }

    // Остановка воспроизведения и освобождение ресурсов
    private void stopCurrentPlayback() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        isPaused = false;
    }

    // Настройка обработчиков событий медиаплеера
    private void setupMediaPlayerHandlers() {
        // Обработка завершения трека
        mediaPlayer.setOnEndOfMedia(() -> {
            if (playlistManager.isRepeat()) {
                mediaPlayer.seek(Duration.ZERO);
                mediaPlayer.play();
            } else if (onEndOfMedia != null) {
                onEndOfMedia.run();
            }
        });

        // Обработка ошибок воспроизведения
        mediaPlayer.setOnError(() ->
            System.err.println("Ошибка воспроизведения: " +
                (mediaPlayer.getError() != null ? mediaPlayer.getError().getMessage() : "Неизвестная ошибка"))
        );

        // Уведомление о готовности плеера
        if (onPlayerReady != null) {
            onPlayerReady.accept(mediaPlayer);
        }
    }

    // Приостановка воспроизведения
    public void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            isPaused = true;
        }
    }

    // Возобновление воспроизведения
    public void resume() {
        if (mediaPlayer != null && isPaused) {
            mediaPlayer.play();
            isPaused = false;
        }
    }

    // Полная остановка плеера
    public void stop() {
        stopCurrentPlayback();
    }

    public boolean isPaused() {
        return isPaused;
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    // Установка громкости от 0.0 до 1.0
    public void setVolume(double volume) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(Math.max(0.0, Math.min(1.0, volume)));
        }
    }

    // Перемотка на указанную позицию. Не применяется к радио
    public void seek(Duration duration) {
        if (mediaPlayer != null && duration != null && !isRadioMode) {
            Duration total = mediaPlayer.getTotalDuration();
            if (total != null && duration.lessThan(total)) {
                mediaPlayer.seek(duration);
            }
        }
    }
}