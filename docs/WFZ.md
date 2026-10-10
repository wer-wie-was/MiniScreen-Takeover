# Experimental native WFZ renderer

Import a `.wfz` file in **Amazfit Watchfaces (experimental)**, review the compatibility report, then enable **Use WFZ watchface**. The file is not activated automatically. The pinned live preview and rear display use the same renderer. The source archive includes `samples/Native-WFZ-Demo.wfz`, a self-contained original analog/digital example.

The mode draws locally with Android Canvas. It does not boot watch firmware, execute APKs, JavaScript or Zepp bytecode, or connect to a watch. Existing notification, battery, widget and pixelshifting layers remain available. Switching away from Pebble unsubscribes the view from the existing Pebble session.

## Supported subset

- One `watchface.xml`, at the archive root or in one enclosing directory.
- `WatchFaceItem` backgrounds and images with `@wfz/` resource paths.
- `timehand` folders with `hour.png`, `minute.png` (or `minutes.png`) and optional `seconds.png`. Each image is fitted proportionally within the item's XML width/height, preserving its aspect ratio, and rotates about its centre at the item's x/y pivot. If dimensions are absent, the hour image's dimensions are used for the shared hand canvas.
- `timedigital` with bitmap glyphs from `font.xml`: `WatchFaceItem type="font" charset="…" config="@wfz/…"`.
- Stratos 3 `timedis` split seconds (`sec`, `high.x/y`, `low.x/y`) and AM/PM images; AM/PM is hidden in 24-hour mode.
- Calendar image sequences: `week` (Sunday = 0) with a matching language folder or English fallback, and `month_image` (January = 0).
- `batteryimage` sequences with `bitmapArray` and `count`, mapped proportionally to the phone battery; 5-percent buckets for a 20-frame sequence; unavailable battery data uses the first frame with `--`.
- Simple Stratos 3 time/date component children: hour, minute, second, day, month, year and weekday fields. Numeric calendar values and localized weekday names may differ from vendor-specific month/day encodings; the report flags this.
- Simple GTRWidget text fields: dataType 1 (steps), 5 (heart rate), 6 (date), 10 (phone battery); nested text/number/level items inherit that data source.
- Legacy datawidgets use native vector symbols, values, units and progress rings. Their configList alternatives can be selected per XML position (duplicate IDs are safe), and selections are stored per profile. Models 1/2 use compact layouts; date models 1/2/5/6/7 use distinct date layouts. Optional mask bitmaps are drawn over the widget. These are approximate replacements, explicitly reported. Proprietary fonts/icons/layouts bundled only in watch firmware cannot be reconstructed from a WFZ file.
- Alignment 66 = left, 68 = centre, 72 = right. Missing bitmap fonts use system text and produce a warning.

Unsupported components are listed and skipped. Animated images and other image-array variants are not implemented. Status bars show phone battery; watch connectivity indicators have no source. Weather, workout distance and unknown data types show `--`. Daily distance and active calories can be enabled separately through Health Connect; they do not substitute for workout distance. BIN, Zepp OS, APK and arbitrary executable watchfaces are not supported. A static background may load while other unsupported components remain absent; a partial report is not a promise of full compatibility.

## Settings and data

Seconds default to off. Updates are scheduled at minute boundaries, or second boundaries when seconds are enabled; there is no continuous WFZ render loop. Data changes can trigger extra drawing. Independent animated overlays still have their own update rate.

Canvas size follows the background image, otherwise the XML size or a 320 × 320 default. A source-resolution override is available for ambiguous files; it changes coordinate scaling, not format compatibility. Fit/crop keeps the aspect ratio and reserves pixelshifting space. The display is circularly clipped.

Optional health values use the existing Health Connect settings and permissions, with separate opt-in settings for daily distance and active calories. Enable health in this section and grant/select data in Optional health data. A rear-only renderer requires background health permission; the foreground preview may read with foreground access. Health reads are throttled to once per minute per view. Missing, revoked, old, or unavailable values appear as `--`; no fabricated health values are used. Steps reset by the current local date; pulse uses the existing maximum-age setting. Daily metrics require a current-day timestamp. The battery field refers to the phone. Step goals (1,000–50,000) and widget accent color are configurable. Older import reports are labeled as historical; re-import to refresh them.

Profiles include their selected WFZ assets. Remove clears the selection and deletes its managed import directory. The import is transactional, with a 20 MiB expanded archive limit, at most 2,000 ZIP entries (including directories), and path limits. An initial UTF-8 BOM is accepted. XML declarations using DTD/entities are rejected; XML resources are limited to 512 KiB. Referenced decoded bitmaps are limited to about 24 MiB per scene and 2,048 pixels per side. Preview/rear views may share the cached scene. No external resources are fetched.

## Format references

- https://github.com/davidecaruso/pan-luminor1950-wf — example XML/resource layout (inspected as a format reference; its assets are not bundled).
- https://amazfitwatchfaces.com/forum/viewtopic.php?t=1642 — original Stratos 3 WFZ component tutorial.

The renderer is independently implemented. The included demonstration assets are original and follow the project's own license. Third-party watchfaces retain their own licenses.
