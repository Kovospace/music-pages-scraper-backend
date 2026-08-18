package com.kovospace.musicpagesscraper.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the backward-compatibility story of a single endpoint.
 *
 * <p>Picked up by {@code CompatibilityCustomizer} and published into the OpenAPI document twice:
 * as the machine-readable {@code x-compatibility} extension (what API clients and agents parse),
 * and appended to the operation description (what a human sees in Swagger UI).
 *
 * <p>Endpoints are versioned one by one, so this sits on the handler method, not the controller.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Compatibility {

  /**
   * API version this endpoint first appeared in <em>its current shape</em>. Bump it only when the
   * response or parameters actually change, not on every release.
   */
  String since();

  /**
   * True when this version changed the contract in a way that breaks clients written against the
   * previous one. Purely informational for {@code since = "v1"} endpoints, which have no previous.
   */
  boolean breaking() default false;

  /**
   * Path of the older endpoint this one takes over from, e.g. {@code "/v1/{s}/bands"}.
   */
  String supersedes() default "";

  /**
   * Path of the newer endpoint that replaces this one, e.g. {@code "/v2/{s}/bands"}. Setting it
   * also marks the operation deprecated, so Swagger UI strikes it through.
   */
  String replacedBy() default "";

  /**
   * What changed, one entry per change, e.g. {@code "removed field `hrefHash`"}. Meant to be read
   * by a client deciding whether a change affects it.
   */
  String[] changes() default {};

  /**
   * Anything that does not fit the fields above, e.g. a migration hint or a removal date.
   */
  String notes() default "";
}
