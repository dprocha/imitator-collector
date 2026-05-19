# imitator-collector

A REST API that connects to MongoDB-compatible clusters (MongoDB Atlas, Azure CosmosDB, AWS DocumentDB) and provides two capabilities:

1. **Sizing** — collects per-collection storage statistics to estimate the cost of migrating to MongoDB Atlas
2. **Schema extraction** — samples documents from each collection, infers a JSON Schema, and generates anonymised example documents for schema review

Supports MongoDB server versions **3.2 through 8.x**.

---

## Requirements

- Java 26+
- Maven 3.9+ (or use the included `./mvnw` wrapper)
- Network access to the target MongoDB cluster

## Building

```bash
./mvnw clean package
```

Produces a runnable JAR at `target/imitator-collectorInput-0.0.1-SNAPSHOT.jar`.

## Running

```bash
# Development
./mvnw spring-boot:run

# Production
java -jar target/imitator-collectorInput-0.0.1-SNAPSHOT.jar
```

The server starts on port **8081**.

> **Memory note:** all collection results are held in memory before the response is written.
> Increase the heap for large clusters — as a rough guide: `-Xmx512m` covers up to ~2,000 collections;
> `-Xmx1g` up to ~10,000 collections; `-Xmx2g` for anything larger.
>
> ```bash
> java -Xmx1g -jar target/imitator-collectorInput-0.0.1-SNAPSHOT.jar
> ```

| URL | Description |
|---|---|
| `POST /api/sizing/collect` | Sizing report endpoint |
| `POST /api/schema/extract` | Schema extraction endpoint |
| `POST /api/schema/export` | Schema + sample ZIP download |
| `http://localhost:8081/` | Swagger UI (interactive API explorer) |
| `http://localhost:8081/api-docs` | OpenAPI spec (JSON) |

---

## Using the API

Both endpoints accept the same JSON input format. The typical workflow is:

