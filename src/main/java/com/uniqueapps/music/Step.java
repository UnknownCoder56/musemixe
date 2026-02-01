package com.uniqueapps.music;

import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Step {

    private final int index;
    private final ListProperty<InstrumentCellData> cells = new SimpleListProperty<>();

    public Step(int index) {
        this.index = index;
        this.cells.set(FXCollections.observableArrayList());
    }

    public int getIndex() {
        return index;
    }

    public ListProperty<InstrumentCellData> cellsProperty() {
        return cells;
    }

    public ObservableList<InstrumentCellData> getCells() {
        return cells.get();
    }
}

