package solaria.appinventor.spikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/**
 * Musical note constants (C3–C6, all 12 semitones) for PlayNoteForBeats.
 * Sharps written as e.g. "Csharp4" (s = sharp). getMidi() returns the MIDI note number.
 */
public enum MusicNote implements OptionList<String> {
    C3("C3", 48),  Csharp3("Csharp3", 49), D3("D3", 50),  Dsharp3("Dsharp3", 51),
    E3("E3", 52),  F3("F3", 53),   Fsharp3("Fsharp3", 54), G3("G3", 55),
    Gsharp3("Gsharp3", 56), A3("A3", 57),  Asharp3("Asharp3", 58), B3("B3", 59),
    C4("C4", 60),  Csharp4("Csharp4", 61), D4("D4", 62),  Dsharp4("Dsharp4", 63),
    E4("E4", 64),  F4("F4", 65),   Fsharp4("Fsharp4", 66), G4("G4", 67),
    Gsharp4("Gsharp4", 68), A4("A4", 69),  Asharp4("Asharp4", 70), B4("B4", 71),
    C5("C5", 72),  Csharp5("Csharp5", 73), D5("D5", 74),  Dsharp5("Dsharp5", 75),
    E5("E5", 76),  F5("F5", 77),   Fsharp5("Fsharp5", 78), G5("G5", 79),
    Gsharp5("Gsharp5", 80), A5("A5", 81),  Asharp5("Asharp5", 82), B5("B5", 83),
    C6("C6", 84),  Csharp6("Csharp6", 85), D6("D6", 86),  Dsharp6("Dsharp6", 87),
    E6("E6", 88),  F6("F6", 89),   Fsharp6("Fsharp6", 90), G6("G6", 91),
    Gsharp6("Gsharp6", 92), A6("A6", 93),  Asharp6("Asharp6", 94), B6("B6", 95),
    C7("C7", 96);

    private final String display;
    private final int midi;

    MusicNote(String d, int m) { display = d; midi = m; }

    @Override public String toUnderlyingValue() { return display; }
    public int getMidi() { return midi; }

    private static final Map<String, MusicNote> lookup = new HashMap<>();
    static { for (MusicNote n : MusicNote.values()) lookup.put(n.toUnderlyingValue(), n); }
    public static MusicNote fromUnderlyingValue(String v) { return lookup.get(v); }
}
