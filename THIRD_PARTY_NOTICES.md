# Third-party notices

## AccA and AccA-NG

Original AccA: copyright 2019–2021 MatteCarra, Squabbi, VR25 and contributors.
Source: https://github.com/MatteCarra/AccA

AccA-NG modifications: copyright 2026 Ricardo Pimentel and contributors.
The app is distributed under GNU GPL version 3 or, at your option, any later version.
See LICENSE. The source history preserves upstream attribution.

## Bundled charging engine and scheduler

- ACC-NG v1.0.3-ng, based on ACC v2023.10.16. GPL-3.0-or-later.
  Source: https://github.com/ricardojrgpimentel/ACC-NG/tree/v1.0.3-ng
  Original ACC: https://github.com/VR-25/acc
- DJS archive v2021.12.14 (its module.prop reports v2021.11.3 / 202111030).
  Copyright VR25. GPL-3.0-or-later.
  Source: https://github.com/VR-25/djs/tree/v2021.12.14
- The shared tarball installer is derived from VR25's install-tarball.sh,
  copyright 2019–2022 VR25, GPL-3.0-or-later, modified for ACC-NG/DJS selection.

The source archives live in app/src/main/res/raw/acc_bundle and djs_bundle and
include their original License.md files. Their source and checksums are described
in docs/FDROID.md. The GPL license text is also included in the APK assets.

## Vendored CircleProgressBar

Copyright 2015–2019 dinuscxj.
Source: https://github.com/dinuscxj/CircleProgressBar
Licensed under Apache License, Version 2.0. The license text is included in
app/src/main/assets/licenses/Apache-2.0.txt and in the APK.

The vendored Java view and associated resources replace the unavailable JCenter
artifact. The app resource import was adapted to mattecarra.accapp.R.

## Build dependencies

Additional libraries and pinned versions are declared in app/build.gradle and
build.gradle, including AndroidX, Material Components, libsu, Material Dialogs,
Gson, Moshi, Kotlin coroutines, Commons Collections, CircleImageView and
android-target-tooltip. They retain their respective upstream licenses and notices.
