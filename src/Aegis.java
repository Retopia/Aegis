import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.DosFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javafx.animation.PauseTransition;
import javafx.application.Application;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.*;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Duration;
import org.controlsfx.dialog.ProgressDialog;

/**
 * @author Preston Tang
 */
public class Aegis extends Application {

    private static class FileStatus {
        private final File file;
        private boolean success;

        public FileStatus(File file) {
            this.file = file;
            this.success = false;
        }

        public File getFile() { return file; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        @Override
        public String toString() {
            return "FileStatus{file=" + file.getName() + ", success=" + success + "}";
        }
    }

    // Helper class to store duplicate file information
    private static class DuplicateEntry {
        private final File attemptedFile;
        private final File existingFile;

        public DuplicateEntry(File attemptedFile, File existingFile) {
            this.attemptedFile = attemptedFile;
            this.existingFile = existingFile;
        }

        public File getAttemptedFile() { return attemptedFile; }
        public File getExistingFile() { return existingFile; }
    }

    // Helper class to store the results of duplicate checking
    private static class DuplicateCheckResult {
        private final List<FileStatus> newFiles;
        private final List<DuplicateEntry> duplicates;

        public DuplicateCheckResult(List<FileStatus> newFiles, List<DuplicateEntry> duplicates) {
            this.newFiles = newFiles;
            this.duplicates = duplicates;
        }

        public List<FileStatus> getNewFiles() { return newFiles; }
        public List<DuplicateEntry> getDuplicates() { return duplicates; }
    }

    private Stage stage;

    private Rectangle2D dimensions = Screen.getPrimary().getVisualBounds();

    private TableView<FileStatus> fileTable = new TableView<>();
    private ArrayList<FileStatus> files = new ArrayList<>();
    private Image appIcon;

    private MenuItem encryptItem;
    private MenuItem decryptItem;
    private MenuItem removeSelectedItem;
    private MenuItem clearItem;

    @Override
    public void start(Stage stage) {
        Pane root = new Pane();
        MenuBar menu = new MenuBar();
        menu.prefWidthProperty().bind(root.widthProperty());

        // Create styled separators
        SeparatorMenuItem importSeparator = new SeparatorMenuItem();
        SeparatorMenuItem actionSeparator = new SeparatorMenuItem();

        // Set up menu items
        Menu fileMenu = new Menu("File");
        Menu helpMenu = new Menu("Help");
        MenuItem importItem = new MenuItem("Import File(s)");
        removeSelectedItem = new MenuItem("Remove Selected");
        removeSelectedItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        encryptItem = new MenuItem("Encrypt All");
        decryptItem = new MenuItem("Decrypt All");
        clearItem = new MenuItem("Clear Item(s)");
        MenuItem aboutItem = new MenuItem("About");

        // Initially disable items that require files
        encryptItem.setDisable(true);
        decryptItem.setDisable(true);
        removeSelectedItem.setDisable(true);
        clearItem.setDisable(true);

        fileMenu.getItems().addAll(
                importItem,
                importSeparator,
                encryptItem,
                decryptItem,
                actionSeparator,
                removeSelectedItem,
                clearItem
        );

        helpMenu.getItems().add(aboutItem);
        menu.getMenus().addAll(fileMenu, helpMenu);

        Label initText = new Label("Start by drag and dropping files here, double clicking, or clicking \"Import Files\" to get started");
        initText.setFont(new Font(20));
        initText.translateXProperty().bind(root.widthProperty().subtract(initText.widthProperty()).divide(2));
        initText.translateYProperty().bind(root.heightProperty().subtract(initText.heightProperty()).divide(2));

        ScrollPane scroll = new ScrollPane();
        scroll.translateYProperty().bind(menu.heightProperty());
        scroll.prefHeightProperty().bind(root.heightProperty().subtract(menu.heightProperty()));
        scroll.prefWidthProperty().bind(root.widthProperty());
        scroll.setContent(fileTable);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);

