package com.svenruppert.urlshortener.api.security.adapter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.svenruppert.urlshortener.api.security.CurrentSubject;
import com.svenruppert.vaadin.security.rest.RestAuthorizationFilter;
import com.svenruppert.vaadin.security.rest.RestSubjectResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.AnnotatedElement;
import java.util.Objects;

public final class SecurityHttpHandler implements HttpHandler {

  private final RestAuthorizationFilter authFilter;
  private final RestSubjectResolver subjectResolver;
  private final HttpHandler delegate;
  private final AnnotatedElement annotation;

  public SecurityHttpHandler(
      RestAuthorizationFilter authFilter,
      RestSubjectResolver subjectResolver,
      HttpHandler delegate,
      AnnotatedElement annotation) {
    this.authFilter = Objects.requireNonNull(authFilter, "authFilter");
    this.subjectResolver = Objects.requireNonNull(subjectResolver, "subjectResolver");
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.annotation = Objects.requireNonNull(annotation, "annotation");
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    HttpExchangeRestRequest request = new HttpExchangeRestRequest(exchange);
    HttpExchangeRestResponse response = new HttpExchangeRestResponse();
    boolean[] delegated = {false};
    try {
      authFilter.authorizeAndHandle(request, response, (r, s) -> {
        delegated[0] = true;
        subjectResolver.resolveSubject(r).ifPresent(CurrentSubject::set);
        try {
          delegate.handle(exchange);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        } finally {
          CurrentSubject.clear();
        }
      }, annotation);
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
    if (!delegated[0]) {
      response.writeTo(exchange);
    }
  }
}
