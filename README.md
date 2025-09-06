# Pflanzbestand und Lichttest – Haus

## Project goals

This Android sample demonstrates simple plant management combined with light testing. The main
screen lists example plants and measures ambient light to calculate PPFD and DLI, helping evaluate
indoor conditions for plant growth.

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
`LightMeasurementInstrumentedTest`, the reminder scheduling checks in `ReminderInstrumentedTest`, and
the round-trip backup verification in `DataRoundTripInstrumentedTest`.

## Usage

1. Install the generated APK on an Android device or emulator that has a light sensor.
2. Open the app to view the list of sample plants.
3. Tap a plant to view its details.
4. Watch the main screen's live lux, PPFD, and DLI readings to test lighting conditions for your
   plants.

## Backups

Data can be exported and imported via the menu. When importing a backup, you can choose to **merge**
with existing content or **replace** it entirely. Merge discards original identifiers so new plants
and their related records are added alongside current data. Replace wipes the database before
restoring the backup, preserving the identifiers from the archive.

## Species PPFD targets

On first launch the app seeds the database with common species light requirements from
`app/src/main/assets/targets.json`. Each entry defines a `speciesKey` along with its acceptable
`ppfdMin` and `ppfdMax` values. To add or adjust species:

1. Edit `app/src/main/assets/targets.json` and add another object to the list.
2. Reinstall the app or clear its data so the database is recreated and picks up the changes.

## Repository threading

`PlantRepository` executes database work on a background executor and delivers
results or completion callbacks on the Android main thread. This allows UI code
to interact with returned data directly from the callback without additional
thread switching:

```java
PlantRepository repository = ((PlantApp) context.getApplicationContext()).getRepository();
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
