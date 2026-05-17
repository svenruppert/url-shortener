# Plan: security-for-flow 00.60.00 Integration

Ziel-Library: `com.svenruppert:security-{core,rest,vaadin}:00.60.00`
Branch: `feature/security-for-flow-00.60.00`

## Phasenstatus

- **Phase 1** ✅ erledigt — UI auf `security-vaadin:00.60.00` portiert, Funktion unverändert.
- **Phase 2** ✅ erledigt — REST-Server autoritativ geschützt, Owner-Modell, Audit, Bootstrap.
- **Phase 2.5** ✅ erledigt — User-Persistenz (EclipseStore), Legacy-Owner-Migration, Statistics-Owner-Filter.
- **Phase 3** ✅ erledigt — UI loggt real via REST ein, Clients mit Bearer-Token, Operations-Discovery, 401-Auto-Logout.
- **Phase 4** ✅ erledigt — User-Management (REST + Client + UI), Self-Service-Passwort-Änderung.
- **Phase 5** ✅ erledigt — Browserless Vaadin-Tests + PIT-Mutation-Testing für die UI-Views (LoginView, UserManagementView, ProfileView + Dialoge).

**Aktueller Test-Stand:** server 149 grün, client 111 grün, ui 13 grün (browserless), alle 5 Module bauen, BUILD SUCCESS.

## Wesentliche Erkenntnisse aus Phase 2

- `demo-rest` (`com.sun.net.httpserver.HttpServer`) ist der direkte Vorlagen-Stack. Wir behalten das per-context-Wiring statt einen monolithischen Router zu bauen.
- Permission-Wiring per Marker-Klasse `ShortenerSecuredOperations` mit `@RequiresPermission`-annotierten Marker-Methoden — bestehende Handler bleiben unverändert.
- Owner-Propagation per ThreadLocal (`CurrentSubject`), gesetzt im `SecurityHttpHandler` vor `delegate.handle(...)`, immer cleared in `finally`.
- Owner-Check toleriert leeres Subject (Test-Bypass-Modus). Der Filter selbst hält die coarse Permission-Schranke; OwnerCheck ist nur das per-Resource-Refinement.
- Test-Bypass via `System.setProperty("urlshortener.security.bypass", "true")` — **bleibt nur bis Client-Auth in Phase 3 existiert**.
- Statistics-Endpunkte filtern aktuell nicht nach Owner — Permission-Check reicht. Bei Multi-Tenancy nachschärfen.

## Ist-Stand (kurz)

- `urlshortener-ui` nutzt `security-for-flow:00.50.00` mit Single-Password-Login (`auth.properties`, Plaintext-Vergleich), einer Rolle `USER`, ohne REST-Anbindung.
- `urlshortener-server` ist komplett ungeschützt: 23 Admin-Endpoints ohne Auth (nur ein CORS-Preflight-Blocker).
- Öffentliche Short-Link-Auflösung (`RedirectHandler`, Port 8080) ist korrekt ohne Auth — bleibt so.
- 7 Vaadin-Views tragen `@VisibleFor(AppRole.USER)`. `LoginView` ist öffentlich.

## Soll-Architektur

- REST-Server = einzige Vertrauensgrenze. UI = untrusted Client.
- Rollen `ROLE_USER` und `ROLE_ADMIN`, 15 Permissions im Bereich `link:*`, `user:*`, `admin:*`.
- UI ruft REST mit Bearer Token, blendet Aktionen anhand `/api/operations` ein/aus.
- Permissions sind autoritativ am REST-Endpoint, Rollen-Check in der UI nur für Navigation.

## Phasen

Drei Phasen, jede in sich grün und tested vor Phase-Übergang. Phase 1 nicht zwingend committfähig releasebar (es bleibt 00.60-mit-altem-Login), aber kompiliert und läuft.

---

## Phase 1 — UI nach 00.60.00 portieren, Funktion unverändert ✅ erledigt

**Ziel:** Code kompiliert und läuft gegen `security-vaadin:00.60.00`, ohne dass sich für den User funktional etwas ändert. Lokales Password-Login (LoginConfig) bleibt vorerst. **Klein und reversibel.**

### Maven

- `urlshortener-ui/pom.xml`: `security-for-flow:00.50.00` → `security-vaadin:00.60.00` (Wechsel artifactId + Version).
- `security-core:00.60.00` kommt transitiv.

### Code-Änderungen (urlshortener-ui)

1. **`SessionAccessor` → `SubjectStores`**
   - `LoginView.checkCredentials()`: `SessionAccessor.setCurrentSubject(user)` → `SubjectStores.subjectStore().setCurrentSubject(user, AppUser.class)`.
   - `AppRoleAccessEvaluator.evaluateAccess()`: `SessionAccessor.<AppUser>currentSubject()` → `SubjectStores.subjectStore().currentSubject(AppUser.class)` (Rückgabetyp ist jetzt `Optional<T>` statt der vorherigen Wrapper-API — Aufrufstellen anpassen).
