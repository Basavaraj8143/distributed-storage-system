# Workspace D - Monitoring Dashboard UI

## Objective

Build operations screens to monitor node health and overall system state from frontend.

## Scope

- Implement Dashboard page with live health summary
- Implement Node Status page with node-level status table/cards
- Use existing Workspace B APIs:
  - `getNodeStatus()`
  - `getSystemHealth()`
- Auto-refresh every 5-10 seconds

## What To Build

1. Dashboard Page
- Show system health status (Healthy/Degraded/Down as provided by backend)
- Show active node count
- Show failed node count
- Show last refresh time
- Manual refresh button

2. Node Status Page
- List nodes with:
  - node id
  - status (ACTIVE / FAILED)
  - last heartbeat
  - node URL (if available)
- Add status color indicator (green/red)
- Manual refresh button

3. Shared Behavior
- Loading state on first load
- Error message if API fails
- Empty state if no nodes returned
- Polling interval (default 10s) with cleanup on unmount

## Files Expected

- `client-ui/src/pages/DashboardPage.jsx`
- `client-ui/src/pages/NodeStatusPage.jsx`
- optional helper: `client-ui/src/utils/time.js` (for heartbeat formatting)
- `client-ui/src/styles.css` (small additions only)

## Backend Contract Assumption

- `GET /system/health` returns summary object
- `GET /nodes/status` returns node list

If response shape differs, map fields in page-level adapter (keep API layer simple).

## Definition of Done

- Dashboard auto-refreshes and shows live summary
- Node Status page auto-refreshes and shows current node states
- Manual refresh works on both pages
- Works in real mode and mock mode
- Error/empty/loading states are readable

## Verification Checklist

1. Start backend + nodes
2. Open dashboard page and confirm health values update
3. Open node status page and confirm all active nodes visible
4. Stop one node and wait for failure detection window
5. Confirm UI reflects failed node on next polling cycle
6. Re-enable node and confirm recovery status update

## Notes

- Keep implementation simple and direct.
- Avoid extra state libraries or heavy abstractions.
