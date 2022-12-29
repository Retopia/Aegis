import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MultiProgressDialog {
    private Stage stage;
    private Label label1;
    private Label label2;
    private ProgressBar progressBar1;
    private ProgressBar progressBar2;

    public MultiProgressDialog(Stage owner) {
        // Initialize the stage
        stage = new Stage();
        stage.initStyle(StageStyle.DECORATED);
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.getIcons().add(new Image(this.getClass().getResource("tempicon.png").toExternalForm()));

        // Initialize the labels and progress bars
        label1 = new Label();
        label2 = new Label();
        progressBar1 = new ProgressBar();
        progressBar2 = new ProgressBar();

        // Set the indeterminate property to true for both progress bars
        progressBar1.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressBar2.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        // Add the labels and progress bars to a layout
        VBox vbox1 = new VBox(5, label1, progressBar1);
        vbox1.setAlignment(Pos.CENTER_LEFT);
        VBox vbox2 = new VBox(5, label2, progressBar2);
        vbox2.setAlignment(Pos.CENTER_LEFT);
        VBox vbox = new VBox(5, vbox1, vbox2);

        // Set the scene and show the stage
        Scene scene = new Scene(vbox);
        scene.getStylesheets().add(this.getClass().getResource("stylesheet.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    public Label getLabel1() {
        return label1;
    }

    public Label getLabel2() {
        return label2;
    }

    public ProgressBar getProgressBar1() {
        return progressBar1;
    }

    public ProgressBar getProgressBar2() {
        return progressBar2;
    }

    public void close() {
        // Close the stage on the JavaFX application thread
        Platform.runLater(() -> stage.close());
    }
}