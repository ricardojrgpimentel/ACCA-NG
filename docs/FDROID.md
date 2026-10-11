# F-Droid preparation

Status: **not submitted; not listed**. GitHub Releases is the beta distribution
channel. Do not display an F-Droid download badge until this package is listed.

## Ready

- Public source with a distinct application ID, `com.accang.app`.
- GPL-3.0-or-later licensing and upstream attribution.
- Unsigned release builds without private credentials.
- Fastlane metadata, changelog and actual app screenshots.
- Public certificate/fingerprint for the upstream signed APK.
- Versions pinned in Gradle and the wrapper distribution checksum recorded.

## Before submission

1. Build in F-Droid's environment and audit all direct/transitive dependencies,
   including the JitPack tooltip and libsu artifacts. Do not assume eligibility
   solely because the app's top-level license is GPL.
2. Review the bundled ACC-NG and DJS source archives, reproduce them from the
   linked sources or document a verifiable build recipe. Check archive contents,
   license notices and any executable components against the inclusion policy.
3. Review engine download consent. External engine updates bypass F-Droid's build
   checks; make that explicit in any F-Droid variant's update flow. Review whether
   an anti-feature declaration or variant configuration is required.
4. Produce a clean Linux build matching the signed upstream APK. Reproducibility
   has **not** been established. Prefer developer-signed APK distribution after
   F-Droid reproduces and verifies it; this preserves the GitHub signing identity.
5. Only after those checks, submit a recipe to fdroiddata or an inclusion request
   through F-Droid's documented process. No signing credentials are needed.

Current ACC-NG bundle: **v1.0.7-ng unreleased candidate**, built using
`python3 tools/build_ng.py` from local engine source commit
`8305720c356fec9c8b4be8ee7a5de1bffc4983aa` (not yet pushed), over
[I3 source](https://github.com/ricardojrgpimentel/ACC-NG/tree/1e8291bcc69d59de677674728075b0c9063b561a).
See the [I4 integration record](acc-upstream-implementation.md#i4--preservação-do-staging-de-instalação-e-recuperação).
A clean git archive of that commit reproduces the bundled engine archive;
the fixed flashable uninstaller matches source. Signed APK reproducibility and
F-Droid eligibility remain separate checks. I4 passed synthetic host/Android
tests and a real upgrade from acc-* staging on Samsung; successful power-limit
application/restoration remains unverified. See [I4 validation](acc-ng-validation.md#i4-staging-cleanup-candidate--2026-10-11).
The previous published engine source remains [v1.0.3-ng](https://github.com/ricardojrgpimentel/ACC-NG/tree/v1.0.3-ng);
it does not reproduce the candidate.
DJS source: https://github.com/VR-25/djs/tree/v2021.12.14
(commit `fec70823e379197a612fd18af644b2616d1168e0`).
The DJS module metadata reports v2021.11.3 despite the archive tag v2021.12.14.

The current archive checksums are in [bundled-source-sha256.txt](bundled-source-sha256.txt).

References: [inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/),
[reproducible builds](https://f-droid.org/docs/Reproducible_Builds/).
