package com.svenruppert.urlshortener.ui.vaadin.tools;



import com.svenruppert.urlshortener.client.StatisticsClient;

public class StatisticsClientFactory {

  private StatisticsClientFactory() {
  }

  public static StatisticsClient newInstance() {
    StatisticsClient client = new StatisticsClient();
    AuthTokenAccessor.currentToken().ifPresent(client::setAuthToken);
    return client;
  }
}