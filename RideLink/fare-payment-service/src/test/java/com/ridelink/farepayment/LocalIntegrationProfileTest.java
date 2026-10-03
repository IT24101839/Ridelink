package com.ridelink.farepayment;

import com.ridelink.farepayment.config.LocalIntegrationMongoConfig;
import de.bwaldvogel.mongo.MongoServer;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import java.net.Socket;
import static org.assertj.core.api.Assertions.*;

class LocalIntegrationProfileTest {
    private AnnotationConfigApplicationContext localContext() {
        var context=new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("local-integration");
        context.register(LocalIntegrationMongoConfig.class);
        context.refresh();
        return context;
    }
    @Test void defaultProfileDoesNotCreateLocalPersistence() {
        try(var context=new AnnotationConfigApplicationContext(LocalIntegrationMongoConfig.class)) {
            assertThat(context.getBeansOfType(MongoServer.class)).isEmpty();
            assertThat(context.getBeansOfType(MongoDatabaseFactory.class)).isEmpty();
        }
    }
    @Test void startsWithoutUriAndDiscardsDataOnShutdown() {
        int port;
        try(var context=localContext()) {
            var address=context.getBean(MongoServer.class).getLocalAddress();
            assertThat(address.getAddress().isLoopbackAddress()).isTrue();
            port=address.getPort();
            var db=context.getBean(MongoDatabaseFactory.class).getMongoDatabase();
            assertThat(db.getName()).isEqualTo("ridelink_payment");
            db.getCollection("lifetime_probe").insertOne(new Document("value","local"));
            assertThat(db.getCollection("lifetime_probe").countDocuments()).isEqualTo(1);
        }
        assertThatThrownBy(()-> { try(var socket=new Socket("127.0.0.1",port)) {} })
                .isInstanceOf(java.io.IOException.class);
        try(var context=localContext()) {
            assertThat(context.getBean(MongoDatabaseFactory.class).getMongoDatabase()
                    .getCollection("lifetime_probe").countDocuments()).isZero();
        }
    }
}
