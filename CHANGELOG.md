# Changelog

All notable changes to this project will be documented in this file.

## [2.0.0] - 2025-11-17

### Added
- Upload: SHA-256 based instant-upload check (`/api/files/exists`), per-user scope.
- Chunk upload: receive, status, merge with SHA-256 integrity check.
- Download pipeline: Range/If-Range/ETag/304 and HEAD responses; counters.
- Admin operations: admin download, recycle bin filtering, scheduled deletes.
- Upload policy: allow-all switch and suffix whitelist with admin UI.

### Changed
- More robust quota handling: atomic reserve before write; rollback on failure.

### Notes
- Instant-upload only skips client upload; it does not create another record in a new target folder. Content de-duplication/aliasing is not implemented.

[2.0.0]: https://github.com/your-org/your-repo/releases/tag/v2.0.0

