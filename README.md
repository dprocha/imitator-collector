# imitator-collector

<p align="center">
  <img src="images/wilted_leaf_mongodb.png" alt="imitator-collector logo" width="320">
</p>

---

> *A true story. Probably.*

Once upon a time, a developer deployed MongoDB to production.
Life was good. Queries were fast. `collStats` returned real numbers. Indexes made sense.

Then, one fateful Tuesday, a cloud architect said the four most dangerous words in software engineering:

**"Let's just use CosmosDB or DocumentDB."**

Suddenly, `listCollections` started returning a collection named `"lection"`. Index sizes were all zero — except for a mysterious `DocumentDBDefaultIndex_1` that appeared on every collection, indexed every field on the planet, and yet somehow reported a size of `0 bytes`. The Azure Portal claimed you were using **47 GB of storage**. Your `collStats` said **11 GB**. Nobody knew who was lying. The portal wasn't talking.

The driver threw `"Range [X, Y) out of bounds"` for no reason. Wire protocol 3.2 did not support feelings.

Your MongoDB leaf, once proud and green, began to wilt.

---

**imitator-collector** is the rescue team.

It speaks wire protocol 3.2. It ignores the phantom collection. It excludes the wildcard index that has no Atlas equivalent. It samples your documents when the stats come back empty. It tells you, in plain bytes, exactly how much real data you have — so you can finally escape to **MongoDB Atlas**, where `collStats` tells the truth and leaves stay green.

Point it at your CosmosDB. Point it at your DocumentDB. Point it at anything that *claims* to be MongoDB. It will collect the sizing report and the JSON Schemas you need to plan your migration, and it will do it without throwing `"Range [X, Y) out of bounds"` at you.

Your leaf deserves better.

---
# About this project

A REST API that connects to MongoDB-compatible clusters (Azure CosmosDB, AWS DocumentDB and also on MongoDB) and provides two capabilities:

1. **Sizing** — collects per-collection storage statistics to estimate the cost of migrating to MongoDB Atlas
2. **Schema extraction** — samples documents from each collection, infers a JSON Schema, and generates anonymised example documents for schema review

Supports MongoDB server versions **3.2 through 8.x**.

---

## Requirements

- Java 17+
- Network access to the target MongoDB cluster
- Maven 3.9+ (or use the included `./mvnw` wrapper) — only needed if building from source

## Download

