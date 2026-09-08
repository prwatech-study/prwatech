package com.prwatech.common.config;

import com.prwatech.skillama.model.Organization;
import com.prwatech.skillama.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mapping context drives startup index creation, so its entity set must stay scoped to
 * skillamaDB documents. A wider scan would create prwatechDB indexes inside skillamaDB.
 */
class SkillamaMongoConfigTest {

    private MongoMappingContext mappingContext(boolean autoIndexCreation) throws Exception {
        SkillamaMongoConfig config = new SkillamaMongoConfig();
        ReflectionTestUtils.setField(config, "autoIndexCreation", autoIndexCreation);
        MongoMappingContext context =
                config.skillamaMongoMappingContext(config.skillamaMongoCustomConversions());
        context.afterPropertiesSet();
        return context;
    }

    @Test
    void autoIndexCreationFlagIsHonoured() throws Exception {
        assertTrue(mappingContext(true).isAutoIndexCreation());
        assertFalse(mappingContext(false).isAutoIndexCreation());
    }

    @Test
    void scanIncludesSkillamaDocuments() throws Exception {
        MongoMappingContext context = mappingContext(true);
        assertNotNull(context.getPersistentEntity(Organization.class));
        assertNotNull(context.getPersistentEntity(User.class));
    }

    @Test
    void scanIsLimitedToTheSkillamaModelPackage() throws Exception {
        MongoMappingContext context = mappingContext(true);
        boolean foreignEntity = context.getPersistentEntities().stream()
                .map(entity -> entity.getType().getName())
                .anyMatch(name -> !name.startsWith("com.prwatech.skillama.model."));
        assertFalse(foreignEntity, "mapping context must only contain skillamaDB documents");
    }

    @Test
    void scanFindsTheFullDocumentSet() throws Exception {
        MongoMappingContext context = mappingContext(true);
        long count = context.getPersistentEntities().stream()
                .filter(entity -> entity.getType().getName().startsWith("com.prwatech.skillama.model."))
                .count();
        assertTrue(count > 40, "expected the skillama model package to be scanned, found " + count);
    }
}
