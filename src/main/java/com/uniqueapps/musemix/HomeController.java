package com.uniqueapps.musemix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import javax.sound.midi.Instrument;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.MetaMessage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.ResourceBundle;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class HomeController implements Initializable, EventHandler<MouseEvent> {

    @FXML
    private TextField instrument;
    @FXML
    private TextField note;
    @FXML
    private TextField duration;
    @FXML
    private TextArea orchestraList;
    @FXML
    private Button play;
    @FXML
    private Button loopInstruments;
    @FXML
    private Button loopNotes;
    @FXML
    private Button loopAll;
    @FXML
    private Button loopRandom;
    @FXML
    private CheckBox wait;
    @FXML
    private Label instrumentLabel;
    @FXML
    public Button scrollToPlayheadButton;

    @FXML
    public VBox noteHeaderColumn;
    @FXML
    private ListView<Step> sequencerGrid;
    @FXML
    private Region playheadOverlay;

    private int MAX_INSTRUMENTS = 0;
    private static final int[] DEFAULT_NOTES = {60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72};
    private Instrument[] orchestra;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "timeline-thread");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> timelineFuture;
    AtomicInteger playheadColumn = new AtomicInteger(0);
    private int tempo = 60;
    private boolean scrollToPlayhead = false;
    private ScrollBar hBar;
    private VirtualFlow<ListCell<Step>> virtualFlow;
    private final ObservableList<Step> steps = FXCollections.observableArrayList(step -> new Observable[]{step.cellsProperty()});

    private static final double STEP_WIDTH = 100.0;
    private AnimationTimer playheadAnimator;
    private boolean isPaused = true;
    private double stepDurationNanos;

    private record Composition(int version, int tempo, List<Integer> notes, int steps, List<List<Integer>> grid, List<List<Integer>> durations) {}

    @SuppressWarnings("unchecked")
    @FXML
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        StringBuilder sb = new StringBuilder();
        sb.append("Instruments:\n");
        try (Synthesizer synthesizer = MidiSystem.getSynthesizer()) {
            synthesizer.open();
            orchestra = synthesizer.getLoadedInstruments();
            MAX_INSTRUMENTS = orchestra.length;
            sb.append("\n").append(InstrumentCellData.DRUM).append(") ").append("Drum Kit");
            for (int i = 0; i < MAX_INSTRUMENTS; i++) {
                sb.append("\n").append(i).append(") ").append(orchestra[i].getName().trim());
            }
        } catch (MidiUnavailableException e) {
            new Alert(Alert.AlertType.ERROR, "MIDI system is unavailable").showAndWait();
            System.exit(1);
        }
        orchestraList.setText(sb.toString());
        instrumentLabel.setText("Instrument (0 - " + (MAX_INSTRUMENTS - 1) + " or " + InstrumentCellData.DRUM + ")");

        noteHeaderColumn.setStyle("-fx-border-color: gray; -fx-border-width: 1px;");
        sequencerGrid.setStyle("-fx-border-color: gray; -fx-border-width: 1px;");
        Label labelRC = new Label("Note/Step");
        labelRC.setStyle("-fx-text-fill: white; -fx-padding: 3px; -fx-background-color: rgba(255,255,255,0.2); -fx-border-color: gray; -fx-border-width: 1px;");
        labelRC.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(labelRC, Priority.ALWAYS);
        labelRC.setAlignment(Pos.CENTER);
        labelRC.setTextAlignment(TextAlignment.CENTER);
        noteHeaderColumn.getChildren().addFirst(labelRC);
        AtomicInteger index = new AtomicInteger(1);
        Arrays.stream(DEFAULT_NOTES).forEach(note -> {
            NoteHeaderCell noteHeaderCell = new NoteHeaderCell(this, note, index.get(), 0);
            noteHeaderColumn.getChildren().add(index.get(), noteHeaderCell);
            index.getAndIncrement();
        });

        sequencerGrid.setSelectionModel(null);
        sequencerGrid.setItems(steps);
        sequencerGrid.setCellFactory(stepListView -> new ListCell<>() {
            private final VBox column = new VBox();
            private final Label stepLabel = new Label();
            {
                setStyle("-fx-padding: 0px");

                VBox.setVgrow(column, Priority.ALWAYS);
                column.setMinWidth(STEP_WIDTH);
                column.setPrefWidth(STEP_WIDTH);
                column.setMaxWidth(STEP_WIDTH);
                column.setSpacing(0);
                column.setAlignment(Pos.TOP_CENTER);

                stepLabel.setStyle("-fx-text-fill: white; -fx-padding: 3px; -fx-background-color: rgba(255,255,255,0.2); -fx-border-color: gray; -fx-border-width: 1px;");
                stepLabel.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(stepLabel, Priority.ALWAYS);
                stepLabel.setAlignment(Pos.CENTER);
                stepLabel.setTextAlignment(TextAlignment.CENTER);
                column.getChildren().add(stepLabel);
            }
            @Override
            protected void updateItem(Step step, boolean empty) {
                super.updateItem(step, empty);
                if (empty || step == null) {
                    setGraphic(null);
                    return;
                }
                stepLabel.setText(Integer.toString(step.getIndex()));
                if (column.getChildren().size() - 1 > step.getCells().size()) {
                    while (column.getChildren().size() - 1 > step.getCells().size()) {
                        column.getChildren().remove(1);
                    }
                } else if (column.getChildren().size() - 1 < step.getCells().size()) {
                    while (column.getChildren().size() - 1 < step.getCells().size()) {
                        Label cellLabel = createCellLabel();
                        column.getChildren().add(cellLabel);
                    }
                }
                for (int i = 0; i < step.getCells().size(); i++) {
                    InstrumentCellData cellData = step.getCells().get(i);
                    Label cellLabel = (Label) column.getChildren().get(i + 1);
                    cellLabel.textProperty().unbind();
                    cellLabel.textProperty().bind(cellData.labelProperty());
                    cellLabel.setUserData(cellData);
                }
                setGraphic(column);
            }
            private Label createCellLabel() {
                Label cellLabel = new Label();
                cellLabel.setStyle("-fx-text-fill: white; -fx-padding: 3px; -fx-border-color: gray; -fx-border-width: 1px;");
                cellLabel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                VBox.setVgrow(cellLabel, Priority.ALWAYS);
                HBox.setHgrow(cellLabel, Priority.ALWAYS);
                cellLabel.setFont(Font.font(cellLabel.getFont().getFamily(), FontWeight.BOLD, cellLabel.getFont().getSize() + 2));
                cellLabel.setAlignment(Pos.CENTER);
                cellLabel.setTextAlignment(TextAlignment.CENTER);
                cellLabel.setOnMouseClicked(HomeController.this);
                return cellLabel;
            }
        });

        for (int i = 1; i <= 10; i++) {
            Step step = new Step(i);
            for (int j = 1; j <= DEFAULT_NOTES.length; j++) {
                InstrumentCellData instrumentCellData = new InstrumentCellData(j, i);
                step.getCells().add(instrumentCellData);
            }
            steps.add(step);
        }

        playheadOverlay.prefHeightProperty().bind(sequencerGrid.heightProperty());
        playheadOverlay.setVisible(false);
        createTimeline();

        Platform.runLater(() -> {
            for (Node node : sequencerGrid.lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar scrollBar && scrollBar.getOrientation() == Orientation.HORIZONTAL) {
                    hBar = scrollBar;
                    break;
                }
            }
            virtualFlow = (VirtualFlow<ListCell<Step>>) sequencerGrid.lookup(".virtual-flow");
            noteHeaderColumn.paddingProperty().bind(Bindings.createObjectBinding(() -> {
                double bottomPadding = hBar.isVisible() ? hBar.getHeight() + 1 : 0;
                return new Insets(0, 0, bottomPadding, 0);
            }, hBar.visibleProperty(), hBar.heightProperty()));
        });
    }

    @FXML
    private void playClicked() {
        try {
            Thread.startVirtualThread(new Player(Integer.parseInt(instrument.getText()), Integer.parseInt(note.getText()), Double.parseDouble(duration.getText())));
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "Invalid input").showAndWait();
        }
    }

    @FXML
    private void loopInstrumentsClicked() {
        new Thread(() -> {
            try {
                Platform.runLater(() -> lockInstrumentAndControls(true));
                for (int i = 0; i < MAX_INSTRUMENTS; i++) {
                    final int x = i;
                    if (Integer.parseInt(note.getText()) >= 0 && Integer.parseInt(note.getText()) <= 127) {
                        Platform.runLater(() -> instrument.setText(String.valueOf(x)));
                        Thread thread = Thread.startVirtualThread(new Player(x, Integer.parseInt(note.getText()), Double.parseDouble(duration.getText())));
                        if (wait.isSelected()) thread.join();
                    } else {
                        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Invalid input").showAndWait());
                        break;
                    }
                }
            } catch (NumberFormatException | InterruptedException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Invalid input").showAndWait());
            } finally {
                Platform.runLater(() -> lockInstrumentAndControls(false));
            }
        }).start();
    }

    @FXML
    private void loopNotesClicked() {
        new Thread(() -> {
            try {
                Platform.runLater(() -> lockNoteAndControls(true));
                for (int i = 0; i <= 127; i++) {
                    final int x = i;
                    if (Integer.parseInt(instrument.getText()) >= 0 && Integer.parseInt(instrument.getText()) <= 127) {
                        Platform.runLater(() -> note.setText(String.valueOf(x)));
                        Thread thread = Thread.startVirtualThread(new Player(Integer.parseInt(instrument.getText()), x, Double.parseDouble(duration.getText())));
                        if (wait.isSelected()) thread.join();
                    } else {
                        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Invalid input").showAndWait());
                        break;
                    }
                }
            } catch (NumberFormatException | InterruptedException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Invalid input").showAndWait());
            } finally {
                Platform.runLater(() -> lockNoteAndControls(false));
            }
        }).start();
    }

    @FXML
    private void loopAllClicked() {
        new Thread(() -> {
            try {
                Platform.runLater(() -> lockAllAndControls(true));
                for (int i = 0; i <= 127; i++) {
                    for (int j = 0; j < MAX_INSTRUMENTS; j++) {
                        final int x = i;
                        final int y = j;
                        Platform.runLater(() -> note.setText(String.valueOf(x)));
                        Platform.runLater(() -> instrument.setText(String.valueOf(y)));
                        Thread thread = Thread.startVirtualThread(new Player(y, x, Double.parseDouble(duration.getText())));
                        if (wait.isSelected()) thread.join();
                    }
                }
            } catch (NumberFormatException | InterruptedException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Invalid input").showAndWait());
            } finally {
                Platform.runLater(() -> lockAllAndControls(false));
            }
        }).start();
    }

    @FXML
    private void loopRandomClicked() {
        new Thread(() -> {
            try {
                Platform.runLater(() -> lockAllAndControls(true));
                for (int i = 1; i <= 5; i++) {
                    final int x = (int) (Math.random() * 128);
                    final int y = (int) (Math.random() * MAX_INSTRUMENTS);
                    Platform.runLater(() -> note.setText(String.valueOf(x)));
                    Platform.runLater(() -> instrument.setText(String.valueOf(y)));
                    Thread thread = Thread.startVirtualThread(new Player(y, x, Double.parseDouble(duration.getText())));
                    if (wait.isSelected()) thread.join();
                }
            } catch (NumberFormatException | InterruptedException e) {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Invalid input").showAndWait());
            } finally {
                Platform.runLater(() -> lockAllAndControls(false));
            }
        }).start();
    }

    @FXML
    private void playCompositionClicked() {
        if (playheadAnimator == null) {
            startPlayhead();
        } else {
            resumePlayhead();
        }
    }

    @FXML
    private void pauseCompositionClicked() {
        pausePlayhead();
    }

    @FXML
    private void resetTimelineClicked() {
        for (int i = 1; i < noteHeaderColumn.getChildren().size(); i++) {
            NoteHeaderCell noteHeaderCell = (NoteHeaderCell) noteHeaderColumn.getChildren().get(i);
            if (noteHeaderCell == null) continue;
            for (Step step : steps) {
                InstrumentCellData cell = step.getCells().get(i - 1);
                if (cell != null) {
                    int instr = cell.getInstrument();
                    if (instr != InstrumentCellData.INACTIVE) {
                        Player.playNoteOff(instr, noteHeaderCell.getNote());
                    }
                }
            }
        }
        playheadColumn.set(0);
        if (isPaused) {
            stopPlayhead();
        }
    }

    @FXML
    private void scrollToPlayheadClicked() {
        scrollToPlayhead = !scrollToPlayhead;
        scrollToPlayheadButton.setText(scrollToPlayhead ? "Disable Scroll to Playhead" : "Enable Scroll to Playhead");
    }

    @FXML
    private void changeTempoClicked() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Change Tempo");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField tempoField = new TextField(String.valueOf(tempo));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Tempo (BPM):"), 0, 0);
        grid.add(tempoField, 1, 0);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                try {
                    int newTempo = Integer.parseInt(tempoField.getText());
                    if (newTempo > 0) {
                        tempo = newTempo;
                        return true;
                    } else {
                        new Alert(Alert.AlertType.ERROR, "Tempo must be positive").showAndWait();
                    }
                } catch (NumberFormatException e) {
                    new Alert(Alert.AlertType.ERROR, "Invalid input").showAndWait();
                }
            }
            return false;
        });
        dialog.showAndWait().ifPresent(changed -> {
            if (changed) {
                createTimeline();
            }
        });
    }

    @FXML
    public void addStepClicked() {
        int newStep = steps.size() + 1;
        Step step = new Step(newStep);
        for (int i = 1; i < noteHeaderColumn.getChildren().size(); i++) {
            InstrumentCellData instrumentCellData = new InstrumentCellData(i, newStep);
            step.getCells().add(instrumentCellData);
        }
        steps.add(step);
    }

    @FXML
    public void removeStepClicked() {
        if (steps.size() > 1) {
            steps.removeIf(step -> step.getIndex() == steps.size());
        }
    }

    @FXML
    public void addRowClicked() {
        int newRow = noteHeaderColumn.getChildren().size();
        NoteHeaderCell noteHeaderCell = new NoteHeaderCell(this, DEFAULT_NOTES[(newRow - 1) % DEFAULT_NOTES.length], newRow, 0);
        noteHeaderColumn.getChildren().add(newRow, noteHeaderCell);
        for (int i = 0; i < steps.size(); i++) {
            InstrumentCellData instrumentCellData = new InstrumentCellData(newRow, i + 1);
            steps.get(i).getCells().add(instrumentCellData);
        }
    }

    @FXML
    public void removeRowClicked() {
        if (noteHeaderColumn.getChildren().size() > 2) {
            noteHeaderColumn.getChildren().removeIf(node -> {
                if (!(node instanceof NoteHeaderCell noteHeaderCell)) return false;
                return noteHeaderCell.getRow() == noteHeaderColumn.getChildren().size() - 1;
            });
            for (int i = 1; i <= steps.size(); i++) {
                steps.get(i - 1).getCells().removeLast();
            }
        }
    }

    private void lockControls(boolean lock) {
        play.setDisable(lock);
        loopInstruments.setDisable(lock);
        loopNotes.setDisable(lock);
        loopAll.setDisable(lock);
        loopRandom.setDisable(lock);
    }

    private void lockInstrumentAndControls(boolean lock) {
        instrument.setDisable(lock);
        lockControls(lock);
    }

    private void lockNoteAndControls(boolean lock) {
        note.setDisable(lock);
        lockControls(lock);
    }

    private void lockAllAndControls(boolean lock) {
        instrument.setDisable(lock);
        note.setDisable(lock);
        lockControls(lock);
    }

    private double getPlayheadX(double currentStepDouble, int stepIndex) {
        if (virtualFlow == null) return -1;
        Step firstVisibleStep = virtualFlow.getFirstVisibleCell().getItem();
        Step lastVisibleStep = virtualFlow.getLastVisibleCell().getItem();
        // Let "0 to 1 index" or 1 to 2 step be the last step (according to currentStepDouble hack-inflate logic)
        if (stepIndex == 0) stepIndex = steps.size();
        // This logic stays at-par, had -1 for visibleStep index before to match 0-based LHS, now 1-based both sides
        if (stepIndex < firstVisibleStep.getIndex() || stepIndex > lastVisibleStep.getIndex()) {
            return -1;
        }
        if (scrollToPlayhead && hBar.getValue() != 1.0 && hBar.isVisible()) {
            return 0;
        }
        // According to hack-inflate, when step 1 to 2, instead treat as last step traversal, so add steps.size()
        if (currentStepDouble < 2) {
            return (steps.size() + (currentStepDouble - 1 - 1) - (firstVisibleStep.getIndex() - 1)) * STEP_WIDTH;
        }
        // According to hack-inflate, for all rest, first -1 is to come to 0 based index, second -1 is to align to actual playback
        return ((currentStepDouble - 1 - 1) - (firstVisibleStep.getIndex() - 1)) * STEP_WIDTH;
    }

    private void startPlayhead() {
        playheadOverlay.setVisible(true);
        stepDurationNanos = (60.0 / tempo / 4) * 1_000_000_000;
        long playheadStartTime = System.nanoTime();
        if (playheadAnimator != null) {
            playheadAnimator.stop();
            isPaused = true;
        }
        playheadAnimator = new javafx.animation.AnimationTimer() {
            private int lastCol = -1;
            private long stepStartTime = playheadStartTime;

            @Override
            public void handle(long now) {
                double currentStepDouble;
                int col = playheadColumn.get();
                if (col != lastCol) {
                    stepStartTime = now;
                    lastCol = col;
                }
                long elapsed = now - stepStartTime;
                double fraction = Math.min(elapsed / stepDurationNanos, 1.0);
                currentStepDouble = col + 1 + fraction;
                if (currentStepDouble > steps.size() + 1) currentStepDouble -= steps.size();
                double toSetX = getPlayheadX(currentStepDouble, col);
                playheadOverlay.setVisible(toSetX != -1);
                playheadOverlay.setTranslateX(toSetX);
                if (scrollToPlayhead) {
                    if (hBar == null) return;
                    // According to hack-inflate, when step 1 to 2, instead treat as last step traversal, so add steps.size()
                    if (currentStepDouble < 2) {
                        hBar.setValue(Math.clamp(((steps.size() + (currentStepDouble - 1 - 1)) * STEP_WIDTH) / (steps.size() * STEP_WIDTH - sequencerGrid.getWidth()), 0.0, 1.0));
                        return;
                    }
                    // According to hack-inflate, for all rest, first -1 is to come to 0 based index, second -1 is to align to actual playback
                    hBar.setValue(Math.clamp(((currentStepDouble - 1 - 1) * STEP_WIDTH) / (steps.size() * STEP_WIDTH - sequencerGrid.getWidth()), 0.0, 1.0));
                }
            }
        };
        playheadAnimator.start();
        isPaused = false;
    }

    private void pausePlayhead() {
        if (playheadAnimator != null) {
            playheadAnimator.stop();
            isPaused = true;
        }
    }

    private void resumePlayhead() {
        if (playheadAnimator != null) {
            playheadAnimator.start();
            isPaused = false;
        }
    }

    private void stopPlayhead() {
        if (playheadAnimator != null) {
            playheadAnimator.stop();
            playheadAnimator = null;
            isPaused = true;
        }
        playheadOverlay.setVisible(false);
    }

    private void createTimeline() {
        pauseCompositionClicked();
        long periodNanos = TimeUnit.SECONDS.toNanos(60) / tempo / 4;
        if (timelineFuture != null && !timelineFuture.isDone()) {
            timelineFuture.cancel(false);
        }
        timelineFuture = scheduler.scheduleAtFixedRate(() -> {
            if (isPaused) return;

            int currentStep = playheadColumn.get() + 1;

            for (int i = 1; i < noteHeaderColumn.getChildren().size(); i++) {
                NoteHeaderCell noteHeaderCell = (NoteHeaderCell) noteHeaderColumn.getChildren().get(i);
                if (noteHeaderCell == null) continue;
                for (int s = 1; s <= steps.size(); s++) {
                    InstrumentCellData cell = steps.get(s - 1).getCells().get(i - 1);
                    if (cell != null) {
                        int instr = cell.getInstrument();
                        if (instr != InstrumentCellData.INACTIVE) {
                            int dur = cell.getDuration();
                            int endStep = ((s - 1 + dur) % steps.size()) + 1;
                            if (endStep == currentStep) {
                                Player.playNoteOff(instr, noteHeaderCell.getNote());
                                Platform.runLater(noteHeaderCell::highlightOff);
                            }
                        }
                    }
                }
            }

            for (int i = 1; i < noteHeaderColumn.getChildren().size(); i++) {
                NoteHeaderCell noteHeaderCell = (NoteHeaderCell) noteHeaderColumn.getChildren().get(i);
                if (noteHeaderCell == null) continue;
                InstrumentCellData currentCell = steps.get(currentStep - 1).getCells().get(i - 1);
                if (currentCell != null) {
                    int instr = currentCell.getInstrument();
                    if (instr != InstrumentCellData.INACTIVE) {
                        Player.playNoteOn(instr, noteHeaderCell.getNote());
                        Platform.runLater(noteHeaderCell::highlightOn);
                    }
                }
            }

            playheadColumn.set(playheadColumn.get() + 1);
            if (playheadColumn.get() >= steps.size()) {
                playheadColumn.set(0);
            }
        }, 0, periodNanos, TimeUnit.NANOSECONDS);
        resetTimelineClicked();
    }

    @FXML
    private void exportCompositionClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Composition");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Composition Files (*.comp)", "*.comp"));
        fileChooser.setInitialFileName("composition.comp");
        File file = fileChooser.showSaveDialog(sequencerGrid.getScene().getWindow());
        if (file != null) {
            try {
                List<Integer> notes = new ArrayList<>();
                for (int r = 1; r < noteHeaderColumn.getChildren().size(); r++) {
                    NoteHeaderCell cell = (NoteHeaderCell) noteHeaderColumn.getChildren().get(r);
                    if (cell != null) notes.add(cell.getNote());
                }
                List<List<Integer>> grid = new ArrayList<>();
                List<List<Integer>> durations = new ArrayList<>();
                for (int r = 1; r < noteHeaderColumn.getChildren().size(); r++) {
                    List<Integer> row = new ArrayList<>();
                    List<Integer> durRow = new ArrayList<>();
                    for (int c = 1; c <= steps.size(); c++) {
                        InstrumentCellData cell = steps.get(c - 1).getCells().get(r - 1);
                        row.add(cell != null ? cell.getInstrument() : InstrumentCellData.INACTIVE);
                        durRow.add(cell != null ? cell.getDuration() : 1);
                    }
                    grid.add(row);
                    durations.add(durRow);
                }
                Composition composition = new Composition(2, tempo, notes, steps.size(), grid, durations);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(file.toPath(), gson.toJson(composition));
                new Alert(Alert.AlertType.INFORMATION, "Composition exported successfully!").showAndWait();
            } catch (IOException e) {
                new Alert(Alert.AlertType.ERROR, "Failed to export: " + e.getMessage()).showAndWait();
            }
        }
    }

    @FXML
    private void importCompositionClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Composition");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Composition Files (*.comp)", "*.comp"));
        File file = fileChooser.showOpenDialog(sequencerGrid.getScene().getWindow());
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                Gson gson = new Gson();
                Composition composition = gson.fromJson(content, Composition.class);
                if (composition.version() != 1 && composition.version() != 2) {
                    new Alert(Alert.AlertType.ERROR, "Unsupported composition version: " + composition.version()).showAndWait();
                    return;
                }
                clearGrid();
                tempo = composition.tempo();
                for (int i = 0; i < composition.notes().size(); i++) {
                    int noteValue = composition.notes().get(i);
                    NoteHeaderCell noteHeaderCell = new NoteHeaderCell(this, noteValue, i + 1, 0);
                    noteHeaderColumn.getChildren().add(i + 1, noteHeaderCell);
                }
                for (int c = 1; c <= composition.steps(); c++) {
                    Step step = new Step(c);
                    steps.add(step);
                }
                boolean hasDurations = composition.version() == 2 && composition.durations() != null;
                for (int r = 0; r < composition.grid().size(); r++) {
                    List<Integer> row = composition.grid().get(r);
                    List<Integer> durRow = hasDurations ? composition.durations().get(r) : null;
                    for (int c = 0; c < row.size(); c++) {
                        int instr = row.get(c);
                        int dur = (durRow != null && c < durRow.size()) ? durRow.get(c) : 1;
                        InstrumentCellData instrumentCellData = new InstrumentCellData(r + 1, c + 1);
                        if (instr != InstrumentCellData.INACTIVE) {
                            instrumentCellData.setInstrumentAndDuration(instr, dur, orchestra);
                        }
                        steps.get(c).getCells().add(instrumentCellData);
                    }
                }
                createTimeline();
                new Alert(Alert.AlertType.INFORMATION, "Composition imported successfully!").showAndWait();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "Failed to import: " + e.getMessage()).showAndWait();
            }
        }
    }

    @FXML
    private void importMidiClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import MIDI File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MIDI Files (*.mid, *.midi)", "*.mid", "*.midi"));
        File file = fileChooser.showOpenDialog(sequencerGrid.getScene().getWindow());
        if (file == null) return;

        Dialog<Void> waitDialog = new Dialog<>();
        waitDialog.setTitle("Preparing Composition");
        waitDialog.setHeaderText("Processing MIDI data...");
        ProgressIndicator progress = new ProgressIndicator();
        progress.setProgress(-1);
        StackPane progressPane = new StackPane();
        progressPane.getChildren().add(progress);
        StackPane.setAlignment(progress, Pos.CENTER);
        waitDialog.getDialogPane().setContent(progressPane);
        Window waitDialogWindow = waitDialog.getDialogPane().getScene().getWindow();
        waitDialog.show();

        Thread.startVirtualThread(() -> {
            try {
                Sequence sequence = MidiSystem.getSequence(file);
                int resolution = sequence.getResolution();
                int midiTempo = 500000;
                long ticksPerStep = resolution / 4;
                TreeSet<Integer> allNotes = new TreeSet<>();
                int[] channelInstruments = new int[16];
                record NoteEvent(long startTick, int instrument, int note, int durationSteps) {
                }
                List<NoteEvent> noteEvents = new ArrayList<>();
                Map<Integer, Map<Integer, Long>> activeNotes = new HashMap<>();
                for (Track track : sequence.getTracks()) {
                    for (int i = 0; i < track.size(); i++) {
                        MidiEvent event = track.get(i);
                        MidiMessage message = event.getMessage();
                        if (message instanceof MetaMessage meta) {
                            if (meta.getType() == 0x51 && meta.getData().length == 3) {
                                byte[] data = meta.getData();
                                midiTempo = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                            }
                        } else if (message instanceof ShortMessage sm) {
                            int channel = sm.getChannel();
                            int command = sm.getCommand();
                            int noteValue = sm.getData1();
                            int velocity = sm.getData2();
                            if (command == ShortMessage.PROGRAM_CHANGE) {
                                channelInstruments[channel] = sm.getData1();
                            } else if (command == ShortMessage.NOTE_ON && velocity > 0) {
                                long tick = event.getTick();
                                int instrument = (channel == 9) ? InstrumentCellData.DRUM : channelInstruments[channel];
                                allNotes.add(noteValue);
                                activeNotes.computeIfAbsent(channel, k -> new HashMap<>()).put(noteValue, tick);
                                activeNotes.get(channel).put(noteValue | (instrument << 16), tick);
                            } else if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && velocity == 0)) {
                                long endTick = event.getTick();
                                Map<Integer, Long> channelActive = activeNotes.get(channel);
                                if (channelActive != null) {
                                    int instrument = (channel == 9) ? InstrumentCellData.DRUM : channelInstruments[channel];
                                    int key = noteValue | (instrument << 16);
                                    Long startTick = channelActive.remove(key);
                                    if (startTick != null) {
                                        long durationTicks = endTick - startTick;
                                        int durationSteps = (int) Math.max(1, durationTicks / ticksPerStep);
                                        noteEvents.add(new NoteEvent(startTick, instrument, noteValue, durationSteps));
                                    }
                                }
                            }
                        }
                    }
                }
                if (noteEvents.isEmpty()) {
                    Platform.runLater(() -> {
                        waitDialogWindow.hide();
                        new Alert(Alert.AlertType.WARNING, "No notes found in MIDI file").showAndWait();
                    });
                    return;
                }
                int bpm = (int) Math.round(60000000.0 / midiTempo);
                long maxTick = noteEvents.stream().mapToLong(e -> e.startTick + (long) e.durationSteps * ticksPerStep).max().orElse(0);
                int totalSteps = (int) ((maxTick / ticksPerStep) + 1);
                List<Integer> notes = new ArrayList<>(allNotes);
                List<List<Integer>> grid = new ArrayList<>();
                List<List<Integer>> durations = new ArrayList<>();
                for (int n = 0; n < notes.size(); n++) {
                    List<Integer> row = new ArrayList<>();
                    List<Integer> durRow = new ArrayList<>();
                    for (int s = 0; s < totalSteps; s++) {
                        row.add(InstrumentCellData.INACTIVE);
                        durRow.add(1);
                    }
                    grid.add(row);
                    durations.add(durRow);
                }
                for (NoteEvent ne : noteEvents) {
                    int step = (int) (ne.startTick / ticksPerStep);
                    if (step >= totalSteps) step = totalSteps - 1;
                    int rowIndex = notes.indexOf(ne.note);
                    if (rowIndex >= 0 && step < totalSteps) {
                        grid.get(rowIndex).set(step, ne.instrument);
                        durations.get(rowIndex).set(step, ne.durationSteps);
                    }
                }
                CountDownLatch clearLatch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    clearGrid();
                    clearLatch.countDown();
                });
                clearLatch.await();
                tempo = bpm;
                for (int i = 0; i < notes.size(); i++) {
                    int noteValue = notes.get(i);
                    NoteHeaderCell noteHeaderCell = new NoteHeaderCell(this, noteValue, i + 1, 0);
                    int finalI = i;
                    Platform.runLater(() -> noteHeaderColumn.getChildren().add(finalI + 1, noteHeaderCell));
                }
                for (int c = 1; c <= totalSteps; c++) {
                    Step step = new Step(c);
                    CountDownLatch stepLatch = new CountDownLatch(1);
                    Platform.runLater(() -> {
                        steps.add(step);
                        stepLatch.countDown();
                    });
                    stepLatch.await();
                }
                for (int r = 0; r < grid.size(); r++) {
                    List<Integer> row = grid.get(r);
                    List<Integer> durRow = durations.get(r);
                    for (int c = 0; c < row.size(); c++) {
                        int instr = row.get(c);
                        int dur = durRow.get(c);
                        InstrumentCellData instrumentCellData = new InstrumentCellData(r + 1, c + 1);
                        if (instr != InstrumentCellData.INACTIVE) {
                            instrumentCellData.setInstrumentAndDuration(instr, dur, orchestra);
                        }
                        int finalC = c;
                        int finalR = r;
                        Platform.runLater(() -> steps.get(finalC).getCells().add(finalR, instrumentCellData));
                    }
                }
                Platform.runLater(() -> Platform.runLater(() -> {
                    createTimeline();
                    waitDialogWindow.hide();
                    new Alert(Alert.AlertType.INFORMATION, "MIDI imported: " + notes.size() + " notes, " + totalSteps + " steps, " + bpm + " BPM").showAndWait();
                }));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    waitDialogWindow.hide();
                    new Alert(Alert.AlertType.ERROR, "Failed to import MIDI: " + e.getMessage()).showAndWait();
                });
            }
        });
    }

    private void clearGrid() {
        steps.clear();
        noteHeaderColumn.getChildren().removeIf(node -> node instanceof NoteHeaderCell);
    }

    public void shutdownScheduler() {
        if (timelineFuture != null && !timelineFuture.isDone()) {
            timelineFuture.cancel(false);
        }
        scheduler.shutdown();
    }

    @Override
    public void handle(MouseEvent mouseEvent) {
        if (mouseEvent.getButton() == MouseButton.PRIMARY) {
            Node node = mouseEvent.getSource() instanceof Node ? (Node) mouseEvent.getSource() : null;
            switch (node) {
                case NoteHeaderCell noteHeaderCell -> {
                    ContextMenu noteMenu = new ContextMenu();
                    NoteHeaderCell.NOTE_MAP.forEach((noteValue, noteName) -> {
                        MenuItem item = new MenuItem(noteName);
                        item.setOnAction(event -> noteHeaderCell.setNote(noteValue));
                        noteMenu.getItems().add(item);
                    });
                    noteMenu.show(noteHeaderCell, mouseEvent.getScreenX(), mouseEvent.getScreenY());
                }
                case Label instrumentCell -> {
                    InstrumentCellData instrumentCellData = (InstrumentCellData) instrumentCell.getUserData();
                    int currentInstrument = instrumentCellData.getInstrument();
                    if (currentInstrument == InstrumentCellData.INACTIVE) {
                        ContextMenu instrumentMenu = new ContextMenu();
                        MenuItem drumItem = new MenuItem("Drum Kit");
                        drumItem.setOnAction(event -> instrumentCellData.setInstrument(InstrumentCellData.DRUM, orchestra));
                        instrumentMenu.getItems().add(drumItem);
                        for (int i = 0; i < MAX_INSTRUMENTS; i++) {
                            final int instr = i;
                            MenuItem item = new MenuItem(orchestra[instr].getName().trim());
                            item.setOnAction(event -> instrumentCellData.setInstrument(instr, orchestra));
                            instrumentMenu.getItems().add(item);
                        }
                        instrumentMenu.show(instrumentCell, mouseEvent.getScreenX(), mouseEvent.getScreenY());
                    } else {
                        instrumentCellData.setInstrument(InstrumentCellData.INACTIVE, orchestra);
                    }
                }
                case null, default -> {
                }
            }
        } else if (mouseEvent.getButton() == MouseButton.SECONDARY) {
            Node node = mouseEvent.getSource() instanceof Node ? (Node) mouseEvent.getSource() : null;
            if (node instanceof NoteHeaderCell) return;
            if (!(node instanceof Label instrumentCell)) return;
            InstrumentCellData instrumentCellData = (InstrumentCellData) instrumentCell.getUserData();
            if (instrumentCellData.getInstrument() != InstrumentCellData.INACTIVE) {
                ContextMenu durationMenu = new ContextMenu();
                for (int d = 1; d <= 16; d++) {
                    final int dur = d;
                    MenuItem item = new MenuItem("Duration: " + d + " step" + (d > 1 ? "s" : ""));
                    item.setOnAction(event -> instrumentCellData.setDuration(dur, orchestra));
                    durationMenu.getItems().add(item);
                }
                durationMenu.getItems().add(new SeparatorMenuItem());
                MenuItem customItem = new MenuItem("Custom duration...");
                customItem.setOnAction(event -> {
                    TextInputDialog dialog = new TextInputDialog(String.valueOf(instrumentCellData.getDuration()));
                    dialog.setTitle("Set Duration");
                    dialog.setHeaderText("Enter duration in steps:");
                    dialog.showAndWait().ifPresent(result -> {
                        try {
                            int dur = Integer.parseInt(result);
                            if (dur >= 1) {
                                instrumentCellData.setDuration(dur, orchestra);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    });
                });
                durationMenu.getItems().add(customItem);
                durationMenu.show(instrumentCell, mouseEvent.getScreenX(), mouseEvent.getScreenY());
            }
        }
    }
}