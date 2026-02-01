package com.uniqueapps.musemixe;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javax.sound.midi.*;
import java.util.HashMap;
import java.util.Map;

public class Player implements Runnable {

    private final int instrument;
    private final int note;
    private final double durationSeconds;
    private static MidiChannel[] channels;
    private static Instrument[] orchestra;
    private static final Object channelLock = new Object();
    private static int nextChannel = 0;
    private static final int[] channelProgram = new int[16];
    private static final int DRUM_CHANNEL = 9;
    private static final Map<Integer, Integer> activeNotes = new HashMap<>();

    static {
        try {
            Synthesizer synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();
            channels = synthesizer.getChannels();
            orchestra = synthesizer.getLoadedInstruments();
            for (int i = 0; i < 16; i++) channelProgram[i] = -1;
        } catch (MidiUnavailableException e) {
            Platform.runLater(() -> {
                new Alert(Alert.AlertType.ERROR, "MIDI system is unavailable").showAndWait();
                System.exit(1);
            });
        }
    }

    public Player(int instrument, int note, double durationSeconds) {
        this.instrument = instrument;
        this.note = note;
        this.durationSeconds = durationSeconds;
    }

    @Override
    public void run() {
        try {
            playNote();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Playback error: " + e.getMessage()).showAndWait());
        }
    }

    private void playNote() throws InterruptedException {
        playNoteOn(instrument, note);
        Thread.sleep((long) (durationSeconds * 1000));
        playNoteOff(instrument, note);
    }

    private static int allocateChannel() {
        int ch;
        do { ch = nextChannel++ % 16; } while (ch == DRUM_CHANNEL);
        return ch;
    }

    private static void setInstrument(int channelIndex, MidiChannel channel, int instrumentIndex) {
        int programNumber = orchestra[instrumentIndex].getPatch().getProgram();
        int bankNumber = orchestra[instrumentIndex].getPatch().getBank();
        int fullProgram = (bankNumber << 7) | programNumber;
        if (channelProgram[channelIndex] != fullProgram) {
            channel.controlChange(0, bankNumber / 128);
            channel.controlChange(32, bankNumber % 128);
            channel.programChange(programNumber);
            channelProgram[channelIndex] = fullProgram;
        }
    }

    public static void playNoteOn(int instrumentIndex, int note) {
        if (instrumentIndex == InstrumentCellData.DRUM) {
            MidiChannel drumChannel = channels[DRUM_CHANNEL];
            drumChannel.noteOn(note, 100);
            return;
        }
        synchronized (channelLock) {
            int key = (instrumentIndex << 8) | note;
            if (activeNotes.containsKey(key)) return;
            int channelIndex = allocateChannel();
            MidiChannel channel = channels[channelIndex];
            setInstrument(channelIndex, channel, instrumentIndex);
            channel.noteOn(note, 100);
            activeNotes.put(key, channelIndex);
        }
    }

    public static void playNoteOff(int instrumentIndex, int note) {
        if (instrumentIndex == InstrumentCellData.DRUM) {
            MidiChannel drumChannel = channels[DRUM_CHANNEL];
            drumChannel.noteOff(note);
            return;
        }
        synchronized (channelLock) {
            int key = (instrumentIndex << 8) | note;
            Integer channelIndex = activeNotes.remove(key);
            if (channelIndex != null) channels[channelIndex].noteOff(note);
        }
    }
}
