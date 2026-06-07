package com.quarkus.cms.it;

import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.runtime.CmsConfig;
import com.quarkus.cms.runtime.CmsHealthService;
import com.quarkus.cms.runtime.CmsSchemaService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for boot, schema loading and CmsConfig injection.
 *
 * <p>Scope:
 * <ol>
 *   <li>Verifies the extension boots correctly (FeatureBuildItem check)
 *   <li>Verifies schema loading from classpath {@code schemas/} directory
 *   <li>Verifies {@link CmsConfig} injection works with {@code @QuarkusTest}
 * </ol>
 */
@QuarkusTest
class CmsBootSchemaConfigIT {

    @Inject
    CmsHealthService healthService;

    @Inject
    CmsConfig cmsConfig;

    @Inject
    CmsSchemaService schemaService;

    @Inject
    SchemaStorageService schemaStorage;

    // ========================================================
    // 1. FeatureBuildItem / Extension Boot Verification
    // ========================================================

    @Test
    void testExtensionBootsWithFeatureName() {
        // The health endpoint and FeatureBuildItem("quarkus-headless-cms")
        // both identify the extension. If the extension booted correctly the
        // health resource is reachable and returns the feature name.
        given()
            .when().get("/api/health")
            .then()
            .statusCode(200)
            .body("status", is("ok"))
            .body("cms", is("quarkus-headless-cms"))
            .body("version", notNullValue());
    }

    @Test
    void testCmsHealthServiceIsEnabled() {
        // CmsHealthService requires CmsConfig injection and the extension to
        // have booted (implicit FeatureBuildItem verification).
        assertTrue(healthService.isEnabled(), "CMS extension should be enabled");
    }

    @Test
    void testCmsHealthServiceReportsVersion() {
        var health = healthService.getHealth();
        assertEquals("ok", health.get("status"));
        assertEquals("quarkus-headless-cms", health.get("cms"));
        assertNotNull(health.get("version"));
    }

    // ========================================================
    // 2. Schema Loading from Classpath schemas/ directory
    // ========================================================

    @Test
    void testCoreSchemaArticleLoadedFromClasspath() {
        // article.json from cms-core/src/main/resources/schemas/ (uid: api::article.article)
        assertTrue(schemaService.getSchema("api::article.article").isPresent(),
            "Core schema 'api::article.article' should be loaded from classpath schemas/");
    }

    @Test
    void testCoreSchemaSeoComponentLoadedFromClasspath() {
        // seo.json from cms-core/src/main/resources/schemas/ (uid: shared.seo)
        assertTrue(schemaService.getSchema("shared.seo").isPresent(),
            "Core schema 'shared.seo' (component) should be loaded from classpath schemas/");
    }

    @Test
    void testSchemaStorageHasContentTypes() {
        // At minimum the core article schema should be registered
        assertFalse(schemaStorage.getAllContentTypes().isEmpty(),
            "At least one content type should be registered");
    }

    @Test
    void testSchemaStorageHasComponents() {
        // At minimum the core seo component should be registered
        assertFalse(schemaStorage.getAllComponents().isEmpty(),
            "At least one component should be registered");
    }

    @Test
    void testArticleContentTypeHasExpectedFields() {
        var ct = schemaStorage.getContentType("api::article.article");
        assertNotNull(ct, "Article content type must be present");
        assertEquals("api::article.article", ct.getUid());
        assertEquals("article", ct.getSingularName());
        assertNotNull(ct.getFields(), "Article must have fields");
        assertFalse(ct.getFields().isEmpty(), "Article must have at least one field");
    }

    // ========================================================
    // 3. CmsConfig Injection and Default Values
    // ========================================================

    @Test
    void testCmsConfigIsInjectable() {
        assertNotNull(cmsConfig, "CmsConfig should be injectable in @QuarkusTest");
    }

    @Test
    void testCmsConfigTopLevelDefaults() {
        assertTrue(cmsConfig.enabled(), "quarkus.cms.enabled should default to true");
        assertEquals("/api", cmsConfig.apiBasePath(),
            "quarkus.cms.api.base-path should default to /api");
        assertEquals("/admin", cmsConfig.adminBasePath(),
            "quarkus.cms.admin.base-path should default to /admin");
        assertEquals("schemas", cmsConfig.schemaDirectory(),
            "quarkus.cms.schema.directory should default to 'schemas'");
        assertEquals("en", cmsConfig.defaultLocale(),
            "quarkus.cms.default-locale should default to 'en'");
    }

    @Test
    void testCmsConfigMediaDefaults() {
        var media = cmsConfig.media();
        assertNotNull(media);
        assertEquals("10M", media.maxUploadSize(),
            "quarkus.cms.media.max-upload-size should default to 10M");
        assertEquals("local", media.storageProvider(),
            "quarkus.cms.media.storage-provider should default to 'local'");
    }

    @Test
    void testCmsConfigAuthDefaults() {
        var auth = cmsConfig.auth();
        assertNotNull(auth);
        // From test application.properties: quarkus.cms.auth.api-tokens.enabled=false
        assertFalse(auth.apiTokensEnabled(),
            "API tokens should be disabled in integration-test configuration");
    }

    @Test
    void testCmsConfigImageDefaults() {
        var image = cmsConfig.media().image();
        assertNotNull(image);
        assertTrue(image.enabled(), "Image optimization should be enabled by default");
        assertEquals(85, image.quality(), "Default image quality should be 85");
    }
}
