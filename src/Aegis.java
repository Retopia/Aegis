import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.application.Application;

import static java.lang.Thread.sleep;
import static javafx.application.Application.launch;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.*;
import javafx.stage.FileChooser.ExtensionFilter;
import org.controlsfx.dialog.ProgressDialog;

/**
 * @author Preston Tang
 */
public class Aegis extends Application {

    private ArrayList<FilePane> files = new ArrayList<>();
    private ArrayList<HBox> boxes = new ArrayList<>();

    private Stage stage;

    private Rectangle2D dimensions = Screen.getPrimary().getVisualBounds();

    @Override
    public void start(Stage stage) {
        // Initializing and setting the locations for the UI elements
        Pane root = new Pane();

        MenuBar menu = new MenuBar();
        menu.prefWidthProperty().bind(root.widthProperty());
        Menu fileMenu = new Menu("File");
        Menu helpMenu = new Menu("Help");
        MenuItem importItem = new MenuItem("Import File(s)");
        MenuItem encryptItem = new MenuItem("Encrypt All");
        MenuItem decryptItem = new MenuItem("Decrypt All");
        MenuItem clearItem = new MenuItem("Clear Item(s)");
        MenuItem aboutItem = new MenuItem("About");
        fileMenu.getItems().addAll(importItem, encryptItem, decryptItem, clearItem);
        helpMenu.getItems().add(aboutItem);

        menu.getMenus().addAll(fileMenu, helpMenu);

        Label initText = new Label("Start by drag and dropping files here, double clicking, or clicking \"Import Files\" to get started");
        initText.translateXProperty().bind(root.widthProperty().subtract(initText.widthProperty()).divide(2));
        initText.translateYProperty().bind(root.heightProperty().subtract(initText.heightProperty()).divide(2));
        initText.setFont(new Font(20));

        ScrollPane scroll = new ScrollPane();
        scroll.translateYProperty().bind(menu.heightProperty());
        scroll.prefHeightProperty().bind(root.heightProperty().subtract(menu.heightProperty()));
        scroll.prefWidthProperty().bind(root.widthProperty());
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox v = new VBox(5);
        HBox h = new HBox(5);

        v.getChildren().add(h);
        boxes.add(h);

        Scene scene = new Scene(root, dimensions.getMaxX() * 0.75, dimensions.getMaxY() * 0.75);
        scene.getStylesheets().add(this.getClass().getResource("stylesheet.css").toExternalForm());

        stage.setTitle("Aegis V1.1");
        stage.setScene(scene);
        stage.getIcons().add(new Image(this.getClass().getResource("tempicon.png").toExternalForm()));
        stage.show();
        this.stage = stage;

        Pane doubleClickPane = new Pane();
        // Opens import file dialog when a double click occurs
        doubleClickPane.setOnMouseClicked(event -> {
            if(event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
                importItemAction(root, doubleClickPane, scroll, v);
            }
        });
        doubleClickPane.getChildren().add(initText);

        // Initializes action for "Import File(s)" menu item
        importItem.setOnAction(e -> {
            importItemAction(root, doubleClickPane, scroll, v);
        });

        root.getChildren().addAll(doubleClickPane, menu);

        // Initializes action for "Encrypt" menu item
        encryptItem.setOnAction(e -> {
            encryptItemAction();
        });

        // Initializes action for "Decrypt" menu item
        decryptItem.setOnAction(e -> {
            decryptItemAction();
        });

        // Initializes action for "Clear Item(s)" menu item
        clearItem.setOnAction(e -> {
            clearItemAction(root, doubleClickPane, scroll, v);
        });

        // Initializes action for "About" menu item
        aboutItem.setOnAction(e -> {
            aboutItemAction();
        });

        // Consumes horizontal scroll event since it's off by a little
        scroll.addEventFilter(ScrollEvent.SCROLL, (ScrollEvent event) -> {
            if (event.getDeltaX() != 0) {
                event.consume();
            }
        });

        // First part of file drag and drop code
        root.setOnDragOver((DragEvent event) -> {
            if (event.getGestureSource() != root
                    && event.getDragboard().hasFiles()) {
                // Allows for both copying and moving
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        // Second part of file drag and drop code
        root.setOnDragDropped((DragEvent event) -> {
            dragDropPart2Action(event, root, doubleClickPane, scroll, v);
        });

        // Handles the resizing of UI elements
        ChangeListener<Number> stageSizeListener = (observable, oldValue, newValue) -> {
            initText.translateXProperty().bind(root.widthProperty().subtract(initText.widthProperty()).divide(2));
            initText.translateYProperty().bind(root.heightProperty().subtract(initText.heightProperty()).divide(2));

            menu.prefWidthProperty().bind(root.widthProperty());

            for (FilePane fp : files) {
                fp.prefHeightProperty().bind(scroll.heightProperty().divide(3));
                fp.prefWidthProperty().bind(scroll.widthProperty().subtract(15 + 16).divide(4));
            }
        };

        stage.widthProperty().addListener(stageSizeListener);
        stage.heightProperty().addListener(stageSizeListener);
    }

    private void processFile(String password, boolean isEncryption, long startTime) {
        MultiProgressDialog dialog = new MultiProgressDialog(stage);
        // Create the tasks
        Task task1 = new Task() {
            @Override
            protected Object call() throws Exception {
                String process = isEncryption ? "Encrypting " : "Decrypting ";

                // Perform some task here and update the progress and message properties
                for (int i = 0; i < files.size(); i++) {
                    File original = files.get(i).getFile();
                    File aegisFile = new File(original.getParent(), original.getName() + ".aegis");
                    updateProgress(i, files.size() - 1);
                    updateMessage(process + original.getName());

                    // Start task 2 and bind its progress to task 1's progress bar
                    Task task2 = new Task() {
                        @Override
                        protected Object call() throws Exception {
                            // Perform some task here and update the progress and message properties
                            if (isEncryption) {
                                while (aegisFile.length() < original.length()) {
                                    updateProgress(aegisFile.length(), original.length());
                                    updateMessage(aegisFile.length() + "/" + original.length() + " bytes processed");
                                }

                                updateProgress(original.length(), original.length());
                                updateMessage(original.length() + "/" + original.length() + " bytes processed");
                                return null;
                            } else {
                                while (original.length() < aegisFile.length()) {
                                    updateProgress(original.length(), aegisFile.length());
                                    updateMessage(original.length() + "/" + aegisFile.length() + " bytes processed");
                                }

                                updateProgress(aegisFile.length(), aegisFile.length());
                                updateMessage(aegisFile.length() + "/" + aegisFile.length() + " bytes processed");
                                return null;
                            }
                        }
                    };

                    final int counter = i;
                    // Bind the progress and message properties of task2 to the progress bar and label in the MultiProgressDialog
                    Platform.runLater(() -> {
                        boolean processResult = AES.processFile(original, aegisFile, password, isEncryption);
                        AES.secureDelete(original, aegisFile, processResult);
                        files.get(counter).setSuccess(processResult);
                        dialog.getProgressBar2().progressProperty().bind(task2.progressProperty());
                        dialog.getLabel2().textProperty().bind(task2.messageProperty());
                    });

                    // Start the task2 thread and wait for it to finish before continuing the loop in task1
                    Thread task2Thread = new Thread(task2);
                    task2Thread.start();
                    task2Thread.join();
                }
                return null;
            }
        };

        // Bind the progress and message properties of task1 to the progress bar and label in the MultiProgressDialog
        dialog.getLabel1().textProperty().bind(task1.messageProperty());
        dialog.getProgressBar1().progressProperty().bind(task1.progressProperty());

        // Start the task1 thread
        Thread process = new Thread(task1);
        process.setDaemon(true);
        process.start();

        if (!process.isAlive()) {
            dialog.close();
        }
    }

    private boolean checkDuplicates(File f) {
        boolean result = false;
        for (FilePane fp : files) {
            if (f.getAbsolutePath().equals(fp.getFile().getAbsolutePath())) {
                result = true;
            }
        }
        return result;
    }

    private void importItemAction(Pane root, Pane doubleClickPane, ScrollPane scroll, VBox v) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("All Files", "*.*"));
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);

        // If the user has selected 1 or more files
        if (selectedFiles != null) {
            addFilePanesToDisplay(selectedFiles, root, scroll, doubleClickPane, v);
        }
    }

