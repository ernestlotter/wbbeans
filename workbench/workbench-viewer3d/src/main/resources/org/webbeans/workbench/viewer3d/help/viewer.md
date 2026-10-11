# 3D Viewer

The **3D Viewer** shows a turbine: hub, shaft, housing, a ring of blades and three temperature sensors.

## Navigating

- Drag with the left mouse button to orbit, with the right button to pan; scroll to zoom.
- The toolbar above the viewport fits the view to the model, resets the camera, toggles the grid and saves a
  PNG. **Snapshot** in the toolbar and under **Tools** reports the size of a server-side rendering.

## Selecting parts

Click a part to select it — the selection is shared with the [Parts](parts.md) tree and the Properties window.
Double-click a part to fly the camera to it. Hovering highlights the part under the pointer.

## Changing the model

- **More blades** / **Fewer blades** (toolbar or **Tools**) change the blade count; the scene follows.
- **View ▸ Spin rotor** starts or stops the rotation; the choice is remembered for your account.
- A part's colour can be edited in the Properties window as a hex value such as `#d9822b`.

Sensors heat up while the rotor spins and cool down when it stops. A sensor above 85 °C turns red and its label
shows the reading; the Parts tree shows it in red as well.
