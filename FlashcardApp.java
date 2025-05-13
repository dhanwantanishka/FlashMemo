import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class FlashcardApp extends Application {
    private ObservableList<Flashcard> flashcards = FXCollections.observableArrayList();
    private LineChart<String, Number> progressChart;
    private XYChart.Series<String, Number> updateSeries = new XYChart.Series<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final String DATA_FILE = "flashcards.dat";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        loadData();
        setupUI(primaryStage);
    }

    private void setupUI(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f0f4f8;");

        // Input Section
        GridPane inputPanel = createInputPanel();
        root.setTop(inputPanel);

        // Flashcard List
        ListView<Flashcard> listView = createListView();
        root.setCenter(listView);

        // Progress Chart
        BorderPane chartContainer = createChartPanel();
        root.setBottom(chartContainer);

        Scene scene = new Scene(root, 800, 600);
        stage.setTitle("Smart Flashcard Manager");
        stage.setScene(scene);
        stage.show();
    }

    private GridPane createInputPanel() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.setStyle("-fx-background-color: #ffffff; -fx-border-radius: 5; -fx-border-color: #d1d8e0;");

        TextField questionField = new TextField();
        questionField.setPromptText("Enter question");
        TextArea answerField = new TextArea();
        answerField.setPromptText("Enter answer");
        answerField.setPrefRowCount(3);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setVisible(false);

        Button addBtn = new Button("Add Flashcard");
        addBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addBtn.setOnAction(e -> {
            errorLabel.setVisible(false);
            String question = questionField.getText().trim();
            String answer = answerField.getText().trim();
            
            if (question.isEmpty() || answer.isEmpty()) {
                errorLabel.setText("Both question and answer are required");
                errorLabel.setVisible(true);
                return;
            }
            
            try {
                Flashcard card = new Flashcard(question, answer);
                flashcards.add(card);
                questionField.clear();
                answerField.clear();
                updateChart();
                saveData();
            } catch (Exception ex) {
                errorLabel.setText("Error adding flashcard: " + ex.getMessage());
                errorLabel.setVisible(true);
            }
        });

        grid.add(new Label("Question:"), 0, 0);
        grid.add(questionField, 1, 0);
        grid.add(new Label("Answer:"), 0, 1);
        grid.add(answerField, 1, 1);
        grid.add(errorLabel, 1, 2);
        grid.add(addBtn, 1, 3);
        return grid;
    }

    private ListView<Flashcard> createListView() {
        ListView<Flashcard> listView = new ListView<>(flashcards);
        listView.setCellFactory(lv -> new ListCell<Flashcard>() {
            @Override
            protected void updateItem(Flashcard item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%s\n%s", item.getQuestion(), item.getAnswer()));
                }
            }
        });

        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("Edit");
        MenuItem deleteItem = new MenuItem("Delete");
        
        editItem.setOnAction(e -> {
            Flashcard selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) showEditDialog(selected);
        });
        
        deleteItem.setOnAction(e -> {
            Flashcard selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                flashcards.remove(selected);
                updateChart();
                saveData();
            }
        });

        contextMenu.getItems().addAll(editItem, deleteItem);
        listView.setContextMenu(contextMenu);
        return listView;
    }

    private BorderPane createChartPanel() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(15));
        pane.setStyle("-fx-background-color: #ffffff; -fx-border-radius: 5; -fx-border-color: #d1d8e0;");

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        progressChart = new LineChart<>(xAxis, yAxis);
        progressChart.setTitle("Flashcard Update Frequency");
        progressChart.getData().add(updateSeries);
        updateChart();

        pane.setCenter(progressChart);
        return pane;
    }

    private void showEditDialog(Flashcard card) {
        Dialog<Flashcard> dialog = new Dialog<>();
        dialog.setTitle("Edit Flashcard");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField questionField = new TextField(card.getQuestion());
        TextArea answerField = new TextArea(card.getAnswer());
        answerField.setPrefRowCount(3);

        grid.add(new Label("Question:"), 0, 0);
        grid.add(questionField, 1, 0);
        grid.add(new Label("Answer:"), 0, 1);
        grid.add(answerField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                card.setQuestion(questionField.getText());
                card.setAnswer(answerField.getText());
                card.recordUpdate();
                updateChart();
                saveData();
                return card;
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void updateChart() {
        Map<String, Long> dateCounts = flashcards.stream()
            .flatMap(card -> card.getUpdateDates().stream())
            .collect(Collectors.groupingBy(
                date -> date.format(formatter),
                Collectors.counting()
            ));

        updateSeries.getData().clear();
        dateCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> updateSeries.getData().add(
                new XYChart.Data<>(entry.getKey(), entry.getValue())
            ));
    }

    private void saveData() {
        File backupFile = new File(DATA_FILE + ".backup");
        File dataFile = new File(DATA_FILE);
        
        // Create backup of existing file
        if (dataFile.exists()) {
            try {
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                showAlert("Warning: Could not create backup file: " + e.getMessage());
            }
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(dataFile)))) {
            oos.writeObject(new ArrayList<>(flashcards));
        } catch (IOException e) {
            showAlert("Error saving data: " + e.getMessage());
            // Restore from backup if save failed
            if (backupFile.exists()) {
                try {
                    Files.copy(backupFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    showAlert("Data restored from backup file.");
                } catch (IOException ex) {
                    showAlert("Critical error: Could not restore from backup: " + ex.getMessage());
                }
            }
        }
    }

    private void loadData() {
        Path dataPath = Paths.get(DATA_FILE);
        Path backupPath = Paths.get(DATA_FILE + ".backup");
        
        if (!Files.exists(dataPath) && !Files.exists(backupPath)) {
            return; // No data to load
        }

        // Try loading from main file first
        boolean loadedMain = tryLoadData(dataPath);
        
        // If main file failed, try backup
        if (!loadedMain && Files.exists(backupPath)) {
            if (tryLoadData(backupPath)) {
                showAlert("Data restored from backup file.");
                // Save to main file immediately
                saveData();
            }
        }
    }

    private boolean tryLoadData(Path path) {
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            @SuppressWarnings("unchecked")
            ArrayList<Flashcard> loaded = (ArrayList<Flashcard>) ois.readObject();
            if (loaded != null) {
                flashcards.setAll(loaded);
                return true;
            }
        } catch (IOException | ClassNotFoundException e) {
            showAlert("Error loading data from " + path.getFileName() + ": " + e.getMessage());
        }
        return false;
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Flashcard Manager");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class Flashcard implements Serializable {
        private static final long serialVersionUID = 1L;
        private String question;
        private String answer;
        private List<LocalDate> updateDates;
        private transient static final int MAX_HISTORY = 100; // Limit update history

        public Flashcard(String question, String answer) {
            if (question == null || question.trim().isEmpty()) {
                throw new IllegalArgumentException("Question cannot be empty");
            }
            if (answer == null || answer.trim().isEmpty()) {
                throw new IllegalArgumentException("Answer cannot be empty");
            }
            this.question = question.trim();
            this.answer = answer.trim();
            this.updateDates = new ArrayList<>();
            recordUpdate();
        }

        public void recordUpdate() {
            if (updateDates == null) {
                updateDates = new ArrayList<>();
            }
            updateDates.add(LocalDate.now());
            // Keep only the last MAX_HISTORY updates
            if (updateDates.size() > MAX_HISTORY) {
                updateDates = new ArrayList<>(updateDates.subList(
                    updateDates.size() - MAX_HISTORY, 
                    updateDates.size()
                ));
            }
        }

        public String getQuestion() { 
            return question; 
        }
        
        public void setQuestion(String question) {
            if (question == null || question.trim().isEmpty()) {
                throw new IllegalArgumentException("Question cannot be empty");
            }
            this.question = question.trim();
            recordUpdate();
        }
        
        public String getAnswer() { 
            return answer; 
        }
        
        public void setAnswer(String answer) {
            if (answer == null || answer.trim().isEmpty()) {
                throw new IllegalArgumentException("Answer cannot be empty");
            }
            this.answer = answer.trim();
            recordUpdate();
        }
        
        public List<LocalDate> getUpdateDates() { 
            return Collections.unmodifiableList(updateDates); 
        }

        private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
            ois.defaultReadObject();
            if (updateDates == null) {
                updateDates = new ArrayList<>();
            }
        }
    }
}
