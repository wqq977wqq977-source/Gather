# Bundled font

The app bundles **Manrope** for Latin text and financial numerals. Chinese text
continues to use Android's system fallback. The original font binary is included
without modification; only its asset filename is shortened.

- Asset: `app/src/main/assets/fonts/Manrope.ttf`
- Android asset path: `fonts/Manrope.ttf`
- Weight axis: `wght`, 200–800. Select the intended weight explicitly; the
  variable font's default axis value is 200.
- Copyright: 2018 The Manrope Project Authors.
- License: SIL Open Font License 1.1. The complete, unmodified license and
  copyright notice ship in `app/src/main/assets/fonts/OFL-Manrope.txt`.
- Source: [Google Fonts Manrope directory](https://github.com/google/fonts/tree/main/ofl/manrope)
- Original font: [Manrope\[wght\].ttf](https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf)
- License source: [OFL.txt](https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/OFL.txt)
- Retrieved: 2026-10-23.
- Font size: 164,700 bytes.
- Font SHA-256: `3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6`
- Google Fonts font blob: `75274da58537d6123b14f2cd0c355ad4681fc2b3`.

The app's minimum SDK is 26, where Android supports variable fonts and
`Typeface.Builder`. For example:

```java
Typeface numerals = new Typeface.Builder(getAssets(), "fonts/Manrope.ttf")
        .setFontVariationSettings("'wght' 600")
        .setWeight(600)
        .build();
```

The Gradle build includes `src/main/assets` by convention. The standalone builder
passes the same directory to `aapt -A` when it exists, so the font and its license
are available offline in both APK variants.

## 0.5 hand-drawn styles

Both selectable styles use bundled **LXGW WenKai Regular** for Chinese and **Patrick Hand** for financial numerals. These replace Manrope in the active interface. Full font files are unmodified; no runtime network requests are needed. Both ship with their SIL Open Font License text. Retrieved 2026-10-24.

- `WenKai.ttf`: 25928940 bytes; SHA-256 `2d73eea94ad808999d90c0000c239a84a33b09d4bc6ab6c5b8bbf1eda8a7fc8b`. [Upstream font](https://raw.githubusercontent.com/lxgw/LxgwWenKai/main/fonts/TTF/LXGWWenKai-Regular.ttf); license `app/src/main/assets/fonts/OFL-WenKai.txt`.
- `PatrickHand.ttf`: 214772 bytes; SHA-256 `0f173b3e6cb6d1af25babf7f0057c5ac4ee11f9992b0469bb817e967ef4ad0fc`. [Upstream font](https://raw.githubusercontent.com/google/fonts/main/ofl/patrickhand/PatrickHand-Regular.ttf); license `app/src/main/assets/fonts/OFL-PatrickHand.txt`.
