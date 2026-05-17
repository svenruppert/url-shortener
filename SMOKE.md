# Smoke Test — REST end-to-end

Quick `curl` recipe to verify a running URL Shortener installation is wired
correctly: bootstrap, login, user management, self-service password change,
core link operations. Covers everything Phases 1–4 introduced.

Assumes the admin REST server is reachable at `http://localhost:9090` and
the redirect server at `http://localhost:8081` (the defaults from
`DefaultValues`).

---

## A. Fresh install (persistent mode)

A freshly started server with no existing user store. Expected default:
`urlshortener.security.bootstrap.mode=PERSISTENT_FILE` — the server writes a
one-time bootstrap token to `data/.bootstrap-token` (POSIX `0600`) and waits
for the initial-admin call.

### A1. Confirm bootstrap is required

```bash
curl -s http://localhost:9090/api/bootstrap/status | jq
# { "required": true }
```

### A2. Read the bootstrap token

```bash
cat data/.bootstrap-token
# token=6J5N-DP4C-H4MQ-4Q8Z-TT98
# createdAt=2026-05-17T08:00:00Z
```

Capture the token value:

```bash
BOOT_TOKEN=$(awk -F= '/^token=/{print $2}' data/.bootstrap-token)
echo "$BOOT_TOKEN"
```

### A3. Create the initial administrator

```bash
curl -s -X POST http://localhost:9090/api/bootstrap/admin \
  -H 'Content-Type: application/json' \
  -d "{
    \"bootstrapToken\": \"$BOOT_TOKEN\",
    \"username\":      \"admin\",
    \"password\":      \"choose-a-strong-pw\",
    \"displayName\":   \"Administrator\"
  }" | jq
# { "status": "created" }
```

Password must be ≥ 8 characters. The token is single-use; the file is
deleted on success. Bootstrap status now returns `required: false`.

---

## B. Dev / test mode (in-memory)

For a quick run without bootstrap, start the server with
`-Durlshortener.security.bootstrap.mode=DISABLED`. Two seed accounts are
created:

| username | password | role |
|---|---|---|
| `admin` | `admin` | `ROLE_ADMIN` |
| `user`  | `user`  | `ROLE_USER` |

Do **not** use this mode for anything reachable from a public network.

---

## C. Login + identity

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:9090/api/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)
echo "admin token: $ADMIN_TOKEN"

# Subject info
curl -s http://localhost:9090/api/me \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Operations visible to the current subject
curl -s http://localhost:9090/api/operations \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.operations | length'
```

Wrong password returns `401 { "error": "invalid_credentials" }`. After
five rapid failed attempts you get `429 { "error": "too_many_attempts" }`
with a `Retry-After` header (brute-force throttle).

---

## D. User management (admin only)

```bash
# List
curl -s http://localhost:9090/api/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Create
curl -s -X POST http://localhost:9090/api/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "username":    "alice",
    "password":    "alice-strong-pw",
    "displayName": "Alice",
    "role":        "ROLE_USER"
  }' | jq

# Promote to admin
curl -s -X PUT http://localhost:9090/api/users/alice \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"role":"ROLE_ADMIN"}' | jq

# Disable
curl -s -X PUT http://localhost:9090/api/users/alice \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"enabled":false}' | jq

# Admin reset password (revokes alice's existing tokens)
curl -s -X POST http://localhost:9090/api/users/alice/password \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"newPassword":"alice-reset-pw"}' -w '%{http_code}\n'
# 204

# Delete
curl -s -X DELETE http://localhost:9090/api/users/alice \
  -H "Authorization: Bearer $ADMIN_TOKEN" -w '%{http_code}\n'
# 204
```

Self-protection (server enforces, returns `409`):

- `DELETE /api/users/admin` while logged in as admin → `self_delete_forbidden`
- `PUT /api/users/admin {"enabled":false}` → `self_disable_forbidden`
- `PUT /api/users/admin {"role":"ROLE_USER"}` on the last admin → `last_admin_protected`

`ROLE_USER` calling any of these gets `403 { "error": "forbidden" }`.

---

## E. Self-service password change

Anyone authenticated can change their own password. On success the server
revokes **all** of the caller's active tokens.

```bash
USER_TOKEN=$(curl -s -X POST http://localhost:9090/api/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user","password":"user"}' | jq -r .token)

curl -s -X POST http://localhost:9090/api/me/password \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"oldPassword":"user","newPassword":"user-new-pw"}' -w '%{http_code}\n'
# 204

# Old token is now revoked
curl -s http://localhost:9090/api/me \
  -H "Authorization: Bearer $USER_TOKEN" -w '\n%{http_code}\n'
# 401
```

Wrong old password returns `401 { "error":"invalid_credentials" }` and
leaves the password unchanged (the UI does **not** log you out on this
specific 401 — domain failure, not session failure).

---

## F. Link operations (Phase 2 surface)

```bash
# Create a short link (owner = current subject)
curl -s -X POST http://localhost:9090/api/shorten \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"shortURL":"hello","url":"https://example.com"}' | jq

# Resolve via the public redirect server (NO auth)
curl -s -I http://localhost:8081/hello
# HTTP/1.1 302 Found
# Location: https://example.com

# List own links
curl -s "http://localhost:9090/api/list" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.items | length'

# Owner check: a ROLE_USER cannot edit someone else's link
curl -s -X PUT "http://localhost:9090/api/edit" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"shortCode":"hello","originalUrl":"https://elsewhere.example.com"}' \
  -w '%{http_code}\n'
# 403
```

---

## G. Logout

```bash
curl -s -X POST http://localhost:9090/api/logout \
  -H "Authorization: Bearer $ADMIN_TOKEN" -w '%{http_code}\n'
# 204

# Token is revoked
curl -s http://localhost:9090/api/me \
  -H "Authorization: Bearer $ADMIN_TOKEN" -w '\n%{http_code}\n'
# 401
```

---

## Expected HTTP shape reference

| Path | Methods | Permission (filter) | Notes |
|---|---|---|---|
| `/api/bootstrap/status` | GET | — | Never leaks the token |
| `/api/bootstrap/admin` | POST | — | One-shot, token-gated |
| `/api/login` | POST | — | Throttled |
| `/api/logout` | POST | — | Revokes the bearer token |
| `/api/me` | GET | authenticated | Subject info |
| `/api/me/password` | POST | authenticated | Self-service, revokes all sessions |
| `/api/operations` | GET | authenticated | Subject-filtered operation set |
| `/api/users` | GET, POST | `user:read` (filter) + per-method check | Admin-only in practice |
| `/api/users/{u}` | PUT, DELETE | `user:read` filter + per-method | Self-protection rules apply |
| `/api/users/{u}/password` | POST | `user:read` + `user:update` | Revokes target's sessions |
| `/api/shorten` etc. | … | `link:*` | Owner-aware for `:own` perms |
| `/{shortCode}` (port 8081) | GET | **public** | Never `401`/`403` |
