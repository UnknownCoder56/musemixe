package com.uniqueapps.music;

import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;

public class NoteHeaderCell extends Label {

    public static final LinkedHashMap<Integer, String> NOTE_MAP = new LinkedHashMap<>();
    static {
        NOTE_MAP.put(21, "A0");
        NOTE_MAP.put(22, "A#0");
        NOTE_MAP.put(23, "B0");
        NOTE_MAP.put(24, "C1");
        NOTE_MAP.put(25, "C#1");
        NOTE_MAP.put(26, "D1");
        NOTE_MAP.put(27, "D#1");
        NOTE_MAP.put(28, "E1");
        NOTE_MAP.put(29, "F1");
        NOTE_MAP.put(30, "F#1");
        NOTE_MAP.put(31, "G1");
        NOTE_MAP.put(32, "G#1");
        NOTE_MAP.put(33, "A1");
        NOTE_MAP.put(34, "A#1");
        NOTE_MAP.put(35, "B1 / Bass Drum 2");
        NOTE_MAP.put(36, "C2 / Bass Drum 1");
        NOTE_MAP.put(37, "C#2 / Side Stick");
        NOTE_MAP.put(38, "D2 / Acoustic Snare");
        NOTE_MAP.put(39, "D#2 / Hand Clap");
        NOTE_MAP.put(40, "E2 / Electric Snare");
        NOTE_MAP.put(41, "F2 / Low Floor Tom");
        NOTE_MAP.put(42, "F#2 / Closed Hi-Hat");
        NOTE_MAP.put(43, "G2 / High Floor Tom");
        NOTE_MAP.put(44, "G#2 / Pedal Hi-Hat");
        NOTE_MAP.put(45, "A2 / Low Tom");
        NOTE_MAP.put(46, "A#2 / Open Hi-Hat");
        NOTE_MAP.put(47, "B2 / Low-Mid Tom");
        NOTE_MAP.put(48, "C3 / Hi-Mid Tom");
        NOTE_MAP.put(49, "C#3 / Crash Cymbal 1");
        NOTE_MAP.put(50, "D3 / High Tom");
        NOTE_MAP.put(51, "D#3 / Ride Cymbal 1");
        NOTE_MAP.put(52, "E3 / Chinese Cymbal");
        NOTE_MAP.put(53, "F3 / Ride Bell");
        NOTE_MAP.put(54, "F#3 / Tambourine");
        NOTE_MAP.put(55, "G3 / Splash Cymbal");
        NOTE_MAP.put(56, "G#3 / Cowbell");
        NOTE_MAP.put(57, "A3 / Crash Cymbal 2");
        NOTE_MAP.put(58, "A#3 / Vibraslap");
        NOTE_MAP.put(59, "B3 / Ride Cymbal 2");
        NOTE_MAP.put(60, "C4 / Hi Bongo");
        NOTE_MAP.put(61, "C#4 / Low Bongo");
        NOTE_MAP.put(62, "D4 / Mute Hi Conga");
        NOTE_MAP.put(63, "D#4 / Open Hi Conga");
        NOTE_MAP.put(64, "E4 / Low Conga");
        NOTE_MAP.put(65, "F4 / High Timbale");
        NOTE_MAP.put(66, "F#4 / Low Timbale");
        NOTE_MAP.put(67, "G4 / High Agogo");
        NOTE_MAP.put(68, "G#4 / Low Agogo");
        NOTE_MAP.put(69, "A4 / Cabasa");
        NOTE_MAP.put(70, "A#4 / Maracas");
        NOTE_MAP.put(71, "B4 / Short Whistle");
        NOTE_MAP.put(72, "C5 / Long Whistle");
        NOTE_MAP.put(73, "C#5 / Short Guiro");
        NOTE_MAP.put(74, "D5 / Long Guiro");
        NOTE_MAP.put(75, "D#5 / Claves");
        NOTE_MAP.put(76, "E5 / Hi Wood Block");
        NOTE_MAP.put(77, "F5 / Low Wood Block");
        NOTE_MAP.put(78, "F#5 / Mute Cuica");
        NOTE_MAP.put(79, "G5 / Open Cuica");
        NOTE_MAP.put(80, "G#5 / Mute Triangle");
        NOTE_MAP.put(81, "A5 / Open Triangle");
        NOTE_MAP.put(82, "A#5");
        NOTE_MAP.put(83, "B5");
        NOTE_MAP.put(84, "C6");
        NOTE_MAP.put(85, "C#6");
        NOTE_MAP.put(86, "D6");
        NOTE_MAP.put(87, "D#6");
        NOTE_MAP.put(88, "E6");
        NOTE_MAP.put(89, "F6");
        NOTE_MAP.put(90, "F#6");
        NOTE_MAP.put(91, "G6");
        NOTE_MAP.put(92, "G#6");
        NOTE_MAP.put(93, "A6");
        NOTE_MAP.put(94, "A#6");
        NOTE_MAP.put(95, "B6");
        NOTE_MAP.put(96, "C7");
        NOTE_MAP.put(97, "C#7");
        NOTE_MAP.put(98, "D7");
        NOTE_MAP.put(99, "D#7");
        NOTE_MAP.put(100, "E7");
        NOTE_MAP.put(101, "F7");
        NOTE_MAP.put(102, "F#7");
        NOTE_MAP.put(103, "G7");
        NOTE_MAP.put(104, "G#7");
        NOTE_MAP.put(105, "A7");
        NOTE_MAP.put(106, "A#7");
        NOTE_MAP.put(107, "B7");
        NOTE_MAP.put(108, "C8");
    }
    private int note;
    private final int row;
    private final int column;
    private static final PseudoClass HIGHLIGHT = PseudoClass.getPseudoClass("highlighted");

    public NoteHeaderCell(EventHandler<MouseEvent> mouseHandler, int note, int row, int column) {
        super();
        setText(NOTE_MAP.get(note));
        getStyleClass().add("note-header-cell");
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(this, Priority.ALWAYS);
        HBox.setHgrow(this, Priority.ALWAYS);
        setOnMouseClicked(mouseHandler);

        this.note = note;
        this.row = row;
        this.column = column;
    }

    public int getNote() {
        return note;
    }

    public void setNote(int note) {
        this.note = note;
        setText(NOTE_MAP.get(note));
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public void highlightOn() {
        pseudoClassStateChanged(HIGHLIGHT, true);
    }

    public void highlightOff() {
        pseudoClassStateChanged(HIGHLIGHT, false);
    }
}
