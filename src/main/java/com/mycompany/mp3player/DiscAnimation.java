package com.mycompany.mp3player;

import javafx.animation.RotateTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import java.io.ByteArrayInputStream;
import java.io.File;

// Визуализация вращающегося диска с обложкой альбома или иконкой радио
public class DiscAnimation {
    private static final int DISK_SIZE = 200;
    private static final Color BG_COLOR = Color.rgb(18, 7, 29);
    private static final Color DISK_COLOR = Color.rgb(35, 25, 46);
    
    private final ImageView discImageView;
    private final RotateTransition rotation;

    public DiscAnimation() {
        discImageView = new ImageView();
        discImageView.setFitWidth(DISK_SIZE);
        discImageView.setFitHeight(DISK_SIZE);
        discImageView.setPreserveRatio(true);

        // Настройка анимации вращения
        rotation = new RotateTransition(Duration.seconds(2), discImageView);
        rotation.setByAngle(360);
        rotation.setCycleCount(RotateTransition.INDEFINITE);

        // Установка изображения по умолчанию
        discImageView.setImage(createDefaultDiscImage());
    }

    public ImageView getDiscImageView() {
        return discImageView;
    }

    // Запуск вращения диска
    public void startRotation() {
        rotation.play();
    }

    // Остановка вращения
    public void stopRotation() {
        rotation.pause();
    }

    // Загрузка обложки из MP3 файла. При отсутствии используется заглушка
    public void loadAlbumArt(File mp3File) {
        if (mp3File == null || !mp3File.exists()) {
            discImageView.setImage(createDefaultDiscImage());
            return;
        }
        try {
            Mp3File mp3file = new Mp3File(mp3File);
            if (mp3file.hasId3v2Tag()) {
                byte[] data = mp3file.getId3v2Tag().getAlbumImage();
                if (data != null && data.length > 0) {
                    Image albumArt = new Image(new ByteArrayInputStream(data), DISK_SIZE, DISK_SIZE, true, true);
                    discImageView.setImage(createDiscWithAlbumArt(albumArt));
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка загрузки обложки: " + e.getMessage());
        }
        discImageView.setImage(createDefaultDiscImage());
    }

    // Загрузка иконки радиостанции на диск
    public void loadRadioIcon(Image radioIcon) {
        if (radioIcon == null) {
            discImageView.setImage(createDefaultDiscImage());
            return;
        }
        try {
            discImageView.setImage(createDiscWithAlbumArt(radioIcon));
        } catch (Exception e) {
            System.err.println("Ошибка загрузки иконки радио: " + e.getMessage());
            discImageView.setImage(createDefaultDiscImage());
        }
    }

    // Обрезка изображения по кругу и наложение на диск
    private Image createDiscWithAlbumArt(Image albumArt) {
        WritableImage result = new WritableImage(DISK_SIZE, DISK_SIZE);
        PixelWriter pw = result.getPixelWriter();
        PixelReader pr = albumArt.getPixelReader();
        int cx = DISK_SIZE / 2;
        int cy = DISK_SIZE / 2;
        int radius = DISK_SIZE / 2;

        // Перенос пикселей обложки в круглую область
        for (int y = 0; y < DISK_SIZE; y++) {
            for (int x = 0; x < DISK_SIZE; x++) {
                double dist = Math.sqrt(Math.pow(x - cx, 2) + Math.pow(y - cy, 2));
                if (dist <= radius) {
                    int sx = Math.min((int)((double)x / DISK_SIZE * albumArt.getWidth()), (int)albumArt.getWidth() - 1);
                    int sy = Math.min((int)((double)y / DISK_SIZE * albumArt.getHeight()), (int)albumArt.getHeight() - 1);
                    pw.setColor(x, y, pr.getColor(sx, sy));
                } else {
                    pw.setColor(x, y, BG_COLOR);
                }
            }
        }
        return decorateDisc(result);
    }

    // Добавление декоративных элементов: затемнение, рамка, центр
    private Image decorateDisc(WritableImage baseImage) {
        Canvas canvas = new Canvas(DISK_SIZE, DISK_SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.drawImage(baseImage, 0, 0);

        gc.setFill(Color.rgb(0, 0, 0, 0.2));
        gc.fillOval(0, 0, DISK_SIZE, DISK_SIZE);

        gc.setStroke(Color.rgb(255, 255, 255, 0.6));
        gc.setLineWidth(2);
        gc.strokeOval(0, 0, DISK_SIZE, DISK_SIZE);

        gc.setFill(Color.BLACK);
        gc.fillOval(60, 60, 80, 80);
        gc.setFill(BG_COLOR);
        gc.fillOval(80, 80, 40, 40);
        gc.setFill(Color.rgb(255, 255, 255, 0.3));
        gc.fillOval(85, 85, 30, 30);

        return canvas.snapshot(null, null);
    }

    // Создание стандартного изображения диска без обложки
    private Image createDefaultDiscImage() {
        Canvas canvas = new Canvas(DISK_SIZE, DISK_SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, DISK_SIZE, DISK_SIZE);

        gc.setFill(DISK_COLOR);
        gc.fillOval(0, 0, DISK_SIZE, DISK_SIZE);

        gc.setStroke(Color.rgb(200, 200, 200));
        gc.setLineWidth(2);
        gc.strokeOval(0, 0, DISK_SIZE, DISK_SIZE);

        gc.setFill(Color.BLACK);
        gc.fillOval(60, 60, 80, 80);
        gc.setFill(DISK_COLOR);
        gc.fillOval(80, 80, 40, 40);
        gc.setFill(Color.rgb(255, 255, 255, 0.3));
        gc.fillOval(85, 85, 30, 30);

        gc.setFill(Color.rgb(255, 255, 255, 0.2));
        gc.fillOval(10, 10, 90, 90);

        return canvas.snapshot(null, null);
    }
}