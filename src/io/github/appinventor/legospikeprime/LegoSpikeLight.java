package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.Options;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.HubLightColor;
import com.google.appinventor.components.common.LightMatrixImage;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;

/**
 * LegoSpikeLight — 5×5 light matrix and center button LED control.
 * Matches LEGO SPIKE Prime "Light Blocks Category".
 *
 * Configure Image and ButtonColor in the Designer or via blocks, then call
 * the corresponding action functions.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 4,
    description = "Controls the 5x5 light matrix and center button LED on a LEGO SPIKE Prime hub. "
        + "Set Image and ButtonColor, then call TurnOnLightMatrix or SetCenterButtonLight. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeLight extends AndroidNonvisibleComponent {

    private LegoSpikeConnectivity connectivity;

    private String image       = "happy";
    private String buttonColor = "white";

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
        description = "Image to show when TurnOnLightMatrix is called")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"Heart", "HeartSmall", "Happy", "Smile", "Sad", "Confused",
                        "Angry", "Asleep", "Surprised", "Yes", "No",
                        "ArrowNorth", "ArrowEast", "ArrowSouth", "ArrowWest"},
        defaultValue = "Happy")
    public void Image(@Options(LightMatrixImage.class) String value) {
        if (value != null && !value.trim().isEmpty()) {
            image = value.trim().toLowerCase();
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Image to show when TurnOnLightMatrix is called")
    public String Image() { return image; }

    // =========================================================================
    // ButtonColor property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Color of the center button LED when SetCenterButtonLight is called")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"Black", "Red", "Green", "Yellow", "Blue", "White",
                        "Cyan", "Magenta", "Orange", "Violet", "Azure"},
        defaultValue = "White")
    public void ButtonColor(@Options(HubLightColor.class) String value) {
        if (value != null && !value.trim().isEmpty()) {
            buttonColor = value.trim().toLowerCase();
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Color of the center button LED when SetCenterButtonLight is called")
    public String ButtonColor() { return buttonColor; }

    // =========================================================================
    // Light control blocks
    // =========================================================================

    /**
     * Turn on the 5×5 light matrix with the configured Image.
     */
    @SimpleFunction(description =
        "Turn on the 5x5 light matrix with the configured Image.")
    public void TurnOnLightMatrix() {
        if (!checkConnected()) return;
        connectivity.sendCommand("LGT:ON:" + image);
    }

    /** Turn off the 5×5 light matrix. */
    @SimpleFunction(description = "Turn off the 5x5 light matrix")
    public void TurnOffLightMatrix() {
        if (!checkConnected()) return;
        connectivity.sendCommand("LGT:OFF");
    }

    /**
     * Scroll text across the 5×5 light matrix.
     *
     * @param text text to display (numbers and letters)
     */
    @SimpleFunction(description = "Scroll text across the 5x5 light matrix")
    public void WriteOnLightMatrix(String text) {
        if (!checkConnected()) return;
        if (text == null) text = "";
        connectivity.sendCommand("LGT:TXT:" + text);
    }

    /**
     * Set the brightness of a single pixel on the 5×5 light matrix.
     *
     * @param x          column 0–4 (left to right)
     * @param y          row 0–4 (top to bottom)
     * @param brightness 0–100 percent
     */
    @SimpleFunction(description =
        "Set the brightness (0–100) of a single pixel. "
        + "x: column 0–4 (left to right), y: row 0–4 (top to bottom).")
    public void SetPixelBrightness(int x, int y, int brightness) {
        if (!checkConnected()) return;
        x          = Math.max(0, Math.min(4, x));
        y          = Math.max(0, Math.min(4, y));
        brightness = Math.max(0, Math.min(100, brightness));
        connectivity.sendCommand(String.format("LGT:PIX:%d:%d:%d", x, y, brightness));
    }

    /**
     * Set the center button LED to the configured ButtonColor.
     */
    @SimpleFunction(description =
        "Set the center button LED to the configured ButtonColor.")
    public void SetCenterButtonLight() {
        if (!checkConnected()) return;
        connectivity.sendCommand("LGT:BTN:" + buttonColor);
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