        Pane doubleClickPane = new Pane();
        doubleClickPane.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
                importItemAction(root, doubleClickPane, scroll, null);
            }
        });
        doubleClickPane.prefWidthProperty().bind(root.widthProperty());
        doubleClickPane.prefHeightProperty().bind(root.heightProperty());
        doubleClickPane.setStyle("-fx-background-color: transparent;");
        doubleClickPane.getChildren().add(initText);

        setupFileTable();
        setupDragAndDrop(root, doubleClickPane, scroll);

        // Menu item actions
        importItem.setOnAction(e -> importItemAction(root, doubleClickPane, scroll, null));
        removeSelectedItem.setOnAction(e -> removeSelectedItems());
        encryptItem.setOnAction(e -> encryptItemAction());
        decryptItem.setOnAction(e -> decryptItemAction());
        clearItem.setOnAction(e -> clearItemAction(root, doubleClickPane, scroll, null));
        aboutItem.setOnAction(e -> aboutItemAction());

        root.getChildren().addAll(doubleClickPane, menu);

        Scene scene = new Scene(root, dimensions.getMaxX() * 0.75, dimensions.getMaxY() * 0.75);
        stage.setTitle("Aegis V2");
        stage.setScene(scene);
        stage.show();

        // Load and set the application icon
        appIcon = new Image(getClass().getResourceAsStream("tempicon.png"));
        stage.getIcons().add(appIcon);

        scene.getStylesheets().add(getClass().getResource("stylesheet.css").toExternalForm());
        this.stage = stage;
    }

    private void updateMenuStates() {
        boolean hasFiles = !files.isEmpty();
        Platform.runLater(() -> {
            encryptItem.setDisable(!hasFiles);
            decryptItem.setDisable(!hasFiles);
            clearItem.setDisable(!hasFiles);
            removeSelectedItem.setDisable(!hasFiles || fileTable.getSelectionModel().getSelectedItems().isEmpty());
        });
    }

    // Helper method to set icon for any dialog
    private void setDialogIcon(Dialog<?> dialog) {
        Window window = dialog.getDialogPane().getScene().getWindow();
        if (window instanceof Stage) {
            ((Stage) window).getIcons().add(appIcon);
        }
    }

    private void showFilePreview(FileStatus fileStatus) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("File Information");
        dialog.setHeaderText(fileStatus.getFile().getName());
        setDialogIcon(dialog);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        File file = fileStatus.getFile();

        // Basic file information
        grid.add(new Label("Path:"), 0, 0);
        TextField pathField = new TextField(file.getAbsolutePath());
        pathField.setEditable(false);
        pathField.setPrefWidth(300);
        grid.add(pathField, 1, 0);

        grid.add(new Label("Size:"), 0, 1);
        grid.add(new Label(formatFileSize(file.length())), 1, 1);

        grid.add(new Label("Last Modified:"), 0, 2);
        grid.add(new Label(new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
                .format(new java.util.Date(file.lastModified()))), 1, 2);

        // File permissions
        grid.add(new Label("Permissions:"), 0, 3);
        String permissions = String.format("Read: %s, Write: %s, Execute: %s",
                file.canRead() ? "Yes" : "No",
                file.canWrite() ? "Yes" : "No",
                file.canExecute() ? "Yes" : "No");
        grid.add(new Label(permissions), 1, 3);

        // File type detection
        String contentType;
        try {
            contentType = Files.probeContentType(file.toPath());
            if (contentType == null) contentType = "Unknown";
        } catch (IOException e) {
            contentType = "Unknown";
        }
        grid.add(new Label("Content Type:"), 0, 4);
        grid.add(new Label(contentType), 1, 4);

        // Add checksum information
        grid.add(new Label("MD5 Checksum:"), 0, 5);
        Label checksumLabel = new Label("Calculating...");
        grid.add(checksumLabel, 1, 5);

        // Calculate checksum in background
        Task<String> checksumTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                try (FileInputStream fis = new FileInputStream(file)) {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = fis.read(buffer)) != -1) {
                        md.update(buffer, 0, length);
                    }
                    byte[] bytes = md.digest();
                    StringBuilder sb = new StringBuilder();
                    for (byte b : bytes) {
                        sb.append(String.format("%02x", b));
                    }
                    return sb.toString();
                }
            }
        };

        checksumTask.setOnSucceeded(e ->
                checksumLabel.setText(checksumTask.getValue())
        );

        Thread checksumThread = new Thread(checksumTask);
        checksumThread.setDaemon(true);
        checksumThread.start();

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("stylesheet.css").toExternalForm());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        // Center dialog
        final Window window = dialog.getDialogPane().getScene().getWindow();
        window.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> {
            window.setX((dimensions.getWidth() - window.getWidth()) / 2);
            window.setY((dimensions.getHeight() - window.getHeight()) / 2);
        });

        dialog.showAndWait();
    }

    private Pane findDoubleClickPane() {
        Scene scene = fileTable.getScene();
        if (scene == null) return null;

        Pane root = (Pane)scene.getRoot();
        for (Node node : root.getChildren()) {
            if (node instanceof Pane && !(node instanceof MenuBar)) {
                return (Pane)node;
            }
        }
        return null;
    }

    private void removeAllItems() {
        Alert confirmation = new Alert(AlertType.CONFIRMATION);
        confirmation.setTitle("Remove All Files");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Are you sure you want to remove all files from the list?");
        setDialogIcon(confirmation);

        // Style the dialog
        DialogPane dialogPane = confirmation.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("stylesheet.css").toExternalForm());
        dialogPane.getStyleClass().addAll("dialog-pane", "confirmation-dialog");

        // Style the buttons
        dialogPane.lookupButton(ButtonType.OK).getStyleClass().add("dialog-button");
        dialogPane.lookupButton(ButtonType.CANCEL).getStyleClass().add("dialog-button");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Pane root = (Pane)fileTable.getScene().getRoot();
            ScrollPane scroll = (ScrollPane)fileTable.getParent();
            clearItemAction(root, findDoubleClickPane(), scroll, null);
        }
    }

    private void removeSelectedItems() {
        ObservableList<FileStatus> selectedItems = fileTable.getSelectionModel().getSelectedItems();

        if (selectedItems.isEmpty()) {
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Remove Files");
        confirmation.setHeaderText(null);
        setDialogIcon(confirmation);

        String contentText = selectedItems.size() == 1
                ? "Are you sure you want to remove the selected file from the list?"
                : String.format("Are you sure you want to remove %d files from the list?", selectedItems.size());

        confirmation.setContentText(contentText);

        // Style the dialog
        DialogPane dialogPane = confirmation.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("stylesheet.css").toExternalForm());
        dialogPane.getStyleClass().addAll("dialog-pane", "confirmation-dialog");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            List<FileStatus> itemsToRemove = new ArrayList<>(selectedItems);
            fileTable.getItems().removeAll(itemsToRemove);
            files.removeAll(itemsToRemove);
            fileTable.getSelectionModel().clearSelection();

            if (fileTable.getItems().isEmpty()) {
                clearItemAction(
                        (Pane)fileTable.getScene().getRoot(),
                        findDoubleClickPane(),
                        (ScrollPane)fileTable.getParent(),
                        null
                );
            }
            updateMenuStates();
        }
    }

    private void displayProgressDialog(boolean isEncryption, String password) {
        Task copyWorker = processFile(password, isEncryption, System.currentTimeMillis());
        Thread processThread = new Thread(copyWorker);

        ProgressDialog progressDialog = new ProgressDialog(copyWorker);
        progressDialog.getDialogPane().getStylesheets().clear();
        progressDialog.getDialogPane().getStylesheets().add(this.getClass().getResource("stylesheet.css").toExternalForm());
        progressDialog.getDialogPane().getStyleClass().add("dialog");
        setDialogIcon(progressDialog);

        copyWorker.setOnSucceeded(event -> progressDialog.close());
        copyWorker.setOnCancelled(event -> {
            Platform.runLater(() -> {
                progressDialog.close();
            });
        });

        progressDialog.setGraphic(null);
        progressDialog.setHeaderText(null);
        progressDialog.setContentText("Processing files...");

        progressDialog.initStyle(StageStyle.DECORATED);
        progressDialog.initOwner(stage);
        progressDialog.initModality(Modality.WINDOW_MODAL);
        progressDialog.setTitle("In Progress...");

        // Spawns the dialog in the middle of the screen
        final Window window = progressDialog.getDialogPane().getScene().getWindow();
        window.addEventHandler(WindowEvent.WINDOW_SHOWN, new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                window.setX((dimensions.getWidth() - window.getWidth()) / 2);
                window.setY((dimensions.getHeight() - window.getHeight()) / 2);
            }
        });

        progressDialog.setOnCloseRequest(e ->{
            copyWorker.cancel();
        });

        // A "hack" which adds an invisible button to allow this dialog to be closed using the "x" button
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Node closeButton = progressDialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        closeButton.managedProperty().bind(closeButton.visibleProperty());
        closeButton.setVisible(false);
        processThread.start();
        progressDialog.showAndWait();
    }

    private Task<Void> processFile(String password, boolean isEncryption, long startTime) {
        return new Task<Void>() {
            @Override
            protected Void call() {
                try {
                    String process = isEncryption ? "Encrypting " : "Decrypting ";
                    // List to collect all errors
                    List<String> errorMessages = new ArrayList<>();

                    for (int i = 0; i < files.size(); i++) {
                        FileStatus fileStatus = files.get(i);
                        File original = fileStatus.getFile();
                        File aegisFile = new File(original.getParent(), original.getName() + ".aegis");
                        updateMessage(process + aegisFile.getName());

                        AES.ProcessingResult result = AES.processFile(original, aegisFile, password, isEncryption, this);
                        updateProgress(i, files.size() - 1);
                        updateMessage("Secure deleting temporary file " + aegisFile.getName());

                        if (!result.isSuccess()) {
                            // Collect error message
                            errorMessages.add(String.format("• %s: %s - %s",
                                    original.getName(),
                                    result.getError().getMessage(),
                                    result.getDetails()));
                        }

                        AES.secureDelete(original, aegisFile, result.isSuccess());
                        final boolean success = result.isSuccess();
                        Platform.runLater(() -> {
                            fileStatus.setSuccess(success);
                            fileTable.refresh();
                        });
                    }

                    // Show single error dialog if there were any errors
                    if (!errorMessages.isEmpty()) {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Processing Errors");
                            alert.setHeaderText(String.format("Failed to process %d file%s:",
                                    errorMessages.size(),
                                    errorMessages.size() == 1 ? "" : "s"));

                            // Create scrollable text area for errors
                            TextArea textArea = new TextArea(String.join("\n", errorMessages));
                            textArea.setEditable(false);
                            textArea.setWrapText(true);
                            textArea.setMaxHeight(200);  // Limit height
                            textArea.setStyle("-fx-control-inner-background: #333333; -fx-text-fill: #ffffff;");

                            alert.getDialogPane().setContent(textArea);
                            alert.getDialogPane().getStylesheets().add(
                                    getClass().getResource("stylesheet.css").toExternalForm());

                            // Make the dialog resizable
                            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                            alert.setResizable(true);

                            alert.showAndWait();
                        });
                    }

                    long elapsedTime = System.currentTimeMillis() - startTime;
                    if (elapsedTime < 500) {
                        updateProgress(1, 1);
                        Thread.sleep(500L - elapsedTime);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
    }

    private List<File> getAllFilesFromDirectory(File directory) throws IOException {
        return Files.walk(Paths.get(directory.toURI()))
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .collect(Collectors.toList());
    }

    private void importItemAction(Pane root, Pane doubleClickPane, ScrollPane scroll, VBox v) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Files");
        fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("All Files", "*.*"));

        // Add a "Select Folder" button next to the file chooser
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Folder to Import");

        // Create a custom dialog that combines both options
        Dialog<List<File>> dialog = new Dialog<>();
        dialog.setTitle("Import Files or Folders");
        dialog.setHeaderText("Choose what to import:");

        // Create buttons for the dialog
        ButtonType selectFilesType = new ButtonType("Select Files", ButtonBar.ButtonData.LEFT);
        ButtonType selectFolderType = new ButtonType("Select Folder", ButtonBar.ButtonData.LEFT);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(selectFilesType, selectFolderType, cancelType);

        // Style the dialog
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("stylesheet.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog");

        // Handle the result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == selectFilesType) {
                List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);
                return selectedFiles;
            } else if (dialogButton == selectFolderType) {
                File selectedDir = dirChooser.showDialog(stage);
                if (selectedDir != null) {
                    try {
                        return getAllFilesFromDirectory(selectedDir);
                    } catch (IOException e) {
                        showErrorDialog("Error", "Failed to process folder: " + e.getMessage());
                    }
                }
            }
            return null;
        });

        // Process the selected files/folder
        Optional<List<File>> result = dialog.showAndWait();
        result.ifPresent(selectedFiles -> {
            if (selectedFiles != null && !selectedFiles.isEmpty()) {
                addFilePanesToDisplay(selectedFiles, root, scroll, doubleClickPane, v);
            }
        });
    }

    // Add method to filter files
    private boolean isValidFile(File file) {
        // Skip null, hidden, or system files
        if (file == null || file.isHidden()) {
            return false;
        }

        try {
            DosFileAttributes attrs = Files.readAttributes(file.toPath(), DosFileAttributes.class);
            if (attrs.isHidden() || attrs.isSystem()) {
                return false;
            }
        } catch (IOException e) {
            // If we can't read attributes, skip the file
            return false;
        }

        // Skip temporary files and specific extensions
        String name = file.getName().toLowerCase();
        if (name.startsWith("~") || name.startsWith(".")) {
            return false;
        }

        return !name.endsWith(".tmp") &&
                !name.endsWith(".aegis") &&  // Skip already processed files
                !name.endsWith(".lnk");      // Skip shortcuts
    }

    private void clearItemAction(Pane root, Pane doubleClickPane, ScrollPane scroll, VBox v) {
        if (root.getChildren().contains(scroll)) {
            files.clear();
            fileTable.getItems().clear();
            fileTable.getSelectionModel().clearSelection();

            root.getChildren().remove(scroll);
            root.getChildren().add(doubleClickPane);

            doubleClickPane.toBack();
            updateMenuStates();
        }
    }

    private void aboutItemAction() {
        Alert dialog = new Alert(AlertType.INFORMATION);
        dialog.setTitle("About Aegis");
        dialog.setHeaderText("");
        dialog.setContentText("Aegis is a file encryption program originally written by Preston Tang in July 2021. This is the second version of Aegis with added functionality.\n\nFor more information, please visit https://github.com/Retopia/Aegis");
        dialog.getDialogPane().getStylesheets().add(this.getClass().getResource("stylesheet.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog");
        setDialogIcon(dialog);

        // Spawns the dialog in the middle of the screen
        final Window window = dialog.getDialogPane().getScene().getWindow();
        window.addEventHandler(WindowEvent.WINDOW_SHOWN, new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                window.setX((dimensions.getWidth() - window.getWidth()) / 2);
                window.setY((dimensions.getHeight() - window.getHeight()) / 2);
            }
        });

        dialog.showAndWait();
    }

    private void encryptItemAction() {
        // Remove the old FilePane style reset as it's no longer needed

        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("Encryption Window");
        dialog.setHeaderText("");
        dialog.setContentText("Enter password: ");
        dialog.getDialogPane().getStylesheets().add(this.getClass().getResource("stylesheet.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog");
        setDialogIcon(dialog);

        // Center the dialog
        final Window window = dialog.getDialogPane().getScene().getWindow();
        window.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> {
            window.setX((dimensions.getWidth() - window.getWidth()) / 2);
            window.setY((dimensions.getHeight() - window.getHeight()) / 2);
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(password -> displayProgressDialog(true, password));
    }

    private void decryptItemAction() {
        // Remove the old FilePane style reset as it's no longer needed

        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("Decryption Window");
        dialog.setHeaderText("");
        dialog.setContentText("Enter password: ");
        dialog.getDialogPane().getStylesheets().add(this.getClass().getResource("stylesheet.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog");
        setDialogIcon(dialog);

        // Center the dialog
        final Window window = dialog.getDialogPane().getScene().getWindow();
        window.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> {
            window.setX((dimensions.getWidth() - window.getWidth()) / 2);
            window.setY((dimensions.getHeight() - window.getHeight()) / 2);
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(password -> displayProgressDialog(false, password));
    }

    // Add drag and drop support
    private void setupDragAndDrop(Pane root, Pane doubleClickPane, ScrollPane scroll) {
        // Use AtomicBoolean to track if we're currently showing the drag effect
        final AtomicBoolean isDragging = new AtomicBoolean(false);

        root.setOnDragOver(event -> {
            if (event.getGestureSource() != root && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);

                // Only apply the style if we haven't already
                if (!isDragging.get()) {
                    isDragging.set(true);
                    root.setStyle("-fx-border-color: #0096c9; -fx-border-width: 2; -fx-border-style: dashed;");
                }
            }
            event.consume();
        });

        root.setOnDragExited(event -> {
            isDragging.set(false);
            root.setStyle("");
            event.consume();
        });

        root.setOnDragDropped(event -> {
            isDragging.set(false);
            root.setStyle("");
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles()) {
                success = true;
                try {
                    // Pre-process files to get total count
                    List<File> toProcess = new ArrayList<>();
                    for (File file : db.getFiles()) {
                        if (file.isDirectory()) {
                            List<File> dirFiles = Files.walk(file.toPath())
                                    .filter(Files::isRegularFile)
                                    .filter(p -> isValidFile(p.toFile()))
                                    .map(Path::toFile)
                                    .collect(Collectors.toList());
                            toProcess.addAll(dirFiles);
                        } else if (isValidFile(file)) {
                            toProcess.add(file);
                        }
                    }

                    if (!toProcess.isEmpty()) {
                        // Use existing addFilePanesToDisplay which already handles duplicates
                        addFilePanesToDisplay(toProcess, root, scroll, doubleClickPane, null);
                    } else {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            setDialogIcon(alert);
                            alert.setTitle("No Valid Files");
                            alert.setHeaderText(null);
                            alert.setContentText("No valid files found to import.");
                            alert.getDialogPane().getStylesheets().add(
                                    getClass().getResource("stylesheet.css").toExternalForm());
                            alert.showAndWait();
                        });
                    }
                } catch (IOException e) {
                    Platform.runLater(() ->
                            showErrorDialog("Error", "Failed to process dropped files: " + e.getMessage())
                    );
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void setupFileTable() {
        // Ensure we're on the JavaFX Application Thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::setupFileTable);
            return;
        }

        // Clear existing columns
        fileTable.getColumns().clear();

        // Name column (45%)
        TableColumn<FileStatus, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFile().getName()));
        nameColumn.setCellFactory(column -> new TableCell<FileStatus, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-font-size: 15px;");
                setText(empty ? null : item);
            }
        });
        // Add case-insensitive sorting for names
        nameColumn.setComparator((s1, s2) -> {
            if (s1 == null && s2 == null) return 0;
            if (s1 == null) return -1;
            if (s2 == null) return 1;
            return s1.toLowerCase().compareTo(s2.toLowerCase());
        });

        // Size column (20%)
        TableColumn<FileStatus, Long> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(data -> {
            if (data.getValue() != null && data.getValue().getFile() != null) {
                return new SimpleObjectProperty<>(data.getValue().getFile().length());
            }
            return new SimpleObjectProperty<>(0L);
        });
        sizeColumn.setCellFactory(column -> new TableCell<FileStatus, Long>() {
            @Override
            protected void updateItem(Long size, boolean empty) {
                super.updateItem(size, empty);
                setStyle("-fx-font-size: 15px; -fx-alignment: CENTER-RIGHT;");
                setText(empty || size == null ? null : formatFileSize(size));
            }
        });

        // Date column (25%)
        TableColumn<FileStatus, Long> dateColumn = new TableColumn<>("Last Modified");
        dateColumn.setCellValueFactory(data -> {
            if (data.getValue() != null && data.getValue().getFile() != null) {
                return new SimpleObjectProperty<>(data.getValue().getFile().lastModified());
            }
            return new SimpleObjectProperty<>(0L);
        });
        dateColumn.setCellFactory(column -> new TableCell<FileStatus, Long>() {
            @Override
            protected void updateItem(Long timestamp, boolean empty) {
                super.updateItem(timestamp, empty);
                setStyle("-fx-font-size: 15px; -fx-alignment: CENTER;");
                if (empty || timestamp == null) {
                    setText(null);
                } else {
                    setText(new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm")
                            .format(new java.util.Date(timestamp)));
                }
            }
        });

        // Status column (10%)
        TableColumn<FileStatus, Boolean> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data ->
                new SimpleObjectProperty<>(data.getValue() != null && data.getValue().isSuccess()));
        statusColumn.setCellFactory(column -> new TableCell<FileStatus, Boolean>() {
            @Override
            protected void updateItem(Boolean success, boolean empty) {
                super.updateItem(success, empty);
                if (empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(success ? "✓" : "");
                    setStyle(success ?
                            "-fx-text-fill: green; -fx-font-weight: bold; -fx-font-size: 15px; -fx-alignment: CENTER;" :
                            "-fx-font-size: 15px; -fx-alignment: CENTER;");
                }
            }
        });

        // Set up column widths using a custom resize policy
        fileTable.setColumnResizePolicy(param -> {
            double width = param.getTable().getWidth();
            nameColumn.setPrefWidth(width * 0.45);
            sizeColumn.setPrefWidth(width * 0.20);
            dateColumn.setPrefWidth(width * 0.25);
            statusColumn.setPrefWidth(width * 0.10);
            return true;
        });

        fileTable.getColumns().addAll(nameColumn, sizeColumn, dateColumn, statusColumn);

        // Create an observable list backed by our ArrayList
        fileTable.setItems(FXCollections.observableArrayList(files));

        // Enable multiple selection
        TableView.TableViewSelectionModel<FileStatus> selectionModel = fileTable.getSelectionModel();
        selectionModel.setSelectionMode(SelectionMode.MULTIPLE);

        // Listen for changes in the items list
        fileTable.getItems().addListener((ListChangeListener<FileStatus>) c -> {
            while (c.next()) {
                if (c.wasRemoved()) {
                    selectionModel.clearSelection();
                }
            }
        });

        // Add key handler for delete/backspace
        fileTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                removeSelectedItems();
            }
        });

        fileTable.setRowFactory(tv -> {
            TableRow<FileStatus> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showFilePreview(row.getItem());
                }
            });
            return row;
        });

        // Context menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem removeItem = new MenuItem("Remove Selected");
        removeItem.setOnAction(e -> removeSelectedItems());
        MenuItem removeAllItem = new MenuItem("Remove All");
        removeAllItem.setOnAction(e -> removeAllItems());
        contextMenu.getItems().addAll(removeItem, removeAllItem);
        fileTable.setContextMenu(contextMenu);

        // Make table fill available space
        fileTable.setMinWidth(400);

        Platform.runLater(() -> {
            if (stage != null) {
                fileTable.prefWidthProperty().bind(stage.widthProperty());
                fileTable.prefHeightProperty().bind(stage.heightProperty());
            }
        });

        // Apply CSS styling
        fileTable.setStyle("-fx-table-cell-border-color: transparent;");
        fileTable.getStyleClass().add("styled-table");
    }

    // Improved thread-safe version of addFilePanesToDisplay
    private void addFilePanesToDisplay(List<File> importedFiles, Pane root, ScrollPane scroll, Pane doubleClickPane, VBox v) {
        // Initial UI setup
        Platform.runLater(() -> {
            if (root.getChildren().contains(doubleClickPane)) {
                root.getChildren().remove(doubleClickPane);
                root.getChildren().add(scroll);
                scroll.setContent(fileTable);
                scroll.setFitToWidth(true);
                scroll.setFitToHeight(true);
            }
        });

        Task<DuplicateCheckResult> loadFilesTask = new Task<DuplicateCheckResult>() {
            @Override
            protected DuplicateCheckResult call() throws Exception {
                List<FileStatus> newFiles = new ArrayList<>();
                List<DuplicateEntry> duplicates = new ArrayList<>();
                int totalFiles = importedFiles.size();

                for (int i = 0; i < importedFiles.size(); i++) {
                    File file = importedFiles.get(i);

                    // Check for duplicates
                    Optional<FileStatus> existingFile = files.stream()
                            .filter(status -> status.getFile().getAbsolutePath().equals(file.getAbsolutePath()))
                            .findFirst();

                    if (existingFile.isPresent()) {
                        duplicates.add(new DuplicateEntry(file, existingFile.get().getFile()));
                    } else {
                        FileStatus status = new FileStatus(file);
                        newFiles.add(status);
                    }

                    updateProgress(i + 1, totalFiles);
                }

                return new DuplicateCheckResult(newFiles, duplicates);
            }
        };

        // Create and show progress indicator
        ProgressIndicator progress = new ProgressIndicator();
        progress.progressProperty().bind(loadFilesTask.progressProperty());

        // Add progress indicator to the scene
        Platform.runLater(() -> {
            progress.setLayoutX((root.getWidth() - progress.getWidth()) / 2);
            progress.setLayoutY((root.getHeight() - progress.getHeight()) / 2);
            root.getChildren().add(progress);
        });

        loadFilesTask.setOnSucceeded(event -> {
            Platform.runLater(() -> {
                DuplicateCheckResult result = loadFilesTask.getValue();

                if (!result.getDuplicates().isEmpty()) {
                    showDuplicatesDialog(result.getDuplicates());
                }

                List<FileStatus> newFiles = result.getNewFiles();
                if (!newFiles.isEmpty()) {
                    files.addAll(newFiles);
                    fileTable.setItems(FXCollections.observableArrayList(files));
                    if (!fileTable.getSortOrder().isEmpty()) {
                        TableColumn<FileStatus, ?> sortColumn = fileTable.getSortOrder().get(0);
                        fileTable.getSortOrder().clear();
                        fileTable.getSortOrder().add(sortColumn);
                    }
                }

                root.getChildren().remove(progress);
                updateMenuStates();
            });
        });

        // Handle loading failures
        loadFilesTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                Throwable ex = loadFilesTask.getException();
                ex.printStackTrace();
                showErrorDialog("Error Loading Files", "An error occurred while loading files: " + ex.getMessage());
                root.getChildren().remove(progress);
            });
        });

        // Start the loading task
        Thread thread = new Thread(loadFilesTask);
        thread.setDaemon(true);
        thread.start();
    }

    // Helper method to format file sizes
    private String formatFileSize(long size) {
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int unitIndex = 0;
        double sizeInUnit = size;

        while (sizeInUnit >= 1024 && unitIndex < units.length - 1) {
            sizeInUnit /= 1024;
            unitIndex++;
        }

        return String.format("%.2f %s", sizeInUnit, units[unitIndex]);
    }

    private void showErrorDialog(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showDuplicatesDialog(List<DuplicateEntry> duplicates) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        setDialogIcon(alert);
        alert.setTitle("Duplicate Files Detected");
        alert.setHeaderText(String.format("Found %d duplicate file%s:",
                duplicates.size(), duplicates.size() == 1 ? "" : "s"));

        // Create a text area for the duplicate file details
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxHeight(200);

        // Build the duplicate files message
        StringBuilder message = new StringBuilder();
        for (DuplicateEntry entry : duplicates) {
            message.append("• ").append(entry.getAttemptedFile().getName())
                    .append("\n  Already exists as: ")
                    .append(entry.getExistingFile().getAbsolutePath())
                    .append("\n\n");
        }
        textArea.setText(message.toString());
        textArea.setStyle("-fx-control-inner-background: #333333; -fx-text-fill: #ffffff;");

        // Configure the dialog
        alert.getDialogPane().setContent(textArea);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("stylesheet.css").toExternalForm());

        // Make the dialog resizable
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.setResizable(true);

        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
