package com.quarkus.cms.core.schema.annotation;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.schema.model.ComponentDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeDefinition;
import com.quarkus.cms.core.schema.model.ContentTypeKind;
import com.quarkus.cms.core.schema.model.DynamicZoneDefinition;
import com.quarkus.cms.core.schema.model.FieldDefinition;
import com.quarkus.cms.core.schema.model.FieldType;
import com.quarkus.cms.core.schema.model.RelationDefinition;
import com.quarkus.cms.core.schema.model.RelationType;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AnnotationSchemaBuilder}.
 *
 * <p>Tests that annotated POJOs are correctly converted into {@link ContentTypeDefinition} and
 * {@link ComponentDefinition} objects.
 */
class AnnotationSchemaBuilderTest {

  private final AnnotationSchemaBuilder builder = new AnnotationSchemaBuilder();

  // ---- @ContentType conversion ----

  @Test
  void shouldBuildContentTypeFromAnnotation() {
    ContentTypeDefinition ct = builder.buildContentType(Article.class);

    assertEquals("api::article.article", ct.getUid());
    assertEquals(ContentTypeKind.COLLECTION_TYPE, ct.getKind());
    assertEquals("article", ct.getSingularName());
    assertEquals("articles", ct.getPluralName());
    assertEquals("Article", ct.getDisplayName());
    assertTrue(ct.isDraftAndPublish());
    assertFalse(ct.isLocalized());
    assertEquals(4, ct.getFields().size());
    assertEquals(1, ct.getRelations().size());
  }

  @Test
  void shouldBuildSingleTypeFromAnnotation() {
    ContentTypeDefinition ct = builder.buildContentType(Homepage.class);

    assertEquals("api::homepage.homepage", ct.getUid());
    assertEquals(ContentTypeKind.SINGLE_TYPE, ct.getKind());
    assertTrue(ct.isSingleType());
    assertFalse(ct.isCollectionType());
    assertEquals("Homepage", ct.getDisplayName());
  }

  @Test
  void shouldBuildContentTypeWithFieldConstraints() {
    ContentTypeDefinition ct = builder.buildContentType(Article.class);

    FieldDefinition title = ct.getField("title");
    assertNotNull(title);
    assertEquals(FieldType.STRING, title.getType());
    assertTrue(title.isRequired());
    assertEquals(200, title.getMaxLength());
    assertTrue(title.isUnique());

    FieldDefinition body = ct.getField("body");
    assertNotNull(body);
    assertEquals(FieldType.RICHTEXT, body.getType());

    FieldDefinition views = ct.getField("views");
    assertNotNull(views);
    assertEquals(FieldType.INTEGER, views.getType());
    assertEquals(Integer.valueOf(0), views.getMin());
  }

  @Test
  void shouldBuildContentTypeWithRelations() {
    ContentTypeDefinition ct = builder.buildContentType(Article.class);

    assertEquals(1, ct.getRelations().size());
    RelationDefinition authorRel = ct.getRelations().get(0);
    assertEquals("author", authorRel.getFieldName());
    assertEquals(RelationType.MANY_TO_ONE, authorRel.getType());
    assertEquals("api::author.author", authorRel.getTarget());
    assertEquals("articles", authorRel.getTargetAttribute());
  }

  @Test
  void shouldBuildContentTypeWithDynamicZones() {
    ContentTypeDefinition ct = builder.buildContentType(Page.class);

    assertEquals(1, ct.getDynamicZones().size());
    DynamicZoneDefinition dz = ct.getDynamicZones().get(0);
    assertEquals("contentBlocks", dz.getName());
    assertTrue(dz.getComponents().contains("shared.quote"));
    assertTrue(dz.getComponents().contains("shared.slider"));
    assertEquals(0, dz.getMin());
    assertEquals(20, dz.getMax());
  }

