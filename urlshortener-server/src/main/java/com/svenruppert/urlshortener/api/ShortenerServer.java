package com.svenruppert.urlshortener.api;

import com.sun.net.httpserver.HttpServer;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.api.handler.DeleteMappingHandler;
import com.svenruppert.urlshortener.api.handler.ListHandler;
import com.svenruppert.urlshortener.api.handler.RedirectHandler;
import com.svenruppert.urlshortener.api.handler.ShortenHandler;
import com.svenruppert.urlshortener.api.store.InMemoryUrlMappingStore;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static com.svenruppert.urlshortener.core.DefaultValues.*;

public class ShortenerServer
    implements HasLogger {

  private HttpServer server;

  public static void main(String[] args)
      throws IOException {
    new ShortenerServer().init();
  }

  public void init()
      throws IOException {
    logger().info("Starting URL Shortener server... with default params");
    init(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
  }

  public void init(String host, int port)
      throws IOException {
    var store = new InMemoryUrlMappingStore(new ShortCodeGenerator(1));

    logger().info("Starting URL Shortener server... with params: host={}, port={}", host, port);
    this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
    server.createContext(PATH_SHORTEN, new ShortenHandler(store));
    server.createContext(PATH_LIST, new ListHandler(store));
    server.createContext(PATH_DELETE, new DeleteMappingHandler(store)); // DELETE /mapping/{code}
    server.createContext(PATH_REDIRECT, new RedirectHandler(store));

    server.setExecutor(null); // default executor
    server.start();

    logger().info("URL Shortener server running at {}:{}...",
                  server.getAddress().getHostName(),
                  server.getAddress().getPort());
  }

  public void shutdown() {
    if (server != null) {
      server.stop(0);
      logger().info("URL Shortener server stopped");
    }
  }

  public int getPort() {
    return server.getAddress().getPort();
  }

  public InetAddress getInetAddress() {
    return server.getAddress().getAddress();
  }
}