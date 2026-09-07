package com.mycompany.mp3player;

import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Вспомогательные методы для работы с файловой системой и выбора файлов
public class FileUtils {
    // Открытие диалога выбора одного MP3 файла
    public static File chooseMp3File(Stage stage) {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Выберите MP3 файл");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP3 файлы", "*.mp3"));
            return fc.showOpenDialog(stage);
        } catch (Exception e) {
            System.err.println("Ошибка выбора файла: " + e.getMessage());
            return null;
        }
    }

    // Открытие диалога выбора папки с музыкой
    public static File chooseDirectory(Stage stage) {
        try {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Выберите папку");
            return dc.showDialog(stage);
        } catch (Exception e) {
            System.err.println("Ошибка выбора папки: " + e.getMessage());
            return null;
        }
    }

    // Добавление файла с проверкой на дубликаты через диалог
    public static File addFileWithDuplicateHandling(Stage stage, PlaylistManager manager) {
        File file = chooseMp3File(stage);
        if (file == null) return null;
        return handleFileAddition(stage, manager, file);
    }

    // Добавление всех MP3 файлов из выбранной папки
    public static List<File> addFolderWithDuplicateHandling(Stage stage, PlaylistManager manager) {
        File directory = chooseDirectory(stage);
        if (directory == null) return new ArrayList<>();

        File[] files = getMp3FilesFromDirectory(directory);
        if (files == null || files.length == 0) return new ArrayList<>();

        List<File> addedFiles = new ArrayList<>();
        int skippedCount = 0;
        for (File file : files) {
            File result = handleFileAddition(stage, manager, file);
            if (result != null) addedFiles.add(result);
            else skippedCount++;
        }

        if (skippedCount > 0) {
            showAlert(stage, "Результат добавления",
                "Добавлено: " + addedFiles.size() + "\nПропущено (уже есть в списке): " + skippedCount);
        }
        return addedFiles;
    }

    // Универсальная обработка добавления одного файла
    private static File handleFileAddition(Stage stage, PlaylistManager manager, File file) {
        int existingIndex = findFileIndexStrict(manager, file);
        if (existingIndex != -1) {
            return handleDuplicateFile(stage, file, manager, existingIndex);
        } else {
            manager.addFile(file.getName(), file.getAbsolutePath());
            return file;
        }
    }

    // Поиск файла в плейлисте по пути или имени
    private static int findFileIndexStrict(PlaylistManager manager, File targetFile) {
        String targetPath = targetFile.getAbsolutePath();
        String targetName = targetFile.getName().toLowerCase();
        for (int i = 0; i < manager.size(); i++) {
            String existingPath = manager.getFilePath(i);
            if (existingPath != null) {
                File existingFile = new File(existingPath);
                if (targetPath.equalsIgnoreCase(existingFile.getAbsolutePath())) return i;
                if (targetName.equals(existingFile.getName().toLowerCase())) return i;
            }
        }
        return -1;
    }

    // Обработка ситуации дубликата файла
    private static File handleDuplicateFile(Stage stage, File file, PlaylistManager manager, int existingIndex) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Файл уже существует");
        alert.setHeaderText("Файл " + file.getName() + " уже есть в плейлисте");
        alert.setContentText("Выберите действие:");
        
        ButtonType buttonCancel = new ButtonType("Отменить", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType buttonRename = new ButtonType("Переименовать", ButtonBar.ButtonData.OK_DONE);
        ButtonType buttonReplace = new ButtonType("Заменить", ButtonBar.ButtonData.YES);
        alert.getButtonTypes().setAll(buttonCancel, buttonRename, buttonReplace);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == buttonReplace) {
                manager.removeFile(existingIndex);
                manager.addFile(file.getName(), file.getAbsolutePath());
                return file;
            } else if (result.get() == buttonRename) {
                return handleRenameDialog(stage, file, manager);
            }
        }
        return null;
    }

    // Диалог переименования и создание копии файла
    private static File handleRenameDialog(Stage stage, File originalFile, PlaylistManager manager) {
        TextInputDialog textDialog = new TextInputDialog(originalFile.getName().replace(".mp3", "") + "_copy");
        textDialog.setTitle("Переименовать файл");
        textDialog.setHeaderText("Введите новое имя для копии файла:");
        textDialog.setContentText("Имя:");

        Optional<String> renameResult = textDialog.showAndWait();
        if (renameResult.isPresent()) {
            String newName = renameResult.get().trim();
            if (!newName.isEmpty()) {
                if (!newName.toLowerCase().endsWith(".mp3")) newName += ".mp3";
                try {
                    File newFile = new File(originalFile.getParent(), newName);
                    Files.copy(originalFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    manager.addFile(newName, newFile.getAbsolutePath());
                    return newFile;
                } catch (IOException e) {
                    showAlert(stage, "Ошибка", "Не удалось создать копию: " + e.getMessage());
                }
            }
        }
        return null;
    }

    // Показ информационного окна
    private static void showAlert(Stage stage, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Получение массива MP3 файлов из директории
    public static File[] getMp3FilesFromDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) return new File[0];
        try {
            return dir.listFiles((d, name) -> name.toLowerCase().endsWith(".mp3"));
        } catch (Exception e) {
            return new File[0];
        }
    }
}