  @Test
  void shouldBuildContentTypeWithLocalizedFields() {
    ContentTypeDefinition ct = builder.buildContentType(LocalizedArticle.class);

    assertTrue(ct.isLocalized());
    FieldDefinition title = ct.getField("title");
    assertTrue(title.isLocalized());
    FieldDefinition body = ct.getField("body");
    assertFalse(body.isLocalized());
  }

  // ---- @Component conversion ----

  @Test
  void shouldBuildComponentFromAnnotation() {
    ComponentDefinition comp = builder.buildComponent(Seo.class);

    assertEquals("shared.seo", comp.getUid());
    assertEquals("shared", comp.getCategory());
    assertEquals("SEO", comp.getDisplayName());
    assertEquals(2, comp.getFields().size());
  }

  @Test
  void shouldBuildComponentWithFields() {
    ComponentDefinition comp = builder.buildComponent(Seo.class);

    FieldDefinition metaTitle = comp.getField("metaTitle");
    assertNotNull(metaTitle);
    assertEquals(FieldType.STRING, metaTitle.getType());
    assertEquals(Integer.valueOf(60), metaTitle.getMaxLength());

    FieldDefinition metaDesc = comp.getField("metaDescription");
    assertNotNull(metaDesc);
    assertEquals(FieldType.STRING, metaDesc.getType());
    assertEquals(Integer.valueOf(160), metaDesc.getMaxLength());
  }

  // ---- Error cases ----

  @Test
  void shouldThrowForMissingContentTypeAnnotation() {
    assertThrows(IllegalArgumentException.class, () -> builder.buildContentType(String.class));
  }

  @Test
  void shouldThrowForMissingComponentAnnotation() {
    assertThrows(IllegalArgumentException.class, () -> builder.buildComponent(String.class));
  }

  // ---- Annotated test classes ----

  @ContentType(
      uid = "api::article.article",
      kind = ContentTypeKind.COLLECTION_TYPE,
      singularName = "article",
      pluralName = "articles",
      displayName = "Article")
  static class Article {
    @ContentTypeField(type = FieldType.STRING, required = true, maxLength = 200, unique = true)
    String title;

    @ContentTypeField(type = FieldType.RICHTEXT)
    String body;

    @ContentTypeField(type = FieldType.INTEGER, min = 0)
    int views;

    @ContentTypeField(type = FieldType.EMAIL)
    String editorEmail;

    @ContentTypeRelation(type = RelationType.MANY_TO_ONE, target = "api::author.author", targetAttribute = "articles")
    String author;
  }

  @ContentType(
      uid = "api::homepage.homepage",
      kind = ContentTypeKind.SINGLE_TYPE,
      displayName = "Homepage")
  static class Homepage {
    @ContentTypeField(type = FieldType.STRING, required = true)
    String heroTitle;

    @ContentTypeField(type = FieldType.RICHTEXT)
    String heroBody;
  }

  @ContentType(
      uid = "api::page.page",
      kind = ContentTypeKind.COLLECTION_TYPE,
      displayName = "Page")
  static class Page {
    @ContentTypeField(type = FieldType.STRING, required = true)
    String title;

    @DynamicZone(allowedComponents = {"shared.quote", "shared.slider"}, min = 0, max = 20)
    String contentBlocks;
  }

  @ContentType(
      uid = "api::localized-article.localized-article",
      kind = ContentTypeKind.COLLECTION_TYPE,
      displayName = "Localized Article",
      localized = true)
  static class LocalizedArticle {
    @ContentTypeField(type = FieldType.STRING, required = true, localized = true)
    String title;

    @ContentTypeField(type = FieldType.RICHTEXT)
    String body;
  }

  @Component(uid = "shared.seo", category = "shared", displayName = "SEO")
  static class Seo {
    @ContentTypeField(type = FieldType.STRING, maxLength = 60)
    String metaTitle;

    @ContentTypeField(type = FieldType.STRING, maxLength = 160)
    String metaDescription;
  }
}
