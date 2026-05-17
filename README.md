# URL Shortener – Core Java + Vaadin Flow

A fully self-contained **URL Shortener** implemented in **pure Java 26**, featuring a lightweight REST service, a Vaadin Flow–based administration UI, and a simple Java client SDK.  
The project demonstrates how to build a secure, modular web application **without using any external frameworks** like Spring Boot or Jakarta EE.

---

## 🧩 Project Overview

### Modules

| Module | Description |
|---------|-------------|
| **urlshortener-core** | Core logic, DTOs, encoding utilities, and validation policies. |
| **urlshortener-server** | REST server for administration and redirection, implemented using `com.sun.net.httpserver.HttpServer`. |
| **urlshortener-client** | Minimal Java client that communicates with the admin API. |
| **urlshortener-ui** | Vaadin Flow 25.1.1 web application (WAR) providing a graphical interface for managing shortened URLs. |

---

## ⚙️ Architecture

```
┌────────────────────────────────────────────────────────────┐
│                         UI (Vaadin)                        │
│  - Create new short links                                  │
│  - Manage and delete existing mappings                     │
│  - Visualize statistics and status                         │
└──────────────▲───────────────────────────────┬─────────────┘
               │ REST (JSON over HTTP)         │
               │                               │
┌──────────────┴───────────────────────────────▼───────────────┐
│                    Admin REST Server                         │
│  /shorten     → Create new mapping                           │
│  /delete      → Remove existing mapping                      │
│  /list        → List active and expired mappings             │
│  /{code}      → Redirect to original URL                     │
│                                                    Port 8081 │
└──────────────────────────────────────────────────────────────┘
```

Data is stored via **EclipseStore** (file-based persistence) when the server is started in persistent mode, with an in-memory fallback for tests and ephemeral runs. The admin API is protected by an authoritative REST security layer (token-based authentication, role/permission model, owner-aware authorization, audit pipeline). The public redirect endpoint remains unauthenticated by design.

---

## 🧰 Technology Stack

- **Java 26**
- **Vaadin Flow 25.1.1**
- **Jetty 12 (WAR Deployment)** – for the UI
- **Core JDK HttpServer** – for the REST and redirect endpoints
- **EclipseStore 4.0.1** – file-based persistence for mappings, statistics, and preferences
- **security-for-flow 00.60.00** – REST + Vaadin security stack (authentication, RBAC, audit, bootstrap)
- **No frameworks**, no Spring, no Jakarta EE

---

## 🧪 Running the Application

### Start the REST Server
```bash
cd urlshortener-server
mvn clean package
java -jar target/urlshortener-server-*.jar
```

Default ports:
- **Admin API:** `http://localhost:9090`
- **Redirect Server:** `http://localhost:8081`

### Start the Vaadin UI
Deploy the WAR file from `urlshortener-ui/target/` into your Jetty installation:
```bash
cp target/urlshortener-ui.war /opt/jetty-base/webapps/
```

The UI connects to the Admin API (default: `localhost:9090`) and allows you to create, list, and delete short URLs.

---

## 🧩 Example API Calls

### Create a new mapping
```bash
curl -X POST http://localhost:9090/shorten   -H "Content-Type: application/json"   -d '{"originalUrl":"https://svenruppert.com"}'
```

### List all mappings
```bash
curl http://localhost:9090/list
```

### Redirect
Visit `http://localhost:8081/{shortCode}` in your browser.

---

