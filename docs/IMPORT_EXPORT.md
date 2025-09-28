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

| Section              | Columns                                                                                                                                                                                                                                                                                                                                                                                       |
|----------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `LedProfiles`        | `id,name,type,mountingDistanceCm,ambientFactor,cameraFactor`                                                                  |
| `Plants`             | `id,name,description,species,locationHint,acquiredAtEpoch,photoUri,ledProfileId`                                             |
| `PlantPhotos`        | `id,plantId,uri,createdAt`                                                                                                   |
| `SpeciesTargets`     | `speciesKey,commonName,scientificName,category,seedlingPpfdMin,seedlingPpfdMax,seedlingDliMin,seedlingDliMax,vegetativePpfdMin,vegetativePpfdMax,vegetativeDliMin,vegetativeDliMax,flowerPpfdMin,flowerPpfdMax,flowerDliMin,flowerDliMax,wateringFrequency,wateringSoilType,wateringTolerance,temperatureMin,temperatureMax,humidityMin,humidityMax,growthHabit,toxicToPets,careTips,sources` |
| `Measurements`       | `id,plantId,timeEpoch,luxAvg,ppfd`                                                                                            |
| `EnvironmentEntries` | `id,plantId,timestamp,temperature,humidity,soilMoisture,height,width,naturalDli,notes,photo`                                            |
| `DiaryEntries`       | `id,plantId,timeEpoch,type,note,photoUri`                                                                                    |
| `Reminders`          | `id,plantId,triggerAt,message`                                                                                               |
The importer is tolerant of missing trailing columns and blank numeric values; empty strings map to
`NULL` in Room. LED profile calibrations expect positive floating-point factors and are silently
skipped when malformed. Environment entries restore optional photos by resolving the exported file
name against the archive contents.

## JSON manifest

JSON exports write a single prettified file `data.json` with the following top-level structure:

```json
{
    "version": 4,
    "ledProfiles": [
        ...
    ],
    "plants": [
        ...
    ],
    "plantPhotos": [
        ...
    ],
    "speciesTargets": [
        ...
    ],
    "measurements": [
        ...
    ],
    "environmentEntries": [
        ...
    ],
    "diaryEntries": [
        ...
    ],
    "reminders": [
        "..."
    ]
}
```

Each array entry mirrors the Room entities and uses `null` for optional fields. The expected object
schemas are:

- **ledProfiles** – `{ "id": long, "name": string, "type": string?, "mountingDistanceCm": float?,
  "calibrationFactors": { "ambient": float?, "camera": float? } }`
- **plants** – `{ "id": long, "name": string, "description": string?, "species": string?,
  "locationHint": string?, "acquiredAtEpoch": long, "photo": string?, "ledProfileId": long? }`
- **plantPhotos** – `{ "id": long, "plantId": long, "fileName": string?, "createdAt": long }`
- **speciesTargets** – `{ "speciesKey": string, "commonName": string?, "scientificName": string?,
  "category": string, "seedling": Stage?, "vegetative": Stage?, "flower": Stage?,
  "watering": WateringInfo?, "temperature": Range?, "humidity": Range?, "growthHabit": string?,
  "toxicToPets": boolean?, "careTips": string[]?, "sources": string[]? }`
    - `Stage` objects contain optional `ppfdMin`, `ppfdMax`, `dliMin`, and `dliMax` floats.
    - `WateringInfo` contains optional `frequency`, `soilType`, and `tolerance` strings. For
      backwards
      compatibility the importer also accepts legacy `schedule` and `soil` properties.
- **measurements** – `{ "id": long, "plantId": long, "timeEpoch": long,
  "luxAvg": float, "ppfd": float?, "dli": float?, "note": string? }`
- **environmentEntries** – `{ "id": long, "plantId": long, "timestamp": long,
  "temperature": float?, "humidity": float?, "soilMoisture": float?, "height": float?,
  "width": float?, "naturalDli": float?, "notes": string?, "photo": string? }`
- **diaryEntries** – `{ "id": long, "plantId": long, "timeEpoch": long, "type": string,
  "note": string?, "photo": string? }`
- **reminders** – `{ "id": long, "plantId": long, "triggerAt": long, "message": string }`

Referenced media file names (`photo`, `fileName`) are blank when the entity has no associated file.
During import the `PlantRepository` resolves these names against the extracted files to rebuild
content URIs.

### Environment entry example

CSV exports express environment data using the `EnvironmentEntries` section. A single entry looks
like:

```
EnvironmentEntries
id,plantId,timestamp,temperature,humidity,soilMoisture,height,width,naturalDli,notes,photo
42,11,1707410400,,58.2,,12.5,8.0,2.1,"Bluetooth sensor",
```

The matching JSON export uses compact objects:

```json
{
    "environmentEntries": [
        {
            "id": 42,
            "plantId": 11,
            "timestamp": 1707410400,
            "temperature": null,
            "humidity": 58.2,
            "soilMoisture": null,
            "height": 12.5,
            "width": 8.0,
            "naturalDli": 2.1,
            "notes": "Bluetooth sensor",
            "photo": null
        }
    ]
}
```

## Import process

`ImportManager` detects JSON archives via MIME type, file extension, or the presence of `data.json`.
JSON archives are streamed directly to the parser to minimise memory pressure, while CSV archives
are
fully extracted before section parsing. All inserts and updates execute inside a Room transaction so
a failure rolls back the partial import. Media files are copied before data insertion to ensure the
manifest never references missing assets.
