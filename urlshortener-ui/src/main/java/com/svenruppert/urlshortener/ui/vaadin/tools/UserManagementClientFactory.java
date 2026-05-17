package com.svenruppert.urlshortener.ui.vaadin.tools;

import com.svenruppert.urlshortener.client.UserManagementClient;
import com.svenruppert.urlshortener.core.DefaultValues;

public final class UserManagementClientFactory {

  private UserManagementClientFactory() {
  }

  public static UserManagementClient newInstance() {
    UserManagementClient client = new UserManagementClient(DefaultValues.ADMIN_SERVER_URL);
    AuthTokenAccessor.currentToken().ifPresent(client::setAuthToken);
    return client;
  }
}
