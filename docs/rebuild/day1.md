1. Error handling

New GlobalExceptionHandler returns JSON errors ({timestamp, status, error, message, path}): unknown file → 404, no healthy replica → 503, file over 10 MB → 413, bad or missing upload → 400.
If a storage node can’t be reached, the response is 503. Any other error returns a generic 500 and logs the details server side instead of leaking them.
ChunkService used to wrap every exception in a plain RuntimeException, which would have hidden the 404 and 503 cases. It now throws and rethrows StoredFileNotFoundException and ChunkUnavailableException as they are. Other failures are still wrapped.

2. Validation

Upload rejects empty files and strips any path from the filename (C:\x\..\a.pdf becomes a.pdf). It uses unnamed if nothing is left and rejects names over 255 characters.
Every {fileId} route returns 400 if the ID isn’t a UUID. handleUpload(file) still works, because the new handleUpload(file, filename) sits alongside it.

4. New endpoints

GET /files lists files with fileId, filename, chunkCount and minReplicas, where a low minReplicas means the file is under-replicated.
GET /files/{id} returns the chunks with their checksums and the nodes holding each one.
DELETE /files/{id} returns 204. It deletes the metadata first, and removes the chunks from the nodes only after the commit succeeds. That cleanup is best effort, so a node that is down keeps an orphaned chunk file, and the log records it.

Changes beyond the controller

Storage node: I added DELETE /deleteChunk/{chunkId}, since delete would otherwise never free disk space. The storage nodes need a rebuild (docker compose up --build) before delete reaches them. Until then, delete still removes the metadata and logs a warning.
UI: parseError in client-ui/src/api/index.js now shows the JSON message instead of the raw JSON. Otherwise the existing pages would have displayed the whole body. The UI doesn’t call the new endpoints yet.
Tests: I added FileControllerTest and FileManagementServiceTest. They cover the status codes, validation, delete, the list and details endpoints, and the unwrapped exceptions.

Limitations

The list has no size or upload date because the database doesn’t store them. Adding them means new columns on FileMetadataEntity.
If a repair job runs while a file is being deleted, the two could interfere. It’s unlikely at demo scale.