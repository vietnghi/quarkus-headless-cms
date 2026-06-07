package com.quarkus.cms.deployment;

import com.quarkus.cms.audit.CmsAuditLog;
import com.quarkus.cms.auth.entity.CmsApiToken;
import com.quarkus.cms.auth.entity.CmsPermission;
import com.quarkus.cms.auth.entity.CmsRole;
import com.quarkus.cms.auth.entity.CmsUser;
import com.quarkus.cms.auth.provider.LocalAuthenticationProvider;
import com.quarkus.cms.auth.repository.CmsApiTokenRepository;
import com.quarkus.cms.auth.repository.CmsUserRepository;
import com.quarkus.cms.auth.resource.AdminAuthResource;
import com.quarkus.cms.auth.resource.AdminRolesResource;
import com.quarkus.cms.auth.resource.AdminTokensResource;
import com.quarkus.cms.auth.resource.AdminUsersResource;
import com.quarkus.cms.auth.security.CmsJwtGenerator;
import com.quarkus.cms.auth.security.CmsSecurityHeaders;
import com.quarkus.cms.auth.security.PermissionCheckInterceptor;
import com.quarkus.cms.auth.service.ApiTokenService;
import com.quarkus.cms.auth.service.AuthService;
import com.quarkus.cms.auth.service.PermissionService;
import com.quarkus.cms.auth.service.RoleService;
import com.quarkus.cms.auth.service.TokenService;
import com.quarkus.cms.core.domain.CmsEntry;
import com.quarkus.cms.core.domain.CmsRelation;
import com.quarkus.cms.core.repository.CmsEntryRepository;
import com.quarkus.cms.core.schema.config.SchemaConfigLoader;
import com.quarkus.cms.core.schema.config.SchemaInitializer;
import com.quarkus.cms.core.schema.relation.RelationService;
import com.quarkus.cms.core.schema.storage.CoreSchema;
import com.quarkus.cms.core.schema.storage.SchemaCache;
import com.quarkus.cms.core.schema.storage.SchemaStorageService;
import com.quarkus.cms.core.schema.zone.DynamicZoneService;
import com.quarkus.cms.customfields.CustomFieldDefinition;
import com.quarkus.cms.customfields.CustomFieldValue;
import com.quarkus.cms.draft.DraftPublishService;
import com.quarkus.cms.i18n.model.CmsLocale;
import com.quarkus.cms.media.entity.CmsFile;
import com.quarkus.cms.media.entity.CmsFolder;
import com.quarkus.cms.rest.CmsExceptionMapper;
import com.quarkus.cms.rest.ContentApiResource;
import com.quarkus.cms.review.CmsReview;
import com.quarkus.cms.runtime.CmsConfig;
import com.quarkus.cms.runtime.CmsContentService;
import com.quarkus.cms.runtime.CmsExtension;
import com.quarkus.cms.runtime.CmsHealthResource;
import com.quarkus.cms.runtime.CmsHealthService;
import com.quarkus.cms.runtime.CmsRecorder;
import com.quarkus.cms.runtime.CmsSchemaService;
import com.quarkus.cms.runtime.MigrationLifecycleManager;
import com.quarkus.cms.webhooks.entity.CmsWebhook;
import com.quarkus.cms.webhooks.entity.CmsWebhookDelivery;
import com.quarkus.cms.webhooks.resource.AdminWebhooksResource;
import com.quarkus.cms.webhooks.service.LifecycleEventBus;
import com.quarkus.cms.webhooks.service.WebhookDispatcher;
import com.quarkus.cms.webhooks.service.WebhookEventConsumer;
import com.quarkus.cms.webhooks.service.WebhookPayloadBuilder;
import com.quarkus.cms.webhooks.service.WebhookSecurityService;
import com.quarkus.cms.webhooks.service.WebhookService;

import com.quarkus.cms.plugin.PluginConfig;
import com.quarkus.cms.review.WorkflowService;
import com.quarkus.cms.media.config.MediaConfig;
import com.quarkus.cms.media.repository.CmsFileRepository;
import com.quarkus.cms.media.storage.LocalStorageProvider;
import com.quarkus.cms.media.storage.S3StorageProvider;
import com.quarkus.cms.media.storage.R2StorageProvider;
import com.quarkus.cms.media.storage.StorageProviderProducer;
import com.quarkus.cms.media.service.MediaService;
import com.quarkus.cms.media.resource.MediaResource;
import com.quarkus.cms.media.image.ImageOptimizer;
import com.quarkus.cms.media.validation.UploadValidator;
import com.quarkus.cms.i18n.resource.AdminI18nResource;
import com.quarkus.cms.i18n.service.LocaleService;
import com.quarkus.cms.customfields.resource.CustomFieldAdminResource;
import com.quarkus.cms.customfields.CustomFieldService;
import com.quarkus.cms.customfields.spi.CustomFieldTypeRegistry;
import com.quarkus.cms.customfields.type.BuiltinTypesInitializer;
import com.quarkus.cms.customfields.validation.FieldValidationFramework;
import com.quarkus.cms.customfields.hook.HookExecutor;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.hibernate.orm.deployment.AdditionalJpaModelBuildItem;

/**
 * Build-time processor for the Quarkus Headless CMS extension.
 *
 * <p>This class contains {@link BuildStep} methods that execute during the Quarkus augmentation
 * phase. It registers the CMS feature, configures CDI beans, processes schema files, and wires
 * everything together for both JVM and native modes.
 */
