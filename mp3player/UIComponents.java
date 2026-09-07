package com.mycompany.mp3player;

import com.mpatric.mp3agic.ID3v2;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import com.mpatric.mp3agic.Mp3File;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Класс UIComponents отвечает за создание и управление пользовательским интерфейсом приложения.
 * Содержит все элементы управления, обработчики событий и логику взаимодействия с пользователем.
 */
public class UIComponents {
    // Цветовые константы для оформления интерфейса
    public static final String COLOR_BG_MAIN = "#12071D";
    public static final String COLOR_BG_LIST = "#24192E";
    public static final String COLOR_TEXT = "#FFFFFF";
    public static final String COLOR_BTN = "#7B688E";
    public static final String COLOR_BTN_HOVER = "#755C8E";
    public static final String COLOR_ACCENT = "#500a91";

    // Основные компоненты приложения
    private final PlaylistManager playlistManager; 
    private final AudioPlayer audioPlayer; 
    private final DiscAnimation discAnimation; 
    private final Stage stage; 
    private final NetworkClient networkClient; 

    // Элементы управления для воспроизведения и отображения информации
    private ListView<PlaylistItem> playlistView; 
    private Label currentSongLabel; 
    private Slider timeSlider;
    private Slider volumeSlider; 
    private Label currentTimeLabel; 
    private Label totalTimeLabel; 
    private Label volumePercentLabel; 
    private TextField searchField; 
    private ToggleButton playlistModeToggle; 
    private ToggleButton radioModeToggle; 
    private ToggleGroup modeToggleGroup; 
    private ListView<RadioStation> radioListView; 
    private HBox timeBox; 

    // Кнопки управления плейлистом
    private Button addFileButton; 
    private Button addFolderButton; 
    private Button removeButton;
    private Button renameButton; 
    private Button saveButton; 

    // Кнопки воспроизведения
    private Button previousButton; 
    private Button playButton; 
    private Button pauseButton;
    private Button nextButton; 
    private ToggleButton shuffleButton; 
    private ToggleButton repeatButton; 

    // Сетевые компоненты
    private Button btnConnect; 
    private Button btnDisconnect; 
    private Button btnRefreshServer; 
    private Button btnDownload; 
    private ListView<String> serverFilesListView; 

    // Служебные переменные
    private boolean isUserDraggingTimeSlider; 
    private final Map<String, Image> albumIconCache; 
    private double currentScale; 
    private boolean isPlaying; 
    private int currentRadioIndex; 
    private boolean isRadioModeActive; 
    private ObservableList<PlaylistItem> masterPlaylistData; 
    private ObservableList<RadioStation> radioStations; 

    /**
     * Конструктор класса UIComponents.
     * Инициализирует все компоненты интерфейса и устанавливает обработчики сетевых событий.
     */
    public UIComponents(PlaylistManager playlistManager, AudioPlayer audioPlayer, Stage stage, NetworkClient networkClient) {
        this.playlistManager = playlistManager;
        this.audioPlayer = audioPlayer;
        this.discAnimation = new DiscAnimation();
        this.stage = stage;
        this.networkClient = networkClient;
        this.isUserDraggingTimeSlider = false;
        this.albumIconCache = new HashMap<>();
        this.currentScale = 1.0;
        this.isPlaying = false;
        this.currentRadioIndex = 0;
        this.isRadioModeActive = false;
        this.masterPlaylistData = FXCollections.observableArrayList();
        this.radioStations = FXCollections.observableArrayList();

        // Добавление предустановленных радиостанций
        radioStations.add(new RadioStation("Европа Плюс", "http://ep128.hostingradio.ru:8030/ep128"));
        radioStations.add(new RadioStation("Русское Радио", "https://rusradio.hostingradio.ru/rusradio128.mp3"));
        radioStations.add(new RadioStation("DFM", "https://dfm.hostingradio.ru/dfm128.mp3"));
        radioStations.add(new RadioStation("Дорожное радио", "http://dorognoe.hostingradio.ru:8000/dorognoe"));

        // Настройка обработчиков сетевых событий
        setupNetworkCallbacks();
    }

    /**
     * Настраивает обработчики сетевых событий.
     * Обновляет интерфейс при изменении состояния подключения и получении данных.
     */
    private void setupNetworkCallbacks() {
        if (networkClient == null) return;
        
        // Обработчик изменения состояния подключения
        networkClient.setOnConnectionChanged(connected -> 
            Platform.runLater(() -> {
                if (btnConnect != null) btnConnect.setDisable(connected);
                if (btnDisconnect != null) btnDisconnect.setDisable(!connected);
                if (btnRefreshServer != null) btnRefreshServer.setDisable(!connected);
                if (btnDownload != null) btnDownload.setDisable(!connected || 
                    (serverFilesListView != null && serverFilesListView.getSelectionModel().getSelectedItem() == null));
            }));
        
        // Обработчик получения списка файлов с сервера
        networkClient.setOnListUpdated(files -> 
            Platform.runLater(() -> {
                if (serverFilesListView != null) {
                    serverFilesListView.getItems().setAll(files);
                    if (btnDownload != null) {
                        btnDownload.setDisable(serverFilesListView.getSelectionModel().getSelectedItem() == null);
                    }
                }
            }));
        
        // Обработчик завершения загрузки файла
        networkClient.setOnFileDownloaded(data -> 
            Platform.runLater(() -> {
                String path = data[0];
                String name = data[1];
                playlistManager.addFile(name, path);
                loadAlbumArtFromMp3(new File(path));
                refreshPlaylist();
                showAlert("Готово", "Трек " + name + " скачан и добавлен в плейлист");
            }));
    }

