package com.kovospace.musicpagesscraper.config;

import com.kovospace.musicpagesscraper.annotations.Compatibility;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Projects {@link Compatibility} onto every annotated operation.
 *
 * <p>Implements {@code GlobalOperationCustomizer} rather than plain {@code OperationCustomizer}:
 * plain ones are only applied to a {@code GroupedOpenApi} when registered on its builder, while
 * global ones are picked up by every group automatically. Since the groups are per API version,
 * a plain customizer would silently produce no extension at all.
 */
@Component
public class CompatibilityCustomizer implements GlobalOperationCustomizer {

  private static final String EXTENSION_NAME = "x-compatibility";

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    Compatibility compat = handlerMethod.getMethodAnnotation(Compatibility.class);
    if (compat == null) {
      return operation;
    }
    operation.addExtension(EXTENSION_NAME, asExtension(compat));
    operation.setDescription(appendToDescription(operation.getDescription(), compat));
    if (!compat.replacedBy().isEmpty()) {
      operation.setDeprecated(true);
    }
    return operation;
  }

  /** Machine-readable half: what a client or agent reads out of /v3/api-docs. */
  private Map<String, Object> asExtension(Compatibility compat) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("since", compat.since());
    meta.put("breaking", compat.breaking());
    if (!compat.supersedes().isEmpty()) {
      meta.put("supersedes", compat.supersedes());
    }
    if (!compat.replacedBy().isEmpty()) {
      meta.put("replacedBy", compat.replacedBy());
      meta.put("deprecated", true);
    }
    if (compat.changes().length > 0) {
      meta.put("changes", Arrays.asList(compat.changes()));
    }
    if (!compat.notes().isEmpty()) {
      meta.put("notes", compat.notes());
    }
    return meta;
  }

  /**
   * Human-readable half. Swagger UI does not render {@code x-} extensions anywhere prominent, so
   * the same facts are folded into the description that the UI does show.
   */
  private String appendToDescription(String description, Compatibility compat) {
    StringBuilder sb = new StringBuilder(description == null ? "" : description);
    sb.append("\n\n---\n\n**Compatibility** — since `").append(compat.since()).append("`");
    sb.append(compat.breaking()
      ? ", ⚠️ breaking change against the previous version."
      : ", backward compatible.");
    if (!compat.supersedes().isEmpty()) {
      sb.append("\n\nSupersedes `").append(compat.supersedes()).append("`.");
    }
    if (!compat.replacedBy().isEmpty()) {
      sb.append("\n\n**Deprecated** — use `").append(compat.replacedBy()).append("` instead.");
    }
    if (compat.changes().length > 0) {
      sb.append("\n\nChanges:");
      for (String change : compat.changes()) {
        sb.append("\n- ").append(change);
      }
    }
    if (!compat.notes().isEmpty()) {
      sb.append("\n\n").append(compat.notes());
    }
    return sb.toString();
  }
}
