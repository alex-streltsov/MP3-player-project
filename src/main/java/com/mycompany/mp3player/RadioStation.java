package com.mycompany.mp3player;

import javafx.scene.image.ImageView;

// Класс для хранения данных радиостанции

public class RadioStation {
    
    private final String name;
    private final String streamUrl;
    private final ImageView iconView;

    public RadioStation(String name, String streamUrl) {
        this(name, streamUrl, null);
    }
    
    // Конструктор с возможностью задать иконку
    public RadioStation(String name, String streamUrl, ImageView icon) {
        this.name = name;
        this.streamUrl = streamUrl;
        this.iconView = icon != null ? icon : createDefaultIcon();
        this.iconView.setFitWidth(32);
        this.iconView.setFitHeight(32);
    }

    // Создаёт иконку по умолчанию для радиостанции
    private ImageView createDefaultIcon() {
        ImageView iv = new ImageView();
        
        return iv;
    }

    public String getName() {
        return name;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public ImageView getIconView() {
        return iconView;
    }

    @Override
    public String toString() {
        return name;
    }
}