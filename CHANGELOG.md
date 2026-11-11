# Changelog

All notable changes for users of the app. Versions follow [Semantic Versioning](https://semver.org/).

## [1.0.0] – not yet released

First public release.

### Scene from your photo
- Any photo becomes a live wallpaper. Depth is estimated on the device (Depth Anything V2 Small); the photo is never uploaded.
- Sky by time of day with sun, moon and stars, blended softly into the photo. The slider "Übergang zum Himmel" sets how soft the transition is.
- The clock can sit behind the subject of the photo, or in front of a widget via the layer order.

### Weather and seasons
- Live weather and air quality from Open-Meteo for your approximate location.
- Rain, snow, fog, thunderstorms, and seasonal particles (cherry blossom, autumn leaves).
- Window pane in front of the scene: frost ferns grow in from the edges below about 1 °C; raindrops act as lenses, run down and dry after the rain.
- Weather only tints the photo. The earlier snow cover and puddles painted into the photo were removed.

### Modes
- Day, dawn, dusk, night, a rain look and custom time ranges, switched by sun position or fixed times.
- Each mode inherits from Day and stores only what differs. A separate photo per mode is possible.
- Home screen and lock screen can be designed separately. From Android 14, "Speichern" asks which screen to publish to; editing no longer changes the running wallpaper.

### Editor
- Full-screen preview with drag and drop, size and rotation with two fingers, and guides that snap to edges and centres.
- Several clocks and several "date and weather" elements; any element can be duplicated.
- Clock styles including two-line and column.
- Widgets: temperature, rain, hourly forecast, week, moon phase, sun arc, golden hour, stars, air quality, battery, countdown, next calendar event, time zone, free text.
- Text fills (colour, gradient with adjustable stops, glass with adjustable blur, photo), outline with its own fill, shadow, glow, neon.
- Colour picker with colours from the photo and remembered colours.
- Number fields next to every slider, undo and redo, collapsible tool bar.
- Own fonts can be added and removed.
- Favourites store photo and settings, and can be shared and imported as a ZIP file.

### App
- The user interface font is now Lexend.
- New section "Info" in the settings with the weather data source and open-source licences.

### Known limits
- Tested only on a Samsung Galaxy S24 Ultra (One UI). arm64 devices only.
- Additional clocks have no format options yet.