public class CmsProcessor {

  private static final String FEATURE = "quarkus-headless-cms";

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem(FEATURE);
  }

  @BuildStep
  void registerBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
    // Runtime beans
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsExtension.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsRecorder.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsHealthResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsHealthService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsSchemaService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsContentService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(MigrationLifecycleManager.class));

    // cms-core beans
    additionalBeans.produce(new AdditionalBeanBuildItem(SchemaStorageService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(SchemaCache.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(SchemaConfigLoader.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(SchemaInitializer.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(DynamicZoneService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(RelationService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsEntryRepository.class));

    // cms-core annotation schema beans
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.core.schema.annotation.AnnotationSchemaBuilder.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.core.schema.annotation.AnnotationSchemaScanner.class));

    // cms-draft-publish beans
    additionalBeans.produce(new AdditionalBeanBuildItem(DraftPublishService.class));

    // cms-rest-api beans
    additionalBeans.produce(new AdditionalBeanBuildItem(ContentApiResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsExceptionMapper.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.rest.BulkOperationsResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.rest.service.BulkOperationService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.rest.service.BulkOperationException.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.rest.RelationsResource.class));

    // cms-auth service beans
    additionalBeans.produce(new AdditionalBeanBuildItem(TokenService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(AuthService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(PermissionService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(RoleService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(ApiTokenService.class));

    // cms-auth repository beans
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsUserRepository.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsApiTokenRepository.class));

    // cms-auth provider beans
    additionalBeans.produce(new AdditionalBeanBuildItem(LocalAuthenticationProvider.class));

    // cms-auth security beans
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsJwtGenerator.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsSecurityHeaders.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(PermissionCheckInterceptor.class));

    // cms-auth resource beans (JAX-RS)
    additionalBeans.produce(new AdditionalBeanBuildItem(AdminAuthResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(AdminUsersResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(AdminRolesResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(AdminTokensResource.class));

    // cms-admin-api resource beans (JAX-RS)
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.admin.api.resource.ContentTypeBuilderResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.admin.api.resource.ContentManagerResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.admin.api.resource.AdminMediaResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.admin.api.resource.AdminConfigResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.admin.api.resource.AdminDashboardStatsResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.admin.api.resource.AdminStaticResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.admin.api.service.ContentManagerService.class));

    // cms-admin-api filter beans
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.admin.api.filter.AdminRateLimitingFilter.class));

    // cms-plugin beans
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.plugin.PluginRegistry.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.plugin.PluginManager.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.plugin.PluginAdminResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.plugin.SchemaMerger.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.plugin.hook.PluginHookRegistry.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(com.quarkus.cms.plugin.seo.SeoPlugin.class));

    // cms-webhooks beans (library module, lifecycle events + webhook dispatch)
    additionalBeans.produce(new AdditionalBeanBuildItem(LifecycleEventBus.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(WebhookService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(WebhookDispatcher.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(WebhookPayloadBuilder.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(WebhookSecurityService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(WebhookEventConsumer.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(AdminWebhooksResource.class));

    // runtime config bean
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsConfig.class));

    // cms-plugin config bean
    additionalBeans.produce(new AdditionalBeanBuildItem(PluginConfig.class));

    // cms-review beans
    additionalBeans.produce(new AdditionalBeanBuildItem(WorkflowService.class));

    // cms-media beans
    additionalBeans.produce(new AdditionalBeanBuildItem(MediaConfig.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CmsFileRepository.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(LocalStorageProvider.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(S3StorageProvider.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(R2StorageProvider.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(StorageProviderProducer.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(MediaService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(MediaResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(ImageOptimizer.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(UploadValidator.class));

    // cms-i18n beans
    additionalBeans.produce(new AdditionalBeanBuildItem(AdminI18nResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(LocaleService.class));

    // cms-custom-fields beans
    additionalBeans.produce(new AdditionalBeanBuildItem(CustomFieldAdminResource.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CustomFieldService.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(CustomFieldTypeRegistry.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(BuiltinTypesInitializer.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(FieldValidationFramework.class));
    additionalBeans.produce(new AdditionalBeanBuildItem(HookExecutor.class));
  }

  @BuildStep
  void registerJpaEntities(BuildProducer<AdditionalJpaModelBuildItem> jpaModels) {
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsEntry.class.getName()));
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsRelation.class.getName()));
    jpaModels.produce(new AdditionalJpaModelBuildItem(CoreSchema.class.getName()));

    // cms-auth entities
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsUser.class.getName()));
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsRole.class.getName()));
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsPermission.class.getName()));
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsApiToken.class.getName()));

    // cms-webhooks entities
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsWebhook.class.getName()));
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsWebhookDelivery.class.getName()));

    // cms-media entities
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsFile.class.getName()));
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsFolder.class.getName()));

    // cms-audit entities
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsAuditLog.class.getName()));

    // cms-i18n entities
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsLocale.class.getName()));

    // cms-custom-fields entities
    jpaModels.produce(new AdditionalJpaModelBuildItem(CustomFieldDefinition.class.getName()));
    jpaModels.produce(new AdditionalJpaModelBuildItem(CustomFieldValue.class.getName()));

    // cms-review entities
    jpaModels.produce(new AdditionalJpaModelBuildItem(CmsReview.class.getName()));
  }

  @BuildStep
  @Record(ExecutionTime.RUNTIME_INIT)
  void initializeRuntime(CmsRecorder recorder) {
    recorder.initialize();
  }
}
