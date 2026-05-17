package com.svenruppert.urlshortener.ui.vaadin.tools;

import com.svenruppert.urlshortener.client.AuditClient;
import com.svenruppert.urlshortener.core.DefaultValues;

public final class AuditClientFactory {

  private AuditClientFactory() {
  }

  public static AuditClient newInstance() {
    AuditClient client = new AuditClient(DefaultValues.ADMIN_SERVER_URL);
    AuthTokenAccessor.currentToken().ifPresent(client::setAuthToken);
    return client;
  }
}
