# Authentication, phase 1: HTTP Basic on the master

## Why

The master had no authentication. Anyone who could reach port 8080 could upload, download, delete files, read logs and take nodes offline with the Chaos Simulator. Phase 1 puts one login in front of all of that.

This phase protects the **user-facing** endpoints. Node traffic (`POST /heartbeat`, and the master's calls to the nodes) is phase 2.

## What changed

| File | Change |
| --- | --- |
| `master-service/pom.xml` | `spring-boot-starter-security`, and `spring-security-test` for tests |
| `config/AuthProperties.java` | Validated `app.auth.username` and `app.auth.password`. A blank password stops startup. |
| `config/SecurityConfig.java` | The rules below |
| `application.properties` | `app.auth.username=${APP_USERNAME:admin}`, `app.auth.password=${APP_PASSWORD:}` |
| `compose.yaml`, `compose.prod.yaml` | Pass `APP_USERNAME` and `APP_PASSWORD` to the master. Compose stops with a message if `APP_PASSWORD` is missing. |
| `.env.example`, `.gitignore` | Template for the password; root `.env` is not committed |
| `client-ui` | `LoginPage`, login header kept in `sessionStorage` and sent on every request, "Sign out" button |
| Tests | New `SecurityConfigTest`; `FileControllerTest` and `MasterServiceApplicationTests` updated |

## The rules (`SecurityConfig`)

- Every endpoint needs the login, except `POST /heartbeat` (still open until phase 2).
- Stateless: no session, no CSRF token. Each request carries its own `Authorization: Basic ...` header.
- CORS preflight (`OPTIONS`) is allowed through, and error pages are not hidden behind a 401.
- A failed login answers `401` **without** the `WWW-Authenticate` header, so the browser does not open its own login dialog over the UI's form.
- There is no default password. The master refuses to start without `APP_PASSWORD`.

## How to run

```bash
# 1. Docker: copy the template and set a password
cp .env.example .env        # then edit .env and set APP_PASSWORD
docker compose up --build

# 2. Without Docker (IDE or mvn): set the variable first
#    PowerShell:  $env:APP_PASSWORD = "choose-a-password"
mvn spring-boot:run
```

Open the UI and sign in with `APP_USERNAME` (default `admin`) and your password.

Quick check from a terminal:

```bash
curl -i http://localhost:8080/files                       # 401
curl -i -u admin:YOUR_PASSWORD http://localhost:8080/files  # 200
```

## Things to know

- **The UI keeps the login in `sessionStorage`.** A page refresh keeps you signed in, and closing the tab signs you out. The trade-off: any script injected into the page (XSS) could read it. A short-lived token (phase 3) is the proper fix.
- **Basic auth over plain HTTP is readable on the network.** Use HTTPS before this is exposed beyond your own machine or a firewalled address.
- **Production compose:** nginx's `auth_basic` and the master both read the same `Authorization` header that the UI sends. `deploy/.htpasswd` must therefore contain the same user and password as `APP_USERNAME` and `APP_PASSWORD`.
- **One shared login, not user accounts.** Everyone who knows the password sees every file. Per-user accounts and file ownership are phase 3.
- **`POST /heartbeat` was left open in phase 1.** Phase 2 closes it with a shared node token; see `authentication-phase2.md`.
