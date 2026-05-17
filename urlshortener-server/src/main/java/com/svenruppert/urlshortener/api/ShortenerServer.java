package com.svenruppert.urlshortener.api;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.filter.BlockBrowserPreflightFilter;
import com.svenruppert.urlshortener.api.handler.RedirectHandler;
import com.svenruppert.urlshortener.api.handler.admin.StoreInfoHandler;
import com.svenruppert.urlshortener.api.handler.admin.columns.ColumnVisibilityBulkHandler;
import com.svenruppert.urlshortener.api.handler.admin.columns.ColumnVisibilityHandler;
import com.svenruppert.urlshortener.api.handler.admin.columns.ColumnVisibilitySingleHandler;
import com.svenruppert.urlshortener.api.handler.statistics.*;
import com.svenruppert.urlshortener.api.handler.statistics.exports.StatisticsExportHandler;
import com.svenruppert.urlshortener.api.handler.statistics.imports.StatisticsImportHandler;
import com.svenruppert.urlshortener.api.handler.urlmapping.*;
import com.svenruppert.urlshortener.api.handler.urlmapping.imports.ImportApplyHandler;
import com.svenruppert.urlshortener.api.handler.urlmapping.imports.ImportConflictsListHandler;
import com.svenruppert.urlshortener.api.handler.urlmapping.imports.ImportInvalidListHandler;
import com.svenruppert.urlshortener.api.handler.urlmapping.imports.ImportValidateHandler;
import com.svenruppert.urlshortener.api.security.LegacyOwnerMigration;
import com.svenruppert.urlshortener.api.security.ShortenerSecurityModule;
import com.svenruppert.urlshortener.api.security.StatisticsOwnerGuard;
import com.svenruppert.urlshortener.api.security.handler.SelfChangePasswordHandler;
import com.svenruppert.urlshortener.api.security.handler.UserManagementHandler;
import com.svenruppert.urlshortener.api.security.user.UserStore;
import com.svenruppert.urlshortener.api.store.imports.ImportStagingStore;
import com.svenruppert.urlshortener.api.store.imports.InMemoryImportStagingStore;
import com.svenruppert.urlshortener.api.store.preferences.PreferencesStore;
import com.svenruppert.urlshortener.api.store.provider.eclipsestore.EclipseStore;
import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryPreferencesStore;
import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryStatisticsStore;
import com.svenruppert.urlshortener.api.store.provider.inmemory.InMemoryUrlMappingStore;
import com.svenruppert.urlshortener.api.store.statistics.StatisticsStore;
import com.svenruppert.urlshortener.api.store.urlmapping.UrlMappingStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.Arrays;
import java.util.concurrent.Executors;

import static com.svenruppert.urlshortener.core.DefaultValues.*;

