package com.quarkus.cms.customfields.storage;

import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.customfields.CustomFieldValue;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alternative storage strategy: stores custom field values in a separate {@link CustomFieldValue}
 * database table (cms_custom_field_values).
 *
 * <p>This strategy is useful when you need to query custom field values independently of the entry,
 * or when the JSONB approach has size/capability limitations.
 */
@ApplicationScoped
public class SeparateTableFieldStorageStrategy implements FieldStorageStrategy {

  @Override
  public String getName() {
    return "separate_table";
  }

  @Override
  public Object readValue(CmsEntry entry, String fieldName) {
    CustomFieldValue value = CustomFieldValue.findByEntryAndField(entry.id, fieldName);
    return value != null ? value.getValue() : null;
  }

  @Override
  @Transactional
  public void writeValue(CmsEntry entry, String fieldName, Object value) {
    CustomFieldValue existing = CustomFieldValue.findByEntryAndField(entry.id, fieldName);
    if (existing != null) {
      existing.setValue(value);
      existing.persist();
    } else {
      CustomFieldValue cv = new CustomFieldValue();
      cv.entryId = entry.id;
      cv.fieldName = fieldName;
      cv.setValue(value);
      cv.contentType = entry.contentType;
      cv.persist();
    }
  }

  @Override
  @Transactional
  public void removeValue(CmsEntry entry, String fieldName) {
    CustomFieldValue existing = CustomFieldValue.findByEntryAndField(entry.id, fieldName);
    if (existing != null) {
      existing.delete();
    }
  }

  @Override
  public Map<String, Object> readAllValues(CmsEntry entry) {
    List<CustomFieldValue> values = CustomFieldValue.findByEntryId(entry.id);
    Map<String, Object> result = new LinkedHashMap<>();
    for (CustomFieldValue cv : values) {
      result.put(cv.fieldName, cv.getValue());
    }
    return result;
  }

  @Override
  @Transactional
  public void removeAllValues(CmsEntry entry) {
    List<CustomFieldValue> values = CustomFieldValue.findByEntryId(entry.id);
    for (CustomFieldValue cv : values) {
      cv.delete();
    }
  }
}