2. **`AccessEvaluator` weg → neue API-Form**
   - `AppRoleAccessEvaluator` implementiert nicht mehr `AccessEvaluator<VisibleFor>`. Stattdessen die in 00.60 vorhandene Variante (`AuthorizationEvaluator` o. ä., genaues Interface erst beim Bump verifizieren — wir prüfen das beim ersten Compile-Run).
3. **`@NavigationAnnotation` → `@SecurityAnnotation`**
   - `VisibleFor` trägt jetzt `@SecurityAnnotation(AppRoleAccessEvaluator.class)` statt `@NavigationAnnotation(...)`.
4. **`SecurityServiceResolver`-Aufrufe prüfen**
   - `AppAuthenticationService` und `LoginView` rufen `SecurityServiceResolver.authenticationService()`. Laut Sources noch vorhanden; wenn der Aufruf bricht, anpassen.
5. **`LoginListener.restrictionAnnotation()`**
   - `AppLoginListener.restrictionAnnotation()` zeigt auf `VisibleFor.class`. Methode in 00.60 verifizieren — vermutlich umbenannt oder weg, da Annotation-Auflösung jetzt zentral läuft.

### SPI-Files unverändert prüfen

Pfade unter `urlshortener-ui/src/main/resources/META-INF/services/`:
- `com.svenruppert.vaadin.security.authorization.api.AuthenticationService`
- `com.svenruppert.vaadin.security.authorization.api.AuthorizationService`
- `com.svenruppert.vaadin.security.authorization.api.AccessEvaluator` → wenn AccessEvaluator weg, **umbenennen** auf das neue Interface.
- `com.svenruppert.vaadin.security.authorization.LoginListener`

### Tests Phase 1

- Bestehende UI-Tests müssen weiter grün laufen (`mvn -pl urlshortener-ui test`).
- Smoke-Test: lokal starten, mit `admin/admin` einloggen, OverviewView öffnen.

### Abnahmekriterien Phase 1

- `mvn clean install` grün auf Root.
- App startet, Login funktioniert wie vorher (`auth.properties` + `admin/admin`).
- Alle 7 Views weiter mit `@VisibleFor(AppRole.USER)` erreichbar.
- Keine neuen Dependencies außer `security-vaadin:00.60.00` (+ transitive).

---

## Phase 2 — REST-Server bekommt Security ✅ erledigt

**Tatsächlich umgesetzt** (Abweichungen vom ursprünglichen Plan in Klammern):

- Domain `security/{permissions,user,token,auth,bootstrap,handler,adapter}`.
- `ShortUrlMapping.ownerUsername` (Domain-Erweiterung), `UrlMappingFilter.ownerUsername`, Owner-Check zentral via `OwnerCheck.isOwnerOrHasAll`.
- `CurrentSubject`-ThreadLocal, gesetzt im `SecurityHttpHandler`.
- `ShortenerSecuredOperations` als Annotation-Träger (statt Annotation an Handler-Klassen).
- `InMemoryUserStore` mit Seed-Admin + Seed-User, wenn `urlshortener.security.bootstrap.mode=DISABLED`.
- Bootstrap-Status & Admin-Endpoints; Bootstrap-Token in `data/.bootstrap-token` (mode `PERSISTENT_FILE`).
- SPI-Files: `SecurityAuditService` → `DefaultCompositeAuditService`, `SessionPolicy` → `TimeoutSessionPolicy`.
- Test-Suite: `SecurityIntegrationTest` (Login/Logout/Me/Operations/Bootstrap/Public-Redirect/401/403) + `OwnerCheckIntegrationTest` (Owner-Verhalten). Bestehende E2E-Tests umgestellt auf Bearer-Token via `SecurityTestSupport`.

**Bekannte Limits / TODOs:**

- **Statistics-Endpoints filtern nicht nach Owner** — Permission `link:stats:own` und `link:stats:all` sind aktuell faktisch identisch. Bei Multi-Tenancy nachziehen (eigene Phase 2.5).
- **Test-Bypass** `urlshortener.security.bypass=true` für legacy Client-Tests aktiv (via JUnit-5-Extension auto-registriert). **Wird in Phase 3 zurückgebaut**, sobald `URLShortenerClient` Auth-Header beherrscht.
- **Owner-Migration für Legacy-Mappings**: existierende Mappings ohne Owner werden vom Owner-Check als „nur Admin" behandelt. Falls produktive Daten existieren, muss eine Migration sie einem konkreten Admin zuweisen.

---

## Phase 2 — REST-Server bekommt Security (ursprünglicher Plan, zur Referenz)

**Ziel:** Authoritative Security im `urlshortener-server`. UI ändert sich noch **nicht**. Login geht weiterhin nur lokal in der UI, aber die REST-Endpoints sind dahinter abgeschirmt.
**Risiko:** Größte Phase, weil viele neue Klassen + Endpoint-Wrapping. **Tests vorher schreiben** wo möglich.

### Neue Maven-Dependency

- `urlshortener-server/pom.xml`: `security-rest:00.60.00`.

### Neue Pakete im `urlshortener-server`