## 🧑‍💻 Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-repo/url-shortener.git
   cd url-shortener
   ```

2. **Build all modules**
   ```bash
   mvn clean verify
   ```

3. **Run tests**
   ```bash
   mvn test
   ```

4. **Open the UI**
   Deploy the WAR or start Vaadin in development mode.

---

## 🧱 Testing

The codebase has four test layers, each targeting a different abstraction:

| Module | Tests | Focus |
|---|---|---|
| `urlshortener-core` | 92 | Pure-logic utilities (Base62, alias policy, JSON, statistics aggregates). |
| `urlshortener-server` | 153 | REST handlers, security filter, owner checks, user management, owner-filtered statistics export — driven against a real `HttpServer` instance. |
| `urlshortener-client` | 111 | Java SDK behavior against a real server (login, bulk ops, user management, 401 handling). |
| `urlshortener-ui` | 17 | Vaadin Flow browserless tests for `LoginView`, `UserManagementView`, `ProfileView` + dialogs. |

**Vaadin browserless tests** use the free `com.vaadin:browserless-test-junit6` library (Vaadin ≥ 25.1) — no Selenium, no browser, no servlet container. The base class spins up an in-process `ShortenerServer` so every UI test exercises a real REST round-trip. No mocks for code under test; isolation comes from in-memory stores and per-test `SubjectStores` reset.

**Mutation testing** is wired via the [PIT](https://pitest.org/) plugin (`pitest-maven` + `pitest-junit5-plugin`). Run scoped per package during development:

```bash
mvn -pl urlshortener-ui org.pitest:pitest-maven:mutationCoverage \
    -Dpitest-prod-classes='com.svenruppert.urlshortener.ui.vaadin.views.*' \
    -Dpitest-test-classes='junit.com.svenruppert.urlshortener.ui.browserless.*'
```

The HTML report lands in `urlshortener-ui/target/pit-reports/index.html`.

**Current mutation coverage on the Vaadin views** (isolated PIT runs; skill target 60–75 % for view code):

| Class | Mutation Coverage | Test Strength |
|---|---|---|
| `ProfileView` | 91 % | 100 % |
| `ChangePasswordDialog` | 85 % | 100 % |
| `CreateUserDialog` | 83 % | 100 % |
| `LoginView` | 82 % | 100 % |
| `AdminResetPasswordDialog` | 77 % | 100 % |
| `EditUserDialog` | 71 % | 100 % |
| `UserManagementView` | 53 % | 100 % |

Test Strength of 100 % across the board means every covered line is reliably mutation-tested — no "lying tests" that pass while assertions fail to catch behavior changes. The single class below the 60 % target (`UserManagementView`) has remaining survivors in notification strings and tooltip attributes, classified as observationally equivalent.

To run the full test suite:
```bash
mvn verify
```

---

## 🔒 Security Notes

- The admin API is authoritatively protected by the integrated `security-for-flow` stack:
  - Token-based authentication (`POST /api/login` → bearer token, `POST /api/logout` revokes it).
  - Role-based authorization with two roles (`ROLE_USER`, `ROLE_ADMIN`) and a fine-grained permission model (`link:*`, `user:*`, `admin:*`).
  - Owner-aware checks for `:own`-scoped operations; admins with `:all` permissions bypass owner restrictions.
  - Initial-administrator bootstrap via a one-time token written to `data/.bootstrap-token` (mode `PERSISTENT_FILE`).
  - Login throttling, security audit pipeline, and operation discovery via `GET /api/operations` for the UI.
- The Admin API should still **not be publicly accessible**; restrict it to `localhost` or a private subnet.
- Redirect endpoints (`/{shortCode}` on the redirect server) are public by design and never require authentication.

---

## 🌍 Deployment Notes

Typical deployment setup:
- **Server 1 (public):** Redirect server on port 80/8081  
- **Server 2 (private or localhost):** Admin server + Vaadin UI (port 9090)

You can connect both servers on the same host using `localhost` communication.

---

## 📚 Upcoming Features (for Advent Calendar Series)

This project serves as the foundation for a **24-day educational series** that incrementally improves the shortener with:
- Vaadin features (Grid, Dialogs, Charts, Theming, i18n, PWA)
- Security layers (login, role-based access, CORS handling)
- File persistence and export/import
- Live updates via Server-Sent Events (SSE)
- Deployment & packaging best practices

---

## 🧑‍🏫 Author

**Sven Ruppert**  
Developer Advocate for Secure Coding and Vaadin Flow  
🌐 [svenruppert.com](https://svenruppert.com)  
📫 [LinkedIn](https://www.linkedin.com/in/svenruppert/)

---

## 📄 License

Licensed under the [European Union Public Licence v1.2 (EUPL-1.2)](https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12). See [`LICENSE`](LICENSE) for the full text.
