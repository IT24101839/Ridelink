package com.ridelink.ride;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class MongoConfigurationTest {
    private String resolve(String key,Map<String,Object> overrides) throws Exception {
        Properties config=new Properties();
        try(var input=Files.newInputStream(Path.of("src/main/resources/application.properties"))){config.load(input);}
        MutablePropertySources sources=new MutablePropertySources();
        sources.addFirst(new MapPropertySource("overrides",overrides));
        sources.addLast(new PropertiesPropertySource("main",config));
        return new PropertySourcesPropertyResolver(sources).getRequiredProperty(key);
    }
    @Test void defaultsToOwnLocalDatabase() throws Exception {
        assertThat(resolve("spring.data.mongodb.uri",Map.of())).isEqualTo("mongodb://localhost:27017/ridelink_ride");
    }
    @Test void suppliedUriOverridesDefault() throws Exception {
        String uri="mongodb://localhost:27018/custom_test";
        assertThat(resolve("spring.data.mongodb.uri",Map.of("MONGODB_URI",uri))).isEqualTo(uri);
    }
    @Test void portSupportsEnvironmentOverride() throws Exception {
        assertThat(resolve("server.port",Map.of())).isEqualTo("8083");
        assertThat(resolve("server.port",Map.of("PORT","9090"))).isEqualTo("9090");
    }
    @Test void databaseRemainsServiceSpecific() throws Exception {
        assertThat(resolve("spring.data.mongodb.database",Map.of("MONGODB_URI","mongodb://localhost:27017/shared")))
                .isEqualTo("ridelink_ride");
    }
}