```
com.svenruppert.urlshortener.api.security
├── permissions/
│   ├── ShortenerPermission.java          (Enum mit 15 Permissions)
│   ├── ShortenerRole.java                (Enum: ROLE_USER, ROLE_ADMIN)
│   └── ShortenerRolePermissionMapping.java (StaticRolePermissionMapping)
├── user/
│   ├── ShortenerUser.java                (Domain-Aggregat: id, username, displayName, passwordHash, roles, enabled)
│   ├── UserStore.java                    (SPI für Persistenz, in-memory + eclipse-store)
│   └── InMemoryUserStore.java
├── token/
│   ├── TokenService.java                 (issue, resolveSubject, revoke, revokeAllFor)
│   ├── InMemoryTokenStore.java           (Opaque-UUID, idle-/absolute-Timeout via SessionPolicy)
│   └── TokenSessionRegistry.java         (implements SubjectSessionRegistry)
├── auth/
│   ├── ShortenerAuthenticationService.java (AuthenticationService<Credentials, ShortenerUser>)
│   ├── ShortenerAuthorizationService.java  (AuthorizationService<ShortenerUser>)
│   └── ShortenerRestSubjectResolver.java   (RestSubjectResolver)
├── handler/
│   ├── LoginHandler.java                 (POST /api/login)
│   ├── LogoutHandler.java                (POST /api/logout)
│   ├── MeHandler.java                    (GET  /api/me)
│   ├── OperationsHandler.java            (GET  /api/operations)
│   ├── users/                            (CRUD + role assign)
│   ├── roles/                            (read-only)
│   ├── permissions/                      (read-only)
│   └── bootstrap/                        (status + admin)
└── filter/
    └── HttpExchangeRestAdapter.java      (com.sun.net.httpserver.HttpExchange → RestRequest/RestResponse)
```

### ShortenerServer-Wiring (rückkompatibel)

`ShortenerServer.java` wird erweitert:
- Statt `server.createContext(PATH_*, handler)` jetzt `server.createContext(PATH_*, wrapped(handler, ShortenerHandlers.class.getDeclaredMethod(...)))` über eine kleine Helper-Methode, die `RestAuthorizationFilter.authorizeAndHandle(...)` analog zum Demo durchschleift.
- Annotationen auf den Handler-**Methoden** im neuen `ShortenerHandlers`-Facade-Bean (oder direkt auf den bestehenden Handler-Klassen, wenn wir die Methode reflective adressieren) — pro Endpoint genau eine `@RequiresPermission("link:...")`.

**Konkrete Annotationen pro Endpoint** (Abbildung Endpoint → Permission):

| Endpoint | Permission |
|---|---|
| POST `/api/shorten` | `link:create` |
| POST `/api/shorten/bulk` | `link:create` |
| POST `/api/validate/bulk` | `link:create` |
| GET  `/api/list` | `link:read:own` |
| GET  `/api/list/count` | `link:read:own` |
| PUT  `/api/edit` | `link:update:own` (+ Owner-Check im Handler) |
| DELETE `/api/delete` | `link:delete:own` (+ Owner-Check im Handler) |
| POST `/api/toggle-active` | `link:update:own` (+ Owner-Check) |
| GET  `/api/store-info` | `admin:access` |
| POST `/api/import/validate` | `link:create` |
| POST `/api/import/apply` | `link:create` |
| GET  `/api/import/conflicts` | `link:read:own` |
| GET  `/api/import/invalid` | `link:read:own` |
| Alle `/api/preferences/*` | `link:read:own` (User-Präferenzen) |
| Alle `/api/statistics/*` (read) | `link:stats:own` |
| POST `/api/statistics/export` | `link:stats:own` |
| POST `/api/statistics/import` | `link:stats:all` (oder `admin:access`) |
| GET  `/{shortCode}` | **keine** (öffentlich) |

Owner-Checks für `:own`-Permissions werden **im Handler** nach dem Filter ausgeführt — die Annotation prüft nur, dass die Aktion grundsätzlich erlaubt ist.

### SPI-Registrierungen `urlshortener-server`

Neu unter `src/main/resources/META-INF/services/`:
- `…AuthenticationService` → `ShortenerAuthenticationService`
- `…AuthorizationService` → `ShortenerAuthorizationService`
- `…RolePermissionMapping` → `ShortenerRolePermissionMapping`
- `…RestSubjectResolver` → `ShortenerRestSubjectResolver`
- `…SecurityAuditService` → `DefaultCompositeAuditService` (oder Custom-Composite)
- `…SessionPolicy` → `TimeoutSessionPolicy`
- `…AdministratorAccountStore` → `UserStoreBackedAdminAccountStore`

### Bootstrap

- `/api/bootstrap/status` liefert `{"required": true|false}`, niemals Token.
- `/api/bootstrap/admin` validiert Bootstrap-Token (aus `BootstrapTokenStore`), legt Initial-Admin via `InitialAdminBootstrapService.createInitialAdmin(...)` an.
- Bootstrap-Token wird beim Server-Start einmalig generiert, in eine Datei `data/.bootstrap-token` geschrieben (mit File-Permissions 0600), in Logs nur Hinweis "Bootstrap token written to …", **nie der Token-Wert selbst**.

### Tests Phase 2

Neu unter `urlshortener-server/src/test/java/junit/…/security/`:

