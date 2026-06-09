package com.quarkus.cms.i18n;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that uses H2 for I18n integration tests.
 * <p>
 * The I18nService integration tests (CRUD → query within same thread)
 * rely on the database showing flushed data to subsequent {@code findBestEntry}
 * queries within a @{@code Transactional} boundary. SQLite's transaction
 * isolation semantics do not guarantee this even after explicit flush +
 * commit, while H2 with PostgreSQL-mode does.
 * <p>
 * All other unit test modules use SQLite. Only cms-i18n integration
 * tests use this H2 profile, via {@code @TestProfile(I18nH2Profile.class)}.
 */
public class I18nH2Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.datasource.db-kind", "h2",
            "quarkus.datasource.jdbc.url",
                "jdbc:h2:mem:test_i18n;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "quarkus.datasource.jdbc.driver", "",
            "quarkus.hibernate-orm.dialect", ""
        );
    }
}
