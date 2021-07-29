package aegis;

import java.io.File;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 *
 * @author Preston Tang
 */
public class FilePane extends Pane {

    private File file;
    private Label icon;
    private Label title;
    private Button close;
    private boolean success;

    public FilePane(File file) {
        this.file = file;
        this.success = false;

        String path = file.getAbsolutePath();
        if (file.isDirectory()) {
            icon = new Label("folder");
        } else if (path.contains(".")) {
            icon = new Label(path.substring(path.lastIndexOf(".") + 1));
        } else {
            icon = new Label("N/A");
        }

        icon.setFont(Font.font("System", FontWeight.BOLD, 35));
        icon.setText(icon.getText().toUpperCase());

        title = new Label(file.getName());
        close = new Button("X");
        close.setStyle("-fx-background-color: transparent");
        close.setFont(new Font(16));

        super.getChildren().addAll(icon, title, close);

        icon.translateXProperty().bind(this.widthProperty().subtract(icon.widthProperty()).divide(2));
        icon.translateYProperty().bind(this.heightProperty().subtract(icon.heightProperty()).divide(2.8));

        title.translateXProperty().bind(this.widthProperty().subtract(title.widthProperty()).divide(2));
        title.translateYProperty().bind(this.heightProperty().subtract(title.heightProperty()).divide(1.3));

        close.translateXProperty().bind(this.widthProperty().subtract(close.widthProperty()).divide(1.05));
        close.translateYProperty().bind(this.heightProperty().subtract(close.heightProperty()).divide(20));

        super.getStyleClass().add("filePane");
    }

    public Button getButton() {
        return close;
    }

    public File getFile() {
        return file;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;

        if (success) {
            super.setStyle("-fx-effect: innershadow(three-pass-box, rgba(0,255,0,0.8), 10, 0, 0, 0);");
        } else {
            super.setStyle("-fx-effect: innershadow(three-pass-box, rgba(255,0,0,0.8), 10, 0, 0, 0);");
        }
    }

}
