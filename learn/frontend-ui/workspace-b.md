# Workspace B - API Client Layer

## Objective

Build a centralized frontend API layer so all UI pages use one consistent contract for backend communication, loading state, and error handling.

## Scope

- Create reusable API modules under `client-ui/src/api/`.
- Define shared request/response models in `client-ui/src/types.ts`.
- Support both real backend mode and mock mode.
- Expose clean functions for upload, download, node status, and system health.

## Tasks

1. Project Wiring
- Create `api` folder structure:
  - `client.ts` (base fetch/axios wrapper)
  - `files.ts` (upload/download APIs)
  - `monitoring.ts` (node status/health APIs)
  - `index.ts` (single export surface)

2. Shared Types
- Add or update `client-ui/src/types.ts` with:
  - `UploadResponse` (`fileId`, optional filename info)
  - `NodeStatus` (`nodeId`, `status`, `lastHeartbeat`, optional `nodeUrl`)
  - `SystemHealth` (active/failed counts, optional node lists)
  - Standard `ApiError` shape

3. API Functions
- Implement:
  - `uploadFile(file: File): Promise<UploadResponse>`
  - `downloadFile(fileId: string): Promise<Blob>`
  - `getNodeStatus(): Promise<NodeStatus[]>`
  - `getSystemHealth(): Promise<SystemHealth>`

4. Error Normalization
- Convert transport/server failures into one standard error object:
  - `message`
  - `statusCode` (if available)
  - `code` (optional)

5. Loading Utilities
- Add lightweight helpers to track:
  - request start
  - request success/failure
- Keep utilities UI-framework friendly (usable with existing state management).

6. Mock Mode
- Add environment switch (example: `VITE_USE_MOCK_API=true`).
- Provide mock responses for all four APIs.
- Ensure pages can run without backend availability.

7. Contract Guardrails
- Keep API endpoint paths centralized as constants.
- Validate required fields before returning typed objects.

## Deliverables

- `api/` module ready and imported by pages.
- `types.ts` finalized and used everywhere for API contracts.
- Mock mode tested for all API functions.
- Real mode tested against running backend services.

## Definition of Done

- Upload API returns typed `fileId` with proper error handling.
- Download API returns file blob and handles invalid `fileId`.
- Node status and health APIs return normalized data for dashboard usage.
- All functions work in both mock mode and real backend mode.
- No page performs raw fetch calls directly; all use `api/` layer.

## Dependencies

- Workstream A (routing/shell) completed.
- Backend endpoints available or mocked:
  - `POST /upload`
  - `GET /download/{fileId}`
  - node status/health endpoints (or temporary mock mapping)

## Risks and Mitigation

- Endpoint mismatch:
  - Keep endpoint constants in one file for quick updates.
- Inconsistent backend error payloads:
  - Normalize errors in base client wrapper.
- Missing monitoring endpoints:
  - Continue using mock mode until backend contract is finalized.

## Execution Order

1. Create `types.ts`
2. Build base API client
3. Implement files APIs
4. Implement monitoring APIs
5. Add mock adapter
6. Integrate into pages and smoke test
