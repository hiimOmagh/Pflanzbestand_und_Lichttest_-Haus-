# Import and Export Formats

The application persists backups as ZIP archives that contain a structured manifest and any
referenced media assets. Two manifest formats are supported and can be selected from the export
format picker:

- **CSV + ZIP** – legacy manifest expressed as sectioned CSV with the media files stored next to the
  manifest inside the archive.
- **JSON archive** – streaming-friendly JSON manifest named `data.json` packaged with the same media
  files.

Both formats are interchangeable for import. JSON is used by default for automated backups because
it preserves richer typing and optional metadata while remaining resilient to large datasets through
stream parsing.

## Archive layout and media handling

Exports always produce a flat ZIP archive whose root contains the manifest (`data.csv` or
`data.json`). Each referenced asset is copied into the same archive using deterministic file names:

- Plant hero photos → `plant_<plantId>_<originalName>`
- Gallery photos → `plant_photo_<photoId>_<originalName>`
- Diary attachments → `diary_<entryId>_<originalName>`

The importer recreates `content://` URIs for these files when restoring on API 29+ by copying the
files into `MediaStore.Images` under `Pictures/PlantImports`. On older API levels the files are
written directly into the app cache. The manifest references media by the exported file name, so
missing files simply result in blank URIs without aborting the import.

## CSV manifest

The CSV manifest is organised into sections separated by single-line headers. Each section begins
with the section name followed by a header row listing the column names. Rows are comma-separated
and quoted where needed. The supported sections match `ImportManager.Section`:

| Section | Columns |
| --- | --- |
| `Plants` | `id,name,description,species,locationHint,acquiredAtEpoch,photo,activeProfileId` |
| `PlantProfiles` | `id,plantId,label,growthStage,lightingStrategy,humidityMin,humidityMax,temperatureMin,temperatureMax,ppfdTargetMin,ppfdTargetMax,dliTargetMin,dliTargetMax,notes,createdAt,updatedAt` |
| `PlantPhotos` | `id,plantId,fileName,createdAt` |
| `PlantCalibrations` | `plantId,ambientFactor,cameraFactor` |
| `SpeciesTargets` | `speciesKey,seedlingPpfdMin,seedlingPpfdMax,seedlingDliMin,seedlingDliMax,vegetativePpfdMin,vegetativePpfdMax,vegetativeDliMin,vegetativeDliMax,flowerPpfdMin,flowerPpfdMax,flowerDliMin,flowerDliMax,tolerance,source` |
| `Measurements` | `id,plantId,profileId,timeEpoch,luxAvg,ppfd,dli,note,photo` |
| `EnvironmentEntries` | `id,profileId,timeEpoch,temperatureC,humidityPercent,co2Ppm,vpdKpa,ppfd,source` |
| `DiaryEntries` | `id,plantId,timeEpoch,type,note,photo` |
| `Reminders` | `id,plantId,triggerAt,message` |
| `RecommendationSnapshots` | `id,profileId,generatedAt,strategy,priority,message,metric,delta,setPoint` |

The importer is tolerant of missing trailing columns and blank numeric values; empty strings map to
`NULL` in Room. Plant calibrations expect positive floating-point factors and are silently skipped
when malformed. Species targets use the expanded schema and gracefully fall back to a legacy single
range when only PPFD values are provided. Plant profiles require at minimum `id`, `plantId`, and
`growthStage`. When `activeProfileId` in the `Plants` section references a missing profile the import
logs a warning and clears the pointer. Measurements and environment entries default their
`profileId` to the plant's active profile when absent, preserving backwards compatibility with
archives exported before profiles were introduced.

## JSON manifest

JSON exports write a single prettified file `data.json` with the following top-level structure:

```json
{
  "version": 2,
  "plants": [ ... ],
  "plantProfiles": [ ... ],
  "plantPhotos": [ ... ],
  "plantCalibrations": [ ... ],
  "speciesTargets": [ ... ],
  "measurements": [ ... ],
  "environmentEntries": [ ... ],
  "diaryEntries": [ ... ], 
  "reminders": [ ... ],
  "recommendationSnapshots": [ ... ]
}
```

Each array entry mirrors the Room entities and uses `null` for optional fields. The expected object
schemas are:

- **plants** – `{ "id": long, "name": string, "description": string?, "species": string?,
  "locationHint": string?, "acquiredAtEpoch": long, "photo": string?, "activeProfileId": long? }`
