package com.uniqueapps.music;

import java.util.ArrayList;
import java.util.List;

public class Step {

    private final int index;
    private final List<InstrumentCell> cells = new ArrayList<>();

    public Step(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public List<InstrumentCell> getCells() {
        return cells;
    }
}