- `ShortenerRolePermissionMappingTest` — ROLE_USER → erwartete Permissions, ROLE_ADMIN → Superset.
- `PasswordHasherIntegrationTest` — Default-Hasher rundtrips.
- `InMemoryTokenStoreTest` — issue/resolve/revoke + Idle-Timeout via injizierter Clock.
- `ShortenerRestSubjectResolverTest` — kein Bearer → empty; valid Bearer → Subject mit Rollen+Permissions; expired → empty.
- `LoginAttemptPolicyIntegrationTest` — n × falsches Password → LockedOut, Audit `BruteForceLimitReached`.
- `LoginHandlerTest` — 200 mit Token bei Success, 401 bei Fail, generische Fehlermeldung.
- `LogoutHandlerTest` — CURRENT_SESSION invalidiert nur eines, ALL_SESSIONS alle.
- `LinkEndpointSecurityTest` (Integration über echte HttpServer-Instanz):
  - ohne Token → 401
  - mit Token ohne Permission → 403
  - mit Permission → 2xx
  - `link:update:own` an fremdem Link → 403 (Owner-Check)
  - `link:update:all` (Admin) an fremdem Link → 200
- `PublicRedirectTest` — `GET /{shortCode}` ohne Auth → 200/404, **nie** 401/403.
- `OperationsHandlerTest` — Rolle-abhängige Ergebnismenge.
- `BootstrapHandlerTest` — status leakt kein Token; valid token → 200; invalid token → 403 + `BootstrapTokenRejected`.
- `AuditPipelineTest` — alle Erfolgs- und Fehlerpfade schreiben das erwartete Event.

### Abnahmekriterien Phase 2

- Alle bestehenden Tests weiter grün.
- Neue Security-Tests grün.
- Manueller Smoke: `curl -X POST /api/login -d '{"username":"admin","password":"<bootstrap>"}'` liefert Token; `curl -H 'Authorization: Bearer …' /api/list` liefert Daten; ohne Header → 401.
- UI funktioniert **noch nicht** gegen den geschützten REST-Server (das ist Phase 3).
- Keine Klartext-Passwörter, Tokens oder Bootstrap-Tokens in Logs (manuelle Grep-Prüfung der Test-Logs).

---

## Phase 3 — UI auf REST-Auth umstellen ✅ erledigt

**Tatsächlich umgesetzt** (Abweichungen vom ursprünglichen Plan in Klammern):

- `LoginClient` (POST `/api/login`, POST `/api/logout`, `AuthSession`, `AuthenticationException`) im urlshortener-client.
- 4 Production-Clients mit `setAuthToken(String)`: `URLShortenerClient`, `AdminClient`, `StatisticsClient`, `ColumnVisibilityClient` — Bearer-Header in jedem `openConnection(...)`.
- `AppUser` direkt erweitert um `accessToken`, `permissions`, `operations` und `canInvoke(opId)` (statt eines separaten `UiSessionSubject`-Records — spart View-/Evaluator-Refactor).
- `AppRole`: `USER` → `ROLE_USER` + `ROLE_ADMIN` (Match mit Server).
- `AppAuthenticationService` ist echter REST-Client via `LoginClient`. ThreadLocal-Cache zwischen `checkCredentials`/`loadSubject`. 401/429 → `false`, andere I/O → `false` mit Warn-Log.
- `AppAuthorizationService.permissionsFor` implementiert.
- **Gelöscht:** `LoginConfig.java`, `LoginConfigInitializer.java`, `auth.properties`. Login ist Pflicht (kein Guest-Fallback in `AppServiceInitListener`).
- `LoginView` ruft `OperationsClient.fetch()` nach Login und packt das Ergebnis in den `AppUser`.
- `MainLayout`-Logout ruft `LoginClient.logout(token)` (server-seitige Token-Revocation) und löscht den lokalen Subject.
- `AuthTokenAccessor` zentraler Token-Lieferant für die 4 UI-Client-Factories.
- `AuthFailureRegistry` als globaler 401-Hook im client-Modul. `AppServiceInitListener.serviceInit(...)` registriert einen Handler, der lokalen Subject löscht und über `UI.access(...)` zur `LoginView` navigiert.
- `OperationsClient` mit `Operation(id, label, permission)`-Record. `OperationVisibility.applyTo(component, opId)` als UI-Helper.
- 7 Action-Buttons (CreateView `saveAllButton`, BulkCreateView `primaryButton`, BulkActionsBar `bulk{Delete,Activate,Deactivate,SetExpiry,ClearExpiry}Btn`) auf `OperationVisibility.applyTo(...)` umgestellt.
- Bypass aus Phase 2 vollständig entfernt: `ClientSecurityBypassExtension`, SPI-Datei, `junit-platform.properties`, `SecurityHttpHandler.SYSPROP_BYPASS`. Alle 10 legacy Client-Tests umgestellt auf `ClientAuthSupport.enableTestBootstrap()` + echten Login.
- Neue Tests:
  - `LoginClientTest` (5) — admin-Login, falsches Passwort, unbekannter User, Logout revokes Token.
  - `OperationsClientTest` (5) — admin/user-Listen, Filterung, fehlender Token.
  - `AuthFailureRegistryTest` (5) — Listener-Lifecycle, faulty-Listener-Isolation.

