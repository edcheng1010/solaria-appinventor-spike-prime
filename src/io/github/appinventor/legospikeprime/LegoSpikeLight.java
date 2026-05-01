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
 * MVP blocks: TurnOnLightMatrix, TurnOffLightMatrix, WriteOnLightMatrix,
 *             SetPixelBrightness, SetCenterButtonLight.
 *
 * Image names for TurnOnLightMatrix:
 *   HEART, HEART_SMALL, HAPPY, SMILE, SAD, CONFUSED, ANGRY, ASLEEP,
 *   SURPRISED, YES, NO, ARROW_N, ARROW_E, ARROW_S, ARROW_W
 *
 * Color names for SetCenterButtonLight:
 *   RED, GREEN, BLUE, YELLOW, WHITE, CYAN, MAGENTA, ORANGE, BLACK
 *
 * Dependency: set the Connectivity property to a LegoSpikeConnectivity instance.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 2,
    description = "Controls the 5x5 light matrix and center button LED on a LEGO SPIKE Prime hub. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeLight extends AndroidNonvisibleComponent {

    private LegoSpikeConnectivity connectivity;

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
    // MVP blocks
    // =========================================================================

    /**
     * Turn on the 5×5 light matrix with a named image.
     * Available images: HEART, HEART_SMALL, HAPPY, SMILE, SAD, CONFUSED, ANGRY,
     * ASLEEP, SURPRISED, YES, NO, ARROW_N, ARROW_E, ARROW_S, ARROW_W.
     *
     * @param image image name (case-insensitive)
     */
    @SimpleFunction(description =
        "Turn on the 5x5 light matrix with a named image. "
        + "Images: HEART, HAPPY, SMILE, SAD, CONFUSED, ANGRY, ASLEEP, SURPRISED, "
        + "YES, NO, ARROW_N, ARROW_E, ARROW_S, ARROW_W.")
    public void TurnOnLightMatrix(@Options(LightMatrixImage.class) String image) {
        if (!checkConnected()) return;
        if (image == null || image.isEmpty()) image = "HAPPY";
        connectivity.sendCommand("LGT:ON:" + image.toUpperCase().trim());
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
        "Set the brightness (0-100) of a single pixel on the 5x5 light matrix. "
        + "x: column 0-4 (left to right), y: row 0-4 (top to bottom).")
    public void SetPixelBrightness(int x, int y, int brightness) {
        if (!checkConnected()) return;
        x          = Math.max(0, Math.min(4, x));
        y          = Math.max(0, Math.min(4, y));
        brightness = Math.max(0, Math.min(100, brightness));
        connectivity.sendCommand(String.format("LGT:PIX:%d:%d:%d", x, y, brightness));
    }

    /**
     * Set the color of the center button LED.
     * Color names: RED, GREEN, BLUE, YELLOW, WHITE, CYAN, MAGENTA, ORANGE, BLACK.
     *
     * @param color color name (case-insensitive)
     */
    @SimpleFunction(description =
        "Set the center button LED color. "
        + "Colors: RED, GREEN, BLUE, YELLOW, WHITE, CYAN, MAGENTA, ORANGE, BLACK.")
    public void SetCenterButtonLight(@Options(HubLightColor.class) String color) {
        if (!checkConnected()) return;
        if (color == null || color.isEmpty()) color = "WHITE";
        connectivity.sendCommand("LGT:BTN:" + color.toUpperCase().trim());
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
