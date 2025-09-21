# Pflanzbestand und Lichttest – Haus

## Project goals

This Android sample demonstrates simple plant management combined with light testing. The main
screen lists example plants and measures ambient light to calculate PPFD and DLI, helping evaluate
indoor conditions for plant growth. The application targets Android 14 (API level 34) while
supporting devices running Android 7.0 (API level 24) and above.

## Build and setup

The project uses the Gradle wrapper. From the repository root run:

```bash
./gradlew assemble
```

This will download dependencies and produce the app's APK in `app/build/outputs/apk/`.

## Running tests

To execute the JVM unit tests run:

```bash
./gradlew test
```

Instrumentation tests require a connected Android device or emulator. They can be launched with:

```bash
./gradlew connectedAndroidTest
```

This command runs the full Android test suite, including the light measurement scenario covered by
`LightMeasurementInstrumentedTest`, the reminder scheduling checks in `ReminderInstrumentedTest`, the
widget update validation in `ReminderWidgetInstrumentedTest`, the round-trip backup verification in
`DataRoundTripInstrumentedTest`, and the DLI/PPFD chart validation in `StatsFragmentInstrumentedTest`.

To execute only the statistics fragment test, run:

```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=de.oabidi.pflanzenbestandundlichttest.StatsFragmentInstrumentedTest
```

## Usage

1. Install the generated APK on an Android device or emulator that has a light sensor.
2. Open the app to view the list of sample plants.
3. Tap a plant to view its details.
4. Watch the main screen's live lux, PPFD, and DLI readings to test lighting conditions for your
   plants.
5. Use the search action to filter plants by name, species, or location hint.

### Reminder notifications

Reminder delivery uses a WorkManager-backed scheduler on Android 12 and higher to request exact
alarms when the `SCHEDULE_EXACT_ALARM` special permission is available, falling back to expedited
jobs when it is not. Devices running Android 11 or lower continue to rely on `AlarmManager`. On
Android 13+ the notification itself still requires the `POST_NOTIFICATIONS` runtime permission; when
it is denied the receiver records the missed delivery in shared preferences and reschedules the
reminder for the following day. See [docs/NOTIFICATIONS.md](docs/NOTIFICATIONS.md) for the complete
permission matrix and troubleshooting tips.

## Data Backup

Data can be exported and imported via the menu. When importing a backup, you can choose to **merge**
with existing content or **replace** it entirely. Merge discards original identifiers so new plants
and their related records are added alongside current data. Replace wipes the database before
restoring the backup, preserving the identifiers from the archive.

After a successful export you can immediately share the resulting archive using the system share
sheet.

![Share backup screenshot](docs/share_backup.png)

Automatic weekly backups can be enabled in Settings. When active, the app writes a ZIP archive
named `backup-YYYYMMDD-HHmmss.zip` to its external files directory, typically
`Android/data/de.oabidi.pflanzenbestandundlichttest/files/`. Only the five most recent automatic
backups are retained; older archives are deleted before creating a new one.

Individual plants can also be exported from the detail screen. Use the **Export Plant Data** menu
action while viewing a plant to save only its measurements, diary entries, and reminders.

## Appearance

The Settings screen includes a **Theme** option with Light, Dark, and System default modes. The
selected mode is stored in shared preferences and applied whenever the app starts. The main
navigation chrome now uses Material 3 components – a top app bar, navigation bar, and dialogs – so
colors, typography, and shapes automatically match the active dynamic color scheme.

## Species PPFD targets

On first launch the app seeds the database with common species light requirements from
`app/src/main/assets/targets.json`. Each entry defines a `speciesKey` along with its acceptable
`ppfdMin` and `ppfdMax` values. To add or adjust species:

1. Edit `app/src/main/assets/targets.json` and add another object to the list.
2. Reinstall the app or clear its data so the database is recreated and picks up the changes.

## Localization

The project currently provides English, German, Spanish, and French translations. French strings
were contributed by the community. To add another language, copy `app/src/main/res/values/strings.xml`
into a new `values-xx` directory, translate all strings, and open a pull request.

## Repository threading

`PlantRepository` executes database work on a background executor and delivers
results or completion callbacks on the Android main thread. This allows UI code
to interact with returned data directly from the callback without additional
thread switching:

```java
PlantRepository repository = ((RepositoryProvider) context.getApplicationContext()).getRepository();
repository.getAllPlants(plants -> {
    // This code runs on the main thread.
    // Update UI with the retrieved plants.
});
```

Synchronous methods ending with `Sync` still block and must be invoked from a
background thread.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the planned module layout, fragment structure,
and resource naming conventions.
