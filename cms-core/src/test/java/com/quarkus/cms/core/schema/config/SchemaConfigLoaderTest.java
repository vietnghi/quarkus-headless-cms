package com.quarkus.cms.core.schema.config;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.core.schema.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SchemaConfigLoaderTest {

  private final SchemaConfigLoader loader = new SchemaConfigLoader();

  @Test
  void shouldParseContentTypeJson() throws IOException {
    String json =
        """
        {
            "uid": "api::article.article",
            "kind": "COLLECTION_TYPE",
            "singularName": "article",
            "pluralName": "articles",
            "displayName": "Article",
            "description": "Blog articles",
            "draftAndPublish": true,
            "fields": [
                {
                    "name": "title",
                    "type": "STRING",
                    "required": true,
                    "maxLength": 255
                },
                {
                    "name": "body",
                    "type": "RICHTEXT"
                }
            ],
            "relations": [
                {
                    "fieldName": "author",
                    "type": "MANY_TO_ONE",
                    "target": "api::author.author",
                    "targetAttribute": "articles"
                }
            ]
        }""";

    var result = loader.parseJson(json);
    assertEquals(1, result.contentTypes().size());
    assertEquals(0, result.components().size());

    var ct = result.contentTypes().get(0);
    assertEquals("api::article.article", ct.getUid());
    assertEquals(ContentTypeKind.COLLECTION_TYPE, ct.getKind());
    assertEquals(2, ct.getFields().size());
    assertEquals(1, ct.getRelations().size());
  }

  @Test
  void shouldParseComponentJson() throws IOException {
    String json =
        """
        {
            "uid": "shared.seo",
            "category": "shared",
            "displayName": "SEO",
            "fields": [
                {
                    "name": "metaTitle",
                    "type": "STRING",
                    "maxLength": 60
                },
                {
                    "name": "metaDescription",
                    "type": "TEXT",
                    "maxLength": 160
                }
            ]
        }""";

    var result = loader.parseJson(json);
    assertEquals(0, result.contentTypes().size());
    assertEquals(1, result.components().size());

    var comp = result.components().get(0);
    assertEquals("shared.seo", comp.getUid());
    assertEquals(2, comp.getFields().size());
  }

  @Test
  void shouldParseBatchJson() throws IOException {
    String json =
        """
        {
            "contentTypes": [
                {
                    "uid": "api::article.article",
                    "kind": "COLLECTION_TYPE",
                    "displayName": "Article",
                    "fields": [
                        {"name": "title", "type": "STRING"}
                    ]
                },
                {
                    "uid": "api::page.page",
                    "kind": "COLLECTION_TYPE",
                    "displayName": "Page",
                    "fields": [
                        {"name": "content", "type": "RICHTEXT"}
                    ]
                }
            ],
            "components": [
                {
                    "uid": "shared.seo",
                    "fields": [
                        {"name": "metaTitle", "type": "STRING"}
                    ]
                }
            ]
        }""";

    var result = loader.parseJson(json);
    assertEquals(2, result.contentTypes().size());
    assertEquals(1, result.components().size());
    assertEquals(3, result.totalCount());
  }

  @Test
  void shouldLoadFromFilesystemDirectory() throws IOException {
    Path tmpDir = Files.createTempDirectory("cms-schema-test");

    Path articleFile = tmpDir.resolve("article.json");
    Files.writeString(
        articleFile,
        """
        {
            "uid": "api::article.article",
            "kind": "COLLECTION_TYPE",
            "displayName": "Article",
            "fields": [
                {"name": "title", "type": "STRING", "required": true}
            ]
        }""");

    Path seoFile = tmpDir.resolve("seo.json");
    Files.writeString(
        seoFile,
        """
        {
            "uid": "shared.seo",
            "category": "shared",
            "displayName": "SEO",
            "fields": [
                {"name": "metaTitle", "type": "STRING"}
            ]
        }""");

    try {
      var result = loader.loadFromFilesystem(tmpDir);
      assertEquals(1, result.contentTypes().size());
      assertEquals(1, result.components().size());

      var ct = result.contentTypes().get(0);
      assertEquals("api::article.article", ct.getUid());
    } finally {
      // Clean up
      Files.deleteIfExists(articleFile);
      Files.deleteIfExists(seoFile);
      Files.deleteIfExists(tmpDir);
    }
  }

  @Test
  void shouldParseAllFieldTypes() throws IOException {
    String json =
        """
        {
            "uid": "api::alltypes.alltypes",
            "kind": "COLLECTION_TYPE",
            "displayName": "All Types",
            "fields": [
                {"name": "str", "type": "STRING"},
                {"name": "txt", "type": "TEXT"},
                {"name": "num", "type": "INTEGER"},
                {"name": "flt", "type": "FLOAT"},
                {"name": "dec", "type": "DECIMAL"},
                {"name": "flag", "type": "BOOLEAN"},
                {"name": "d", "type": "DATE"},
                {"name": "dt", "type": "DATETIME"},
                {"name": "t", "type": "TIME"},
                {"name": "eml", "type": "EMAIL"},
                {"name": "pwd", "type": "PASSWORD"},
                {"name": "slug", "type": "UID"},
                {"name": "enu", "type": "ENUMERATION", "enumValues": ["a","b"]},
                {"name": "js", "type": "JSON"},
                {"name": "rt", "type": "RICHTEXT"},
                {"name": "img", "type": "MEDIA"},
                {"name": "rel", "type": "RELATION", "target": "api::x.x"},
                {"name": "comp", "type": "COMPONENT", "component": "shared.x"},
                {"name": "dz", "type": "DYNAMIC_ZONE", "allowedComponents": ["shared.x"]}
            ]
        }""";

    var result = loader.parseJson(json);
    assertEquals(1, result.contentTypes().size());

    var ct = result.contentTypes().get(0);
    assertEquals(19, ct.getFields().size());

    // Verify a few types
    assertEquals(FieldType.STRING, ct.getField("str").getType());
    assertEquals(FieldType.BOOLEAN, ct.getField("flag").getType());
    assertEquals(FieldType.ENUMERATION, ct.getField("enu").getType());
    assertEquals(FieldType.RELATION, ct.getField("rel").getType());
    assertEquals(FieldType.COMPONENT, ct.getField("comp").getType());
    assertEquals(FieldType.DYNAMIC_ZONE, ct.getField("dz").getType());
  }
}
