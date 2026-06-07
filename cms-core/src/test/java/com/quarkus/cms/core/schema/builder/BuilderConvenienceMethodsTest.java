package com.quarkus.cms.core.schema.builder;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.schema.builder.ComponentBuilder;
import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;

import org.junit.jupiter.api.Test;

/**
 * Tests for the convenience field builder methods ({@link
 * ContentTypeBuilder.FieldInserter} and {@link ComponentBuilder.FieldInserter}) added to the
 * programmatic builder API.
 */
class BuilderConvenienceMethodsTest {

  @Test
  void shouldBuildContentTypeWithConvenienceStringField() {
    ContentTypeDefinition ct =
        ContentTypeBuilder.create("api::article.article", ContentTypeKind.COLLECTION_TYPE)
            .displayName("Article")
            .addStringField("title").required(true).maxLength(200).unique(true).add()
            .addRichTextField("body").add()
            .build();

    assertEquals(2, ct.getFields().size());

    FieldDefinition title = ct.getField("title");
    assertEquals(FieldType.STRING, title.getType());
    assertTrue(title.isRequired());
    assertEquals(200, title.getMaxLength());
    assertTrue(title.isUnique());

    FieldDefinition body = ct.getField("body");
    assertEquals(FieldType.RICHTEXT, body.getType());
  }

  @Test
  void shouldBuildContentTypeWithNumericFields() {
    ContentTypeDefinition ct =
        ContentTypeBuilder.create("api::product.product", ContentTypeKind.COLLECTION_TYPE)
            .displayName("Product")
            .addStringField("name").required(true).add()
            .addIntegerField("quantity").min(0).max(99999).add()
            .addFloatField("price").min(0).add()
            .addDecimalField("rating").min(0).max(5).add()
            .build();

    assertEquals(4, ct.getFields().size());
    assertEquals(FieldType.INTEGER, ct.getField("quantity").getType());
    assertEquals(FieldType.FLOAT, ct.getField("price").getType());
    assertEquals(FieldType.DECIMAL, ct.getField("rating").getType());
  }

  @Test
  void shouldBuildContentTypeWithTemporalFields() {
    ContentTypeDefinition ct =
        ContentTypeBuilder.create("api::event.event", ContentTypeKind.COLLECTION_TYPE)
            .displayName("Event")
            .addStringField("name").required(true).add()
            .addDateField("startDate").required(true).add()
            .addDateTimeField("endDate").add()
            .addBooleanField("allDay").defaultValue("false").add()
            .build();

    assertEquals(FieldType.DATE, ct.getField("startDate").getType());
    assertEquals(FieldType.DATETIME, ct.getField("endDate").getType());
    assertEquals(FieldType.BOOLEAN, ct.getField("allDay").getType());
    assertEquals("false", ct.getField("allDay").getDefaultValue());
  }

  @Test
  void shouldBuildContentTypeWithSpecializedFields() {
    ContentTypeDefinition ct =
        ContentTypeBuilder.create("api::user.user", ContentTypeKind.COLLECTION_TYPE)
            .displayName("User")
            .addEmailField("email").required(true).unique(true).add()
            .addPasswordField("password").required(true).add()
            .addUidField("slug").required(true).unique(true).add()
            .addEnumerationField("role", "admin", "editor", "viewer").required(true).add()
            .addJsonField("metadata").add()
            .addMediaField("avatar").add()
            .build();

    assertEquals(FieldType.EMAIL, ct.getField("email").getType());
    assertEquals(FieldType.PASSWORD, ct.getField("password").getType());
    assertEquals(FieldType.UID, ct.getField("slug").getType());
    assertEquals(FieldType.ENUMERATION, ct.getField("role").getType());
    assertTrue(ct.getField("role").isRequired());
    assertEquals(FieldType.JSON, ct.getField("metadata").getType());
    assertEquals(FieldType.MEDIA, ct.getField("avatar").getType());
  }

  @Test
  void shouldBuildContentTypeWithComponentField() {
    ContentTypeDefinition ct =
        ContentTypeBuilder.create("api::article.article", ContentTypeKind.COLLECTION_TYPE)
            .displayName("Article")
            .addStringField("title").required(true).add()
            .addComponentField("seo", "shared.seo").add()
            .build();

    FieldDefinition seo = ct.getField("seo");
    assertEquals(FieldType.COMPONENT, seo.getType());
    assertEquals("shared.seo", seo.getComponent());
  }

  @Test
  void shouldBuildComponentWithConvenienceMethods() {
    ComponentDefinition seo =
        ComponentBuilder.create("shared.seo")
            .category("shared")
            .displayName("SEO")
            .addStringField("metaTitle").maxLength(60).add()
            .addTextField("metaDescription").maxLength(160).add()
            .addIntegerField("priority").min(0).max(10).add()
            .build();

    assertEquals(3, seo.getFields().size());
    assertEquals(FieldType.STRING, seo.getField("metaTitle").getType());
    assertEquals(Integer.valueOf(60), seo.getField("metaTitle").getMaxLength());
    assertEquals(FieldType.TEXT, seo.getField("metaDescription").getType());
    assertEquals(FieldType.INTEGER, seo.getField("priority").getType());
  }

  @Test
  void shouldBuildComponentWithRepeatableField() {
    ComponentDefinition gallery =
        ComponentBuilder.create("shared.gallery")
            .displayName("Gallery")
            .addMediaField("images").repeatable(true).add()
            .build();

    assertEquals(1, gallery.getFields().size());
    assertTrue(gallery.getField("images").isRepeatable());
  }
}
