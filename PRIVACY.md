# Privacy

Applies to AccA-NG 2.0.0-ng-beta.1. Updated 9 October 2026.

AccA-NG does not require an account and does not include an advertising or
analytics SDK. The project does not operate a telemetry backend for the app.

## Data on the device

Profiles, schedules and preferences are stored locally. Root commands read battery
and charging-control information and change settings when requested. ACC-NG and
DJS keep configuration, backups and diagnostic logs in their root-accessible
runtime directories. Removing the frontend app does not necessarily remove these
separately installed modules or their data.

Exporting profiles creates a file at the location you choose. Copying diagnostics
puts the displayed information on the clipboard. Logs and diagnostics may contain
device paths, kernel information, readings, profile names and commands. Review them
before sharing. Other apps, root tools and the operating system may have their own
access, backup or retention behaviour.

## Network requests

The version selector and engine download features contact GitHub to obtain ACC-NG
version metadata and source archives. GitHub receives ordinary connection data
such as your IP address. The bundled engine can be installed without downloading
its archive. Links to documentation, GitHub profiles and the upstream community
open external services subject to their own privacy policies.

The bundled root modules are separate software components. Review their source and
documentation for module update and runtime behaviour:
[ACC-NG](https://github.com/ricardojrgpimentel/ACC-NG) and
[DJS](https://github.com/VR-25/djs).

## Permissions

Root access is needed for charging controls and module management. Internet access
supports the version/download features. Notifications and the foreground service
support charging notices and the battery widget; boot access supports restarting
services. Legacy storage permissions support file operations on older Android
versions. The app can ask to be excluded from battery optimisation for background
operation.

## Contact

Use the [project issue tracker](https://github.com/ricardojrgpimentel/ACCA-NG/issues)
for privacy questions without including private logs or credentials. For a security
issue, follow [SECURITY.md](SECURITY.md).
