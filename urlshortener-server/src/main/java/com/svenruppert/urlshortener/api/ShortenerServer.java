package com.svenruppert.urlshortener.api;

import com.sun.net.httpserver.HttpServer;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.handler.*;
import com.svenruppert.urlshortener.api.store.InMemoryUrlMappingStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;

import static com.svenruppert.urlshortener.core.DefaultValues.*;

public class ShortenerServer
    implements HasLogger {

  private HttpServer serverRedirect;
  private HttpServer serverAdmin;

  //TODO CLI Argumente - IP Address
  public static void main(String[] args)
      throws IOException {
    // Defaults (Fallbacks)
    String host = DEFAULT_SERVER_HOST;
    int port = DEFAULT_SERVER_PORT;
    HasLogger.staticLogger().info("main is called with {}", Arrays.toString(args));

    // Parameter aus CLI lesen
    if (args.length > 0 && !args[0].isBlank()) {
      host = args[0].trim();
      HasLogger.staticLogger().info("host is {}", host);
    }
    if (args.length > 1) {
      try {
        port = Integer.parseInt(args[1].trim());
      } catch (NumberFormatException e) {
        HasLogger.staticLogger().warn("Invalid port argument: {} → using default {}", args[1], port);
      }
    }

    HasLogger.staticLogger().warn("Starting ShortenerServer on {}:{}", host, port);

    new ShortenerServer().init(host, port);
  }

  public void init()
      throws IOException {
    logger().info("Starting URL Shortener server... with default params");
    init(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
  }

  public void init(String hostRedirect, int portRedirect)
      throws IOException {
    var store = new InMemoryUrlMappingStore(new ShortCodeGenerator(1));

    logger().info("Starting URL Shortener server (redirect)... with params: host={}, port={}", hostRedirect, portRedirect);
    this.serverRedirect = HttpServer.create(new InetSocketAddress(hostRedirect, portRedirect), 0);
    serverRedirect.createContext(PATH_REDIRECT, new RedirectHandler(store));

    logger().info("Starting URL Shortener server (admin)... with params: host={}, port={}", ADMIN_SERVER_HOST, ADMIN_SERVER_PORT);
    this.serverAdmin = HttpServer.create(new InetSocketAddress(ADMIN_SERVER_HOST, ADMIN_SERVER_PORT), 0);
    serverAdmin.createContext(PATH_ADMIN_SHORTEN, new ShortenHandler(store));
    serverAdmin.createContext(PATH_ADMIN_LIST, new ListHandler(store));
    serverAdmin.createContext(PATH_ADMIN_LIST_COUNT, new ListCountHandler(store));
    serverAdmin.createContext(PATH_ADMIN_DELETE, new DeleteMappingHandler(store)); // DELETE /mapping/{code}

    serverRedirect.setExecutor(null);
    serverRedirect.start();

    serverAdmin.setExecutor(null);
    serverAdmin.start();

    logger().info("URL Shortener server (redirect) running at {}:{}...",
                  serverRedirect.getAddress().getHostName(),
                  serverRedirect.getAddress().getPort());
    logger().info("URL Shortener server (admin) running at {}:{}...",
                  serverAdmin.getAddress().getHostName(),
                  serverAdmin.getAddress().getPort());
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