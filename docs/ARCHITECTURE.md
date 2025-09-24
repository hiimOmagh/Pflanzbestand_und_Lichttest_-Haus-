# Architecture

This document captures the current high-level structure of the app, the MVP relationships between
screens, and the data pipelines that feed Room and background work.

## Module layout
- `app` – Android application module containing Activities, Fragments, presenters, Room database,
  background workers, and instrumentation tests.

## MVP overview

Each screen is implemented as a Fragment (or the hosting `MainActivity`) paired with a presenter that
owns the business logic. Presenters communicate exclusively with `PlantRepository`, which exposes
asynchronous APIs backed by Room DAOs. The new Environment and Profile experiences follow the same
pattern so that sensor ingestion, manual entry, and recommendation generation remain isolated from
the UI widgets.

```mermaid
flowchart LR
    subgraph UI
        MainActivity
        PlantListFragment
        LightMeasurementFragment
        EnvironmentFragment
        DiaryFragment
        StatsFragment
        SpeciesTargetListFragment
        PlantProfileFragment
    end
    subgraph Presenters
        MainPresenterImpl
        PlantListPresenter
        LightMeasurementPresenter
        EnvironmentPresenter
        DiaryPresenter
        StatsPresenter
        PlantProfilePresenter
    end
    subgraph Data
        PlantRepository
        PlantDatabase[(Room DB)]
    end
    MainActivity -->|hosts| PlantListFragment
    MainActivity --> LightMeasurementFragment
    MainActivity --> DiaryFragment
    MainActivity --> StatsFragment
    MainActivity --> SpeciesTargetListFragment
    PlantListFragment -->|delegates| PlantListPresenter
    LightMeasurementFragment -->|delegates| LightMeasurementPresenter
    DiaryFragment --> DiaryPresenter
    EnvironmentFragment -->|delegates| EnvironmentPresenter
    StatsFragment --> StatsPresenter
    MainActivity --> MainPresenterImpl
    PlantProfileFragment --> PlantProfilePresenter
    MainPresenterImpl --> PlantRepository
    PlantListPresenter --> PlantRepository
    LightMeasurementPresenter --> PlantRepository
    DiaryPresenter --> PlantRepository
    StatsPresenter --> PlantRepository
    PlantRepository --> PlantDatabase
```

Repository callbacks are marshalled to the main thread, allowing presenters to update their attached
views synchronously without leaking executors into the UI layer.

## Data flow diagrams

### Light measurement and calibration pipeline

```mermaid
flowchart LR
    LightSensor --> LightSensorHelper --> LightMeasurementPresenter
    Camera --> CameraLumaMonitor --> LightMeasurementPresenter
    LightMeasurementPresenter -->|updates| LightMeasurementFragment
    LightMeasurementPresenter -->|loads calibration| PlantRepository
    PlantRepository --> PlantCalibrationDao
    PlantRepository --> MeasurementDao
    LightMeasurementFragment -->|opens| CalibrationFragment
    CalibrationFragment --> LightSensorHelper
    CalibrationFragment -->|save factors| PlantRepository --> PlantCalibrationDao
```

The presenter smooths readings from both the ambient light sensor and the camera luma monitor, applies
per-plant calibration factors, and emits PPFD/DLI values to the fragment. The calibration fragment
lets the user persist ambient and camera factors, which are stored in the `PlantCalibration` Room
entity and fed back into future measurements.

### Environment logging pipeline

```mermaid
flowchart LR
    subgraph Sensors
        Hygrometer
        Thermometer
        LuxBridge
    end
    EnvironmentFragment -->|requests sync| EnvironmentPresenter
    EnvironmentPresenter -->|polls| Sensors
    EnvironmentPresenter -->|saves batch| PlantRepository
    PlantRepository --> EnvironmentEntryDao
    EnvironmentPresenter -->|binds history| EnvironmentFragment
    EnvironmentFragment -->|manual entry| EnvironmentPresenter
    EnvironmentPresenter -->|upserts| PlantRepository
    PlantRepository --> PlantProfileDao
```

Environment readings arrive either from Bluetooth/USB sensors or from manual controls inside the
Environment tab. `EnvironmentPresenter` aggregates the data by plant profile, debounces rapid
updates, and persists them as `EnvironmentEntry` rows. Whenever entries are saved the presenter also
refreshes the `PlantProfile` summary, keeping average PPFD/DLI, humidity, and temperature visible in
the UI without additional queries.

### Recommendation engine overview

```mermaid
flowchart TD
    PlantRepository --> MeasurementDao
    PlantRepository --> EnvironmentEntryDao
    PlantRepository --> PlantProfileDao
    MeasurementDao --> RecommendationEngine
    EnvironmentEntryDao --> RecommendationEngine
    PlantProfileDao --> RecommendationEngine
    SpeciesTargetDao --> RecommendationEngine
    RecommendationEngine --> RecommendationRepository
    RecommendationRepository --> PlantProfilePresenter
    RecommendationRepository --> EnvironmentPresenter
```