public class ShortenerServer
    implements HasLogger {

  private HttpServer serverRedirect;
  private HttpServer serverAdmin;

  public static void main(String[] args)
      throws IOException {
    // Defaults (Fallbacks)
    String host = DEFAULT_SERVER_HOST;
    int port = DEFAULT_SERVER_PORT;
    HasLogger.staticLogger().info("Main called with arguments: {}", Arrays.toString(args));

    // Read parameters from CLI
    if (args.length > 0 && !args[0].isBlank()) {
      host = args[0].trim();
      HasLogger.staticLogger().info("Host is {}", host);
    }
    if (args.length > 1) {
      try {
        port = Integer.parseInt(args[1].trim());
      } catch (NumberFormatException e) {
        HasLogger.staticLogger().warn("Invalid port argument: {} - using default {}", args[1], port);
      }
    }


    boolean persistent = true;
    new ShortenerServer().init(host, port, persistent);
  }

  public void init()
      throws IOException {
    logger().info("Starting URL Shortener server with default parameters...");
    init(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
  }

  public void init(String hostRedirect, int portRedirect)
      throws IOException {
    init(hostRedirect, portRedirect, false);
  }

  public void init(String hostRedirect, int portRedirect, boolean persistent)
      throws IOException {
    logger().info("Starting server with urlMappingStore - persistent={}", persistent);
    final long startedAt = System.currentTimeMillis();
    var shortCodeGenerator = new ShortCodeGenerator(1);

    UrlMappingStore urlMappingStore;
    PreferencesStore preferencesStore;
    StatisticsStore statisticsStore;
    UserStore userStore = null;
    ImportStagingStore importStagingStore = new InMemoryImportStagingStore();

    if (persistent) {
      var eclipseStore = new EclipseStore(
          STORAGE_DATA_PATH,
          shortCodeGenerator,
          Clock.systemUTC()
      );
      urlMappingStore = eclipseStore.getUrlMappingStore();
      preferencesStore = eclipseStore.getPreferencesStore();
      statisticsStore = eclipseStore.getStatisticsStore();
      userStore = eclipseStore.getUserStore();

      // Start background threads for statistics processing
      eclipseStore.start();
      logger().info("EclipseStore started with statistics processing");

      logger().info("Registering shutdown hook for EclipseStore...");
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        shutdown();
        logger().info("Closing EclipseStore...");
        try {
          eclipseStore.close();
        } catch (Exception ignored) {
        }
        logger().info("Server shutdown complete");
      }));
    } else {
      urlMappingStore = new InMemoryUrlMappingStore(shortCodeGenerator);
      preferencesStore = new InMemoryPreferencesStore();
      statisticsStore = new InMemoryStatisticsStore(Clock.systemUTC());
      // statisticsStore remains null for in-memory mode
    }

    logger().info("Starting URL Shortener server (redirect) with parameters: host={}, port={}", hostRedirect, portRedirect);
    this.serverRedirect = HttpServer.create(new InetSocketAddress(hostRedirect, portRedirect), 0);
    serverRedirect.createContext(PATH_REDIRECT, new RedirectHandler(urlMappingStore, statisticsStore));

    logger().info("Starting URL Shortener server (admin) with parameters: host={}, port={}", ADMIN_SERVER_HOST, ADMIN_SERVER_PORT);
    this.serverAdmin = HttpServer.create(new InetSocketAddress(ADMIN_SERVER_HOST, ADMIN_SERVER_PORT), 0);

    Path storageRoot = Paths.get(STORAGE_DATA_PATH);
    ShortenerSecurityModule security = new ShortenerSecurityModule(storageRoot, userStore);

    // Optional one-shot legacy-owner migration, gated by system property.
    // No-op when the property is absent or the target user does not exist.
    new LegacyOwnerMigration(urlMappingStore, security.userStore()).runIfConfigured();

    register(PATH_ADMIN_VALIDATE_BULK, security.wrap(new BulkValidateHandler(urlMappingStore), "bulkValidateLinks"));
    register(PATH_ADMIN_SHORTEN_BULK, security.wrap(new BulkShortenHandler(urlMappingStore), "bulkCreateLinks"));
    register(PATH_ADMIN_SHORTEN, security.wrap(new ShortenHandler(urlMappingStore), "createLink"));
    register(PATH_ADMIN_LIST, security.wrap(new ListHandler(urlMappingStore), "listLinks"));
    register(PATH_ADMIN_LIST_COUNT, security.wrap(new ListCountHandler(urlMappingStore), "listLinksCount"));
    register(PATH_ADMIN_EDIT, security.wrap(new EditMappingHandler(urlMappingStore), "editLink"));
    register(PATH_ADMIN_DELETE, security.wrap(new DeleteMappingHandler(urlMappingStore), "deleteLink"));
    register(PATH_ADMIN_TOGGLE_ACTIVE, security.wrap(new ToggleActiveHandler(urlMappingStore), "toggleActive"));
    register(PATH_ADMIN_STORE_INFO, security.wrap(new StoreInfoHandler(urlMappingStore, startedAt), "storeInfo"));

    register(PATH_ADMIN_IMPORT_VALIDATE, security.wrap(new ImportValidateHandler(urlMappingStore, importStagingStore), "importValidate"));
    register(PATH_ADMIN_IMPORT_APPLY, security.wrap(new ImportApplyHandler(urlMappingStore, importStagingStore), "importApply"));
    register(PATH_ADMIN_IMPORT_CONFLICTS, security.wrap(new ImportConflictsListHandler(importStagingStore), "importConflicts"));
    register(PATH_ADMIN_IMPORT_INVALID, security.wrap(new ImportInvalidListHandler(importStagingStore), "importInvalid"));

    register(PATH_ADMIN_PREFERENCES_COLUMNS, security.wrap(new ColumnVisibilityHandler(preferencesStore), "preferencesColumns"));
    register(PATH_ADMIN_PREFERENCES_COLUMNS_EDIT, security.wrap(new ColumnVisibilityBulkHandler(preferencesStore), "preferencesColumnsBulk"));
    register(PATH_ADMIN_PREFERENCES_COLUMNS_SINGLE, security.wrap(new ColumnVisibilitySingleHandler(preferencesStore), "preferencesColumnsSingle"));

    StatisticsOwnerGuard statsGuard = new StatisticsOwnerGuard(urlMappingStore);
    register(PATH_ADMIN_STATISTICS_COUNT, security.wrap(new StatisticsCountHandler(statisticsStore, statsGuard), "statisticsCount"));
    register(PATH_ADMIN_STATISTICS_HOURLY, security.wrap(new StatisticsHourlyHandler(statisticsStore, statsGuard), "statisticsHourly"));
    register(PATH_ADMIN_STATISTICS_DAILY, security.wrap(new StatisticsDailyHandler(statisticsStore, statsGuard), "statisticsDaily"));
    register(PATH_ADMIN_STATISTICS_TIMELINE, security.wrap(new StatisticsTimelineHandler(statisticsStore, statsGuard), "statisticsTimeline"));
    register(PATH_ADMIN_STATISTICS_CONFIG, security.wrap(new StatisticsConfigHandler(statisticsStore), "statisticsConfig"));
    register(PATH_ADMIN_STATISTICS_DEBUG, security.wrap(new StatisticsDebugHandler(statisticsStore), "statisticsDebug"));
    register(PATH_ADMIN_STATISTICS_EXPORT, security.wrap(new StatisticsExportHandler(statisticsStore, urlMappingStore), "statisticsExport"));
    register(PATH_ADMIN_STATISTICS_IMPORT, security.wrap(new StatisticsImportHandler(statisticsStore), "statisticsImport"));
    logger().info("Statistics API handlers registered");

    // User-management endpoints. Coarse filter checks user:read; mutating
    // branches in the handler verify finer permissions (user:create,
    // user:update, user:delete, user:role:assign).
    register(PATH_API_USERS, security.wrap(
        new UserManagementHandler(security.userStore(), security.tokenStore()),
        "listUsers"));
    serverAdmin.createContext(PATH_API_ME_PASSWORD,
        new SelfChangePasswordHandler(security.userStore(), security.tokenStore()))
        .getFilters().add(new BlockBrowserPreflightFilter());

    // Unauthenticated bootstrap endpoints and public auth endpoints.
    serverAdmin.createContext(ShortenerSecurityModule.PATH_API_BOOTSTRAP_STATUS, security.bootstrapStatusHandler())
        .getFilters().add(new BlockBrowserPreflightFilter());
    serverAdmin.createContext(ShortenerSecurityModule.PATH_API_BOOTSTRAP_ADMIN, security.bootstrapAdminHandler())
        .getFilters().add(new BlockBrowserPreflightFilter());
    serverAdmin.createContext(ShortenerSecurityModule.PATH_API_LOGIN, security.loginHandler())
        .getFilters().add(new BlockBrowserPreflightFilter());
    serverAdmin.createContext(ShortenerSecurityModule.PATH_API_LOGOUT, security.logoutHandler())
        .getFilters().add(new BlockBrowserPreflightFilter());
    serverAdmin.createContext(ShortenerSecurityModule.PATH_API_ME, security.meHandler())
        .getFilters().add(new BlockBrowserPreflightFilter());
    serverAdmin.createContext(ShortenerSecurityModule.PATH_API_OPERATIONS, security.operationsHandler())
        .getFilters().add(new BlockBrowserPreflightFilter());
    logger().info("Security endpoints registered");


    var execRedirect = Executors.newVirtualThreadPerTaskExecutor();
    serverRedirect.setExecutor(execRedirect);
    serverRedirect.start();

    var execAdmin = Executors.newVirtualThreadPerTaskExecutor();
    serverAdmin.setExecutor(execAdmin);
    serverAdmin.start();

    logger().info("URL Shortener server (redirect) running at {}:{}",
                  serverRedirect.getAddress().getHostName(),
                  serverRedirect.getAddress().getPort());
    logger().info("URL Shortener server (admin) running at {}:{}",
                  serverAdmin.getAddress().getHostName(),
                  serverAdmin.getAddress().getPort());


    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  private void register(String path, HttpHandler handler) {
    serverAdmin.createContext(path, handler).getFilters().add(new BlockBrowserPreflightFilter());
  }

  public void shutdown() {
    if (serverRedirect != null) {
      serverRedirect.stop(0);
      logger().info("URL Shortener server stopped (redirect)");
    }
    if (serverAdmin != null) {
      serverAdmin.stop(0);
      logger().info("URL Shortener server stopped (admin)");
    }
  }

  public int getPortRedirect() {
    return serverRedirect.getAddress().getPort();
  }

  public int getPortAdmin() {
    return serverAdmin.getAddress().getPort();
  }


}