# URL Shortener – Core Java + Vaadin Flow

A fully self-contained **URL Shortener** implemented in **pure Java 24**, featuring a lightweight REST service, a Vaadin Flow–based administration UI, and a simple Java client SDK.  
The project demonstrates how to build a secure, modular web application **without using any external frameworks** like Spring Boot or Jakarta EE.

---

## 🧩 Project Overview

### Modules

| Module | Description |
|---------|-------------|
| **urlshortener-core** | Core logic, DTOs, encoding utilities, and validation policies. |
| **urlshortener-server** | REST server for administration and redirection, implemented using `com.sun.net.httpserver.HttpServer`. |
| **urlshortener-client** | Minimal Java client that communicates with the admin API. |
| **urlshortener-ui** | Vaadin Flow 24.9.0 web application (WAR) providing a graphical interface for managing shortened URLs. |

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

All data is currently stored **in-memory**.  
Future extensions will include file-based persistence, access control, and live synchronization with the UI.

---

## 🧰 Technology Stack

- **Java 24**
- **Vaadin Flow 24.9.0**
- **Jetty 12 (WAR Deployment)** – for the UI
- **Core JDK HttpServer** – for the REST and redirect endpoints
- **No frameworks**, no Spring, no external dependencies

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

- Unit tests cover:
  - Base62 encoding / decoding
  - JSON utilities
  - Alias validation
- Integration tests:
  - Full end-to-end flow (`Shorten → Redirect → Delete`)

To run all tests:
```bash
mvn verify
```

---

## 🔒 Security Notes

- The Admin API should **not be publicly accessible**.  
  Restrict it to `localhost` or a private subnet.
- Redirect endpoints are public by design.
- Planned improvements:
  - Authenticated admin interface
  - Access control for administrative operations
  - Secure configuration and API tokens

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

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
