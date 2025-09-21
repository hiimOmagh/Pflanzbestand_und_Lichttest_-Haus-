# Import and Export Formats

The application supports two archive formats for backing up and restoring data:

- **CSV + ZIP** – the legacy format containing a CSV manifest and media files.
- **JSON archive** – a structured `data.json` manifest packaged with the same media files.

Both archives bundle plant photos, diary images, and gallery photos alongside metadata for
plants, measurements (including DLI and notes), reminders, species targets, and calibration
factors. Media files are stored as individual files inside the ZIP container and referenced by
filename from the manifest.

## Choosing a Format

Manual exports now display a format picker with CSV+ZIP and JSON options. The last selection is
saved in the application settings and reused for subsequent exports. Automated weekly backups use
JSON archives by default to preserve the richest dataset.

## Import Compatibility

The import pipeline automatically detects JSON archives (based on MIME type or file extension) and
parses them with a streaming JSON reader before inserting records via the same Room transaction
logic used for CSV files. Existing CSV archives remain fully supported.
