package org.webbeans.windows;

/** The NetBeans-familiar modes shipped by the platform. */
@Mode.Registrations({
    @Mode.Registration(name = "explorer", side = Mode.Side.LEFT, defaultSize = 260, position = 100, displayName = "#CTL_Mode_explorer"),
    @Mode.Registration(name = "navigator", side = Mode.Side.LEFT, defaultSize = 260, position = 200, displayName = "#CTL_Mode_navigator"),
    @Mode.Registration(name = "editor", side = Mode.Side.CENTER, position = 100, permanent = true, floatable = false, displayName = "#CTL_Mode_editor"),
    @Mode.Registration(name = "properties", side = Mode.Side.RIGHT, defaultSize = 300, position = 100, displayName = "#CTL_Mode_properties"),
    @Mode.Registration(name = "output", side = Mode.Side.BOTTOM, defaultSize = 220, position = 100, displayName = "#CTL_Mode_output")
})
public final class StandardModes {

    private StandardModes() {
    }
}