**Bekannte Limits / TODOs:**

- **Nur 7 Action-Buttons** über `OperationVisibility` geblendet. Bei aktuellen Permissions (ROLE_USER hat alle link-*) ist nichts wirklich versteckt — die Mechanik greift erst bei einer feinkörnigeren Rolle (z.B. Read-only).
- **Statistics-Owner-Filter** weiterhin offen (Phase 2.5).
- **UI-Integrationstests** (LoginView, AppRoleAccessEvaluator) nicht ergänzt — Vaadin-UnitTester ist im Projekt nicht etabliert. Logik wird durch die REST-Tests indirekt abgedeckt.
- **AuthFailureRegistry-Hook nicht an jeder einzelnen `getResponseCode()`-Stelle** der Clients. Die Hauptpfade (zentrale `requireStatus*`, `requestJson`, `putJsonReturnBooleanOn404`, `delete`, und alle StatisticsClient-Stellen via `observe(...)`) sind abgedeckt. Vereinzelte Export/Download-Methoden in `URLShortenerClient` rufen `getResponseCode()` direkt — bei Bedarf nachziehen.

---

## Phase 3 — UI auf REST-Auth umstellen (ursprünglicher Plan, zur Referenz)

**Ziel:** UI kommuniziert mit dem nun geschützten REST-Server. Lokales Password-Login fällt weg. Der Test-Bypass aus Phase 2 wird abgebaut.

### Vorgehensreihenfolge (Schritt-für-Schritt)

1. **`URLShortenerClient` (+ `AdminClient`, `StatisticsClient`, `ColumnVisibilityClient`) bekommt einen Bearer-Token-Slot.** Ein Setter oder ein gemeinsamer Konstruktor-Parameter; alle internen `HttpURLConnection.setRequestProperty(...)` ergänzen `Authorization: Bearer <token>` wenn gesetzt.
2. **Login-Helper im Client-Modul**: `LoginClient.login(baseUrl, user, password) → AuthSession(token, displayName, roles, permissions)`. Spiegelt `POST /api/login`.
3. **Legacy-Client-Tests umstellen** auf `LoginClient` + Token. Bypass-Property entfernen (Datei `META-INF/services/org.junit.jupiter.api.extension.Extension` löschen, `ClientSecurityBypassExtension` löschen, `SYSPROP_BYPASS` im `SecurityHttpHandler` als deprecated markieren oder direkt entfernen).
4. **UI-Domäne**: `UiSessionSubject(SecuritySubject subject, String accessToken)`.
5. **`AppAuthenticationService`** wird REST-Client; nutzt `LoginClient`. Generic-Typ `AuthenticationService<AppCredentials, UiSessionSubject>`.
6. **`AppAuthorizationService<UiSessionSubject>`** liefert `rolesFor`/`permissionsFor` aus dem Subject.
7. **`LoginConfig`, `LoginConfigInitializer`, `auth.properties`** **löschen**.
8. **`LoginView.checkCredentials()`**: AuthenticationService aufrufen, bei Erfolg `SubjectStores.subjectStore().setCurrentSubject(uiSessionSubject, UiSessionSubject.class)`.
9. **UI-REST-Clients** (`StatisticsClient`, `AdminClientFactory`, …) ergänzen den Bearer-Header aus dem aktuellen `UiSessionSubject`. 401 → Subject leeren, navigate to LoginView. 403 → generische Notification.
10. **Operation Discovery**: `OperationsClient.fetch()` ruft `/api/operations`, Ergebnis ins UI-State (z.B. in `UiSessionSubject` oder einem separaten Cache pro UI). Sichtbarkeit von Buttons/Menüs anhand dieser Liste, **nicht** lokaler `@VisibleFor`-Hardcoding.
11. **`@VisibleFor`-Annotationen** bleiben für reine Navigations-Filterung erhalten, aber der Evaluator schaut auf das neue `UiSessionSubject` (Roles aus dem Server). Permissions können optional via separater Annotation `@VisibleForPermission(String)` oder via Operations-Lookup zur View-Render-Zeit unterstützt werden — entscheiden wir konkret in Schritt 10.

### Konkrete REST-Clients, die anzupassen sind

- `urlshortener-client/src/main/java/…/URLShortenerClient.java`
- `…/AdminClient.java` (falls existiert)
- `urlshortener-client/src/main/java/…/StatisticsClient.java`
- UI-seitige Factory-Klassen unter `urlshortener-ui/src/main/java/…/vaadin/tools/` (`AdminClientFactory`, `StatisticsClientFactory`, `ColumnVisibilityClientFactory`, `UrlShortenerClientFactory`) — müssen den aktuellen Token aus `SubjectStores.subjectStore().currentSubject(UiSessionSubject.class)` ziehen.

### Tests Phase 3

