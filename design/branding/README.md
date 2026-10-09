# ACCA-NG identity

Created with Google Stitch for the app's yellow energy identity. The geometric
`A` mark, its angular cuts, palette, and Plus Jakarta Sans wordmark direction
come from the Stitch output. The Android resources and standalone exports use
that same mark.

- Project: `17625380576063513155`
- Screen: `8f8a5efcfbec4e23944f31898ed2a8eb`
- Original design: `stitch-original.html` (trailing whitespace normalized) and
  `stitch-original.png`
- Original generation response: `stitch-generation.json`
- Production preview: `preview.png`

## Assets

| Asset | Use |
| --- | --- |
| `logo.svg` / `logo.png` | Yellow symbol and charcoal wordmark, transparent background |
| `logo-dark.svg` / `logo-dark.png` | Yellow symbol and warm-white wordmark, transparent background |
| `mark.svg` | Standalone yellow mark |
| `icon.svg` / `icon.png` | Square 512px app/store icon |
| `icon-rounded.svg` / `icon-rounded.png` | Rounded-square presentation icon |
| `icon-round.svg` | Circular presentation icon |
| `mark-path.json` | Original Stitch geometry and palette |

The wordmark letters are converted to vector paths, so the exported logos do
not need installed fonts. The outlines use Plus Jakarta Sans ExtraBold (800),
the family selected by Stitch, obtained from Google Fonts.

Electric yellow: `#FFD600`. Charcoal: `#171A1F`. Warm white: `#F7F6F0`.

## Android integration

The adaptive foreground and monochrome layer use the same path with a 0.55
scale and optical centering in a 108dp viewport. Every vertex fits inside the
central 66dp safe circle. The launcher background is solid yellow. Android's
theme controls the monochrome icon color.

All five legacy density sizes and the 512px Fastlane store icon are also
updated. The splash and widget use the complete launcher icon to retain its
yellow background on light and dark surfaces.

Debug builds use the name `AccA-NG Debug`, an orange launcher background and
a `DBG` badge, including in themed monochrome icons. These overrides live in
`app/src/debug/res`, so release branding and the production asset generator
remain unchanged. The debug artwork uses the same monogram at a smaller scale
to keep both the mark and badge inside the adaptive icon safe circle.

## Regeneration

`generate-assets.cjs` recreates the icon SVGs, Android layers, density PNGs,
store icon, logo PNGs, and production preview. It reads `mark-path.json` and
the two outlined logo SVGs. Run with Node.js and `sharp` available:

```sh
node design/branding/generate-assets.cjs
```

If changing the mark, also update the symbol path in both logo SVGs. Keep the
original Stitch exports unchanged as design provenance.
