package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.annotations.DesignerComponent;
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

    private String image       = "Happy";
    private String buttonColor = "White";

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
    public void Image(String value) {
        if (value != null && !value.trim().isEmpty()) {
            image = value.trim(); // stored as-is; hub uses .upper() before IMAGES lookup
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Image to show when TurnOnLightMatrix is called.")
    public String Image() { return image; }

    // =========================================================================
    // ButtonColor property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Color of the center button LED when SetCenterButtonLight is called. "
            + "Use the color constant blocks (Red, Green, Blue, …).")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"Black", "Red", "Green", "Yellow", "Blue", "White",
                        "Cyan", "Magenta", "Orange", "Violet", "Azure"},
        defaultValue = "White")
    public void ButtonColor(String value) {
        if (value != null && !value.trim().isEmpty()) {
            String v = value.trim();
            buttonColor = v.substring(0, 1).toUpperCase() + v.substring(1).toLowerCase();
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Color of the center button LED when SetCenterButtonLight is called.")
    public String ButtonColor() { return buttonColor; }

    // =========================================================================
    // Light control blocks
    // =========================================================================
    @SimpleFunction(description =
        "Turn on the 5x5 light matrix with the configured Image.")
    public void TurnOnLightMatrix() {
        if (!checkConnected()) return;
        connectivity.sendCommand("LGT:ON:" + image);
    }

    @SimpleFunction(description = "Turn off the 5x5 light matrix")
    public void TurnOffLightMatrix() {
        if (!checkConnected()) return;
        connectivity.sendCommand("LGT:OFF");
    }

    @SimpleFunction(description = "Scroll text across the 5x5 light matrix")
    public void WriteOnLightMatrix(String text) {
        if (!checkConnected()) return;
        if (text == null) text = "";
        connectivity.sendCommand("LGT:TXT:" + text);
    }

    @SimpleFunction(description =
        "Set the brightness (0–100) of a single pixel. "
        + "x: column 0–4 (left to right), y: row 0–4 (top to bottom).")
    public void SetPixelBrightness(int x, int y, int brightness) {
        if (!checkConnected()) return;
        connectivity.sendCommand(String.format("LGT:PIX:%d:%d:%d",
            Math.max(0, Math.min(4, x)),
            Math.max(0, Math.min(4, y)),
            Math.max(0, Math.min(100, brightness))));
    }

    @SimpleFunction(description =
        "Set the center button LED to the configured ButtonColor.")
    public void SetCenterButtonLight() {
        if (!checkConnected()) return;
        connectivity.sendCommand("LGT:BTN:" + buttonColor);
    }

    // =========================================================================
    // Image constants — drag into the Image property setter block
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: Heart")
    public String Heart() { return "Heart"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: HeartSmall")
    public String HeartSmall() { return "HeartSmall"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: Happy")
    public String Happy() { return "Happy"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: Smile")
    public String Smile() { return "Smile"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: Sad")
    public String Sad() { return "Sad"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: Confused")
    public String Confused() { return "Confused"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: Angry")
    public String Angry() { return "Angry"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: Asleep")
    public String Asleep() { return "Asleep"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: Surprised")
    public String Surprised() { return "Surprised"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: Yes")
    public String Yes() { return "Yes"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: No")
    public String No() { return "No"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: ArrowNorth")
    public String ArrowNorth() { return "ArrowNorth"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: ArrowEast")
    public String ArrowEast() { return "ArrowEast"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: ArrowSouth")
    public String ArrowSouth() { return "ArrowSouth"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Image constant: ArrowWest")
    public String ArrowWest() { return "ArrowWest"; }

    // =========================================================================
    // Button color constants — drag into the ButtonColor property setter block
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Color constant: Black")
    public String Black() { return "Black"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Color constant: Red")
    public String Red() { return "Red"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Color constant: Green")
    public String Green() { return "Green"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Color constant: Yellow")
    public String Yellow() { return "Yellow"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Color constant: Blue")
    public String Blue() { return "Blue"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Color constant: White")
    public String White() { return "White"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Color constant: Cyan")
    public String Cyan() { return "Cyan"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Color constant: Magenta")
    public String Magenta() { return "Magenta"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Color constant: Orange")
    public String Orange() { return "Orange"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Color constant: Violet")
    public String Violet() { return "Violet"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR, description = "Color constant: Azure")
    public String Azure() { return "Azure"; }

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