- `LoginClientTest` — Login-Roundtrip gegen frischen Server (200 → Token, 401 → Exception/leeres Result).
- `URLShortenerClientAuthTest` — mit Token: 2xx; ohne Token: 401; mit falschem Token: 401.
- `AppAuthenticationServiceTest` — REST 200 → Subject, REST 401 → false, REST timeout → false + Log.
- `AppAuthorizationServiceTest` — Roles/Permissions kommen aus Subject.
- `LoginViewIntegrationTest` (UnitTester) — erfolgreiches Login speichert `UiSessionSubject`, fehlgeschlagenes nicht.
- `OperationsClientTest` — gefilterte Liste je nach Rolle.

### Code-Änderungen `urlshortener-ui`

- **Neu:** `UiSessionSubject(SecuritySubject subject, String accessToken)` (Record).
- **`AppAuthenticationService`** wird REST-Client:
  - `checkCredentials(AppCredentials)` ruft `POST /api/login`, gibt `true` zurück bei 200.
  - `loadSubject(AppCredentials)` re-uses Response von `checkCredentials` (über ThreadLocal oder einmaligen Re-Call), liefert `UiSessionSubject`.
  - Generic-Typ ändert sich von `AuthenticationService<AppCredentials, AppUser>` zu `AuthenticationService<AppCredentials, UiSessionSubject>`.
- **`AppAuthorizationService<UiSessionSubject>`**:
  - `rolesFor(s) = s.subject().roles()`
  - `permissionsFor(s) = s.subject().permissions()`
- **`AppUser` raus**, `AppRole`-Enum bleibt (für Navigation in `@VisibleFor`) oder wird durch `@RequiresPermission` ersetzt — wir entscheiden konkret bei Phase-3-Start.
- **`LoginConfig`, `LoginConfigInitializer`, `auth.properties`** → **löschen**.
- **`LoginView`**: ruft jetzt `AppAuthenticationService.checkCredentials` (das den REST-Call macht), bei Erfolg `SubjectStores.subjectStore().setCurrentSubject(uiSessionSubject, UiSessionSubject.class)`.
- **REST-Clients** (`StatisticsClient`, `AdminClientFactory`, …): pro Request `Authorization: Bearer <token>` aus dem aktuellen `UiSessionSubject` setzen. 401 → Subject leeren, zur LoginView navigieren. 403 → generische Notification.
- **Operation Discovery**: neue Klasse `OperationsClient`, Aufruf nach Login + bei View-Init. Aktionen in `MainLayout` und Views basieren auf dem geladenen Operations-Set, nicht mehr auf `@VisibleFor(AppRole.USER)`-Hardcoding allein.

### View-Annotationen

- `@VisibleFor(AppRole.USER)` bleibt vorerst auf den 7 Views — Navigation-Layer.
- Neue Admin-Views (User-Management, Admin-Dashboard) werden in einem **eigenen, separaten Folgeschritt** ergänzt. Sind **nicht** Teil dieser drei Phasen.

### Tests Phase 3

- `AppAuthenticationServiceTest` — REST 200 → Subject; REST 401 → false; REST timeout → false + Log.
- `AppAuthorizationServiceTest` — Roles/Permissions kommen aus Subject.
- `LoginViewIntegrationTest` (Vaadin TestBench oder UnitTester) — Login speichert Subject, Failed Login speichert nichts.
- `RestClientBearerTest` — Header wird gesetzt, 401 räumt Session.

### Abnahmekriterien Phase 3

- End-to-End: UI starten, mit Bootstrap-erstelltem Admin einloggen, Links anlegen/bearbeiten/löschen, Statistics ansehen, Logout.
- Direkter REST-Aufruf ohne Token weiterhin 401.
- 7 Views funktional unverändert.
- Keine `auth.properties` mehr im Repo.

---

## Branch- und Commit-Strategie

- Branch: `feature/security-for-flow-00.60.00` (existiert bereits).
- Pro Phase mindestens ein PR (oder einen großen PR mit drei nachvollziehbaren Commits).
- Jede Phase einzeln rebasebar — keine Cross-Phase-Refactors.

---

## Phase 2.5 — Persistenz, Owner-Filter, Legacy-Migration ✅ erledigt

**Tatsächlich umgesetzt:**

- **User-Persistenz**: `DataRoot.users` (serialVersionUID 4), `EclipseUserStore` implementiert `UserStore` und persistiert jede Mutation. `EclipseStore.getUserStore()` als Provider, `ShortenerServer.init(persistent=true)` reicht den Store an `ShortenerSecurityModule` durch. Bootstrap-Mode unverändert.
- **Tokens bleiben in-memory** (bewusste Entscheidung) — `InMemoryTokenStore` ist unangetastet. Server-Neustart erzwingt Re-Login, geringere Angriffsfläche.
- **Legacy-Owner-Migration**: `LegacyOwnerMigration` läuft beim Boot gated durch `-Durlshortener.security.legacy-owner=<user>`. Iteriert alle Mappings mit `ownerUsername == null`, weist sie dem Ziel-User zu (muss existieren), idempotent. `UrlMappingUpdater.assignOwner(...)` neu in beiden Store-Impls.
- **Statistics-Owner-Filter**: `StatisticsOwnerGuard` per shortCode. Eingebaut in `StatisticsCountHandler`, `StatisticsHourlyHandler`, `StatisticsDailyHandler`, `StatisticsTimelineHandler` (Count/Hourly/Daily/Timeline). Unknown shortCode passiert den Guard, damit Existenz nicht via 403/404 leakt. Admin (`link:stats:all`) umgeht den Check.
- **Permission-Tightening**: `statisticsDebug` und `statisticsExport` von `link:stats:own` → `link:stats:all` (beide geben heute globale Daten zurück; Owner-Filtering für Export wäre eine weitere Phase).
- **Tests neu**:
  - `EclipseUserStorePersistenceTest` (3) — Restart-Roundtrip, setRole/setEnabled/delete persist, hasAnyAdministrator persist.
  - `LegacyOwnerMigrationTest` (6) — null-only, idempotent, unbekannter target, system-property gate, blank target.
  - `StatisticsOwnerFilterIntegrationTest` (5) — owner OK, stranger 403, admin bypass, unknown shortCode passt durch, Timeline auch.

