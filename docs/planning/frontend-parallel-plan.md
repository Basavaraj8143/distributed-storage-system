# Frontend Parallel Plan (April 26, 2026)

## Goal

Build a usable frontend in parallel with backend progress so upload/download and monitoring demo flows work end-to-end.

## Parallel Workstreams

### Workstream A - App Shell and Routing

- Create base layout: header, sidebar, content area, mobile navigation.
- Add routes/pages:
  - Dashboard
  - Upload
  - Files
  - Node Status
  - Logs
- Deliverable: complete navigable UI skeleton with placeholders.

### Workstream B - API Client Layer

- Create centralized API module (`api/`):
  - `uploadFile(file)`
  - `downloadFile(fileId)`
  - `getNodeStatus()`
  - `getSystemHealth()`
- Add global error normalization and loading helpers.
- Keep a mock mode switch for frontend progress without backend blockers.
- Deliverable: reusable API layer used by all pages.

### Workstream C - Core User Flow UI

- Upload page:
  - file picker
  - submit action
  - progress/loading state
  - success display with `fileId`
- Files page:
  - list/table of uploaded files (or mock list if endpoint absent)
  - download action by `fileId`
- Deliverable: upload and download flow usable from UI.

### Workstream D - Monitoring Dashboard

- Node cards/table showing:
  - node id
  - active/failed status
  - last heartbeat
  - replica health summary (if available)
- Auto refresh every 5-10 seconds.
- Deliverable: live operational dashboard for demo.

## Coordination Rules

- Freeze API contracts in first 30 minutes.
- Use one shared `types.ts` for request/response models.
- Use feature branches per workstream:
  - `frontend/shell-routing`
  - `frontend/api-client`
  - `frontend/upload-download-ui`
  - `frontend/dashboard-monitoring`
- Merge in small increments every 2-3 hours.
- Avoid cross-editing same files unless coordinated.

## Today Timeline

1. 0:00-0:30
- Kickoff, contract freeze, branch split, task ownership.

2. 0:30-3:00
- A/B/C/D workstreams execute in parallel.

3. 3:00-3:30
- Integration sync, resolve API and state wiring conflicts.

4. 3:30-6:00
- Responsive fixes, loading/error polishing, UX cleanup.

5. 6:00-6:30
- End-to-end test run and demo checklist validation.

## Definition of Done (Today)

- Upload works from frontend and returns visible `fileId`.
- Download is triggerable from UI and returns file.
- Dashboard shows node status updates on interval refresh.
- UI is usable on desktop and mobile.
- Demo flow runs without manual backend intervention.

## Risks and Mitigation

- Missing backend endpoint:
  - Use mock mode and keep integration adapter ready.
- API response mismatch:
  - Enforce shared `types.ts` and one contract owner.
- Merge conflicts:
  - Keep write ownership clear and merge frequently.

## End-of-Day Demo Flow

1. Open dashboard and verify node statuses update.
2. Upload a file from UI and capture returned `fileId`.
3. Download same file from files page.
4. Show monitoring panel during operation.
5. Confirm stable flow under normal operation.