1. Create an `input.json` file describing your clusters (see [Input format](#input-format))
2. POST it to the desired endpoint
3. Save the response

---

### Sizing — `POST /api/sizing/collect`

Collects per-collection storage statistics: document count, average document size, total data size, and index definitions. Returns a structured JSON report for Atlas cluster sizing.

#### Save the report to a file

```bash
curl -s -X POST http://localhost:8081/api/sizing/collect \
  -H "Content-Type: application/json" \
  -d @input.json \
  -o report.json
```

#### Save a pretty-printed report

```bash
curl -s -X POST http://localhost:8081/api/sizing/collect \
  -H "Content-Type: application/json" \
  -d @input.json \
  | jq . > report.json
```

#### Check the HTTP status while saving

```bash
curl -s -X POST http://localhost:8081/api/sizing/collect \
  -H "Content-Type: application/json" \
  -d @input.json \
  -o report.json \
  -w "\nHTTP %{http_code}\n"
```

Expected output on success: `HTTP 200`. On error: `HTTP 500` (empty body).

---

### Schema extraction — `POST /api/schema/extract`

Samples up to 50 documents (configurable) from each collection, infers a JSON Schema Draft 2020-12, and returns an anonymised example document alongside the schema. No real data leaves the server — every leaf value is replaced with a fake.

#### curl

**Basic — collect all databases, pretty-print, save to file:**

```bash
curl -s -X POST http://localhost:8081/api/schema/extract \
  -H "Content-Type: application/json" \
  -d @input.json \
  | jq . > schema-report.json
```

**Check HTTP status while saving:**

```bash
curl -s -X POST http://localhost:8081/api/schema/extract \
  -H "Content-Type: application/json" \
  -d @input.json \
  -o schema-report.json \
  -w "\nHTTP %{http_code}\n"
```

Expected: `HTTP 200`. On error: `HTTP 500` with an empty body.

**Target specific databases and collections:**

```bash
curl -s -X POST http://localhost:8081/api/schema/extract \
  -H "Content-Type: application/json" \
  -d '{
    "clusters": [
      {
        "name": "prod",
        "connectionString": "mongodb+srv://admin:secret@prod.example.mongodb.net",
        "databases": [
          {
            "name": "ecommerce",
            "collections": ["orders", "products", "users"]
          },
          {
            "name": "analytics"
          }
        ]
      }
    ]
  }' \
  | jq . > schema-report.json
```

**Multiple clusters in one request:**

```bash
curl -s -X POST http://localhost:8081/api/schema/extract \
  -H "Content-Type: application/json" \
  -d '{
    "clusters": [
      {
        "name": "atlas-prod",
        "connectionString": "mongodb+srv://admin:secret@prod.example.mongodb.net"
      },
      {
        "name": "cosmosdb-staging",
        "connectionString": "mongodb://account:key@account.mongo.cosmos.azure.com:10255/?ssl=true&replicaSet=globaldb&retrywrites=false"
      }
    ]
  }' \
  | jq . > schema-report.json
```

**Print only the inferred schema for a specific collection (requires `jq`):**

```bash
curl -s -X POST http://localhost:8081/api/schema/extract \
  -H "Content-Type: application/json" \
  -d @input.json \
  | jq '.clusters[0].databases[0].collections[] | select(.name == "orders") | .jsonSchema'
```

#### HTTPie

**Basic — all databases, save to file:**

```bash
http --print=b POST localhost:8081/api/schema/extract \
  < input.json \
  > schema-report.json
```

**Inline JSON — target specific databases:**

```bash
http --print=b POST localhost:8081/api/schema/extract \
  clusters:='[
    {
      "name": "prod",
      "connectionString": "mongodb+srv://admin:secret@prod.example.mongodb.net",
      "databases": [
        {"name": "ecommerce", "collections": ["orders", "products"]},
        {"name": "analytics"}
      ]
    }
  ]' \
  > schema-report.json
```

**Multiple clusters inline:**

```bash
http --print=b POST localhost:8081/api/schema/extract \
  clusters:='[
    {
      "name": "atlas-prod",
      "connectionString": "mongodb+srv://admin:secret@prod.example.mongodb.net"
    },
    {
      "name": "cosmosdb-staging",
      "connectionString": "mongodb://account:key@account.mongo.cosmos.azure.com:10255/?ssl=true&replicaSet=globaldb&retrywrites=false"
    }
  ]' \
  > schema-report.json
```

**Print request and response headers for debugging:**

```bash
http --print=hHbB POST localhost:8081/api/schema/extract \
  < input.json
```

---

### Schema ZIP export — `POST /api/schema/export`

Same schema pipeline as `/api/schema/extract`, but packages the output as a ZIP file. Each collection gets its own directory:

```
{cluster}/{database}/{collection}/schema.json   ← JSON Schema Draft 2020-12
                                  sample.json   ← anonymised example document
```

Characters outside `[a-zA-Z0-9._-]` in names are replaced with underscores in ZIP paths.

#### curl

**Save the ZIP:**

```bash
curl -s -X POST http://localhost:8081/api/schema/export \
  -H "Content-Type: application/json" \
  -d @input.json \
  -o schema.zip
```

**Check HTTP status while saving:**

```bash
curl -s -X POST http://localhost:8081/api/schema/export \
  -H "Content-Type: application/json" \
  -d @input.json \
  -o schema.zip \
  -w "\nHTTP %{http_code}\n"
```

Expected: `HTTP 200`. On error: `HTTP 500` with an empty body (the output file will be empty).

**Save and immediately list ZIP contents:**

```bash
curl -s -X POST http://localhost:8081/api/schema/export \
  -H "Content-Type: application/json" \
  -d @input.json \
  -o schema.zip && unzip -l schema.zip
```

**Save and extract into a directory:**

```bash
curl -s -X POST http://localhost:8081/api/schema/export \
  -H "Content-Type: application/json" \
  -d @input.json \
  -o schema.zip && unzip -o schema.zip -d ./schema-output/
```

**Target specific databases and collections:**

```bash
curl -s -X POST http://localhost:8081/api/schema/export \
  -H "Content-Type: application/json" \
  -d '{
    "clusters": [
      {
        "name": "prod",
        "connectionString": "mongodb+srv://admin:secret@prod.example.mongodb.net",
        "databases": [
          {
            "name": "ecommerce",
            "collections": ["orders", "products"]
          }
        ]
      }
    ]
  }' \
  -o schema.zip
```

**Multiple clusters:**

```bash
curl -s -X POST http://localhost:8081/api/schema/export \
  -H "Content-Type: application/json" \
  -d '{
    "clusters": [
      {
        "name": "atlas-prod",
        "connectionString": "mongodb+srv://admin:secret@prod.example.mongodb.net"
      },
      {
        "name": "cosmosdb-staging",
        "connectionString": "mongodb://account:key@account.mongo.cosmos.azure.com:10255/?ssl=true&replicaSet=globaldb&retrywrites=false"
      }
    ]
  }' \
  -o schema.zip
```

#### HTTPie

**Save the ZIP:**

```bash
http --print=b POST localhost:8081/api/schema/export \
  < input.json \
  > schema.zip
```

> **Note:** HTTPie may warn about binary content — this is expected for a ZIP response.
> Use `--print=b` (body only) to suppress headers from the output stream when piping to a file.

**Verify the response headers (Content-Type and Content-Disposition):**

```bash
http --print=h POST localhost:8081/api/schema/export \
  < input.json
```

Expected headers:

```
Content-Type: application/zip
Content-Disposition: attachment; filename="schema.zip"
```

**Save and immediately list contents:**

```bash
http --print=b POST localhost:8081/api/schema/export \
  < input.json \
  > schema.zip && unzip -l schema.zip
```

**Inline JSON — target specific databases:**

```bash
http --print=b POST localhost:8081/api/schema/export \
  clusters:='[
    {
      "name": "prod",
      "connectionString": "mongodb+srv://admin:secret@prod.example.mongodb.net",
      "databases": [
        {"name": "ecommerce", "collections": ["orders", "products"]}
      ]
    }
  ]' \
  > schema.zip
```

---

### With HTTPie — Sizing

[HTTPie](https://httpie.io/) is an alternative HTTP client with a cleaner syntax and built-in JSON formatting.

**Install:**

```bash
brew install httpie          # macOS
pip install httpie           # Python (any OS)
```

```bash
http --print=b POST localhost:8081/api/sizing/collect \
  < input.json \
  > report.json
```

**Target specific databases and collections:**

```bash
http --print=b POST localhost:8081/api/sizing/collect \
  clusters:='[
    {
      "name": "prod",
      "connectionString": "mongodb+srv://admin:secret@prod.example.mongodb.net",
      "databases": [
        {"name": "ecommerce", "collections": ["orders", "products"]},
        {"name": "analytics"}
      ]
    }
  ]' \
  > report.json
```

---

### Using Swagger UI

Open `http://localhost:8081/` in a browser while the server is running to explore and call the API interactively.

---

## Input format

All three endpoints accept the same request body.

### Request body

```json
{
  "clusters": [
    {
      "name": "my-cluster",
      "connectionString": "mongodb+srv://user:pass@cluster0.example.mongodb.net",
      "databases": [
        {
          "name": "ecommerce",
          "collections": ["orders", "products", "users"]
        }
      ]
    }
  ]
}
```

| Field | Required | Description |
|---|---|---|
| `clusters[].name` | Yes | Display name for the cluster (used in the report) |
| `clusters[].connectionString` | Yes | MongoDB connection string including credentials (`mongodb://` or `mongodb+srv://`) |
| `clusters[].databases` | No | Databases to include. Omit to collect **all** databases (skips `admin`, `local`, `config`) |
| `clusters[].databases[].name` | Yes | Database name |
| `clusters[].databases[].collections` | No | Collection names. Omit to collect **all** collections in the database |

### Connection string examples

| Target | Connection string |
|---|---|
| MongoDB Atlas | `mongodb+srv://user:pass@cluster0.abcde.mongodb.net` |
| Self-hosted replica set | `mongodb://user:pass@host1:27017,host2:27017,host3:27017/?replicaSet=rs0` |
| AWS DocumentDB | `mongodb://user:pass@mydbcluster.abcde.us-east-1.docdb.amazonaws.com:27017/?tls=true&tlsCAFile=/path/to/rds-combined-ca-bundle.pem&replicaSet=rs0` |
| Azure CosmosDB | `mongodb://account:key@account.mongo.cosmos.azure.com:10255/?ssl=true&replicaSet=globaldb&retrywrites=false` |
| Local | `mongodb://localhost:27017` |

---

## Sizing output format

```json
{
  "dateCreate": "2026-04-22T20:00:00.000Z",
  "clusters": [
    {
      "name": "prod",
      "version": "7.0.15",
      "estimated": false,
      "clusterStats": {
        "totalDatabases": 2,
        "totalCollections": 12,
        "totalDocuments": 1500000,
        "totalIndex": 10,
        "totalDataSizeB": 3552902016,
        "totalDataSizeKB": 3552902.02,
        "totalDataSizeMB": 3552.91,
        "totalDataSizeGB": 3.56,
        "totalDataSizeTB": 0.004,
        "totalIndexSizeB": 52428800,
        "totalIndexSizeKB": 52428.8,
        "totalIndexSizeMB": 52.43,
        "totalIndexSizeGB": 0.05,
        "totalIndexSizeTB": 0.0
      },
      "databases": [...]
    }
  ]
}
```

## Schema output format

```json
{
  "dateCreate": "2026-04-22T20:00:00.000Z",
  "clusters": [
    {
      "name": "prod",
      "version": "7.0.15",
      "databases": [
        {
          "name": "ecommerce",
          "collections": [
            {
              "name": "orders",
              "sampleSize": 50,
              "jsonSchema": {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "title": "orders",
                "type": "object",
                "properties": {
                  "_id":       { "type": "string", "format": "objectid" },
                  "userId":    { "type": "string", "format": "objectid" },
                  "status":    { "type": "string" },
                  "total":     { "type": "number" },
                  "createdAt": { "type": "string", "format": "date-time" },
                  "items":     {
                    "type": "array",
                    "items": {
                      "type": "object",
                      "properties": {
                        "productId": { "type": "string", "format": "objectid" },
                        "qty":       { "type": "integer" }
                      }
                    }
                  }
                }
              },
              "exampleDocument": {
                "_id": "000000000000000000000000",
                "userId": "000000000000000000000000",
                "status": "3f2a1c4e-...",
                "total": 0.0,
                "createdAt": "1970-01-01T00:00:00.000Z",
                "items": [{ "productId": "000000000000000000000000", "qty": 0 }]
              }
            }
          ]
        }
      ]
    }
  ]
}
```

| Field | Description |
|---|---|
| `sampleSize` | Number of documents actually sampled |
| `jsonSchema` | JSON Schema Draft 2020-12 inferred from the sample |
| `jsonSchema.properties[*].format` | `objectid` for ObjectId, `date-time` for Date, `byte` for Binary |
| `exampleDocument` | Structure-preserving anonymised document — every leaf replaced with a fake value |

---

## Understanding metrics vs cloud portal figures

The numbers this tool reports will **not** match what Azure CosmosDB (or similar managed services) show in their portals. This is expected and intentional.

### Why the numbers differ on Azure CosmosDB for MongoDB

The tool reads statistics directly from the MongoDB wire protocol (`collStats` command for versions 3.2–7.x, `$collStats` aggregation for 8.0+). These commands return the **raw BSON payload size** — the actual bytes that make up your documents and index B-trees.

Azure Portal reports **physically-allocated CosmosDB storage blocks**, which include:

- Internal CosmosDB per-document metadata (version vectors, RU tracking state, partition routing keys)
- Minimum block-allocation granularity (CosmosDB allocates storage in fixed-size chunks)
- The `DocumentDBDefaultIndex_1` wildcard index (see below)

In practice the portal figure is typically **3–5× larger** than the raw BSON data size for document storage, and far larger for index storage due to the wildcard index.

### `DocumentDBDefaultIndex_1` — CosmosDB wildcard index

CosmosDB automatically creates a global wildcard index on every collection that indexes every field at every path. This index:

- **Does not exist in real MongoDB / Atlas** — it is a CosmosDB internal artifact
- Is not reported correctly by `collStats` (its size appears as 0 in the wire-protocol response)
- Accounts for the majority of the index storage shown in the Azure Portal

This tool correctly omits its size from the report because it has no Atlas equivalent and must not be migrated.

### Which numbers to use for Atlas sizing

The tool's raw BSON sizes are the **correct baseline for Atlas migration estimation**:

- The raw document data is what actually transfers during a migration
- Atlas uses WiredTiger compression (typically 0.3–0.5× the uncompressed BSON size), so the Atlas storage footprint will generally be **smaller** than what the tool reports
- Using the inflated CosmosDB portal figures would systematically overestimate the Atlas cluster size you need

---

## Tech stack

- Java 26, Spring Boot 4.0.5
- Spring Web (embedded Tomcat + REST) with virtual threads (`spring.threads.virtual.enabled=true`)
- SpringDoc OpenAPI 3.0.3 (Swagger UI)
- MongoDB Java Driver 3.12.9 (server compatibility: 3.2–8.x)
- Jackson 3.1.0 (`tools.jackson`)

## Package structure

```
io.diegorocha.imitator.collector
├── api/            — REST controllers (SizingController, SchemaController)
├── service/        — Orchestration (CollectorService, SchemaService)
├── collector/      — MongoDB interaction (ClusterCollector, DatabaseCollector,
│                     CollectionCollector, ClusterSchemaCollector,
│                     CollectionSchemaCollector, BsonJsonSchemaGenerator,
│                     MongoConnectionFactory, CursorHelper)
├── config/         — Spring beans and @ConfigurationProperties (CollectorProperties, JacksonConfig)
├── serialization/  — File-based JSON I/O utilities (InputReader, OutputWriter)
├── model/input/    — Request records
├── model/output/   — Response records (sizing and schema)
└── exception/      — CollectorException
```

## Tunable configuration

All behaviour-affecting constants are overridable in `application.properties`:

| Property | Default | Description |
|---|---|---|
| `collector.mongo.connect-timeout-seconds` | `10` | TCP connect timeout |
| `collector.mongo.read-timeout-seconds` | `60` | Socket read timeout |
| `collector.mongo.server-selection-timeout-seconds` | `10` | Server selection timeout |
| `collector.sampling.sample-size` | `50` | Documents sampled for size estimation and schema inference |
| `collector.internal-databases[n]` | `admin,local,config` | System databases always skipped |
| `collector.cosmosdb.system-indexes[n]` | `DocumentDBDefaultIndex_1`, `DocumentDBDefaultIndex_2dsphere` | Indexes excluded from output |
| `collector.cosmosdb.phantom-collections[n]` | `lection` | Phantom collection names to skip |
| `logging.level.io.diegorocha.imitator.collector` | `INFO` | Change to `DEBUG` to trace the full collection flow |