**Bewusst noch nicht umgesetzt:**

- **Token-Persistenz** — als zu großes Risiko bewertet (Angriffsfläche + TTL-Tracking in Storage). Default ist „Neustart = Re-Login".
- **Owner-gefilterter Export** — `statisticsExport` ist jetzt admin-only. Wer einen User-Mode-Export braucht, muss den Writer um Owner-Filterung erweitern.
- **Admin-Endpoint für Owner-Migration on-demand** — nicht nötig, System-Property reicht für den One-Shot-Use-Case.

---

---

## Phase 4 — User-Management ✅ erledigt

**Tatsächlich umgesetzt:**

- **Domain-Erweiterung** `UserStore`: `setDisplayName`, `changePassword(old, new)` mit Verifikation, `resetPassword(new)` für Admin-Force.
- **REST-Endpoints** unter `/api/users` (consolidated `UserManagementHandler`):
  - `GET /api/users` → Liste (`user:read`)
  - `POST /api/users` → Create (`user:create` — Handler-Check zusätzlich zum coarse `user:read`-Filter)
  - `PUT /api/users/{username}` → Partial-Update (`user:update`, plus `user:role:assign` bei Rollenwechsel)
  - `DELETE /api/users/{username}` → Delete (`user:delete`) — Self-Protection + Last-Admin-Guard
  - `POST /api/users/{username}/password` → Admin-Force-Reset, alle Sessions des Ziel-Users werden revoked
  - `POST /api/me/password` → Self-Service mit oldPw + newPw, nur authentifiziert (kein Permission-Check), revoked alle Sessions des aufrufenden Users
- **`LastAdminGuard.isOnlyEnabledAdmin(...)`** als single-source der „letzter Admin"-Logik; konsultiert von Delete/Update-Role/Update-Disable.
- **DTOs in `core/users/`**: `UserSummary`, `CreateUserRequest`, `UpdateUserRequest`, `AdminResetPasswordRequest`, `SelfChangePasswordRequest`. Password-Hash verlässt nie den Server.
- **Client**: `UserManagementClient` deckt alle 6 Endpoints ab, 401-Hook über `AuthFailureRegistry`. Self-Service liefert `false` bei 401 (statt Exception) damit die UI das auf eine Fehlermeldung mappen kann.
- **UI** (`urlshortener-ui`):
  - `UserManagementView` (`/users`, `@VisibleFor(ROLE_ADMIN)`) mit Grid + Toolbar (Create) + Row-Aktionen (Edit, Reset PW, Delete).
  - `CreateUserDialog`, `EditUserDialog` (partial-update mit `null`-Fields wenn unverändert), `AdminResetPasswordDialog` mit Bestätigung.
  - `ProfileView` (`/profile`, alle User) zeigt eigene Daten + Button „Change password" → `ChangePasswordDialog`. Erfolg ⇒ lokales Subject löschen, navigate to LoginView.
  - `MainLayout`: neue Nav-Einträge "Profile" (für alle) und "Users" (admin-only, geprüft am aktuellen `AppUser`).
- **Tests neu**:
  - `UserManagementIntegrationTest` (11) — list/create/duplicate/update/delete + non-admin 403 + self-delete + last-admin demote/disable + admin reset invalidiert Tokens + unknown role/user.
  - `SelfServicePasswordChangeIntegrationTest` (4) — happy path mit Token-Revocation, falsches Old-PW, zu kurzes New-PW, unauthentifiziert.
  - `UserManagementClientTest` (6) — list, create + duplicate, update, delete-404, reset+Token-Revocation, changeOwnPassword.

**Self-Protection-Regeln (Server):**
- Admin kann sich nicht selbst löschen (`self_delete_forbidden`, 409).
- Admin kann sich nicht selbst deaktivieren (`self_disable_forbidden`, 409).
- Letzter enabled-Admin kann nicht gelöscht, deaktiviert oder degradiert werden (`last_admin_protected`, 409).
- `link:role:assign` wird im Handler geprüft, weil der coarse Filter nur eine Permission kann.

**Bewusst nicht umgesetzt:**

