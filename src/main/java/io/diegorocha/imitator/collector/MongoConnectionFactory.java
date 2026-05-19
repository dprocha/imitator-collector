package io.diegorocha.imitator.collector;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.diegorocha.imitator.config.CollectorProperties;
import io.diegorocha.imitator.model.input.ClusterInput;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Builds a {@link com.mongodb.client.MongoClient} from a
 * {@link io.diegorocha.imitator.model.input.ClusterInput} connection string.
 * <p>Applies connection and read timeouts from {@link CollectorProperties.Mongo}. Does not
 * call {@code serverApi()} — the Stable API requires MongoDB 5.0+ and causes an immediate
 * connection error against CosmosDB and DocumentDB.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Component
public class MongoConnectionFactory {

    private final CollectorProperties.Mongo mongoConfig;

    public MongoConnectionFactory(CollectorProperties properties) {
        this.mongoConfig = properties.mongo();
    }

    public MongoClient create(ClusterInput config) {
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(config.connectionString()))
                .applyToSocketSettings(b -> b
                        .connectTimeout(mongoConfig.connectTimeoutSeconds(), TimeUnit.SECONDS)
                        .readTimeout(mongoConfig.readTimeoutSeconds(), TimeUnit.SECONDS))
                .applyToClusterSettings(b -> b
                        .serverSelectionTimeout(mongoConfig.serverSelectionTimeoutSeconds(), TimeUnit.SECONDS));
        return MongoClients.create(builder.build());
    }
}
