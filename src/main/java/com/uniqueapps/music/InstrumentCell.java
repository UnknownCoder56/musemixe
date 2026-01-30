package com.uniqueapps.music;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import javax.sound.midi.Instrument;

public class InstrumentCell extends StackPane {

    @FXML private final Label instrumentLabel;

    public static final int INACTIVE = -1;
    public static final int DRUM = 1000;
    private int instrument;
    private int duration = 1;
    private final int row;
    private final int column;

    public InstrumentCell(EventHandler<MouseEvent> mouseHandler, int row, int column) {
        super();
        setStyle("-fx-border-color: gray; -fx-border-width: 1px;");
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        GridPane.setHgrow(this, Priority.ALWAYS);
        GridPane.setVgrow(this, Priority.ALWAYS);
        setOnMouseClicked(mouseHandler);
        this.row = row;
        this.column = column;
        this.instrument = INACTIVE;

        Label label = new Label();
        label.setFont(Font.font(label.getFont().getFamily(), FontWeight.BOLD, label.getFont().getSize() + 2));
        label.setTextAlignment(TextAlignment.CENTER);
        getChildren().clear();
        getChildren().add(label);
        StackPane.setAlignment(label, Pos.CENTER);
        this.instrumentLabel = label;
    }

    public int getInstrument() {
        return instrument;
    }

    public void setInstrument(int instrument, Instrument[] orchestra) {
        this.instrument = instrument;
        updateLabel(orchestra);
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration, Instrument[] orchestra) {
        this.duration = Math.max(1, duration);
        updateLabel(orchestra);
    }

    public void setInstrumentAndDuration(int instrument, int duration, Instrument[] orchestra) {
        this.instrument = instrument;
        this.duration = Math.max(1, duration);
        updateLabel(orchestra);
    }

    private void updateLabel(Instrument[] orchestra) {
        if (instrument == INACTIVE) {
            this.instrumentLabel.setText("");
        } else {
            String name = (instrument == DRUM) ? "Drum Kit" : orchestra[instrument].getName().trim();
            this.instrumentLabel.setText(duration > 1 ? name + " (" + duration + ")" : name);
        }
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }
}
