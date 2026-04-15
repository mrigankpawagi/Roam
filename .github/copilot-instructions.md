# Copilot Instructions for Roam

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (signed with debug keystore for sideloading)
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew testDebugUnitTest --tests "mrigank.roam.SomeTestClass"

# Run a single test method
./gradlew testDebugUnitTest --tests "mrigank.roam.SomeTestClass.someMethod"
```

CI requires JDK 17. There is no lint or format check configured.

## Architecture

MVVM with a single repository. All source code lives under `app/src/main/java/mrigank/roam/`.

```
Activities (UI) → ViewModels (state) → ExploreRepository → Room DAOs → SQLite
                                                         ↑
LocationTrackingService ─── broadcasts via LocalBroadcastManager ──→ Activities
                        └── persists explored cells directly via DAOs
```

- **Activities**: `MainActivity` (area list), `AreaSelectionActivity` (polygon drawing), `ExplorationActivity` (fog-of-war map with location tracking and eraser)
- **ViewModels**: `MainViewModel` (area CRUD, import/export), `ExplorationViewModel` (exploration state, eraser toggle, explored percentage)
- **Repository**: `ExploreRepository` is the single source of truth. Handles explored-percentage calculation (point-in-polygon for polygon areas, bounding-box grid for simple areas), JSON import/export.
- **Data**: Room database (`AppDatabase`, v2) with two tables — `areas` and `explored_cells`. `ExploredCell` uses a composite PK `(areaId, cellRow, cellCol)` with cascade delete.
- **Service**: `LocationTrackingService` is a foreground service with dual-track location filtering — a lenient track for UI display (smoothed with EMA, ~45m accuracy threshold) and a strict track for exploration recording (~20m accuracy, speed ≤5 m/s).
- **Utilities**: `GridUtils` is a singleton object with pure geographic math — Haversine distance, point-in-polygon (ray-casting), grid cell mapping, and polygon JSON serialization.

## Key Conventions

- **LiveData backing fields**: Private `_name: MutableLiveData` exposed as public `name: LiveData`.
- **Coroutines**: All DB operations run on `Dispatchers.IO` within `viewModelScope`.
- **Polygon storage**: Areas store polygon boundaries as a JSON string (`polygonsJson` column) — an array of rings, each ring being a list of `{lat, lng}` objects. Multi-polygon (disjoint) areas are supported.
- **Grid model**: Areas are overlaid with a uniform cell grid (`cellSizeMeters`, default 5m). Cells are identified by `(row, col)`. `GridUtils.cellsInRadius()` maps a GPS point to all cells within the exploration radius.
- **Explored cell inserts**: Use `OnConflictStrategy.IGNORE` for batch inserts to silently skip duplicates.
- **Service ↔ Activity IPC**: `LocalBroadcastManager` with action `ACTION_LOCATION_UPDATE`. Registered in `onResume()`, unregistered in `onPause()`.
- **Map overlays** (ExplorationActivity): Layer 0 = polygon boundaries, Layer 1 = fog-of-war bitmap, Layer 2 = location dot, Layer 3 = eraser touch overlay.
- **DB migrations**: Explicit `Migration(1, 2)` in `AppDatabase` adds `polygonsJson` and `radiusMeters` columns. New schema changes need a new migration.
- **View Binding**: Used instead of `findViewById`. Enabled in `app/build.gradle`.
- **Activity Result APIs**: Modern `registerForActivityResult` contracts for file picking (import/export), not the deprecated `startActivityForResult`.
