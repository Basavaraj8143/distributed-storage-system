# Running ByteHarvest: Docker and IntelliJ

Run it one way at a time. Docker and IntelliJ both use port 8080 (master) and 5173 (UI), so they clash if started together. They also keep separate data: Docker uses its own volumes, while the IntelliJ master writes `metadata.db` in its working folder and the nodes write `storage_<port>` folders.

## Two values you need

| Name | Used by | What it is |
| --- | --- | --- |
| `APP_PASSWORD` | master only | The password for signing in to the UI and API. `APP_USERNAME` is optional (default `admin`). |
| `NODE_TOKEN` | master and all 5 nodes | The secret they share. At least 16 characters, **the same value everywhere**. |

Generate a token:

```powershell
# PowerShell (prints 64 hex characters)
$b = New-Object byte[] 32; (New-Object Security.Cryptography.RNGCryptoServiceProvider).GetBytes($b); ($b | % { $_.ToString('x2') }) -join ''
```
```bash
# anywhere with openssl
openssl rand -hex 32
```

There are no defaults on purpose. The master refuses to start without `APP_PASSWORD` and `NODE_TOKEN`, and a node refuses to start without `NODE_TOKEN`.

## A. Docker

1. Copy `.env.example` to `.env` in the project root (next to `compose.yaml`) and fill it in:
   ```
   APP_USERNAME=admin
   APP_PASSWORD=your-password
   NODE_TOKEN=the-64-character-value
   ```
2. Start everything:
   ```bash
   docker compose up --build
   ```
3. Open <http://localhost:5173>, sign in, and open **Node Status**. All five nodes should be ACTIVE within about 15 seconds.

If a value is missing, compose stops at once and names it. `.env` is git-ignored; never commit it.

Stop with `docker compose down`. Add `-v` to also delete the stored data.

## B. IntelliJ (no Docker)

Seven processes: the master, five storage nodes and the UI.

| Process | Environment variables |
| --- | --- |
| Master (`MasterServiceApplication`) | `APP_PASSWORD=your-password;NODE_TOKEN=the-token` (optional: `APP_USERNAME=admin`) |
| Node 1 (`StorageNodeApplication`) | `SERVER_PORT=5001;NODE_TOKEN=the-token` |
| Node 2 to 5 | the same, with `SERVER_PORT=5002`, `5003`, `5004`, `5005` |
| UI (`client-ui`) | none |

### Setting it up

1. **Master:** Run → Edit Configurations → select the master application. Paste the variables into *Environment variables*, separated by `;`. If the field is hidden, use *Modify options → Environment variables*.
2. **Nodes:** create one Spring Boot configuration for `StorageNodeApplication` with `SERVER_PORT=5001;NODE_TOKEN=...`. Use *Copy Configuration* four times and change only the port. The node IDs (`node-1` to `node-5`) come from the ports, so use exactly 5001 to 5005.
3. **UI:** in `client-ui`, run `npm install` once, then `npm run dev`. It serves on <http://localhost:5173>.
4. **Start order:** nodes, then master, then UI. Nodes retry their heartbeat every 5 seconds, so a late master is fine.

The defaults already match this layout, so nothing else needs changing:
- the master expects nodes at `localhost:5001` to `5005`;
- the nodes send heartbeats to `localhost:8080`;
- the UI calls `localhost:8080`, which the master's CORS setting allows (`http://localhost:5173`).

If the `NODE_TOKEN` differs between processes, the nodes stay un-ACTIVE and log `Failed to send heartbeat ... 401`.

## Checking that it works

Use `curl.exe` in PowerShell (plain `curl` is an alias for something else there).

```powershell
curl.exe -i http://localhost:8080/files                                   # 401: login required
curl.exe -i -u admin:your-password http://localhost:8080/files            # 200
curl.exe -i -u admin:wrong http://localhost:8080/files                    # 401
curl.exe -i -X POST http://localhost:8080/heartbeat -H "Content-Type: application/json" -d "{}"        # 401: no node token
curl.exe -i -X POST http://localhost:8080/heartbeat -H "Content-Type: application/json" -H "X-Node-Token: the-token" -d "{}"   # 400: got past security
curl.exe -i http://localhost:5001/chunks                                  # 401 (IntelliJ only: Docker does not publish node ports)
```

Then in the UI: sign in, upload a small file, and check it on **Files** with 2/2 replicas.

## Tests

```bash
cd master-service && mvn test
cd storage-node   && mvn test
```

The tests set their own password and token, so they need none of the above.

## Common problems

| Symptom | Cause and fix |
| --- | --- |
| Master fails with `No password configured` | `APP_PASSWORD` is not set. Set it in the run configuration or `.env`. |
| Master or node fails with `No node token configured` or `too short` | `NODE_TOKEN` is missing or under 16 characters. |
| `docker compose` stops with `Set APP_PASSWORD in .env` | `.env` is missing or the value is empty. |
| Nodes never turn ACTIVE | The token differs between the master and the nodes, or the master is not running. Check the node logs for `401`. |
| `curl` complains about `-i` or `-u` | You are using PowerShell's `curl` alias. Use `curl.exe`. |
| Port 8080 or 5173 already in use | Docker and IntelliJ are both running. Stop one. |
| A `WARNING: A restricted method in java.lang.System` message on the master | Harmless (SQLite driver on a newer JDK). Add `--enable-native-access=ALL-UNNAMED` to VM options to silence it. |
| UI asks you to sign in again | The login was rejected (changed password) or the tab was closed. The login is kept in `sessionStorage`. |

## Known limits

See `authentication-phase1.md` and `authentication-phase2.md`. In short: plain HTTP, one shared secret and one shared login, no limit on login attempts, no audit log of failures, and unencrypted chunks on disk.
