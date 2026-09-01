package io.diegorocha.imitator.api;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.diegorocha.imitator.model.input.ClusterInput;
import io.diegorocha.imitator.model.input.CollectorInput;
import io.diegorocha.imitator.model.input.DatabaseInput;
import io.diegorocha.imitator.model.output.CollectionOutput;
import io.diegorocha.imitator.model.output.CollectorOutput;
import io.diegorocha.imitator.model.output.DatabaseOutput;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of {@code POST /api/sizing/collect} against a real MongoDB Atlas cluster.
 * <p>Seeds the {@code imitator} database with {@value #COLLECTION_COUNT} randomly-named
 * collections, each carrying {@value #INDEXES_PER_COLLECTION} indexes ({@value
 * #COLLECTION_COUNT} x {@value #INDEXES_PER_COLLECTION} = 500 indexes total — the same order of
 * magnitude as a "100 collections / 465 indexes" cluster), calls the REST API pointed at that
 * database, and verifies every collection and every index comes back sized. This exercises the
 * full production path — including the bounded thread pool
 * ({@code collector.concurrency.thread-pool-size}) that replaced virtual threads for Java 17
 * compatibility — against a real cluster instead of a local container.</p>
 * <p>The Atlas connection string is read from {@code src/test/resources/application-test.properties}
 * ({@code MONGODB_URI}), which is git-ignored — it is never committed.</p>
 * <p>Each run saves the full response body as pretty-printed JSON under {@code test-output/sizing/}
 * (git-ignored) for manual inspection, timestamped so successive runs don't overwrite each other.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SizingControllerAtlasTest {

    private static final int COLLECTION_COUNT = 100;
    private static final int DOCS_PER_COLLECTION = 5;
    // 4 secondary indexes + the default _id_ index = 5 per collection, 500 total —
    // the same order of magnitude as the "100 collections / 465 indexes" scenario being checked.
    private static final int EXTRA_INDEXES_PER_COLLECTION = 4;
    private static final int INDEXES_PER_COLLECTION = EXTRA_INDEXES_PER_COLLECTION + 1;
    private static final String DATABASE_NAME = "imitator";
    private static final Path OUTPUT_DIR = Path.of("test-output", "sizing");

    private static String mongoUri;
    private static List<String> collectionNames;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeAll
    static void seedAtlasDatabase() throws IOException {
        mongoUri = readMongoUri();
        collectionNames = randomCollectionNames(COLLECTION_COUNT);

        try (MongoClient client = MongoClients.create(mongoUri)) {
            MongoDatabase db = client.getDatabase(DATABASE_NAME);
            for (String name : collectionNames) {
                List<Document> docs = IntStream.range(0, DOCS_PER_COLLECTION)
                        .mapToObj(i -> new Document("idx", i).append("payload", "sample-" + i))
                        .toList();
                db.getCollection(name).insertMany(docs);
                createSecondaryIndexes(db, name);
            }
        }
    }

    // Adds EXTRA_INDEXES_PER_COLLECTION secondary indexes on top of the default _id_ index,
    // so each collection ends up with INDEXES_PER_COLLECTION indexes total.
    private static void createSecondaryIndexes(MongoDatabase db, String collectionName) {
        var collection = db.getCollection(collectionName);
        collection.createIndex(new Document("idx", 1));
        collection.createIndex(new Document("payload", 1));
        collection.createIndex(new Document("idx", -1).append("payload", 1));
        collection.createIndex(new Document("payload", -1));
    }

    @AfterAll
    static void dropTestCollections() {
        try (MongoClient client = MongoClients.create(mongoUri)) {
            MongoDatabase db = client.getDatabase(DATABASE_NAME);
            collectionNames.forEach(name -> db.getCollection(name).drop());
        }
    }

    private static String readMongoUri() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = SizingControllerAtlasTest.class.getClassLoader()
                .getResourceAsStream("application-test.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                        "src/test/resources/application-test.properties not found — create it with a MONGODB_URI entry");
            }
            properties.load(in);
        }
        String uri = properties.getProperty("MONGODB_URI");
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("MONGODB_URI is missing from application-test.properties");
        }
        return uri;
    }

    private Path writeOutputToDisk(CollectorOutput body) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
            Path outputFile = OUTPUT_DIR.resolve("sizing-output-" + timestamp + ".json");
            String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
            Files.writeString(outputFile, json);
            return outputFile;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write sizing output to disk", e);
        }
    }

    private static List<String> randomCollectionNames(int count) {
        Set<String> names = new LinkedHashSet<>();
        while (names.size() < count) {
            names.add("perf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }
        return List.copyOf(names);
    }

    @Test
    void sizingApiCollectsAllCollectionsFromAtlas() {
        ClusterInput clusterInput = new ClusterInput(
                "atlas-imitator",
                mongoUri,
                List.of(new DatabaseInput(DATABASE_NAME, List.of())));
        CollectorInput requestBody = new CollectorInput(List.of(clusterInput));

        Instant start = Instant.now();
        ResponseEntity<CollectorOutput> response =
                restTemplate.postForEntity("/api/sizing/collect", requestBody, CollectorOutput.class);
        Duration elapsed = Duration.between(start, Instant.now());
        CollectorOutput body = response.getBody();

        Path outputFile = writeOutputToDisk(body);
        System.out.printf("[sizing] %d collections / %d indexes collected in %d ms — response saved to %s%n",
                COLLECTION_COUNT, COLLECTION_COUNT * INDEXES_PER_COLLECTION, elapsed.toMillis(),
                outputFile.toAbsolutePath());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body).isNotNull();
        assertThat(body.clusters()).hasSize(1);

        DatabaseOutput databaseOutput = body.clusters().get(0).databases().stream()
                .filter(d -> d.name().equals(DATABASE_NAME))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Database '" + DATABASE_NAME + "' missing from response"));

        assertThat(databaseOutput.collections()).hasSize(COLLECTION_COUNT);
        assertThat(databaseOutput.dbStats().totalCollections()).isEqualTo(COLLECTION_COUNT);
        assertThat(databaseOutput.dbStats().totalIndex()).isEqualTo(COLLECTION_COUNT * INDEXES_PER_COLLECTION);

        Set<String> returnedNames = databaseOutput.collections().stream()
                .map(CollectionOutput::name)
                .collect(Collectors.toSet());
        assertThat(returnedNames).containsExactlyInAnyOrderElementsOf(collectionNames);

        databaseOutput.collections().forEach(c -> {
            assertThat(c.collStats().count()).isEqualTo(DOCS_PER_COLLECTION);
            assertThat(c.collStats().totalIndex()).isEqualTo(INDEXES_PER_COLLECTION);
            assertThat(c.collStats().indexes()).hasSize(INDEXES_PER_COLLECTION);
        });
    }
}
