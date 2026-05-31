package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.Options;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;

@SimpleObject(external = true)
@DesignerComponent(version = 5,
    description = "Controls the 5x5 light matrix on a LEGO SPIKE Prime hub. "
        + "Set Image, then call TurnOnLightMatrix, TurnOffLightMatrix, "
        + "WriteOnLightMatrix, or SetPixelBrightness. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeLight extends AndroidNonvisibleComponent {

    private LegoSpikeConnectivity connectivity;
    private String image = "Happy";

    public LegoSpikeLight(ComponentContainer container) {
        super(container.$form());
    }

    // =========================================================================
    // Connectivity property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT
        + ":io.github.appinventor.legospikeprime.LegoSpikeConnectivity")
    public void Connectivity(Component component) {
        if (component instanceof LegoSpikeConnectivity) {
            this.connectivity = (LegoSpikeConnectivity) component;
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    public Component Connectivity() { return connectivity; }

    // =========================================================================
    // Image property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Image to show when TurnOnLightMatrix is called. "
            + "Use the image constant blocks (Heart, Happy, Sad, …).")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"Heart", "HeartSmall", "Happy", "Smile", "Sad", "Confused",
                        "Angry", "Asleep", "Surprised", "Yes", "No",
                        "ArrowNorth", "ArrowEast", "ArrowSouth", "ArrowWest"},
        defaultValue = "Happy")
    public void Image(@Options(LightMatrixImage.class) String value) {
        if (value != null && !value.trim().isEmpty()) {
            image = value.trim();
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Image to show when TurnOnLightMatrix is called.")
    public String Image() { return image; }

    // =========================================================================
    // Light control blocks
    // =========================================================================
    @SimpleFunction(description =
        "Turn on the 5x5 light matrix with the configured Image.")
    public void TurnOnLightMatrix() {
        if (!checkConnected()) return;
        connectivity.sendSSP(
            new SSPMessage("led.matrix.image")
                .withPort("display")
                .withParam("image", image.toUpperCase()));
    }

    @SimpleFunction(description = "Turn off the 5x5 light matrix")
    public void TurnOffLightMatrix() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("led.matrix.clear").withPort("display"));
    }

    @SimpleFunction(description = "Scroll text across the 5x5 light matrix")
    public void WriteOnLightMatrix(String text) {
        if (!checkConnected()) return;
        if (text == null) text = "";
        connectivity.sendSSP(
            new SSPMessage("led.matrix.text")
                .withPort("display")
                .withParam("text", text));
    }

    @SimpleFunction(description =
        "Set the brightness (0–100) of a single pixel. "
        + "x: column 1–5 (left to right), y: row 1–5 (top to bottom).")
    public void SetPixelBrightness(int x, int y, int brightness) {
        if (!checkConnected()) return;
        connectivity.sendSSP(
            new SSPMessage("led.matrix.pixel")
                .withPort("display")
                .withParam("x", Math.max(1, Math.min(5, x)) - 1)
                .withParam("y", Math.max(1, Math.min(5, y)) - 1)
                .withParam("brightness", Math.max(0, Math.min(100, brightness))));
    }

    // =========================================================================
    // Phase 3 expansion blocks
    // =========================================================================

    @SimpleFunction(description =
        "Show an image on the light matrix for a set number of seconds, then clear it.")
    public void TurnOnLightMatrixForSeconds(double seconds) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("led.matrix.image")
            .withPort("display")
            .withParam("image", image.toUpperCase()));
        // Client-side delay: schedule clear via handler
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (connectivity != null && connectivity.IsConnected()) {
                connectivity.sendSSP(new SSPMessage("led.matrix.clear").withPort("display"));
            }
        }, (long)(seconds * 1000));
    }

    @SimpleFunction(description = "Set the global brightness of the light matrix (0–100).")
    public void SetLightMatrixBrightness(int level) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("led.matrix.brightness")
            .withPort("display")
            .withParam("level", Math.max(0, Math.min(100, level))));
    }

    @SimpleFunction(description =
        "Rotate the light matrix display by the given amount. "
        + "degrees: 90, 180, or 270 — added to the current orientation.")
    public void RotateLightMatrix(int degrees) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("led.matrix.rotate")
            .withPort("display")
            .withParam("degrees", degrees));
    }

    @SimpleFunction(description =
        "Set the absolute orientation of the light matrix display. rotation: 0, 90, 180, or 270.")
    public void SetLightMatrixOrientation(int rotation) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("led.matrix.orientation")
            .withPort("display")
            .withParam("rotation", rotation));
    }

    @SimpleFunction(description = "Set the center button light color.")
    public void SetCenterButtonLight(@Options(CenterButtonColor.class) String color) {
        if (!checkConnected()) return;
        CenterButtonColor c = CenterButtonColor.fromUnderlyingValue(color);
        String name = c != null ? c.toUnderlyingValue().toLowerCase() : "off";
        connectivity.sendSSP(new SSPMessage("led.set")
            .withPort("status")
            .withParam("color", name));
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
