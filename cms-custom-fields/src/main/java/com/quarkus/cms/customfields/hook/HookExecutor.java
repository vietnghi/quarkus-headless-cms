package com.quarkus.cms.customfields.hook;

import com.quarkus.cms.customfields.spi.FieldHook;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes registered field hooks before and after field value persistence.
 *
 * <p>Hooks are CDI beans implementing {@link FieldHook}. They are discovered automatically and
 * applied to matching fields based on configuration.
 */
@ApplicationScoped
public class HookExecutor {

  @Inject Instance<FieldHook> hooks;

  /** Returns all hooks configured for a field. */
  public List<FieldHook> getHooksForField(Map<String, Object> fieldConfig) {
    if (fieldConfig == null) return List.of();
    @SuppressWarnings("unchecked")
    List<String> hookNames = (List<String>) fieldConfig.get("hooks");
    if (hookNames == null || hookNames.isEmpty()) return List.of();

    List<FieldHook> matched = new ArrayList<>();
    for (FieldHook hook : hooks) {
      if (hookNames.contains(hook.getName())) {
        matched.add(hook);
      }
    }
    return matched;
  }

  /**
   * Executes all beforeSave hooks for a field.
   *
   * @param fieldName the field name
   * @param value the current value
   * @param entryData the full entry data
   * @param config field configuration including hook settings
   * @return the (possibly transformed) value
   */
  public Object executeBeforeSave(
      String fieldName, Object value, Map<String, Object> entryData, Map<String, Object> config) {
    Object currentValue = value;
    for (FieldHook hook : getHooksForField(config)) {
      currentValue = hook.beforeSave(fieldName, currentValue, entryData, config);
    }
    return currentValue;
  }

  /** Executes all afterSave hooks for a field. */
  public void executeAfterSave(
      String fieldName,
      Object savedValue,
      Map<String, Object> entryData,
      Map<String, Object> config) {
    for (FieldHook hook : getHooksForField(config)) {
      hook.afterSave(fieldName, savedValue, entryData, config);
    }
  }
}
