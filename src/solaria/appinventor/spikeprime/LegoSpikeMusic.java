package solaria.appinventor.spikeprime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.Options;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;

/**
 * LegoSpikeMusic — play melodic notes and rests on the hub speaker.
 * The hub speaker produces tones only; drums and instrument timbres are not supported.
 * Tempo controls the beat duration. PlayNoteForBeats uses MIDI note numbers
 * (60 = middle C, 69 = A4=440 Hz). Each beat = 60000 / tempo milliseconds.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 1,
    description = "Plays notes and rests on the LEGO SPIKE Prime hub speaker. "
        + "Uses MIDI note numbers (60 = middle C). "
        + "The hub speaker is a tone generator — drums and instrument timbres are not supported. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeMusic extends AndroidNonvisibleComponent {

    private LegoSpikeConnectivity connectivity;
    private int tempo = 120;  // beats per minute

    public LegoSpikeMusic(ComponentContainer container) {
        super(container.$form());
    }

    // =========================================================================
    // Connectivity
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT
        + ":solaria.appinventor.spikeprime.LegoSpikeConnectivity")
    public void Connectivity(Component component) {
        if (component instanceof LegoSpikeConnectivity) {
            this.connectivity = (LegoSpikeConnectivity) component;
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection (read-only)")
    public Component Connectivity() { return connectivity; }

    // =========================================================================
    // Tempo
    // =========================================================================
    @SimpleFunction(description = "Set the tempo in beats per minute (default 120).")
    public void SetTempo(int bpm) {
        tempo = Math.max(1, bpm);
    }

    @SimpleFunction(description = "Change the tempo by the given number of BPM (positive = faster).")
    public void ChangeTempo(int delta) {
        tempo = Math.max(1, tempo + delta);
    }

    @SimpleFunction(description = "Return the current tempo in beats per minute.")
    public int GetTempo() {
        return tempo;
    }

    // =========================================================================
    // Note / Rest
    // =========================================================================
    @SimpleFunction(description =
        "Play a note for the given number of beats. "
        + "note: select from the MusicNote dropdown (C3–C6). "
        + "beats: duration in beats (e.g. 1.0 = one beat, 0.5 = half beat). "
        + "For sharps/flats not in the dropdown, use NoteConstant or a raw MIDI number with a Math block. "
        + "Blocking — the hub waits for the note to finish before playing the next one.")
    public void PlayNoteForBeats(@Options(MusicNote.class) String note, double beats) {
        if (!checkConnected()) return;
        MusicNote n = MusicNote.fromUnderlyingValue(note);
        int midiNote = n != null ? n.getMidi() : 60;
        // Convert MIDI note to frequency: f = 440 * 2^((note-69)/12)
        int freq = (int) Math.round(440.0 * Math.pow(2.0, (midiNote - 69) / 12.0));
        freq = Math.max(20, Math.min(20000, freq));
        int ms = (int) Math.round(beats * 60000.0 / tempo);
        connectivity.sendSSP(new SSPMessage("sound.beep")
            .withParam("freq", freq).withParam("duration", ms).withParam("wait", true));
    }

    @SimpleFunction(description =
        "Wait silently for the given number of beats before playing the next note.")
    public void RestForBeats(double beats) {
        if (!checkConnected()) return;
        int ms = (int) Math.round(beats * 60000.0 / tempo);
        connectivity.sendSSP(new SSPMessage("sound.rest").withParam("duration", ms));
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private boolean checkConnected() {
        if (connectivity == null) { reportError("Connectivity not set"); return false; }
        if (!connectivity.IsConnected()) { reportError("Not connected to hub"); return false; }
        return true;
    }

    private void reportError(String msg) {
        if (connectivity != null) connectivity.ErrorOccurred(msg);
    }
}
