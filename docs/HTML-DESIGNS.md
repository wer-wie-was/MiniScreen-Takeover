# Complete HTML designs

MiniScreen Takeover displays your design as the entire screen. It does not add a native clock on top. If charging indicators are enabled, Android draws them as an independent native layer above the HTML. Use relative CSS units or the actual CSS dimensions: Android reduces the WebView area to leave room for pixel shifting, then moves the entire view.

## Importing files

Import a standalone `.html` file or a ZIP archive. For ZIPs, place `index.html` at the root alongside files such as `style.css`, `clock.js`, `images/` and `fonts/`. Relative URLs work inside the design directory. Resources outside that directory and network resources are blocked. A single `index.html` in a subdirectory is also detected if there is none at the ZIP root.

The included `examples/Classic-HTML-Design.zip` can be imported directly. `examples/minimal.html` is a small standalone example.

## Reading data

```js
function render(d) {
  document.querySelector('#clock').textContent = d.time;
  document.querySelector('#clock').hidden = d.showClock === false;
  document.querySelector('#date').textContent = d.showDate ? d.date : '';
}
window.addEventListener('miniscreen:update', event => render(event.detail));
if (window.miniScreen) render(window.miniScreen);
```

The initial state arrives after the page loads. Further updates occur when settings, time or time zone change, and on every pixel shift. Otherwise, updates arrive at the next whole minute or second, depending on the seconds setting. `window.miniScreen` holds the most recently supplied state. Custom animations can also use `Date.now()`; frequent animations increase power consumption.

| Field | Meaning |
| --- | --- |
| `epochMs` | Unix time in milliseconds at the time of the update |
| `time` | Formatted time respecting 12/24-hour mode, seconds and leading zero settings |
| `date` | Formatted date; empty when disabled |
| `showClock` | Whether the clock should be displayed, independently of the date. Custom designs should hide their clock when this is `false`. |
| `showDate` | Whether the date should be displayed |
| `width`, `height` | Outer rendering area in physical pixels; normally 340 × 340 on the rear display |
| `cssWidth`, `cssHeight` | Inner WebView area in CSS pixels; also use `innerWidth`, `innerHeight` or `clientWidth` as appropriate |
| `timeZone`, `locale` | Android time zone and language identifier |
| `running` | `true` while this rendering surface is active; pause custom animations when `false` |
| `batteryPlugged`, `batteryLevel` | Whether external power is connected and battery level from 0–100; simulated preview values are `true` and 65 |
| `preview` | `true` in the main-screen preview |
| `shiftX`, `shiftY` | Current displacement in reference pixels relative to a 340-pixel display |
| `settings` | Complete profile settings; see the following table |
| `backgroundUrl` | Local URL of the selected background image, or an empty string |
| `fontUrl` | Local URL of the selected font, or an empty string |

The CSS variables `--time-color`, `--date-color` and `--background-color` are set on `document.documentElement`.

| `settings` fields | Meaning |
| --- | --- |
| `schema`, `name`, `mode` | Profile format version (1), profile name and display mode (`native`, `html` or `pebble`) |
| `twentyFour`, `seconds`, `leadingZero`, `showClock`, `showDate`, `datePattern` | Time and date settings |
| `timeColor`, `dateColor`, `backgroundColor` | HEX colors in `#RRGGBB` format |
| `timeSize`, `dateSize` | Reference font sizes relative to a 340-pixel display |
| `timeX`, `timeY`, `dateX`, `dateY` | Requested center positions as percentages; custom designs may interpret them differently |
| `font`, `bold` | System font name and bold setting |
| `imageFit`, `dim`, `shiftBackground` | `cover`, `contain` or `window`; dimming from 0–100%; background shifting |
| `shifting`, `shiftRange`, `shiftInterval` | Whether pixel shifting is enabled, range in reference pixels and interval in seconds |
| `brightness` | Requested display brightness from 1–100%; Android applies it outside the HTML |
| `image`, `fontFile`, `design` | Internal relative asset paths; use the supplied URLs for images and fonts |

## Background and font

Apply the selected image as a CSS background using `backgroundUrl`. Load `fontUrl` with `FontFace` when needed. These URLs remain local despite starting with `https://miniscreen.local/`: the app serves them from the profile directory. The example reloads a font only when it changes.

The HTML design decides whether to use the background image, date option or position sliders. The app does not enforce these design choices. Brightness, touch blocking and pixel shifting are applied outside the HTML.

Pixel shifting moves the entire HTML surface. For a visually stationary background layer, the design can compensate for that movement. To convert reference pixels into CSS pixels:

```js
const factor = Math.min(d.width, d.height) / 340 / (window.devicePixelRatio || 1);
const dx = d.shiftX * factor, dy = d.shiftY * factor;
background.style.transform = `translate(${-dx}px, ${-dy}px)`;
```

Do not add another pixel shift to text when the app already shifts the view. Leave enough space for long dates and 12-hour time strings. The automatic outer margin prevents clipping caused by shifting; your page must still avoid internal layout overflow.

## WebView rules

