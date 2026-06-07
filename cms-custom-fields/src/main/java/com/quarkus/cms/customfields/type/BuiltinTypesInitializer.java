package com.quarkus.cms.customfields.type;

import com.quarkus.cms.customfields.spi.CustomFieldTypeRegistry;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** CDI bean that registers all built-in field types at application startup. */
@ApplicationScoped
public class BuiltinTypesInitializer {

  @Inject CustomFieldTypeRegistry registry;

  @Inject StringFieldType stringFieldType;

  @Inject NumberFieldType numberFieldType;

  @Inject BooleanFieldType booleanFieldType;

  @Inject DateFieldType dateFieldType;

  @Inject EnumerationFieldType enumerationFieldType;

  @Inject JsonFieldType jsonFieldType;

  @Inject MediaFieldType mediaFieldType;

  @Inject RelationFieldType relationFieldType;

  @Inject ComponentFieldType componentFieldType;

  @Inject DynamicZoneFieldType dynamicZoneFieldType;

  @PostConstruct
  void init() {
    registry.registerOrReplace(stringFieldType);
    registry.registerOrReplace(numberFieldType);
    registry.registerOrReplace(booleanFieldType);
    registry.registerOrReplace(dateFieldType);
    registry.registerOrReplace(enumerationFieldType);
    registry.registerOrReplace(jsonFieldType);
    registry.registerOrReplace(mediaFieldType);
    registry.registerOrReplace(relationFieldType);
    registry.registerOrReplace(componentFieldType);
    registry.registerOrReplace(dynamicZoneFieldType);
  }
}
