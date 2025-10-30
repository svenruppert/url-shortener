package com.svenruppert.urlshortener.client;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.urlshortener.core.JsonUtils;
import com.svenruppert.urlshortener.core.StoreInfo;

import java.net.URI;
import java.net.URL;

import static com.svenruppert.urlshortener.core.DefaultValues.PATH_ADMIN_STORE_INFO;
import static java.nio.charset.StandardCharsets.*;

public class AdminClient
    implements HasLogger {

  private final URI serverBaseAdmin;

  public AdminClient(String serverBaseUrlAdmin) {
    var urlToServerAdmin = serverBaseUrlAdmin.endsWith("/")
        ? serverBaseUrlAdmin
        : serverBaseUrlAdmin + "/";

    logger().info("urlToServer ADMIN - {}", urlToServerAdmin);
    this.serverBaseAdmin = URI.create(urlToServerAdmin);
  }

  public StoreInfo getStoreInfo()
      throws java.io.IOException {
    URL url = serverBaseAdmin.resolve(PATH_ADMIN_STORE_INFO).toURL();
    var con = (java.net.HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    con.setConnectTimeout(3000);
    con.setReadTimeout(3000);

    try (var in = con.getInputStream()) {
      var json = new String(in.readAllBytes(), UTF_8);
      logger().info("getStoreInfo - json response from server - {}", json);
      var storeInfo = JsonUtils.fromJson(json, StoreInfo.class);
      logger().info("getStoreInfo - storeInfo {}", storeInfo);
      return storeInfo;

    }
  }


}
