package aegis;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 *
 * @author Preston Tang
 */
public class Aegis extends Application {

    private ArrayList<FilePane> files = new ArrayList<>();
    private ArrayList<HBox> boxes = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        // For the initial size of the window
        Rectangle2D dimensions = Screen.getPrimary().getBounds();

        // Initializing and setting the locations for the UI elements
        Pane root = new Pane();

        MenuBar menu = new MenuBar();
        menu.prefWidthProperty().bind(root.widthProperty());
        Menu fileMenu = new Menu("File");
        Menu helpMenu = new Menu("Help");
        MenuItem importItem = new MenuItem("Import File(s)");
        MenuItem encryptItem = new MenuItem("Encrypt");
        MenuItem decryptItem = new MenuItem("Decrypt");
        MenuItem clearItem = new MenuItem("Clear Item(s)");
        MenuItem aboutItem = new MenuItem("About");
        fileMenu.getItems().addAll(importItem, encryptItem, decryptItem, clearItem);
        helpMenu.getItems().add(aboutItem);

        menu.getMenus().addAll(fileMenu, helpMenu);

        Label initText = new Label("Drag Files Here or Press \"Import Files\" to Get Started");
        initText.translateXProperty().bind(root.widthProperty().subtract(initText.widthProperty()).divide(2));
        initText.translateYProperty().bind(root.heightProperty().subtract(initText.heightProperty()).divide(2));
        initText.setFont(new Font(20));

        root.getChildren().addAll(menu, initText);

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

        stage.setTitle("Aegis - Security Application Developed in July 2021 by Preston Tang");
        stage.setScene(scene);
        stage.getIcons().add(new Image(this.getClass().getResource("tempicon.png").toExternalForm()));
        stage.show();

        // Initializes action for "Import File(s)" menu item
        importItem.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open Resource File");
            fileChooser.getExtensionFilters().addAll(
                    new ExtensionFilter("All Files", "*.*"));
            List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);
            if (!selectedFiles.isEmpty()) {
                root.getChildren().remove(initText);
                root.getChildren().add(scroll);
                scroll.setContent(v);

                for (File f : selectedFiles) {
                    if (!checkDuplicates(f)) {
                        FilePane fp = new FilePane(f);
                        fp.prefHeightProperty().bind(root.heightProperty().divide(3));
                        fp.prefWidthProperty().bind(root.widthProperty().subtract(15 + 16).divide(4));

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
        });

        // Initializes action for "Encrypt" menu item
        encryptItem.setOnAction(e -> {
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

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(password -> {
                for (FilePane fp : files) {
                    fp.setSuccess(AES.encryptFile(fp.getFile(), password));
                }
            });
        });

        // Initializes action for "Decrypt" menu item
        decryptItem.setOnAction(e -> {
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

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(password -> {
                for (FilePane fp : files) {
                    fp.setSuccess(AES.decryptFile(fp.getFile(), password));
                }
            });
        });

        // Initializes action for "Clear Item(s)" menu item
        clearItem.setOnAction(e -> {
            if (root.getChildren().contains(scroll)) {
                files.clear();
                boxes.clear();
                v.getChildren().clear();
                root.getChildren().remove(scroll);
                root.getChildren().add(initText);
            }
        });

        // Initializes action for "About" menu item
        aboutItem.setOnAction(e -> {
            Alert dialog = new Alert(AlertType.INFORMATION);
            dialog.setTitle("About Aegis");
            dialog.setHeaderText("");
            dialog.setContentText("Aegis is a file encryption program written by Preston Tang in July 2021.\n\nFor more information, please visit https://github.com/Retopia/Aegis");
            dialog.showAndWait();

            dialog.getDialogPane().getStylesheets().add(this.getClass().getResource("stylesheet.css").toExternalForm());
            dialog.getDialogPane().getStyleClass().add("dialog");
        });

        // Consumes horizontal scroll event cause it's off by a little
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
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;

                // Files have been dropped here
                if (root.getChildren().contains(initText)) {
                    root.getChildren().remove(initText);
                    root.getChildren().add(scroll);
                    scroll.setContent(v);
                }

                for (File f : db.getFiles()) {
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
                                root.getChildren().add(initText);
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

            /* let the source know whether the string was successfully
            * transferred and used */
            event.setDropCompleted(success);
            event.consume();
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

    private boolean checkDuplicates(File f) {
        boolean result = false;

        for (FilePane fp : files) {
            if (f.getAbsolutePath().equals(fp.getFile().getAbsolutePath())) {
                result = true;
            }
        }

        return result;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
