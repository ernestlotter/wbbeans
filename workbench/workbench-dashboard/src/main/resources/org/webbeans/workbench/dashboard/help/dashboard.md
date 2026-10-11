# Dashboard

The **Dashboard** shows three live charts fed from the server once a second:

- **CPU %** — a line with the last two minutes; the red band marks the "hot" zone above 90 %. Drag across the
  chart to zoom into a time range; double-click to reset.
- **Requests / s** — bars per path (`/api`, `/static`, `/ws`, `/auth`).
- **Traffic by source** — a donut of browsers, mobile, API clients and bots.

Hover a chart for exact values. Click a chart to make it the current one: **Tools ▸ Export chart as PNG** (or the
toolbar button) saves it as an image and reports the size in a notification.

When the CPU exceeds 90 %, a notification appears (at most once a minute) in every tab, and the Output window
logs it. The refresh rate, the history length and the notification can be changed in
[Dashboard options](dashboard-options.md).