The `RecommendationEngine` is a pure Kotlin component fed by DAOs exposed from `PlantRepository`.
It correlates recent light measurements, averaged environment metrics, and the active `PlantProfile`
configuration for each plant against `SpeciesTarget` ranges. The engine produces structured
recommendations (e.g. "increase PPFD by 15%" or "raise humidity above 60%") that presenters inject
into their view models. `RecommendationRepository` caches the latest results so the UI can render
instantaneously after rotation or process recreation.

### Expanded plant profile storage

```mermaid
erDiagram
    PLANT ||--|{ PLANT_PROFILE : has
    PLANT_PROFILE ||--|{ ENVIRONMENT_ENTRY : captures
    PLANT_PROFILE }|--|| RECOMMENDATION_SNAPSHOT : summarizes
    PLANT ||--|{ MEASUREMENT : records
```

Plant profiles extend the legacy `Plant` entity with growth phase tracking, environment baselines,
and recommendation history. Each `PlantProfile` row stores the active growth stage, preferred
environment ranges, and cumulative stats used to power the recommendation engine. Environment
entries are keyed by profile so that multiple environments (e.g. veg tent vs. bloom room) can exist
per plant. Recommendation snapshots are denormalized results saved after each engine run to provide
an audit trail for the user.

### Import / export pipeline

```mermaid
flowchart LR
    User --> ExportManager --> BulkReadDao
    BulkReadDao --> PlantDatabase
    ExportManager -->|writes ZIP| data.json
    data.json --> User
    User --> ImportManager --> ImportParsers
    ImportParsers --> PlantRepository
    PlantRepository --> PlantDatabase
```

`ExportManager` gathers plants, measurements, diary entries, reminders, calibrations, species targets,
and media paths via `BulkReadDao`, writes the chosen manifest format, and zips the result with copied
media files. `ImportManager` detects JSON or CSV manifests, streams them through parser helpers, and
persists the entities inside a Room transaction to ensure atomic imports.

## Background work and scheduling

Reminder notifications are routed through `ReminderScheduler`. On Android 12+ the scheduler delegates
future triggers to `ReminderWorkManager`, which configures unique `WorkManager` jobs that invoke
`ReminderWorker` when alarms should fire. On earlier API levels the scheduler falls back to
`AlarmManager`, keeping the WorkManager code path ready once WorkManager is available on the device.
Whenever reminders are created, updated, or cancelled the scheduler broadcasts a widget update so the
home-screen quick actions reflect the latest state.

## Calibration storage

Per-plant calibration data lives in the `PlantCalibration` entity with columns `plantId`,
`ambientFactor`, and `cameraFactor`. `PlantRepository` exposes `getPlantCalibration` and
`savePlantCalibration` helpers used by both `LightMeasurementPresenter` (to refresh factors when a
plant is selected) and `CalibrationFragment` (to persist user-entered values). Calibrations are
exported in both CSV (`PlantCalibrations` section) and JSON (`plantCalibrations` array) backups and are
restored during imports before any measurements are processed.

## Resource and code conventions

- Layout files follow `fragment_<feature>.xml` and `activity_<screen>.xml` naming.
- Strings, IDs, and drawables are prefixed with their feature area (e.g. `measurement_*`, `diary_*`).
- Java code adheres to the Android Open Source Project style guide; fragments end in `Fragment`,
  presenters end in `Presenter`, and DAO interfaces live alongside their entities.
- Use `TODO(name):` comments for follow-up work and keep indentation at 4 spaces with UTF-8 encoding.

## Environment tab integration example

The sequence below illustrates how the Environment tab keeps Room and recommendation data in sync
when a new humidity reading is captured.

```mermaid
sequenceDiagram
    participant UI as EnvironmentFragment
    participant Presenter as EnvironmentPresenter
    participant Repo as PlantRepository
    participant DAO as EnvironmentEntryDao
    participant Engine as RecommendationEngine
    UI->>Presenter: onHumidityChanged(plantId, 58.2)
    Presenter->>Repo: saveEnvironmentEntry(plantId, HUMIDITY, 58.2)
    Repo->>DAO: insertOrMerge(entry)
    DAO-->>Repo: entryId
    Repo-->>Presenter: entryId
    Presenter->>Engine: refresh(plantId)
    Engine-->>Presenter: Recommendation(setPoint=60, delta=+1.8)
    Presenter-->>UI: render(entry, recommendation)
```

Because the presenter persists entries through the repository before running the engine, the Room
database remains the single source of truth. Recommendations are always derived from committed
measurements, ensuring that exports/imports and background sync generate the same advice as the live
UI.