- **Email-Feld** in `ShortenerUser` — bleibt aus Scope, kein Schema-Wandel nötig.
- **UI-Integrationstests** weiterhin Skip (UnitTester nicht etabliert; siehe Phase 3 TODO).
- **Admin-„unlock-locked-out-user"-Aktion** — nicht modelliert, Lockout läuft per Timeout aus.

---

---

## Phase 5 — Browserless UI-Tests + Mutation-Hunt ✅ erledigt

**Tatsächlich umgesetzt:**

- `com.vaadin:browserless-test-junit6:1.0.0` als Test-Dep im UI-Modul + `urlshortener-server` als Test-Dep (für realen REST-Round-Trip).
- PIT-Plugin auf 1.2.3, `<threads>2</threads>`, XML+HTML-Output.
- [`AbstractBrowserlessTest`](urlshortener-ui/src/test/java/junit/com/svenruppert/urlshortener/ui/browserless/AbstractBrowserlessTest.java): startet realen `ShortenerServer` (in-memory, Bootstrap-DISABLED) pro Test-Klasse, resettet `SubjectStores.AppUser` per `@BeforeEach`. Disziplin nach Skill: kein Mock, echte Stores, Asserts auf side effects.
- Browserless-Tests:
  - `LoginViewBrowserlessTest` (5) — admin/user/wrong-pw/unknown/button-click.
  - `UserManagementViewBrowserlessTest` (8) — grid, create-dialog round-trip, refresh, navigation guard für ROLE_USER, EditDialog persistiert, AdminResetDialog persistiert + revoked tokens, mismatch wird client-side abgefangen, row-action delete via Grid-Cell-Component-Walk.
  - `ProfileViewBrowserlessTest` (4) — Anzeige, happy-path PW-change mit subject clear + server-side neue creds, wrong-old keeps subject, mismatch-confirmation no-op.
- **Real bug found**: `UserManagementClient.changeOwnPassword` triggerte `AuthFailureRegistry` auf 401, was die UI bei „altes PW falsch" ausloggte. Fix: für diesen Endpoint kein Auth-Hook — domain-failure, kein session-failure.

**Final mutation coverage (max aus joint + isolated PIT-Lauf), Skill-Target 60–75 % für Vaadin views:**

| Klasse | Mutation Coverage | Test Strength |
|---|---|---|
| LoginView | 82 % | 100 % |
| ProfileView | 91 % | 100 % |
| ChangePasswordDialog | 85 % | 100 % |
| CreateUserDialog | 83 % | 100 % |
| AdminResetPasswordDialog | 77 % (was 0 %) | 100 % |
| EditUserDialog | 71 % (was 0 %) | 100 % |
| UserManagementView | 53 % (was 43 %) | 100 % |

6 von 7 erreichen das Skill-Target. Test-Strength 100 % über alle Klassen — keine "lying tests" mehr; gecoverte Codepfade werden zuverlässig auf Mutanten geprüft.

**Joint-Run-Artefakt** (Skill §2.5): PIT zeigt im reaktorweiten Joint-Run niedrigere Zahlen für einige Klassen, weil Vaadin's statische State zwischen Test-Klassen leaked. Die isolierten Per-Klasse-Läufe sind belastbar; die o. g. Zahlen sind canonical.

**Bewusst nicht erweitert:**

- UserManagementView bleibt bei 53 % — die surviving mutants sind in Notification-Strings + Row-Action-UI-Plumbing (Tooltip-Strings etc.), wo Mutationen nicht load-bearing sind. Skill §3.3-„equivalent mutant"-Bucket.
- Kein PIT-Lauf über server/client — die existierende Unit-Suite (149+111 Tests) ist die richtige Stelle, das nachzuziehen, wenn der Bedarf entsteht.

---

## Risiken & offene Punkte (Stand: nach Phase 5)

- **AuthFailureRegistry-Hook** war an wenigen Export-/Download-Methoden in `URLShortenerClient` lückenhaft → in der vorherigen Iteration komplett geschlossen.
- **Legacy-Mappings ohne Migration**: Wenn jemand `urlshortener.security.legacy-owner` nicht setzt, bleiben null-Owner-Mappings admin-only sichtbar. Akzeptabel, weil der Migrationsweg dokumentiert ist.
- **UI-Integrationstests** fehlen weiterhin (Phase 3 TODO).
- **Dependabot-Warnings (2 high)** auf default branch — separat angehen.

---

## Was als Nächstes konkret offen ist

1. **Smoke-Test-Strecke** dokumentieren (`curl`-Sequenz: Bootstrap-Status, Admin anlegen, Login, geschützter Endpoint).
2. **Operations-Visibility breiter ausrollen** — aktuell 7 Buttons. Sinnvoll, wenn eine Read-only-Rolle dazukommt.
3. **UI-Integrationstests** mit Vaadin TestBench / Karibu-Testing (Phase 3.5).
4. **Bootstrap-Token-File-Berechtigungen** auf POSIX `0600` setzen — geht nur via NIO + `PosixFilePermissions`. Verifizieren ob das security-core schon erledigt.
5. **Owner-gefilterter Statistics-Export** falls User selbst Exports brauchen (heute admin-only).