    private void encryptItemAction() {
        for (FilePane fp : files) {
            // Adds back default effect for every FilePane
            fp.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 10, 0, 0, 0);");
        }

        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("Encryption Window");
        dialog.setHeaderText("");
        dialog.setContentText("Enter password: ");
        dialog.getDialogPane().getStylesheets().add(this.getClass().getResource("stylesheet.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog");

        // Spawns the dialog in the middle of the screen
        final Window window = dialog.getDialogPane().getScene().getWindow();
        window.addEventHandler(WindowEvent.WINDOW_SHOWN, new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                window.setX((dimensions.getWidth() - window.getWidth()) / 2);
                window.setY((dimensions.getHeight() - window.getHeight()) / 2);
            }
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(password -> {
            // Show progress bar
            processFile(password, true, System.currentTimeMillis());
        });
    }

    private void decryptItemAction() {
        for (FilePane fp : files) {
            // Adds back default effect for every FilePane
            fp.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 10, 0, 0, 0);");
        }

        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("Decryption Window");
        dialog.setHeaderText("");
        dialog.setContentText("Enter password: ");
        dialog.getDialogPane().getStylesheets().add(this.getClass().getResource("stylesheet.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog");

        // Spawns the dialog in the middle of the screen
        final Window window = dialog.getDialogPane().getScene().getWindow();
        window.addEventHandler(WindowEvent.WINDOW_SHOWN, new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                window.setX((dimensions.getWidth() - window.getWidth()) / 2);
                window.setY((dimensions.getHeight() - window.getHeight()) / 2);
            }
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(password -> {
            // Show progress bar
            processFile(password, false, System.currentTimeMillis());
        });
    }