    /**
     * Создает корневую панель интерфейса.
     * Возвращает BorderPane, содержащий все элементы интерфейса.
     */
    public BorderPane getRootPane() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        HBox mainContent = new HBox(20);
        VBox leftPanel = createLeftPanel();
        VBox rightPanel = createRightPanel();
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        mainContent.getChildren().addAll(leftPanel, rightPanel);
        root.setCenter(mainContent);
        return root;
    }

    /**
     * Создает левую панель интерфейса.
     * Содержит управление плейлистом, поиск и сетевые компоненты.
     */
    public VBox createLeftPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(320);
        panel.setMinWidth(280);
        panel.setStyle("-fx-background-color: transparent;");
        HBox.setHgrow(panel, Priority.NEVER);

        // Создание элементов левой панели
        HBox headerBox = createModeToggleBox();
        searchField = createSearchField();
        playlistView = createPlaylistView();
        radioListView = createRadioListView();
        HBox buttonBox = createFileButtonsBox();
        VBox networkPanel = createNetworkPanel();

        // Добавление элементов в панель
        panel.getChildren().addAll(headerBox, searchField, playlistView, radioListView, buttonBox, networkPanel);
        refreshPlaylist();
        return panel;
    }

    /**
     * Создает панель управления сетевым сервером.
     * Содержит кнопки подключения и список файлов на сервере.
     */
    private VBox createNetworkPanel() {
        VBox p = new VBox(5);
        p.setStyle("-fx-background-color: " + COLOR_BG_LIST + "; -fx-padding: 8; -fx-border-radius: 4; -fx-background-radius: 4;");
        Label title = new Label("🌐 Сетевой сервер");
        title.setStyle("-fx-text-fill: " + COLOR_BTN + "; -fx-font-weight: bold; -fx-font-size: 13px;");
        
        // Создание кнопок сетевого управления
        btnConnect = createButton("🔗", "Подключиться к серверу");
        btnDisconnect = createButton("🔌", "Отключиться от сервера");
        btnRefreshServer = createButton("🔄", "Обновить список файлов");
        btnDownload = createButton("⬇️", "Скачать выбранный файл");
        
        // Применение стиля к кнопкам
        applyButtonStyle(btnConnect); 
        applyButtonStyle(btnDisconnect);
        applyButtonStyle(btnRefreshServer); 
        applyButtonStyle(btnDownload);
        
        // Размещение кнопок в строке
        HBox buttonRow = new HBox(5, btnConnect, btnDisconnect, btnRefreshServer, btnDownload);
        buttonRow.setAlignment(Pos.CENTER);
        
        // Создание списка файлов сервера
        serverFilesListView = new ListView<>();
        serverFilesListView.setStyle(getListStyle());
        serverFilesListView.setPrefHeight(80);
        
        // Настройка обработчиков для кнопок
        btnConnect.setOnAction(e -> { 
            if(networkClient.connect("127.0.0.1", 8080)) 
                networkClient.requestFileList(); 
        });
        btnDisconnect.setOnAction(e -> networkClient.disconnect());
        btnRefreshServer.setOnAction(e -> networkClient.requestFileList());
        btnDownload.setOnAction(e -> {
            String sel = serverFilesListView.getSelectionModel().getSelectedItem();
            if(sel != null) networkClient.downloadFile(sel);
        });
        
        // Обновление состояния кнопки скачивания при выборе файла
        serverFilesListView.getSelectionModel().selectedItemProperty()
            .addListener((o,ov,nv) -> {
                if (btnDownload != null) btnDownload.setDisable(nv==null);
            });
        btnDownload.setDisable(true);
        
        p.getChildren().addAll(title, buttonRow, serverFilesListView);
        return p;
    }

    /**
     * Создает строку переключения режимов (плейлист/радио).
     */
    private HBox createModeToggleBox() {
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Label modeLabel = new Label("Режим:");
        modeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + COLOR_BTN + ";");
        modeToggleGroup = new ToggleGroup();
        playlistModeToggle = createModeToggleButton("♫", "Плейлист", true);
        radioModeToggle = createModeToggleButton("📻", "Радио", false);
        
        // Обработчик изменения режима
        modeToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == playlistModeToggle) toggleRadioMode(false);
            else if (newToggle == radioModeToggle) toggleRadioMode(true);
        });
        
        headerBox.getChildren().addAll(modeLabel, new Region(), playlistModeToggle, radioModeToggle);
        HBox.setHgrow(headerBox, Priority.ALWAYS);
        return headerBox;
    }

    /**
     * Создает кнопку переключения режима.
     */
    private ToggleButton createModeToggleButton(String text, String tooltip, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(modeToggleGroup);
        btn.setSelected(selected);
        applyToggleStyle(btn, selected);
        btn.setTooltip(new Tooltip(tooltip));
        return btn;
    }

    /**
     * Создает поле поиска для фильтрации треков.
     */
    private TextField createSearchField() {
        TextField field = new TextField();
        field.setPromptText("Поиск песни...");
        field.setStyle("-fx-background-color: " + COLOR_BG_LIST + "; -fx-text-fill: " + COLOR_TEXT +
                "; -fx-prompt-text-fill: #888888; -fx-border-color: #4A235A; -fx-border-width: 1; " +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 13px;");
        return field;
    }

    /**
     * Создает список треков плейлиста с возможностью поиска.
     */
    private ListView<PlaylistItem> createPlaylistView() {
        FilteredList<PlaylistItem> filteredData = new FilteredList<>(masterPlaylistData, p -> true);
        
        // Настройка фильтрации по поисковому запросу
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setPredicate(item -> {
                if (newVal == null || newVal.isEmpty()) return true;
                return item.getName().toLowerCase().contains(newVal.toLowerCase());
            });
        });
        
        ListView<PlaylistItem> view = new ListView<>(filteredData);
        view.setCellFactory(lv -> createPlaylistCell());
        view.setStyle(getListStyle());
        VBox.setVgrow(view, Priority.ALWAYS);
        
        // Обработчик двойного клика для воспроизведения трека
        view.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                PlaylistItem selectedItem = view.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    int selectedIndex = masterPlaylistData.indexOf(selectedItem);
                    if (selectedIndex != -1) {
                        playSongAtIndex(selectedIndex);
                    }
                }
            }
        });
        return view;
    }

    /**
     * Создает ячейку для отображения трека в списке.
     */
    private ListCell<PlaylistItem> createPlaylistCell() {
        return new ListCell<PlaylistItem>() {
            @Override
            protected void updateItem(PlaylistItem item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Формирование содержимого ячейки
                    HBox hbox = new HBox(10);
                    hbox.setAlignment(Pos.CENTER_LEFT);
                    ImageView icon = item.getIcon();
                    icon.setFitWidth(32);
                    icon.setFitHeight(32);
                    icon.setPreserveRatio(true);
                    Label nameLabel = new Label(item.getName());
                    nameLabel.setTextFill(Color.WHITE);
                    hbox.getChildren().addAll(icon, nameLabel);
                    setGraphic(hbox);
                    setText(null);
                    setStyle(isSelected() ? "-fx-background-color: " + COLOR_BTN + ";" : "");
                }
            }
        };
    }

    /**
     * Создает список радиостанций.
     */
    private ListView<RadioStation> createRadioListView() {
        ListView<RadioStation> view = new ListView<>(radioStations);
        view.setCellFactory(lv -> createRadioCell());
        view.setStyle(getListStyle());
        view.setVisible(false);
        view.setManaged(false);
        VBox.setVgrow(view, Priority.ALWAYS);
        
        // Обработчик выбора радиостанции
        view.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentRadioIndex = radioStations.indexOf(newVal);
                handleRadioStationSelection(newVal);
            }
        });
        return view;
    }

    /**
     * Создает ячейку для отображения радиостанции.
     */
    private ListCell<RadioStation> createRadioCell() {
        return new ListCell<RadioStation>() {
            @Override
            protected void updateItem(RadioStation item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Формирование содержимого ячейки
                    HBox hbox = new HBox(10);
                    hbox.setAlignment(Pos.CENTER_LEFT);
                    ImageView icon = item.getIconView();
                    Label nameLabel = new Label(item.getName());
                    nameLabel.setTextFill(Color.WHITE);
                    hbox.getChildren().addAll(icon, nameLabel);
                    setGraphic(hbox);
                    setText(null);
                    setStyle(isSelected() ? "-fx-background-color: " + COLOR_ACCENT + ";" : "");
                }
            }
        };
    }

    /**
     * Возвращает стиль для списков.
     */
    private String getListStyle() {
        return "-fx-background-color: " + COLOR_BG_LIST + "; -fx-control-inner-background: " + COLOR_BG_LIST +
                "; -fx-text-fill: " + COLOR_TEXT + "; -fx-border-color: #4A235A; -fx-border-width: 1;";
    }

    /**
     * Создает панель с кнопками управления плейлистом.
     */
    private HBox createFileButtonsBox() {
        HBox buttonBox = new HBox(5);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setMaxWidth(Double.MAX_VALUE);
        
        // Создание кнопок
        addFileButton = createButton("➕", "Выбрать файл");
        addFolderButton = createButton("📂", "Выбрать папку с музыкой");
        removeButton = createButton("❌", "Удалить из списка");
        renameButton = createButton("✎", "Изменить имя файла");
        saveButton = createButton("💾", "Сохранить плейлист в папку");
        
        // Применение стиля к кнопкам
        applyButtonStyle(addFileButton);
        applyButtonStyle(addFolderButton);
        applyButtonStyle(removeButton);
        applyButtonStyle(renameButton);
        applyButtonStyle(saveButton);
        
        // Настройка обработчиков для кнопок
        addFileButton.setOnAction(e -> addFileToPlaylist());
        addFolderButton.setOnAction(e -> addFolderToPlaylist());
        removeButton.setOnAction(e -> removeFromPlaylist());
        renameButton.setOnAction(e -> renameSelectedSong());
        saveButton.setOnAction(e -> savePlaylist());
        
        // Растягивание кнопок для равномерного распределения
        for (Button btn : List.of(addFileButton, addFolderButton, removeButton, renameButton, saveButton)) {
            HBox.setHgrow(btn, Priority.ALWAYS);
        }
        
        buttonBox.getChildren().addAll(addFileButton, addFolderButton, removeButton, renameButton, saveButton);
        return buttonBox;
    }

    /**
     * Создает правую панель интерфейса.
     * Содержит визуализацию, управление воспроизведением и громкостью.
     */
    public VBox createRightPanel() {
        VBox panel = new VBox(15);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-background-color: transparent;");
        panel.setFillWidth(true);
        discAnimation.getDiscImageView().setStyle("-fx-background-color: transparent;");
        
        // Добавление элементов на правую панель
        panel.getChildren().add(discAnimation.getDiscImageView());
        currentSongLabel = createSongLabel();
        panel.getChildren().add(currentSongLabel);
        timeBox = createTimeBox();
        panel.getChildren().add(timeBox);
        panel.getChildren().add(createPlaybackControls());
        panel.getChildren().add(createModeControls());
        panel.getChildren().add(createVolumeControl());
        
        // Настройка обновления времени воспроизведения
        setupTimelineUpdates();
        return panel;
    }

    /**
     * Создает метку для отображения текущей песни.
     */
    private Label createSongLabel() {
        Label label = new Label("Нет выбранной песни");
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + COLOR_BTN + ";");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        label.setMaxWidth(600);
        return label;
    }

    /**
     * Создает элементы управления временем воспроизведения.
     */
    private HBox createTimeBox() {
        timeSlider = new Slider(0, 100, 0);
        timeSlider.setPrefWidth(300);
        timeSlider.setMinWidth(200);
        timeSlider.setDisable(true);
        timeSlider.setStyle("-fx-accent: " + COLOR_BTN + ";");
        
        // Обработчики для перемещения слайдера
        timeSlider.setOnMousePressed(e -> isUserDraggingTimeSlider = true);
        timeSlider.setOnMouseDragged(e -> isUserDraggingTimeSlider = true);
        timeSlider.setOnMouseReleased(e -> {
            isUserDraggingTimeSlider = false;
            seekToPosition();
        });
        
        currentTimeLabel = createTimeLabel("0:00");
        totalTimeLabel = createTimeLabel("0:00");
        
        // Размещение элементов в строке
        HBox timeBox = new HBox(10);
        timeBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(timeSlider, Priority.ALWAYS);
        timeBox.getChildren().addAll(currentTimeLabel, timeSlider, totalTimeLabel);
        return timeBox;
    }

    /**
     * Создает метку времени с заданным текстом.
     */
    private Label createTimeLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + COLOR_BTN + ";");
        return label;
    }

    /**
     * Создает элементы управления воспроизведением (кнопки).
     */
    private HBox createPlaybackControls() {
        HBox controlBox = new HBox(10);
        controlBox.setAlignment(Pos.CENTER);
        
        // Создание кнопок
        previousButton = createButton("⏮", "Предыдущая песня");
        playButton = createButton("▶", "Воспроизвести");
        pauseButton = createButton("⏸", "Приостановить воспроизведение");
        nextButton = createButton("⏭", "Следующая песня");
        
        // Применение стиля к кнопкам
        applyButtonStyle(previousButton);
        applyButtonStyle(playButton);
        applyButtonStyle(pauseButton);
        applyButtonStyle(nextButton);
        
        // Настройка обработчиков для кнопок
        previousButton.setOnAction(e -> playPrevious());
        playButton.setOnAction(e -> playCurrent());
        pauseButton.setOnAction(e -> pause());
        nextButton.setOnAction(e -> playNext());
        
        controlBox.getChildren().addAll(previousButton, playButton, pauseButton, nextButton);
        return controlBox;
    }

    /**
     * Создает элементы управления режимами (перемешивание, повтор).
     */
    private HBox createModeControls() {
        HBox modeBox = new HBox(10);
        modeBox.setAlignment(Pos.CENTER);
        
        // Создание кнопки перемешивания
        shuffleButton = new ToggleButton("⇄");
        shuffleButton.setTooltip(new Tooltip("Случайный порядок воспроизведения"));
        shuffleButton.setSelected(false);
        updateModeButtonStyle(shuffleButton, false, 1.0);
        shuffleButton.setOnAction(e -> toggleShuffle());
        
        // Создание кнопки повтора
        repeatButton = new ToggleButton("⟲");
        repeatButton.setTooltip(new Tooltip("Повторять текущую песню"));
        repeatButton.setSelected(false);
        updateModeButtonStyle(repeatButton, false, 1.0);
        repeatButton.setOnAction(e -> toggleRepeat());
        
        modeBox.getChildren().addAll(shuffleButton, repeatButton);
        return modeBox;
    }

    /**
     * Создает элементы управления громкостью.
     */
    private VBox createVolumeControl() {
        VBox volumeBox = new VBox(5);
        volumeBox.setAlignment(Pos.CENTER);
        volumeBox.setFillWidth(true);
        
        // Создание метки громкости
        Label volumeLabel = new Label("Громкость:");
        volumeLabel.setStyle("-fx-text-fill: " + COLOR_BTN + ";");
        
        // Создание слайдера громкости
        volumeSlider = new Slider(0, 100, 50);
        volumeSlider.setPrefWidth(250);
        volumeSlider.setMinWidth(150);
        volumeSlider.setStyle("-fx-accent: " + COLOR_BTN + ";");
        
        // Создание метки процента громкости
        volumePercentLabel = new Label("50%");
        volumePercentLabel.setStyle("-fx-text-fill: " + COLOR_BTN + "; -fx-font-weight: bold;");
        
        // Обработчик изменения громкости
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            audioPlayer.setVolume(newVal.doubleValue() / 100.0);
            volumePercentLabel.setText(Math.round(newVal.doubleValue()) + "%");
        });
        
        // Размещение элементов в строке
        HBox volumeControls = new HBox(10);
        volumeControls.setAlignment(Pos.CENTER);
        HBox.setHgrow(volumeSlider, Priority.ALWAYS);
        volumeControls.getChildren().addAll(volumeSlider, volumePercentLabel);
        
        volumeBox.getChildren().addAll(volumeLabel, volumeControls);
        return volumeBox;
    }

    /**
     * Применяет стиль к кнопке переключения режима.
     */
    private void applyToggleStyle(ToggleButton btn, boolean isSelected) {
        String bgColor = isSelected ? COLOR_ACCENT : COLOR_BTN;
        String textColor = isSelected ? "white" : COLOR_TEXT;
        btn.setStyle("-fx-background-color: " + bgColor + "; -fx-text-fill: " + textColor +
                "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-border-radius: 4; -fx-background-radius: 4; " +
                "-fx-min-width: 50px; -fx-pref-width: 50px; -fx-min-height: 35px; -fx-pref-height: 35px;");
    }

    /**
     * Переключает режим между плейлистом и радио.
     */
    private void toggleRadioMode(boolean isRadio) {
        isRadioModeActive = isRadio;
        audioPlayer.stop();
        discAnimation.stopRotation();
        discAnimation.loadAlbumArt(null);
        setPlayingState(false);
        currentTimeLabel.setText("0:00");
        totalTimeLabel.setText("0:00");
        timeSlider.setValue(0);
        currentSongLabel.setText(isRadio ? "Потоковое вещание" : "Нет выбранной песни");
        
        // Скрытие или отображение соответствующих элементов
        playlistView.setVisible(!isRadio);
        playlistView.setManaged(!isRadio);
        searchField.setVisible(!isRadio);
        searchField.setManaged(!isRadio);
        radioListView.setVisible(isRadio);
        radioListView.setManaged(isRadio);
        timeBox.setVisible(!isRadio);
        timeBox.setManaged(!isRadio);
        shuffleButton.setDisable(isRadio);
        repeatButton.setDisable(isRadio);
        
        // Обновление стиля кнопок переключения режима
        applyToggleStyle(playlistModeToggle, !isRadio);
        applyToggleStyle(radioModeToggle, isRadio);
    }

    /**
     * Обрабатывает выбор радиостанции.
     */
    private void handleRadioStationSelection(RadioStation station) {
        if (station != null) {
            audioPlayer.playRadioStream(station.getStreamUrl());
            currentSongLabel.setText(station.getName());
            discAnimation.loadRadioIcon(station.getIconView().getImage());
            discAnimation.startRotation();
            setPlayingState(true);
        }
    }

    /**
     * Создает кнопку с указанным текстом и подсказкой.
     */
    private Button createButton(String text, String tooltipText) {
        Button btn = new Button(text);
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: #ffffff; -fx-font-size: 12px; " +
                "-fx-padding: 8px; -fx-background-radius: 4px;");
        btn.setTooltip(tooltip);
        return btn;
    }

    /**
     * Применяет стиль к кнопке и добавляет эффект наведения.
     */
    private void applyButtonStyle(Button btn) {
        String baseStyle = "-fx-background-color: " + COLOR_BTN + "; -fx-text-fill: " + COLOR_TEXT +
                "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-weight: bold; -fx-font-size: 16px; " +
                "-fx-min-width: 50px; -fx-pref-width: 50px; -fx-min-height: 50px; -fx-pref-height: 50px;";
        btn.setStyle(baseStyle);
        
        // Эффект при наведении курсора
        btn.setOnMouseEntered(e -> {
            if (!btn.getStyle().contains(COLOR_ACCENT)) {
                String current = btn.getStyle();
                String toReplace = "-fx-background-color: " + COLOR_BTN + ";";
                if (current.contains(toReplace)) {
                    btn.setStyle(current.replace(toReplace, "-fx-background-color: " + COLOR_BTN_HOVER + ";"));
                }
            }
        });
        
        // Возврат стиля при уходе курсора
        btn.setOnMouseExited(e -> {
            if (!btn.getStyle().contains(COLOR_ACCENT)) {
                String current = btn.getStyle();
                String toReplace = "-fx-background-color: " + COLOR_BTN_HOVER + ";";
                if (current.contains(toReplace)) {
                    btn.setStyle(current.replace(toReplace, "-fx-background-color: " + COLOR_BTN + ";"));
                }
            }
        });
    }

    /**
     * Добавляет один файл в плейлист с обработкой дубликатов.
     */
    private void addFileToPlaylist() {
        File addedFile = FileUtils.addFileWithDuplicateHandling(stage, playlistManager);
        if (addedFile != null) {
            loadAlbumArtFromMp3(addedFile);
            refreshPlaylist();
        }
    }

    /**
     * Добавляет все файлы из папки в плейлист с обработкой дубликатов.
     */
    private void addFolderToPlaylist() {
        List<File> addedFiles = FileUtils.addFolderWithDuplicateHandling(stage, playlistManager);
        if (!addedFiles.isEmpty()) {
            for (File file : addedFiles) loadAlbumArtFromMp3(file);
            refreshPlaylist();
        }
    }

    /**
     * Удаляет выбранный трек из плейлиста.
     */
    private void removeFromPlaylist() {
        int index = playlistView.getSelectionModel().getSelectedIndex();
        if (index != -1) {
            boolean wasCurrent = playlistManager.removeFile(index);
            if (wasCurrent) {
                audioPlayer.stop();
                discAnimation.stopRotation();
                discAnimation.loadAlbumArt(null);
                currentSongLabel.setText("Нет выбранной песни");
                setPlayingState(false);
            }
            refreshPlaylist();
        }
    }

    /**
     * Переименовывает выбранный трек.
     */
    private void renameSelectedSong() {
        int index = playlistView.getSelectionModel().getSelectedIndex();
        if (index != -1) {
            String currentName = playlistManager.getPlaylist().get(index);
            TextInputDialog dialog = new TextInputDialog(currentName);
            dialog.setTitle("Переименовать");
            dialog.setHeaderText("Введите новое имя:");
            dialog.showAndWait().ifPresent(newName -> {
                if (!newName.isEmpty()) {
                    if (!newName.toLowerCase().endsWith(".mp3")) newName += ".mp3";
                    playlistManager.renameFile(index, newName);
                    refreshPlaylist();
                }
            });
        }
    }

    /**
     * Сохраняет плейлист в указанную папку.
     */
    private void savePlaylist() {
        if (playlistManager.getPlaylist().isEmpty()) {
            showAlert("Ошибка", "Плейлист пуст!");
            return;
        }
        TextInputDialog dialog = new TextInputDialog("MyPlaylist");
        dialog.setTitle("Сохранить плейлист");
        dialog.setHeaderText("Введите имя для папки плейлиста:");
        dialog.showAndWait().ifPresent(playlistName -> {
            if (!playlistName.isEmpty()) {
                try {
                    File directory = FileUtils.chooseDirectory(stage);
                    if (directory != null) {
                        String path = playlistManager.savePlaylistWithFiles(playlistName, directory.getAbsolutePath());
                        showAlert("Успех", "Плейлист сохранён в:\n" + path);
                    }
                } catch (Exception e) {
                    showAlert("Ошибка", "Не удалось сохранить: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Запускает воспроизведение текущего трека или радиостанции.
     */
    private void playCurrent() {
        if (isRadioModeActive) {
            if (audioPlayer.isRadioMode() && audioPlayer.isPaused()) {
                audioPlayer.resume();
                discAnimation.startRotation();
                setPlayingState(true);
            } else if (!radioStations.isEmpty()) {
                handleRadioStationSelection(radioStations.get(currentRadioIndex));
            }
        } else {
            if (audioPlayer.getMediaPlayer() != null && audioPlayer.isPaused()) {
                audioPlayer.resume();
                discAnimation.startRotation();
                setPlayingState(true);
            } else {
                if (playlistManager.getCurrentFilePath() == null && !masterPlaylistData.isEmpty()) {
                    playlistManager.setCurrentIndex(0);
                }
                String path = playlistManager.getCurrentFilePath();
                if (path != null) {
                    discAnimation.loadAlbumArt(new File(path));
                    audioPlayer.playCurrentSong();
                    discAnimation.startRotation();
                    updateCurrentSongLabel();
                    highlightCurrentSongInList();
                    setPlayingState(true);
                }
            }
        }
    }

    /**
     * Приостанавливает воспроизведение.
     */
    private void pause() {
        audioPlayer.pause();
        discAnimation.stopRotation();
        setPlayingState(false);
    }

    /**
     * Переключается на следующий трек или радиостанцию.
     */
    private void playNext() {
        if (isRadioModeActive) {
            if (radioStations.isEmpty()) return;
            currentRadioIndex = (currentRadioIndex + 1) % radioStations.size();
            radioListView.getSelectionModel().select(currentRadioIndex);
            handleRadioStationSelection(radioStations.get(currentRadioIndex));
        } else {
            int nextIndex = playlistManager.getNextIndex();
            if (nextIndex != -1) {
                playlistManager.setCurrentIndex(nextIndex);
                String path = playlistManager.getCurrentFilePath();
                if (path != null) discAnimation.loadAlbumArt(new File(path));
                audioPlayer.playCurrentSong();
                discAnimation.startRotation();
                updateCurrentSongLabel();
                highlightCurrentSongInList();
                setPlayingState(true);
            }
        }
    }

    /**
     * Переключается на предыдущий трек или радиостанцию.
     */
    private void playPrevious() {
        if (isRadioModeActive) {
            if (radioStations.isEmpty()) return;
            currentRadioIndex = (currentRadioIndex - 1 + radioStations.size()) % radioStations.size();
            radioListView.getSelectionModel().select(currentRadioIndex);
            handleRadioStationSelection(radioStations.get(currentRadioIndex));
        } else {
            int prevIndex = playlistManager.getPreviousIndex();
            if (prevIndex != -1) {
                playlistManager.setCurrentIndex(prevIndex);
                String path = playlistManager.getCurrentFilePath();
                if (path != null) discAnimation.loadAlbumArt(new File(path));
                audioPlayer.playCurrentSong();
                discAnimation.startRotation();
                updateCurrentSongLabel();
                highlightCurrentSongInList();
                setPlayingState(true);
            }
        }
    }

    /**
     * Воспроизводит трек по указанному индексу.
     */
    private void playSongAtIndex(int index) {
        playlistManager.setCurrentIndex(index);
        audioPlayer.playCurrentSong();
        discAnimation.loadAlbumArt(new File(playlistManager.getCurrentFilePath()));
        discAnimation.startRotation();
        updateCurrentSongLabel();
        setPlayingState(true);
    }

    /**
     * Обновляет состояние воспроизведения и внешний вид кнопок.
     */
    private void setPlayingState(boolean playing) {
        isPlaying = playing;
        if (playing) {
            updateButtonScale(playButton, currentScale);
            updateButtonScale(pauseButton, currentScale);
        } else {
            updateButtonScale(pauseButton, currentScale);
            updateButtonScale(playButton, currentScale);
        }
    }

    /**
     * Переключает режим перемешивания треков.
     */
    private void toggleShuffle() {
        if (isRadioModeActive) return;
        boolean newState = !playlistManager.isShuffle();
        playlistManager.setShuffle(newState);
        
        // Отключение повтора при включении перемешивания
        if (newState && playlistManager.isRepeat()) {
            playlistManager.setRepeat(false);
            repeatButton.setSelected(false);
            updateModeButtonStyle(repeatButton, false, currentScale);
        }
        
        shuffleButton.setSelected(newState);
        updateModeButtonStyle(shuffleButton, newState, currentScale);
    }

    /**
     * Переключает режим повтора трека.
     */
    private void toggleRepeat() {
        if (isRadioModeActive) return;
        boolean newState = !playlistManager.isRepeat();
        playlistManager.setRepeat(newState);
        
        // Отключение перемешивания при включении повтора
        if (newState && playlistManager.isShuffle()) {
            playlistManager.setShuffle(false);
            shuffleButton.setSelected(false);
            updateModeButtonStyle(shuffleButton, false, currentScale);
        }
        
        repeatButton.setSelected(newState);
        updateModeButtonStyle(repeatButton, newState, currentScale);
    }

    /**
     * Обновляет стиль кнопки режима с учетом масштаба.
     */
    private void updateModeButtonStyle(ToggleButton btn, boolean isActive, double scale) {
        double size = 50 * scale;
        double fontSize = 16 * scale;
        String bgColor = isActive ? COLOR_ACCENT : COLOR_BTN;
        
        btn.setMinSize(size, size);
        btn.setPrefSize(size, size);
        btn.setMaxSize(size, size);
        
        btn.setStyle("-fx-background-color: " + bgColor + "; -fx-text-fill: white; " +
            "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-weight: bold; -fx-font-size: " + 
            fontSize + "px;");
            
        // Убираем эффекты наведения для кнопок режимов
        btn.setOnMouseEntered(null);
        btn.setOnMouseExited(null);
    }

    /**
     * Выполняет перемотку на указанную позицию.
     */
    private void seekToPosition() {
        MediaPlayer player = audioPlayer.getMediaPlayer();
        if (player != null && player.getTotalDuration() != null) {
            player.seek(Duration.millis(timeSlider.getValue()));
        }
    }

    /**
     * Настраивает обновление времени воспроизведения.
     */
    private void setupTimelineUpdates() {
        // Обработчик завершения трека
        audioPlayer.setOnEndOfMedia(() -> {
            if (!playlistManager.isRepeat() && !audioPlayer.isRadioMode()) {
                playNext();
            }
        });
        
        // Обработчик готовности плеера
        audioPlayer.setOnPlayerReady(player -> {
            if (player != null && !audioPlayer.isRadioMode()) {
                // Обновление максимального времени трека
                player.totalDurationProperty().addListener((obs, oldDur, newDur) -> {
                    if (newDur != null) {
                        timeSlider.setMin(0);
                        timeSlider.setMax(newDur.toMillis());
                        timeSlider.setDisable(false);
                        totalTimeLabel.setText(formatTime(newDur));
                    }
                });
                
                // Обновление текущего времени воспроизведения
                player.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                    if (!isUserDraggingTimeSlider && player.getTotalDuration() != null) {
                        timeSlider.setValue(newTime.toMillis());
                        currentTimeLabel.setText(formatTime(newTime));
                    }
                });
            }
        });
    }

    /**
     * Обновляет метку текущей песни.
     */
    private void updateCurrentSongLabel() {
        String path = playlistManager.getCurrentFilePath();
        if (path != null) currentSongLabel.setText(new File(path).getName());
    }

    /**
     * Форматирует время в строку вида "минуты:секунды".
     */
    private String formatTime(Duration duration) {
        if (duration == null) return "0:00";
        int minutes = (int) duration.toMinutes();
        int seconds = (int) (duration.toSeconds() % 60);
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Показывает информационное окно с указанным заголовком и сообщением.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Создает изображение обложки по умолчанию.
     */
    private Image createDefaultIconImage() {
        Canvas canvas = new Canvas(32, 32);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(50, 50, 70));
        gc.fillRect(0, 0, 32, 32);
        return canvas.snapshot(null, null);
    }

    /**
     * Загружает обложку альбома из MP3 файла.
     */
    private Image loadAlbumArtFromMp3(File mp3File) {
        if (mp3File == null || !mp3File.exists()) return createDefaultIconImage();
        try {
            Mp3File mp3file = new Mp3File(mp3File);
            if (mp3file.hasId3v2Tag()) {
                ID3v2 tag = mp3file.getId3v2Tag();
                byte[] data = tag.getAlbumImage();
                if (data != null && data.length > 0) {
                    return new Image(new ByteArrayInputStream(data), 32, 32, true, true);
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки загрузки обложки
        }
        return createDefaultIconImage();
    }

    /**
     * Обновляет список треков в интерфейсе.
     */
    private void refreshPlaylist() {
        masterPlaylistData.clear();
        for (int i = 0; i < playlistManager.getPlaylist().size(); i++) {
            String fileName = playlistManager.getPlaylist().get(i);
            String filePath = playlistManager.getFilePath(i);
            if (filePath == null) continue;
            File file = new File(filePath);
            // Используем кэш для ускорения загрузки обложек
            Image iconImage = albumIconCache.computeIfAbsent(filePath, k -> loadAlbumArtFromMp3(file));
            masterPlaylistData.add(new PlaylistItem(fileName, new ImageView(iconImage)));
        }
    }

    /**
     * Выделяет текущий трек в списке.
     */
    private void highlightCurrentSongInList() {
        int index = playlistManager.getCurrentIndex();
        if (index >= 0 && index < masterPlaylistData.size()) {
            playlistView.getSelectionModel().select(index);
            playlistView.scrollTo(index);
        }
    }

    /**
     * Настраивает обработку изменения размеров окна.
     */
    public void setupScaling(javafx.scene.Scene scene, Stage stage) {
        // Обработчик изменения ширины окна
        scene.widthProperty().addListener((obs, oldVal, newVal) ->
                scaleUI(newVal.doubleValue(), scene.getHeight()));
        // Обработчик изменения высоты окна
        scene.heightProperty().addListener((obs, oldVal, newVal) ->
                scaleUI(scene.getWidth(), newVal.doubleValue()));
        // Инициализация масштаба
        scaleUI(scene.getWidth() > 0 ? scene.getWidth() : 1000,
                scene.getHeight() > 0 ? scene.getHeight() : 700);
    }

    /**
     * Обновляет масштаб интерфейса в зависимости от размеров окна.
     */
    private void scaleUI(double width, double height) {
        double scale = Math.min(width / 1000.0, height / 700.0);
        scale = Math.max(0.8, Math.min(1.5, scale));
        currentScale = scale;

        // Масштабирование диска
        double discSize = 200 * scale;
        discAnimation.getDiscImageView().setFitWidth(discSize);
        discAnimation.getDiscImageView().setFitHeight(discSize);

        // Масштабирование текста
        double fontSize = 14 * scale;
        currentSongLabel.setStyle("-fx-font-size: " + fontSize + "px; -fx-font-weight: bold; -fx-text-fill: " + COLOR_BTN + ";");
        currentSongLabel.setMaxWidth(width * 0.5);
        volumePercentLabel.setStyle("-fx-text-fill: " + COLOR_BTN + "; -fx-font-weight: bold; -fx-font-size: " + (fontSize * 0.8) + "px;");

        // Масштабирование кнопок
        updateButtonScale(addFileButton, scale);
        updateButtonScale(addFolderButton, scale);
        updateButtonScale(removeButton, scale);
        updateButtonScale(renameButton, scale);
        updateButtonScale(saveButton, scale);
        updateButtonScale(previousButton, scale);
        updateButtonScale(playButton, scale);
        updateButtonScale(pauseButton, scale);
        updateButtonScale(nextButton, scale);

        // Масштабирование кнопок режимов
        updateModeButtonStyle(shuffleButton, playlistManager.isShuffle(), scale);
        updateModeButtonStyle(repeatButton, playlistManager.isRepeat(), scale);

        // Обновление состояния воспроизведения
        if (isPlaying) setPlayingState(true);
    }

    /**
     * Обновляет размер кнопки в зависимости от масштаба.
     */
    private void updateButtonScale(Button btn, double scale) {
        if (btn == null) return;
        double size = 50 * scale;
        double fontSize = 16 * scale;
        String currentStyle = btn.getStyle();

        // Определение текущего цвета кнопки
        String bgColor = COLOR_BTN;
        String textColor = COLOR_TEXT;
        if (currentStyle.contains(COLOR_ACCENT)) {
            bgColor = COLOR_ACCENT;
            textColor = "white";
        } else if (currentStyle.contains(COLOR_BTN_HOVER)) {
            bgColor = COLOR_BTN_HOVER;
        }

        // Установка размеров и стиля
        btn.setMinSize(size, size);
        btn.setPrefSize(size, size);
        btn.setMaxSize(size, size);

        btn.setStyle("-fx-background-color: " + bgColor + "; -fx-text-fill: " + textColor +
                "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-weight: bold; -fx-font-size: " +
                fontSize + "px;");
    }

    /**
     * Внутренний класс для представления элемента плейлиста.
     */
    private static class PlaylistItem {
        private final ImageView icon;
        private final String name;
        
        public PlaylistItem(String name, ImageView icon) {
            this.name = name;
            this.icon = icon;
        }
        
        public ImageView getIcon() { return icon; }
        public String getName() { return name; }
    }
}