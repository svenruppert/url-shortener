package com.svenruppert.urlshortener.ui.vaadin.tools;

import com.svenruppert.urlshortener.client.URLShortenerClient;

public class UrlShortenerClientFactory {




public static URLShortenerClient newInstance(){
  return new URLShortenerClient();
}

}
