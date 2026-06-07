package com.quarkus.cms.core.schema.zone;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a single component instance within a dynamic zone.
 *
 * <p>Each component in a dynamic zone has a {@code __component} discriminator that identifies which
 * component definition it conforms to, plus an arbitrary set of field values.
 *
 * <p>This class supports Jackson polymorphic deserialization via {@code __component}, and
 * round-trips cleanly between JSON and Java.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DynamicZoneComponent {

  private String componentUid;
  private final Map<String, Object> data;

  public DynamicZoneComponent() {
    this.data = new LinkedHashMap<>();
  }

  /**
   * Creates a component instance with the given discriminator UID and field data.
   *
   * @param componentUid the component UID (e.g. {@code "shared.quote"})
   * @param data the field values (may be empty)
   */
  public DynamicZoneComponent(@JsonProperty("__component") String componentUid, Map<String, Object> data) {
    this.componentUid = componentUid;
    this.data = data != null ? new LinkedHashMap<>(data) : new LinkedHashMap<>();
  }

  /**
   * Returns the component UID that identifies which component definition this instance conforms
   * to.
   */
  @JsonProperty("__component")
  public String getComponentUid() {
    return componentUid;
  }

  @JsonProperty("__component")
  public void setComponentUid(String componentUid) {
    this.componentUid = componentUid;
  }

  /**
   * Returns the field data (component field name → value).
   */
  @JsonAnyGetter
  public Map<String, Object> getData() {
    return data;
  }

  /**
   * Sets a field value by name. Fields named {@code __component} are routed to {@link
   * #setComponentUid(String)} instead.
   */
  @JsonAnySetter
  public void setField(String name, Object value) {
    if ("__component".equals(name)) {
      this.componentUid = (String) value;
    } else {
      this.data.put(name, value);
    }
  }

  /**
   * Returns the value of a specific field within this component instance.
   */
  @JsonIgnore
  public Object getField(String name) {
    return data.get(name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DynamicZoneComponent that)) return false;
    return Objects.equals(componentUid, that.componentUid) && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(componentUid, data);
  }

  @Override
  public String toString() {
    return "DynamicZoneComponent{__component='" + componentUid + "', fields=" + data.size() + "}";
  }
}
