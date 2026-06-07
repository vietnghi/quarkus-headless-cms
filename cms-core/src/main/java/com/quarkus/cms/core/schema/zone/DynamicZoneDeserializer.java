package com.quarkus.cms.core.schema.zone;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Jackson deserializer that converts a JSON array of component objects into a list of {@link
 * DynamicZoneComponent} instances.
 *
 * <p>Each element in the array is expected to have a {@code __component} key that identifies the
 * component type. The deserializer reads each element as a raw {@code Map<String, Object>} and then
 * wraps it in a {@link DynamicZoneComponent}.
 *
 * <p>Usage (on a field or getter):
 *
 * <pre>{@code
 * @JsonDeserialize(using = DynamicZoneDeserializer.class)
 * List<DynamicZoneComponent> myZone;
 * }</pre>
 */
public class DynamicZoneDeserializer extends JsonDeserializer<List<DynamicZoneComponent>> {

  @Override
  @SuppressWarnings("unchecked")
  public List<DynamicZoneComponent> deserialize(
      JsonParser p, DeserializationContext ctxt) throws IOException {

    List<DynamicZoneComponent> result = new ArrayList<>();

    if (p.currentToken() == JsonToken.START_ARRAY) {
      ObjectMapper mapper = (ObjectMapper) p.getCodec();
      while (p.nextToken() != JsonToken.END_ARRAY) {
        if (p.currentToken() == JsonToken.START_OBJECT) {
          Map<String, Object> raw = mapper.readValue(p, Map.class);
          String componentUid = raw != null ? (String) raw.get("__component") : null;
          if (componentUid != null) {
            raw.remove("__component");
            result.add(new DynamicZoneComponent(componentUid, raw));
          }
          // If no __component key, skip the element (malformed)
        }
      }
    }

    return result;
  }
}