JavaScript is permitted, but there is **no** `addJavascriptInterface` bridge. The app only supplies JSON data to the page. Offline resources from the design, `data:` images/fonts and supported local media are available. External connections, embedded websites, popups, file/content URLs, external form submissions and web workers are blocked. Local scripts cannot trigger Android actions. DOM storage is disabled; designs should rebuild their state from the supplied data when loaded.

Android intercepts touches before they reach the page. This also applies to the preview, so HTML buttons cannot control the app.

Limits: 20 MiB per extracted design, 200 ZIP entries, 1 MiB per HTML file, 240 characters per ZIP path and a maximum of 12 path levels. Profile exports contain the currently referenced files and current design, excluding unused earlier imports.

`locale` follows the selected app language, or the Android locale when “System language” is selected. The app does not translate arbitrary text inside an imported HTML design.

## Window background and charging indicators

`settings.imageFit` is `cover`, `contain` or `window`. In `window` mode, the image fills the display and `windowZoom` (100–3,000%) magnifies it further. `windowSpeed` (1–100 design px/s at 340 px, default 20) controls travel speed; `windowMotion` enables movement. `dim` remains the dimming percentage. The old `windowSeconds` duration is no longer used. The included classic design keeps a stateful image-space centre and heading: zoom preserves the current centre, speed affects future motion, and random direction changes turn smoothly without segment-start acceleration. Clamp the centre at image edges so the background continues to fill the display. The top-level `windowZoomPauseMs` field is the remaining zoom-adjustment pause (0–2,000 ms); custom designs can honour it relative to their local monotonic clock. The example uses `performance.now()` and a throttled `requestAnimationFrame` loop and never catches up after suspension. Position and heading are retained during changes within the current view, not across process restarts.

Android automatically draws charging indicators above the HTML. The design does not need to render them. Their settings are also available in `settings`: `chargeEnabled`, `chargeAlways`, `chargeGradient`, `chargeRingColorSecond`, `chargeAccentColor`, `chargeCircle`, `chargeAnimated`, `chargeBold`, `chargeColor`, `chargeSymbolColor`, `chargeRingColor`, `chargeFont`, `chargeFontFile`, `chargeSize`, `chargeX`, `chargeY`, `chargeRingWidth`, `chargeAnimationSeconds`.

`chargeFontFile` is an internal profile path for the native layer, not an accessible HTML URL. Imported HTML is not automatically rewritten. The included design does not draw a second charging indicator. Updates also occur when battery state changes and when the rendering surface starts or pauses. The actual animation rate is independent of the clock’s minute/second update interval.

## Notification ticker

By default, Android draws the configurable ticker above the design. In that mode, the HTML page receives no notification content. Enabling **“HTML design renders notifications”** removes that native layer. The page then receives the following fields through the existing `miniscreen:update` event:

- `notification`: the currently displayed notification, or `null` during a pause.
- `notifications`: the filtered queue, containing at most 50 entries.
- `tickerSettings`: font size, font, bold, colors, opacity, position, width, `heightAuto` (default true), `height` (20–300 design pixels at 340 px), scrolling, speed (up to 1,080 design px/s), line count, icon size, display duration and `clockMode` (0 = visible, 1 = dimmed, 2 = hidden).

A notification contains `app` (package name), `label`, `text`, `count` and `iconUrl` (local app icon). In discreet mode, `label` is empty and `text` contains only the count. At other privacy levels, `text` already contains the permitted combination of app, title and message. The title and message are not supplied separately as unfiltered fields. `count` counts active captured notifications from that app. `iconUrl` works only for permitted apps while HTML notification mode is enabled; in the preview it supplies only the example icon.

Data is filtered **before** being passed to JavaScript, according to the current lock state. It is updated or cleared when the device is unlocked, notifications are removed, privacy settings change or rendering stops. The HTML design must remove old text in response. Enable only designs you trust with the selected content level: the app cannot retrieve already supplied content from arbitrary JavaScript variables. Do not embed notification contents in your HTML or files; use `textContent` instead of `innerHTML`.

```js
window.addEventListener('miniscreen:update', ({detail: d}) => {
  const n = d.notification;
  const box = document.getElementById('notification');
  box.hidden = !n;
  box.textContent = n ? n.text : '';
});
```

The complete `examples/notification-clock.html` example displays the clock and a filtered notification. Native font sizes use the 340px reference; scale HTML with `Math.min(d.cssWidth, d.cssHeight) / 340`. The `profile` font uses the profile font, including `fontUrl` for an imported font. Pixel shifting happens outside the WebView. In HTML mode, the design controls clock dimming/hiding itself. Native charging indicators remain independently active.

## Additional native modules

Widgets and Notification Dot appear as native layers above the HTML design. The dot uses its own app/color selection and does not display notification text. Widget bindings and dot settings are device options and are not included in profile exports. The dot is drawn above the ticker and charging indicators so its status stays visible. Clock and date can be disabled independently; the included design respects both switches.

`chargeAlways` keeps the native battery icon and percentage visible without external power when `chargeEnabled` is enabled. The ring and its accent remain restricted to connected power. `chargeGradient` enables a smooth first–second–first color gradient around the ring; `chargeRingColorSecond` sets the second color. `chargeAccentColor` sets the independent animated accent color.
