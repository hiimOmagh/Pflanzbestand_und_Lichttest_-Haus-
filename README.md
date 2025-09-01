# Pflanzbestand und Lichttest – Haus

## Project goals
This Android sample demonstrates simple plant management combined with light testing. The main screen lists example plants and measures ambient light to calculate PPFD and DLI, helping evaluate indoor conditions for plant growth.

## Build and setup
The project uses the Gradle wrapper. From the repository root run:

```bash
./gradlew assemble
```

This will download dependencies and produce the app's APK in `app/build/outputs/apk/`.

## Usage
1. Install the generated APK on an Android device or emulator that has a light sensor.
2. Open the app to view the list of sample plants.
3. Tap a plant to view its details.
4. Watch the main screen's live lux, PPFD, and DLI readings to test lighting conditions for your plants.

## Background thread usage

`PlantRepository` performs blocking database operations. Use an
`ExecutorService` or similar mechanism to call its methods off the main thread:

```java
ExecutorService executor = Executors.newSingleThreadExecutor();
executor.execute(() -> {
    PlantRepository repository = new PlantRepository(context);
    List<Plant> plants = repository.getAllPlants();
    // Update UI on the main thread after retrieving data.
});
```
