package io.github.appinventor.legospikeprime;

import android.os.Handler;
import android.os.Looper;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

import org.json.JSONObject;

@SimpleObject(external = true)
@DesignerComponent(version = 1,
    description = "Controls the speaker on a LEGO SPIKE Prime hub (beeps/tones only). "
        + "The hub speaker cannot play named sound clips — for recorded or named audio, "
        + "use App Inventor's built-in Sound or Player component (plays on the phone). "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeSound extends AndroidNonvisibleComponent
        implements LegoSpikeConnectivity.HubDataListener {

    private LegoSpikeConnectivity connectivity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int volume = 50;

    public LegoSpikeSound(ComponentContainer container) {
        super(container.$form());
    }

    // =========================================================================
    // Connectivity
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT
        + ":io.github.appinventor.legospikeprime.LegoSpikeConnectivity")
    public void Connectivity(Component component) {
        if (this.connectivity != null) this.connectivity.removeDataListener(this);
        if (component instanceof LegoSpikeConnectivity) {
            this.connectivity = (LegoSpikeConnectivity) component;
            this.connectivity.addDataListener(this);
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    public Component Connectivity() { return connectivity; }

    // =========================================================================
    // Sound blocks
    // =========================================================================

    @SimpleFunction(description = "Play a beep at the given frequency (Hz) for a duration (ms).")
    public void Beep(int freq, int duration) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sound.beep")
            .withParam("freq", freq).withParam("duration", duration));
    }

    @SimpleFunction(description =
        "Play a beep at the given pitch (Hz) for a number of seconds.")
    public void PlayBeepForSeconds(int freq, double seconds) {
        Beep(freq, (int)(seconds * 1000));
    }

    @SimpleFunction(description = "Start playing a continuous beep at the given frequency.")
    public void StartPlayingBeep(int freq) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sound.beep").withParam("freq", freq));
    }

    @SimpleFunction(description = "Stop all sounds on the hub.")
    public void StopAllSounds() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sound.stop"));
    }

    @SimpleFunction(description = "Set the hub speaker volume (0–100).")
    public void SetVolume(int level) {
        volume = Math.max(0, Math.min(100, level));
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sound.set_volume")
            .withParam("level", volume));
    }

    @SimpleFunction(description =
        "Get the current volume level. Fires VolumeRead when the hub confirms.")
    public void GetVolume() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sound.read")
            .withParam("metric", "volume"));
    }

    // =========================================================================
    // Events
    // =========================================================================

    @SimpleEvent(description = "Fired when the hub confirms the current volume level.")
    public void VolumeRead(int level) {
        EventDispatcher.dispatchEvent(this, "VolumeRead", level);
    }

    // =========================================================================
    // HubDataListener
    // =========================================================================
    @Override
    public void onHubData(String data) {
        if (data == null || !data.startsWith("{")) return;
        try {
            JSONObject obj = new JSONObject(data);
            String event = obj.optString("event");
            if ("sound".equals(event)) {
                String metric = obj.optString("metric");
                if ("volume".equals(metric)) {
                    final int v = obj.optInt("value", volume);
                    mainHandler.post(() -> VolumeRead(v));
                }
            }
        } catch (Exception ignored) {}
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
