# Architecture

This document outlines the planned high-level structure of the project and conventions for contributors.

## Module layout
- `app`: hosts Android application code and resources.

## Planned fragment structure
Each feature will be isolated in its own fragment and associated resources:

- **Plants** – `PlantListFragment` for listing plants and `PlantDetailFragment` for details.
- **Measurement** – `LightMeasurementFragment` uses the device light sensor to compute PPFD and DLI.
- **Diary** – `DiaryFragment` allows adding notes per plant.
- **Stats** – `StatsFragment` aggregates historical measurements.

## Resource naming conventions
- Layouts: `fragment_<feature>.xml` for fragments and `activity_<screen>.xml` for activities.
- Strings, ids, drawables and other resources: prefix with feature area (`plant_`, `measurement_`, `diary_`, `stats_`).
- Names are lowercase `snake_case`.
- Java classes use `UpperCamelCase`; fragments end with `Fragment` and view models end with `ViewModel`.

## Feature / fragment map

| Feature area | Expected fragments                         | Key files / resources                                                                        |
|--------------|--------------------------------------------|----------------------------------------------------------------------------------------------|
| Plants       | `PlantListFragment`, `PlantDetailFragment` | `fragment_plant_list.xml`, `fragment_plant_detail.xml`, `PlantRepository.java`, `Plant.java` |
| Measurement  | `LightMeasurementFragment`                 | `fragment_measurement.xml`, `LightSensorHelper.java`                                         |
| Diary        | `DiaryFragment`                            | `fragment_diary.xml`, `DiaryEntry.java`, `DiaryRepository.java`                              |
| Stats        | `StatsFragment`                            | `fragment_stats.xml`, `StatisticsCalculator.java`                                            |

## TODO markers and style guides
- Use `TODO(name): description` comments to flag follow-up work. Link to issue numbers when available.
- Adhere to the project's `.editorconfig` for indentation (4 spaces), line length (100 chars), and UTF-8 encoding.
- Java code should follow the [Android Java Style Guide](https://source.android.com/docs/setup/contribute/code-style).

## Species target data schema

Species-specific light targets are bundled in `app/src/main/assets/targets.json` and exported via the
"SpeciesTargets" section in CSV backups. Each species entry is keyed by `speciesKey` and defines
stage-specific PPFD and DLI ranges, optional tolerance notes, and a source reference:

```json
{
  "speciesKey": "basil",
  "tolerance": "moderate",
  "source": "Default dataset",
  "seedling": { "ppfdMin": 100, "ppfdMax": 180, "dliMin": 4.3, "dliMax": 7.8 },
  "vegetative": { "ppfdMin": 150, "ppfdMax": 300, "dliMin": 6.5, "dliMax": 13.0 },
  "flower": { "ppfdMin": 180, "ppfdMax": 320, "dliMin": 7.8, "dliMax": 13.8 }
}
```

The exported CSV mirrors this structure using the following columns:

```
speciesKey,
seedlingPpfdMin,seedlingPpfdMax,seedlingDliMin,seedlingDliMax,
vegetativePpfdMin,vegetativePpfdMax,vegetativeDliMin,vegetativeDliMax,
flowerPpfdMin,flowerPpfdMax,flowerDliMin,flowerDliMax,
tolerance,source
```

Importers treat missing values as `NULL` and compute DLI from PPFD during legacy migrations.