- **plantProfiles** – `{ "id": long, "plantId": long, "label": string?, "growthStage": string,
  "lightingStrategy": string?, "humidity": Range?, "temperature": Range?, "ppfd": Range?,
  "dli": Range?, "notes": string?, "createdAt": long, "updatedAt": long }`
- **plantPhotos** – `{ "id": long, "plantId": long, "fileName": string?, "createdAt": long }`
- **plantCalibrations** – `{ "plantId": long, "ambientFactor": float, "cameraFactor": float }`
- **speciesTargets** – `{ "speciesKey": string, "seedling": Stage?, "vegetative": Stage?,
  "flower": Stage?, "tolerance": string?, "source": string? }`
    - `Stage` objects contain optional `ppfdMin`, `ppfdMax`, `dliMin`, and `dliMax` floats.
- **measurements** – `{ "id": long, "plantId": long, "profileId": long?, "timeEpoch": long,
  "luxAvg": float, "ppfd": float?, "dli": float?, "note": string? }`
- **environmentEntries** – `{ "id": long, "profileId": long, "timeEpoch": long,
  "temperatureC": float?, "humidityPercent": float?, "co2Ppm": float?, "vpdKpa": float?,
  "ppfd": float?, "source": string }`
- **diaryEntries** – `{ "id": long, "plantId": long, "timeEpoch": long, "type": string,
  "note": string?, "photo": string? }`
- **reminders** – `{ "id": long, "plantId": long, "triggerAt": long, "message": string }`
- **recommendationSnapshots** – `{ "id": long, "profileId": long, "generatedAt": long,
  "strategy": string, "priority": string, "message": string, "metric": string,
  "delta": float?, "setPoint": float? }`

Referenced media file names (`photo`, `fileName`) are blank when the entity has no associated file.
During import the `PlantRepository` resolves these names against the extracted files to rebuild
content URIs.

### Calibration and species metadata

Per-plant calibration factors are exported verbatim from the `PlantCalibration` table. The importer
expects both `ambientFactor` and `cameraFactor` to be positive and will drop entries that fail
validation while logging an import warning. Light measurements fall back to the default calibration
factor when a plant lacks an explicit calibration row. Plant profile records are restored before any
dependent measurement or environment entities so that foreign keys always resolve correctly.

Species metadata uses the expanded three-stage schema across both formats. Imports tolerate missing
stage blocks by falling back to other defined stages via `SpeciesTarget.getStageOrFallback`. When
multiple exports are merged, species entries are deduplicated by `speciesKey`; later rows replace the
existing target in Room. Recommendation snapshots are optional and skipped when the target profile or
strategy is absent, but when present they seed the recommendation cache so the UI can immediately
display historical advice after an import.

### Environment entry and recommendation examples

CSV exports express environment data using the `EnvironmentEntries` section. A single humidity entry
looks like:

```
EnvironmentEntries
id,profileId,timeEpoch,temperatureC,humidityPercent,co2Ppm,vpdKpa,ppfd,source
42,11,1707410400,,58.2,,0.9,,"Bluetooth:HygroPro"
```

The matching JSON export uses compact objects:

```json
{
  "environmentEntries": [
    {
      "id": 42,
      "profileId": 11,
      "timeEpoch": 1707410400,
      "temperatureC": null,
      "humidityPercent": 58.2,
      "co2Ppm": null,
      "vpdKpa": 0.9,
      "ppfd": null,
      "source": "Bluetooth:HygroPro"
    }
  ],
  "recommendationSnapshots": [
    {
      "id": 7,
      "profileId": 11,
      "generatedAt": 1707410460,
      "strategy": "Humidity",
      "priority": "medium",
      "message": "Raise humidity by 2% to reach 60% target",
      "metric": "humidityPercent",
      "delta": 1.8,
      "setPoint": 60.0
    }
  ]
}
```

During import the recommendation snapshot links back to the restored profile so that the
recommendation engine can surface "last advice" indicators even before fresh measurements arrive.

## Import process

`ImportManager` detects JSON archives via MIME type, file extension, or the presence of `data.json`.
JSON archives are streamed directly to the parser to minimise memory pressure, while CSV archives are
fully extracted before section parsing. All inserts and updates execute inside a Room transaction so
a failure rolls back the partial import. Media files are copied before data insertion to ensure the
manifest never references missing assets.
