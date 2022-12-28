import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class MultiProgressDialog {
    private final Stage stage;
    private final Task<?> task;
    private final Label label1;
    private final Label label2;
    private final ProgressBar progressBar1;
    private final ProgressBar progressBar2;

    public MultiProgressDialog(Task<?> task) {
        this.task = task;
        this.stage = new Stage();
        this.label1 = new Label();
        this.label2 = new Label();
        this.progressBar1 = new ProgressBar();
        this.progressBar2 = new ProgressBar();

        label1.textProperty().bind(task.messageProperty());
        label2.textProperty().bind(task.messageProperty());
        progressBar1.progressProperty().bind(task.progressProperty());
        progressBar2.progressProperty().bind(task.progressProperty());

        HBox hBox1 = new HBox(5, label1, progressBar1);
        HBox hBox2 = new HBox(5, label2, progressBar2);
        hBox1.setAlignment(Pos.CENTER_LEFT);
        hBox2.setAlignment(Pos.CENTER_LEFT);

        VBox vBox = new VBox(10, hBox1, hBox2);
        vBox.setPadding(new Insets(10));
        vBox.setAlignment(Pos.CENTER);

        Scene scene = new Scene(vBox);
        stage.setScene(scene);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
    }

    public void show() {
        new Thread(task).start();
        stage.showAndWait();
    }

    public void setTitle(String title) {
        stage.setTitle(title);
    }

    public void initOwner(Window window) {
        stage.initOwner(window);
    }

    public DoubleProperty progress1Property() {
        return progressBar1.progressProperty();
    }

    public DoubleProperty progress2Property() {
        return progressBar2.progressProperty();
    }

    public StringProperty message1Property() {
        return label1.textProperty();
    }

    public StringProperty message2Property() {
        return label2.textProperty();
    }
}