    private void clearItemAction(Pane root, Pane doubleClickPane, ScrollPane scroll, VBox v) {
        if (root.getChildren().contains(scroll)) {
            files.clear();
            boxes.clear();
            v.getChildren().clear();
            root.getChildren().remove(scroll);
            root.getChildren().add(doubleClickPane);
            doubleClickPane.toBack();
        }
    }

    private void aboutItemAction() {
        Alert dialog = new Alert(AlertType.INFORMATION);
        dialog.setTitle("About Aegis");
        dialog.setHeaderText("");
        dialog.setContentText("Aegis is a file encryption program written by Preston Tang in July 2021.\n\nFor more information, please visit https://github.com/Retopia/Aegis");
        dialog.getDialogPane().getStylesheets().add(this.getClass().getResource("stylesheet.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog");

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

    private void dragDropPart2Action(DragEvent event, Pane root, Pane doubleClickPane, ScrollPane scroll, VBox v) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            success = true;
            addFilePanesToDisplay(db.getFiles(), root, scroll, doubleClickPane, v);
        }

        // Let the source know whether the string was successfullytransferred and used
        event.setDropCompleted(success);
        event.consume();
    }

    private void addFilePanesToDisplay(List<File> importedFiles, Pane root, ScrollPane scroll, Pane doubleClickPane, VBox v) {
        if (root.getChildren().contains(doubleClickPane)) {
            root.getChildren().remove(doubleClickPane);
            root.getChildren().add(scroll);
            scroll.setContent(v);
        }

        for (File f : importedFiles) {
            if (!checkDuplicates(f)) {
                FilePane fp = new FilePane(f);
                fp.prefHeightProperty().bind(root.heightProperty().divide(3));
                fp.prefWidthProperty().bind(root.widthProperty().subtract(15 + 16).divide(4));

                fp.getButton().setOnAction(e -> {
                    files.remove(fp);

                    boxes.clear();
                    v.getChildren().clear();

                    // Add the initial HBox
                    HBox hb = new HBox(5);

                    v.getChildren().add(hb);
                    boxes.add(hb);

                    // Relocate elements
                    // Add the boxes back
                    while (boxes.size() <= (int) files.size() / 4) {
                        HBox box = new HBox(5);
                        boxes.add(box);
                        v.getChildren().add(box);
                    }

                    for (int i = 0; i < files.size(); i++) {
                        boxes.get((int) (i / 4)).getChildren().add(files.get(i));
                    }

                    // The last one is being deleted
                    // Back to default text
                    if (files.isEmpty()) {
                        root.getChildren().remove(scroll);
                        root.getChildren().add(doubleClickPane);
                        doubleClickPane.toBack();
                    }
                });

                files.add(fp);

                if (boxes.size() <= (int) files.size() / 4) {
                    HBox box = new HBox(5);
                    boxes.add(box);
                    v.getChildren().add(box);
                }

                boxes.get((int) ((files.size() - 1) / 4)).getChildren().add(fp);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
