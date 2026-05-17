package com.svenruppert.urlshortener.core.audit;

/**
 * Read-side projection of a security audit event. Flattens the sealed
 * {@code AuditEvent} hierarchy into a single record so the UI grid can
 * render any event variant with the same five columns.
 */
public record AuditEventView(
    String timestamp,
    String type,
    String subject,
    String target,
    String detail
) {
}