The easiest way to get started is to download the pre-built JAR from the [Releases page](https://github.com/dprocha/imitator-collector/releases/latest):

1. Go to the [latest release](https://github.com/dprocha/imitator-collector/releases/latest)
2. Download `imitator-collector-<version>.jar` from the **Assets** section
3. Run it:

```bash
java -jar imitator-collector-<version>.jar
```

The server starts on port **8081**.

> **Memory note:** all collection results are held in memory before the response is written.
> Increase the heap for large clusters — as a rough guide: `-Xmx512m` covers up to ~2,000 collections;
> `-Xmx1g` up to ~10,000 collections; `-Xmx2g` for anything larger.
>
> ```bash
> java -Xmx1g -jar imitator-collector-<version>.jar
> ```

## Building from source

```bash
./mvnw clean package
```

Produces a runnable JAR at `target/imitator-collector-<version>.jar`.

## Running from source

```bash
# Development
./mvnw spring-boot:run

# Production
java -jar target/imitator-collector-<version>.jar
```

The server starts on port **8081**.

| URL | Description |
|---|---|
| `POST /api/sizing/collect` | Sizing report endpoint |
| `POST /api/schema/extract` | Schema extraction endpoint |
| `POST /api/schema/export` | Schema + sample ZIP download |
| `http://localhost:8081/` | Swagger UI (interactive API explorer) |
| `http://localhost:8081/api-docs` | OpenAPI spec (JSON) |

---

## Compatibility

MongoDB wire-protocol versions known to work with this tool, across each supported distribution.

### Symbol legend

**Tested column**

| Symbol | Meaning                                       |
|:------:|-----------------------------------------------|
|   ✅    | Verified working against this protocol version |
|   ❌    | Not supported on this protocol version      |
|   —    | Not tested                                    |

**CollStats method column**

| Symbol | Meaning                                                    |
|:------:|------------------------------------------------------------|
|   ✅    | Native `collStats` used — server returned accurate stats   |
|   ❌    | `collStats` unusable — BSON sampling used instead          |
|   —    | Not tested                                                 |

### How sampling estimation works

Some distributions (notably CosmosDB RU on protocol 3.2) return `count > 0` but `avgObjSize = 0` from `collStats`. 
When the tool detects this condition it automatically falls back to fetching up to `collector.sampling.sample-size` 
BSON documents and computing average object size from them. Collections processed this way are marked `"estimated": true` in the report.

### Azure CosmosDB for MongoDB (RU-based)

| Protocol Version | Tested | CollStats method |
| :---: | :---: |:----------------:|
| 3.2 | ✅ |        ❌         |
| 3.6 | ✅ |        ✅         |
| 4.0 | ✅ |        ✅         |
| 4.2 | ✅ |        ✅         |
| 5.0 | ✅|         ✅        |
| 6.0 | ✅ |        ✅         |
| 7.0 | ✅ |        ✅         |

### Azure CosmosDB (DocumentDB)

| Protocol Version | Tested | CollStats method |
|:----------------:| :---: |:----------------:|
|       5.0        | ✅ |        ✅         |
|       6.0        | ✅ |        ✅         |
|       7.0        | ✅ |        ✅         |
|       8.0        | ✅ |        ✅         |

### AWS DocumentDB

| Protocol Version | Tested | CollStats method  |
|:----------------:|:------:| :---: |
|       3.6        |   ✅    |  ✅ |
|       4.0        |   ✅    |  ✅ |
|       5.0        |   ✅    | ✅|
|       8.0        |    ✅   | ✅ |

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

## Complete request and response examples

### `POST /api/sizing/collect`

**Request:**

```json
{
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
}
```

**Response (`200 OK`):**

```json
{
  "dateCreate": "2026-05-19T12:00:00.000Z",
  "clusters": [
    {
      "name": "prod",
      "version": "7.0.15",
      "estimated": false,
      "clusterStats": {
        "totalDatabases": 1,
        "totalCollections": 2,
        "totalDocuments": 501000,
        "totalIndex": 5,
        "totalDataSizeB": 392000768,
        "totalDataSizeKB": 392000.77,
        "totalDataSizeMB": 392.0,
        "totalDataSizeGB": 0.39,
        "totalDataSizeTB": 0.0,
        "totalIndexSizeB": 20971520,
        "totalIndexSizeKB": 20971.52,
        "totalIndexSizeMB": 20.97,
        "totalIndexSizeGB": 0.02,
        "totalIndexSizeTB": 0.0
      },
      "databases": [
        {
          "name": "ecommerce",
          "dbStats": {
            "totalCollections": 2,
            "totalDocuments": 501000,
            "totalIndex": 5,
            "totalDataSize": 392000768,
            "totalIndexSize": 20971520
          },
          "collections": [
            {
              "name": "orders",
              "collStats": {
                "count": 500000,
                "avgObjSize": 768,
                "dataSize": 384000000,
                "totalIndex": 3,
                "totalIndexSize": 12582912,
                "indexes": [
                  { "name": "_id_",     "size": 4194304, "key": { "_id": 1 },    "unique": true  },
                  { "name": "userId_1", "size": 4194304, "key": { "userId": 1 }, "unique": false },
                  { "name": "status_1", "size": 4194304, "key": { "status": 1 }, "unique": false }
                ],
                "estimated": false
              }
            },
            {
              "name": "products",
              "collStats": {
                "count": 1000,
                "avgObjSize": 8000,
                "dataSize": 8000768,
                "totalIndex": 2,
                "totalIndexSize": 8388608,
                "indexes": [
                  { "name": "_id_",  "size": 4194304, "key": { "_id": 1 }, "unique": true },
                  { "name": "sku_1", "size": 4194304, "key": { "sku": 1 }, "unique": true }
                ],
                "estimated": false
              }
            }
          ]
        }
      ]
    }
  ]
}
```

> `"estimated": true` at the cluster level means at least one collection fell back to BSON sampling — see [Understanding metrics](#understanding-metrics-vs-cloud-portal-figures).

---

### `POST /api/schema/extract`

**Request:**

```json
{
  "clusters": [
    {
      "name": "prod",
      "connectionString": "mongodb+srv://admin:secret@prod.example.mongodb.net",
      "databases": [
        {
          "name": "ecommerce",
          "collections": ["orders"]
        }
      ]
    }
  ]
}
```

**Response (`200 OK`):**

```json
{
  "dateCreate": "2026-05-19T12:00:00.000Z",
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
                  "_id":       { "type": "string",            "format": "objectid"   },
                  "userId":    { "type": "string",            "format": "objectid"   },
                  "status":    { "type": "string"                                    },
                  "total":     { "type": "number"                                    },
                  "discount":  { "type": ["number", "null"]                          },
                  "createdAt": { "type": "string",            "format": "date-time"  },
                  "items": {
                    "type": "array",
                    "items": {
                      "type": "object",
                      "properties": {
                        "productId": { "type": "string",  "format": "objectid" },
                        "name":      { "type": "string"                        },
                        "qty":       { "type": "integer"                       },
                        "price":     { "type": "number"                        }
                      }
                    }
                  },
                  "shippingAddress": {
                    "type": "object",
                    "properties": {
                      "street": { "type": "string" },
                      "city":   { "type": "string" },
                      "zip":    { "type": "string" }
                    }
                  }
                }
              },
              "exampleDocument": {
                "_id":    "000000000000000000000000",
                "userId": "000000000000000000000000",
                "status": "3f2a1c4e-7b88-4d1a-9c05-1e2f3a4b5c6d",
                "total":  0.0,
                "discount": null,
                "createdAt": "1970-01-01T00:00:00.000Z",
                "items": [
                  {
                    "productId": "000000000000000000000000",
                    "name":  "a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
                    "qty":   0,
                    "price": 0.0
                  }
                ],
                "shippingAddress": {
                  "street": "b2c3d4e5-6f7a-8b9c-0d1e-2f3a4b5c6d7e",
                  "city":   "c3d4e5f6-7a8b-9c0d-1e2f-3a4b5c6d7e8f",
                  "zip":    "d4e5f6a7-8b9c-0d1e-2f3a-4b5c6d7e8f9a"
                }
              }
            }
          ]
        }
      ]
    }
  ]
}
```

| Schema annotation | Meaning |
|---|---|
| `"type": ["number", "null"]` | Field was absent or null in at least one sampled document |
| `"format": "objectid"` | BSON ObjectId — 24-character hex string |
| `"format": "date-time"` | BSON Date — ISO-8601 string |
| `"format": "byte"` | BSON Binary — base64 string |

---

### `POST /api/schema/export`

**Request:** identical structure to `/api/schema/extract`.

**Response (`200 OK`):** binary ZIP file.

```
Content-Type: application/zip
Content-Disposition: attachment; filename="schema.zip"
```

**ZIP directory layout** (for the request example above):

```
prod/
└── ecommerce/
    └── orders/
        ├── schema.json    ← JSON Schema Draft 2020-12 (same content as jsonSchema above)
        └── sample.json    ← anonymised example document (same content as exampleDocument above)
```

Characters outside `[a-zA-Z0-9._-]` in cluster, database, and collection names are replaced with underscores in the ZIP entry paths.

---

## Understanding metrics vs cloud portal figures

<p align="center">
  <img src="images/wilted_leaf_mongodb.png" alt="Wilted MongoDB leaf: 'I used to be MongoDB... now I'm just another DocumentDB / CosmosDB knockoff'" width="480">
</p>

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

- Java 17, Spring Boot 4.0.5
- Spring Web (embedded Tomcat + REST); collector concurrency runs on a bounded thread pool (`collector.concurrency.thread-pool-size`, default 20) rather than virtual threads, for Java 17 compatibility
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
| `collector.concurrency.thread-pool-size` | `20` | Max databases/collections collected concurrently per cluster request |
| `collector.internal-databases[n]` | `admin,local,config` | System databases always skipped |
| `collector.cosmosdb.system-indexes[n]` | `DocumentDBDefaultIndex_1`, `DocumentDBDefaultIndex_2dsphere` | Indexes excluded from output |
| `collector.cosmosdb.phantom-collections[n]` | `lection` | Phantom collection names to skip |
| `logging.level.io.diegorocha.imitator.collector` | `INFO` | Change to `DEBUG` to trace the full collection flow |
