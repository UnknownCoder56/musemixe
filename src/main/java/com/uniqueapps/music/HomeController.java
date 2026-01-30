package com.uniqueapps.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
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
    public ScrollPane superParentScrollPane;
    @FXML
    private StackPane parentStackPane;
    @FXML
    private GridPane sequencerGrid;
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

    private final Map<Integer, NoteHeaderCell> noteHeaderCache = new HashMap<>();
    private final Map<Long, InstrumentCell> instrumentCellCache = new HashMap<>();
    private int cachedSteps = 0;
    private int cachedRows = 0;

    private static final double STEP_WIDTH = 100.0;
    private double noteColumnWidth = 0;
    private AnimationTimer playheadAnimator;
    private boolean isPaused = true;
    private double stepDurationNanos;

    private record Composition(int version, int tempo, List<Integer> notes, int steps, List<List<Integer>> grid, List<List<Integer>> durations) {}

    @FXML
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        StringBuilder sb = new StringBuilder();
        sb.append("Instruments:\n");
        try (Synthesizer synthesizer = MidiSystem.getSynthesizer()) {
            synthesizer.open();
            orchestra = synthesizer.getLoadedInstruments();
            MAX_INSTRUMENTS = orchestra.length;
            sb.append("\n").append(InstrumentCell.DRUM).append(") ").append("Drum Kit");
            for (int i = 0; i < MAX_INSTRUMENTS; i++) {
                sb.append("\n").append(i).append(") ").append(orchestra[i].getName().trim());
            }
        } catch (MidiUnavailableException e) {
            new Alert(Alert.AlertType.ERROR, "MIDI system is unavailable").showAndWait();
            System.exit(1);
        }
        orchestraList.setText(sb.toString());
        instrumentLabel.setText("Instrument (0 - " + (MAX_INSTRUMENTS - 1) + " or " + InstrumentCell.DRUM + ")");

        sequencerGrid.setStyle("-fx-border-color: gray; -fx-border-width: 1px;");
        StackPane topLeftLabel = new StackPane();
        topLeftLabel.setStyle("-fx-padding: 3px; -fx-background-color: rgba(255,255,255,0.2); -fx-border-color: gray; -fx-border-width: 1px;");
        Label labelRC = new Label("Note/Step");
        labelRC.setStyle("-fx-text-fill: white");
        topLeftLabel.getChildren().add(labelRC);
        StackPane.setAlignment(topLeftLabel.getChildren().getFirst(), Pos.CENTER);
        sequencerGrid.add(topLeftLabel, 0, 0);
        AtomicInteger index = new AtomicInteger(0);
        Arrays.stream(DEFAULT_NOTES).forEach(note -> {
            NoteHeaderCell noteHeaderCell = new NoteHeaderCell(this, note, index.get() + 1, 0);
            sequencerGrid.add(noteHeaderCell, 0, index.get() + 1);
            noteHeaderCache.put(index.get() + 1, noteHeaderCell);
            index.set(index.get() + 1);
        });
        cachedRows = DEFAULT_NOTES.length;
        for (int i = 1; i <= 10; i++) {
            StackPane stackPane = new StackPane();
            stackPane.setStyle("-fx-padding: 3px; -fx-background-color: rgba(255,255,255,0.2); -fx-border-color: gray; -fx-border-width: 1px;");
            Label labelC = new Label(String.valueOf(i));
            labelC.setStyle("-fx-text-fill: white");
            stackPane.getChildren().add(labelC);
            StackPane.setAlignment(stackPane.getChildren().getFirst(), Pos.CENTER);
            sequencerGrid.add(stackPane, i, 0);
        }
        cachedSteps = 10;

        ColumnConstraints noteCol = new ColumnConstraints();
        noteCol.setHgrow(Priority.NEVER);
        noteCol.setMinWidth(Region.USE_PREF_SIZE);
        noteCol.setPrefWidth(Region.USE_COMPUTED_SIZE);
        noteCol.setMaxWidth(Region.USE_COMPUTED_SIZE);
        sequencerGrid.getColumnConstraints().add(noteCol);
        for (int c = 1; c <= 10; c++) {
            ColumnConstraints stepCol = new ColumnConstraints(100);
            sequencerGrid.getColumnConstraints().add(stepCol);
        }

        RowConstraints headerRow = new RowConstraints();
        headerRow.setVgrow(Priority.NEVER);
        headerRow.setMinHeight(28);
        headerRow.setPrefHeight(28);
        headerRow.setMaxHeight(28);
        sequencerGrid.getRowConstraints().add(headerRow);
        for (int r = 1; r <= 13; r++) {
            RowConstraints noteRow = new RowConstraints();
            noteRow.setVgrow(Priority.ALWAYS);
            sequencerGrid.getRowConstraints().add(noteRow);
        }

        for (int r = 1; r <= 13; r++) {
            for (int c = 1; c <= 10; c++) {
                InstrumentCell instrumentCell = new InstrumentCell(this, r, c);
                sequencerGrid.add(instrumentCell, c, r);
                instrumentCellCache.put(cellKey(r, c), instrumentCell);
            }
        }

        playheadOverlay.prefHeightProperty().bind(sequencerGrid.heightProperty());
        playheadOverlay.setVisible(false);
        createTimeline();
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
        for (int i = 1; i <= cachedRows; i++) {
            NoteHeaderCell noteHeaderCell = noteHeaderCache.get(i);
            if (noteHeaderCell == null) continue;
            for (int s = 1; s <= cachedSteps; s++) {
                InstrumentCell cell = instrumentCellCache.get(cellKey(i, s));
                if (cell != null) {
                    int instr = cell.getInstrument();
                    if (instr != InstrumentCell.INACTIVE) {
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
        int newStep = cachedSteps + 1;
        StackPane stackPane = new StackPane();
        stackPane.setStyle("-fx-padding: 3px; -fx-background-color: rgba(255,255,255,0.2); -fx-border-color: gray; -fx-border-width: 1px;");
        Label labelC = new Label(String.valueOf(newStep));
        labelC.setStyle("-fx-text-fill: white");
        stackPane.getChildren().add(labelC);
        StackPane.setAlignment(stackPane.getChildren().getFirst(), Pos.CENTER);
        sequencerGrid.add(stackPane, newStep, 0);
        ColumnConstraints stepCol = new ColumnConstraints(100);
        sequencerGrid.getColumnConstraints().add(stepCol);
        for (int r = 1; r <= cachedRows; r++) {
            InstrumentCell instrumentCell = new InstrumentCell(this, r, newStep);
            sequencerGrid.add(instrumentCell, newStep, r);
            instrumentCellCache.put(cellKey(r, newStep), instrumentCell);
        }
        cachedSteps = newStep;
    }

    @FXML
    public void removeStepClicked() {
        if (cachedSteps > 1) {
            sequencerGrid.getChildren().removeIf(node -> {
                Integer colIndex = GridPane.getColumnIndex(node);
                return colIndex != null && colIndex == cachedSteps;
            });
            sequencerGrid.getColumnConstraints().remove(cachedSteps);
            for (int r = 1; r <= cachedRows; r++) {
                instrumentCellCache.remove(cellKey(r, cachedSteps));
            }
            cachedSteps--;
        }
    }

    @FXML
    public void addRowClicked() {
        int newRow = cachedRows + 1;
        NoteHeaderCell noteHeaderCell = new NoteHeaderCell(this, DEFAULT_NOTES[(newRow - 1) % DEFAULT_NOTES.length], newRow, 0);
        sequencerGrid.add(noteHeaderCell, 0, newRow);
        noteHeaderCache.put(newRow, noteHeaderCell);
        RowConstraints noteRow = new RowConstraints();
        noteRow.setVgrow(Priority.ALWAYS);
        sequencerGrid.getRowConstraints().add(noteRow);
        for (int c = 1; c <= cachedSteps; c++) {
            InstrumentCell instrumentCell = new InstrumentCell(this, newRow, c);
            sequencerGrid.add(instrumentCell, c, newRow);
            instrumentCellCache.put(cellKey(newRow, c), instrumentCell);
        }
        cachedRows = newRow;
    }

    @FXML
    public void removeRowClicked() {
        if (cachedRows > 1) {
            sequencerGrid.getChildren().removeIf(node -> {
                Integer rowIndex = GridPane.getRowIndex(node);
                return rowIndex != null && rowIndex == cachedRows;
            });
            sequencerGrid.getRowConstraints().remove(cachedRows);
            noteHeaderCache.remove(cachedRows);
            for (int c = 1; c <= cachedSteps; c++) {
                instrumentCellCache.remove(cellKey(cachedRows, c));
            }
            cachedRows--;
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

    private void updateNoteColumnWidth() {
        sequencerGrid.getChildren().stream()
                .filter(n -> GridPane.getColumnIndex(n) != null && GridPane.getColumnIndex(n) == 0)
                .findFirst().ifPresent(noteHeader -> noteColumnWidth = noteHeader.getBoundsInParent().getWidth());
    }

    private double getPlayheadX(double step) {
        return noteColumnWidth + (step - 1) * STEP_WIDTH;
    }

    private void startPlayhead() {
        updateNoteColumnWidth();
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
                double currentStepFloat;
                    int col = playheadColumn.get();
                    if (col != lastCol) {
                        stepStartTime = now;
                        lastCol = col;
                    }
                    long elapsed = now - stepStartTime;
                    double fraction = Math.min(elapsed / stepDurationNanos, 1.0);
                    currentStepFloat = col + 1 + fraction;
                    if (currentStepFloat > cachedSteps + 1) currentStepFloat -= cachedSteps;
                playheadOverlay.setTranslateX(getPlayheadX(currentStepFloat));
                if (scrollToPlayhead) {
                    superParentScrollPane.setHvalue(getPlayheadX(currentStepFloat) / (sequencerGrid.getWidth() - superParentScrollPane.getViewportBounds().getWidth()));
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

            for (int i = 1; i <= cachedRows; i++) {
                NoteHeaderCell noteHeaderCell = noteHeaderCache.get(i);
                if (noteHeaderCell == null) continue;
                for (int s = 1; s <= cachedSteps; s++) {
                    InstrumentCell cell = instrumentCellCache.get(cellKey(i, s));
                    if (cell != null) {
                        int instr = cell.getInstrument();
                        if (instr != InstrumentCell.INACTIVE) {
                            int dur = cell.getDuration();
                            int endStep = ((s - 1 + dur) % cachedSteps) + 1;
                            if (endStep == currentStep) {
                                Player.playNoteOff(instr, noteHeaderCell.getNote());
                            }
                        }
                    }
                }
            }

            for (int i = 1; i <= cachedRows; i++) {
                NoteHeaderCell noteHeaderCell = noteHeaderCache.get(i);
                if (noteHeaderCell == null) continue;
                InstrumentCell currentCell = instrumentCellCache.get(cellKey(i, currentStep));
                if (currentCell != null) {
                    int instr = currentCell.getInstrument();
                    if (instr != InstrumentCell.INACTIVE) {
                        Player.playNoteOn(instr, noteHeaderCell.getNote());
                    }
                }
            }

            playheadColumn.set(playheadColumn.get() + 1);
            if (playheadColumn.get() >= cachedSteps) {
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
                for (int r = 1; r <= cachedRows; r++) {
                    NoteHeaderCell cell = noteHeaderCache.get(r);
                    if (cell != null) notes.add(cell.getNote());
                }
                List<List<Integer>> grid = new ArrayList<>();
                List<List<Integer>> durations = new ArrayList<>();
                for (int r = 1; r <= cachedRows; r++) {
                    List<Integer> row = new ArrayList<>();
                    List<Integer> durRow = new ArrayList<>();
                    for (int c = 1; c <= cachedSteps; c++) {
                        InstrumentCell cell = instrumentCellCache.get(cellKey(r, c));
                        row.add(cell != null ? cell.getInstrument() : InstrumentCell.INACTIVE);
                        durRow.add(cell != null ? cell.getDuration() : 1);
                    }
                    grid.add(row);
                    durations.add(durRow);
                }
                Composition composition = new Composition(2, tempo, notes, cachedSteps, grid, durations);
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
                    sequencerGrid.add(noteHeaderCell, 0, i + 1);
                    noteHeaderCache.put(i + 1, noteHeaderCell);
                    RowConstraints noteRow = new RowConstraints();
                    noteRow.setVgrow(Priority.ALWAYS);
                    sequencerGrid.getRowConstraints().add(noteRow);
                }
                cachedRows = composition.notes().size();
                for (int c = 1; c <= composition.steps(); c++) {
                    StackPane stackPane = new StackPane();
                    stackPane.setStyle("-fx-padding: 3px; -fx-background-color: rgba(255,255,255,0.2); -fx-border-color: gray; -fx-border-width: 1px;");
                    Label labelC = new Label(String.valueOf(c));
                    labelC.setStyle("-fx-text-fill: white");
                    stackPane.getChildren().add(labelC);
                    StackPane.setAlignment(stackPane.getChildren().getFirst(), Pos.CENTER);
                    sequencerGrid.add(stackPane, c, 0);
                    ColumnConstraints stepCol = new ColumnConstraints(100);
                    sequencerGrid.getColumnConstraints().add(stepCol);
                }
                cachedSteps = composition.steps();
                boolean hasDurations = composition.version() == 2 && composition.durations() != null;
                for (int r = 0; r < composition.grid().size(); r++) {
                    List<Integer> row = composition.grid().get(r);
                    List<Integer> durRow = hasDurations ? composition.durations().get(r) : null;
                    for (int c = 0; c < row.size(); c++) {
                        int instr = row.get(c);
                        int dur = (durRow != null && c < durRow.size()) ? durRow.get(c) : 1;
                        InstrumentCell instrumentCell = new InstrumentCell(this, r + 1, c + 1);
                        if (instr != InstrumentCell.INACTIVE) {
                            instrumentCell.setInstrumentAndDuration(instr, dur, orchestra);
                        }
                        sequencerGrid.add(instrumentCell, c + 1, r + 1);
                        instrumentCellCache.put(cellKey(r + 1, c + 1), instrumentCell);
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
                                int instrument = (channel == 9) ? InstrumentCell.DRUM : channelInstruments[channel];
                                allNotes.add(noteValue);
                                activeNotes.computeIfAbsent(channel, k -> new HashMap<>()).put(noteValue, tick);
                                activeNotes.get(channel).put(noteValue | (instrument << 16), tick);
                            } else if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && velocity == 0)) {
                                long endTick = event.getTick();
                                Map<Integer, Long> channelActive = activeNotes.get(channel);
                                if (channelActive != null) {
                                    int instrument = (channel == 9) ? InstrumentCell.DRUM : channelInstruments[channel];
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
                        row.add(InstrumentCell.INACTIVE);
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
                    parentStackPane.getChildren().remove(sequencerGrid);
                    clearGrid();
                    clearLatch.countDown();
                });
                clearLatch.await();
                tempo = bpm;
                for (int i = 0; i < notes.size(); i++) {
                    int noteValue = notes.get(i);
                    NoteHeaderCell noteHeaderCell = new NoteHeaderCell(this, noteValue, i + 1, 0);
                    int finalI = i;
                    Platform.runLater(() -> sequencerGrid.add(noteHeaderCell, 0, finalI + 1));
                    noteHeaderCache.put(i + 1, noteHeaderCell);
                    RowConstraints noteRow = new RowConstraints();
                    noteRow.setVgrow(Priority.ALWAYS);
                    Platform.runLater(() -> sequencerGrid.getRowConstraints().add(noteRow));
                }
                cachedRows = notes.size();
                for (int c = 1; c <= totalSteps; c++) {
                    StackPane stackPane = new StackPane();
                    stackPane.setStyle("-fx-padding: 3px; -fx-background-color: rgba(255,255,255,0.2); -fx-border-color: gray; -fx-border-width: 1px;");
                    Label labelC = new Label(String.valueOf(c));
                    labelC.setStyle("-fx-text-fill: white");
                    stackPane.getChildren().add(labelC);
                    StackPane.setAlignment(stackPane.getChildren().getFirst(), Pos.CENTER);
                    int finalC = c;
                    Platform.runLater(() -> sequencerGrid.add(stackPane, finalC, 0));
                    ColumnConstraints stepCol = new ColumnConstraints(100);
                    Platform.runLater(() -> sequencerGrid.getColumnConstraints().add(stepCol));
                }
                cachedSteps = totalSteps;
                List<InstrumentCell> cellsToAdd = new ArrayList<>();
                for (int r = 0; r < grid.size(); r++) {
                    List<Integer> row = grid.get(r);
                    List<Integer> durRow = durations.get(r);
                    for (int c = 0; c < row.size(); c++) {
                        int instr = row.get(c);
                        int dur = durRow.get(c);
                        InstrumentCell instrumentCell = new InstrumentCell(this, r + 1, c + 1);
                        if (instr != InstrumentCell.INACTIVE) {
                            instrumentCell.setInstrumentAndDuration(instr, dur, orchestra);
                        }
                        cellsToAdd.add(instrumentCell);
                        GridPane.setConstraints(instrumentCell, c + 1, r + 1);
                        instrumentCellCache.put(cellKey(r + 1, c + 1), instrumentCell);
                    }
                }
                sequencerGrid.getChildren().addAll(cellsToAdd);
                createTimeline();
                Platform.runLater(() -> {
                    try {
                        parentStackPane.getChildren().addFirst(sequencerGrid);
                        sequencerGrid.applyCss();
                        sequencerGrid.layout();
                        Platform.runLater(() -> {
                            waitDialogWindow.hide();
                            new Alert(Alert.AlertType.INFORMATION, "MIDI imported: " + notes.size() + " notes, " + totalSteps + " steps, " + bpm + " BPM").showAndWait();
                        });
                    } catch (OutOfMemoryError oom) {
                        System.gc();
                        parentStackPane.getChildren().remove(sequencerGrid);
                        Platform.runLater(() -> {
                            waitDialogWindow.hide();
                            new Alert(Alert.AlertType.INFORMATION, "MIDI imported: " + notes.size() + " notes, " + totalSteps + " steps, " + bpm + " BPM\nComposer UI disabled due to extremely large size").showAndWait();
                        });
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (parentStackPane.getChildren().getFirst() != sequencerGrid) {
                        parentStackPane.getChildren().addFirst(sequencerGrid);
                    }
                    waitDialogWindow.hide();
                    new Alert(Alert.AlertType.ERROR, "Failed to import MIDI: " + e.getMessage()).showAndWait();
                });
            }
        });
    }

    private void clearGrid() {
        sequencerGrid.getChildren().removeIf(node -> node instanceof InstrumentCell || node instanceof NoteHeaderCell);
        sequencerGrid.getChildren().removeIf(node -> {
            Integer col = GridPane.getColumnIndex(node);
            Integer row = GridPane.getRowIndex(node);
            return (col != null && col > 0 && row != null && row == 0);
        });
        while (sequencerGrid.getRowConstraints().size() > 1) {
            sequencerGrid.getRowConstraints().removeLast();
        }
        while (sequencerGrid.getColumnConstraints().size() > 1) {
            sequencerGrid.getColumnConstraints().removeLast();
        }
        noteHeaderCache.clear();
        instrumentCellCache.clear();
        cachedSteps = 0;
        cachedRows = 0;
    }

    private long cellKey(int row, int col) {
        return ((long) row << 16) | col;
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
                case InstrumentCell instrumentCell -> {
                    int currentInstrument = instrumentCell.getInstrument();
                    if (currentInstrument == InstrumentCell.INACTIVE) {
                        ContextMenu instrumentMenu = new ContextMenu();
                        MenuItem drumItem = new MenuItem("Drum Kit");
                        drumItem.setOnAction(event -> instrumentCell.setInstrument(InstrumentCell.DRUM, orchestra));
                        instrumentMenu.getItems().add(drumItem);
                        for (int i = 0; i < MAX_INSTRUMENTS; i++) {
                            final int instr = i;
                            MenuItem item = new MenuItem(orchestra[instr].getName().trim());
                            item.setOnAction(event -> instrumentCell.setInstrument(instr, orchestra));
                            instrumentMenu.getItems().add(item);
                        }
                        instrumentMenu.show(instrumentCell, mouseEvent.getScreenX(), mouseEvent.getScreenY());
                    } else {
                        instrumentCell.setInstrument(InstrumentCell.INACTIVE, orchestra);
                    }
                }
                case NoteHeaderCell noteHeaderCell -> {
                    ContextMenu noteMenu = new ContextMenu();
                    NoteHeaderCell.NOTE_MAP.forEach((noteValue, noteName) -> {
                        MenuItem item = new MenuItem(noteName);
                        item.setOnAction(event -> noteHeaderCell.setNote(noteValue));
                        noteMenu.getItems().add(item);
                    });
                    noteMenu.show(noteHeaderCell, mouseEvent.getScreenX(), mouseEvent.getScreenY());
                }
                case null, default -> {
                }
            }
        } else if (mouseEvent.getButton() == MouseButton.SECONDARY) {
            Node node = mouseEvent.getSource() instanceof Node ? (Node) mouseEvent.getSource() : null;
            if (node instanceof InstrumentCell instrumentCell && instrumentCell.getInstrument() != InstrumentCell.INACTIVE) {
                ContextMenu durationMenu = new ContextMenu();
                for (int d = 1; d <= 16; d++) {
                    final int dur = d;
                    MenuItem item = new MenuItem("Duration: " + d + " step" + (d > 1 ? "s" : ""));
                    item.setOnAction(event -> instrumentCell.setDuration(dur, orchestra));
                    durationMenu.getItems().add(item);
                }
                durationMenu.getItems().add(new SeparatorMenuItem());
                MenuItem customItem = new MenuItem("Custom duration...");
                customItem.setOnAction(event -> {
                    TextInputDialog dialog = new TextInputDialog(String.valueOf(instrumentCell.getDuration()));
                    dialog.setTitle("Set Duration");
                    dialog.setHeaderText("Enter duration in steps:");
                    dialog.showAndWait().ifPresent(result -> {
                        try {
                            int dur = Integer.parseInt(result);
                            if (dur >= 1) {
                                instrumentCell.setDuration(dur, orchestra);
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







