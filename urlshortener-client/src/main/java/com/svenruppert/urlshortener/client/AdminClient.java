package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.StoreInfo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import static com.svenruppert.urlshortener.core.DefaultValues.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class AdminClient
    implements HasLogger {

  private final URI serverBaseAdmin;
  private volatile String authToken;

  public AdminClient() {
    this(ADMIN_SERVER_URL);
  }

  public AdminClient(String serverBaseUrlAdmin) {
    var urlToServerAdmin = serverBaseUrlAdmin.endsWith("/")
        ? serverBaseUrlAdmin
        : serverBaseUrlAdmin + "/";

    logger().info("urlToServer ADMIN - {}", urlToServerAdmin);
    this.serverBaseAdmin = URI.create(urlToServerAdmin);
  }

  public void setAuthToken(String token) {
    this.authToken = (token == null || token.isBlank()) ? null : token;
  }

  public StoreInfo getStoreInfo()
      throws java.io.IOException {
    URL url = serverBaseAdmin.resolve(PATH_ADMIN_STORE_INFO).toURL();
    var con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    con.setConnectTimeout(3000);
    con.setReadTimeout(3000);
    String token = authToken;
    if (token != null) {
      con.setRequestProperty("Authorization", "Bearer " + token);
    }

    try {
      AuthFailureRegistry.notifyIfUnauthorized(con.getResponseCode());
    } catch (IOException ignored) {
      // fall through to the input-stream read below which propagates the real cause
    }
    try (var in = con.getInputStream()) {
      var json = new String(in.readAllBytes(), UTF_8);
      logger().info("getStoreInfo - json response from server - {}", json);
      var storeInfo = JsonUtils.fromJson(json, StoreInfo.class);
      logger().info("getStoreInfo - storeInfo {}", storeInfo);
      return storeInfo;
    } catch (Exception e) {
      logger().warn(" getStoreInfo - Failed {}", e.getMessage());
      throw new IOException(e.getMessage());
    }
  }


}
