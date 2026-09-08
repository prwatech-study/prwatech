package com.prwatech.common.config;

import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.util.ClassUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Configuration
@EnableMongoRepositories(
    basePackages = {
        "com.prwatech.skillama.repository"
    },
    mongoTemplateRef = "skillamaMongoTemplate"
)
public class SkillamaMongoConfig {

    private static final String DATABASE = "skillamaDB";

    /**
     * Only skillamaDB entities. Scanning wider would let this mapping context pull in
     * prwatechDB documents and create their indexes (and empty collections) in skillamaDB.
     */
    private static final String MODEL_PACKAGE = "com.prwatech.skillama.model";

    @Value("${skillama.mongodb.uri}")
    private String mongoUri;

    /**
     * Creates the indexes declared via {@code @Indexed} on startup. Spring Data leaves this
     * off by default, so the annotations were previously inert and tenant-scoped lookups
     * (organizationId, slug, customDomain) ran as collection scans.
     *
     * <p>Set to {@code false} to boot without touching indexes. Index creation runs against
     * live data, so a pre-existing duplicate on a unique-indexed field fails startup — see
     * {@code scripts/check-mongo-unique-indexes.js} before enabling on a populated database.
     */
    @Value("${skillama.mongodb.auto-index-creation:true}")
    private boolean autoIndexCreation;

    @Bean(name = "skillamaMongoDatabaseFactory")
    public MongoDatabaseFactory skillamaMongoDatabaseFactory() {
        return new SimpleMongoClientDatabaseFactory(MongoClients.create(mongoUri), DATABASE);
    }

    @Bean(name = "skillamaMongoCustomConversions")
    public MongoCustomConversions skillamaMongoCustomConversions() {
        return new MongoCustomConversions(Collections.emptyList());
    }

    @Bean(name = "skillamaMongoMappingContext")
    public MongoMappingContext skillamaMongoMappingContext(
            @Qualifier("skillamaMongoCustomConversions") MongoCustomConversions conversions)
            throws ClassNotFoundException {
        MongoMappingContext mappingContext = new MongoMappingContext();
        // Without this, eager initialization treats types like LocalDateTime as entities and
        // reflects into their private constructors, which JDK 17 module rules reject.
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.setAutoIndexCreation(autoIndexCreation);
        mappingContext.setInitialEntitySet(scanModelEntities());
        return mappingContext;
    }

    /**
     * Mirrors the converter {@link MongoTemplate} builds for itself, so document mapping
     * (including the {@code _class} type hint already written to existing documents) is
     * unchanged; the mapping context is the only difference.
     */
    @Bean(name = "skillamaMongoTemplate")
    public MongoTemplate skillamaMongoTemplate(
            @Qualifier("skillamaMongoDatabaseFactory") MongoDatabaseFactory factory,
            @Qualifier("skillamaMongoMappingContext") MongoMappingContext mappingContext,
            @Qualifier("skillamaMongoCustomConversions") MongoCustomConversions conversions) {
        MappingMongoConverter converter =
                new MappingMongoConverter(new DefaultDbRefResolver(factory), mappingContext);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        return new MongoTemplate(factory, converter);
    }

    private Set<Class<?>> scanModelEntities() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Document.class));
        Set<Class<?>> entities = new HashSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(MODEL_PACKAGE)) {
            String className = candidate.getBeanClassName();
            if (className != null) {
                entities.add(ClassUtils.forName(className, getClass().getClassLoader()));
            }
        }
        return entities;
    }
}
