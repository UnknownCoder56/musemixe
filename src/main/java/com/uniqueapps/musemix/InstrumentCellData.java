package com.uniqueapps.musemix;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javax.sound.midi.Instrument;

public class InstrumentCellData {

    public static final int INACTIVE = -1;
    public static final int DRUM = 1000;
    private int instrument = INACTIVE;
    private int duration = 1;
    private final int row;
    private final int column;
    private final StringProperty labelProperty = new SimpleStringProperty("");

    public InstrumentCellData(int row, int column) {
        this.row = row;
        this.column = column;
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
            labelProperty.set("");
        } else {
            String name = (instrument == DRUM) ? "Drum Kit" : orchestra[instrument].getName().trim();
            labelProperty.set(duration > 1 ? name + " (" + duration + ")" : name);
        }
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public StringProperty labelProperty() {
        return labelProperty;
    }
}
