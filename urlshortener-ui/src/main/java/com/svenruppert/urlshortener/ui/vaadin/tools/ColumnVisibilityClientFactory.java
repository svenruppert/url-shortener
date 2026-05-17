package com.svenruppert.urlshortener.ui.vaadin.tools;



import com.svenruppert.urlshortener.client.ColumnVisibilityClient;

public class ColumnVisibilityClientFactory {

  private ColumnVisibilityClientFactory() {
  }

  public static ColumnVisibilityClient newInstance() {
    ColumnVisibilityClient client = new ColumnVisibilityClient();
    AuthTokenAccessor.currentToken().ifPresent(client::setAuthToken);
    return client;
  }
}