package com.ridelink.drivervehicle.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/** Disposable persistence only. Authentication and business services are unchanged. */
@Configuration(proxyBeanMethods = false)
@Profile("local-integration")
public class LocalIntegrationMongoConfig {
    @Bean(destroyMethod = "shutdownNow")
    MongoServer localIntegrationMongoServer() {
        MongoServer server = new MongoServer(new MemoryBackend());
        try {
            server.bind("127.0.0.1", 0);
            org.slf4j.LoggerFactory.getLogger(LocalIntegrationMongoConfig.class)
                    .warn("LOCAL-INTEGRATION: disposable Mongo-compatible storage; data is lost on shutdown");
            return server;
        } catch (RuntimeException ex) {
            server.shutdownNow();
            throw ex;
        }
    }

    @Bean(destroyMethod = "close")
    MongoClient localIntegrationMongoClient(MongoServer localIntegrationMongoServer) {
        return MongoClients.create(localIntegrationMongoServer.getConnectionString());
    }

    @Bean
    MongoDatabaseFactory localIntegrationMongoDatabaseFactory(MongoClient localIntegrationMongoClient) {
        // Deliberately ignore external URIs in this profile, including inherited Atlas settings.
        return new SimpleMongoClientDatabaseFactory(localIntegrationMongoClient, "ridelink_driver");
    }
}
