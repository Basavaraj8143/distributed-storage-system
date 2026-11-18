# Authentication, phase 2: a shared token between the master and the nodes

## Why

After phase 1, users need a login. The traffic between the master and the storage nodes did not:

- `POST /heartbeat` was open, so any program that could reach the master could register itself as a node. The master would then send it real chunks to "store".
- The nodes' own endpoints (`/storeChunk`, `/getChunk`, `/deleteChunk`, `/chunks`, `/simulate/*`) answered anyone who could reach them.

CORS does not help here. It only restricts browsers; `curl` or another program ignores it.

Phase 2 adds one secret, `NODE_TOKEN`, known only to the master and the nodes. Every call between them carries it in the `X-Node-Token` header.

```
                      X-Node-Token                      X-Node-Token
 storage node  ------------------------>  master  ------------------------>  storage node
        POST /heartbeat (checked by               /storeChunk, /getChunk, ...
        the master's NodeTokenFilter)             (checked by the node's NodeTokenFilter)
```

## What changed

| Where | File | Change |
| --- | --- | --- |
| master | `config/NodeAuthProperties.java` | Validated `app.node.token`: required, at least 16 characters |
| master | `config/NodeTokenFilter.java` | `POST /heartbeat` is rejected with 401 unless the token is right |
| master | `config/SecurityConfig.java` | Puts the filter in the security chain; `/heartbeat` still needs no user login |
| master | `config/AppConfig.java` | The `RestTemplate` adds the token to every call it makes to a node |
| storage-node | `config/NodeAuthProperties.java` | Same validated setting |
| storage-node | `config/NodeTokenFilter.java` | Every request is rejected with 401 unless the token is right |
| storage-node | `config/AppConfig.java` | The `RestTemplate` adds the token to the heartbeat |
| both | `application.properties` | `app.node.token=${NODE_TOKEN:}` (no default) |
| compose | `compose.yaml`, `compose.prod.yaml`, `.env.example` | `NODE_TOKEN` passed to the master and all five nodes |
| tests | `NodeTokenTest` (both services), `SecurityConfigTest` | 401 without or with a wrong token, 200 with it, the token is sent on outgoing calls |

`RestStorageNodeClient` and the services did not change. The token is added in one place, the `RestTemplate`, which is the payoff of having the `StorageNodeClient` gateway.

## Design points

- **No default and a minimum length.** A missing or short token stops the application from starting, so it can never run with a guessable secret.
- **Constant-time comparison** (`MessageDigest.isEqual`), so response timing does not reveal how close a guess was.
- **Two separate secrets for two kinds of caller.** The user login does not work on `/heartbeat`, and the node token does not work on `/files`. Tests cover both directions.
- **The master's filter is not a Spring bean.** `SecurityConfig` creates it, so Spring Boot does not register it a second time as a plain servlet filter.

## How to run

```bash
# .env (next to compose.yaml)
APP_PASSWORD=choose-a-password
NODE_TOKEN=<output of: openssl rand -hex 32>

docker compose up --build
```

Without Docker, every process needs the same value in its environment:

```powershell
$env:NODE_TOKEN = "<the same long value>"   # in the master's terminal / run config
$env:NODE_TOKEN = "<the same long value>"   # in each storage node's terminal / run config
```

Checks (use `curl.exe` in PowerShell):

```bash
# no token: 401
curl.exe -i -X POST http://localhost:8080/heartbeat -H "Content-Type: application/json" -d "{}"
# right token: the controller answers 400 ("nodeId is required"), which shows it got past security
curl.exe -i -X POST http://localhost:8080/heartbeat -H "Content-Type: application/json" -H "X-Node-Token: YOUR_TOKEN" -d "{}"
```

Then open Node Status in the UI: all five nodes should be ACTIVE within about 15 seconds.

## Limits

- **One shared secret.** If one node is compromised, the secret for the whole cluster is. Per-node tokens or mutual TLS would fix that.
- **Plain HTTP.** On a network where others can listen, the token and the data can be read. Inside the Docker network on one machine this is low risk; across machines, use TLS.
- **The master does not check that the heartbeat's `nodeUrl` is one it expects.** With the token, only a holder of the secret can register, but a holder could still announce any URL. Checking it against `storage.nodes` would close that.
