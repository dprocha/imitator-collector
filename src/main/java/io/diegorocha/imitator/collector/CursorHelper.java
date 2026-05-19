package io.diegorocha.imitator.collector;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Fully exhausts a server-side cursor opened by a {@code runCommand()} call.
 * <p>Reads {@code cursor.firstBatch} from the initial command result and loops
 * {@code getMore} commands until {@code cursorId == 0}. This avoids the MongoDB Java Driver
 * 3.12.9 internal skip-count arithmetic bug in {@code ListCollectionsIterableImpl} and
 * {@code ListIndexesIterableImpl}, which throws {@code "Range [X, Y) out of bounds"} against
 * CosmosDB 3.2 cursor responses.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
class CursorHelper {

    private CursorHelper() {}

    /**
     * Reads all documents from the cursor described by {@code cmdResult}.
     * Works for any command that returns a {@code cursor.firstBatch} response.
     *
     * @param db        the database against which to issue {@code getMore} commands
     * @param cmdResult the raw response document from the initiating command
     * @param log       logger to receive warnings on {@code getMore} failures
     * @return all documents across all batches; never null
     */
    static List<Document> exhaustCursor(MongoDatabase db, Document cmdResult, Logger log) {
        List<Document> results = new ArrayList<>();

        Document cursorDoc = cmdResult.get("cursor", Document.class);
        if (cursorDoc == null) return results;

        @SuppressWarnings("unchecked")
        List<Document> firstBatch = (List<Document>) cursorDoc.get("firstBatch");
        if (firstBatch != null) results.addAll(firstBatch);

        long cursorId = toLong(cursorDoc.get("id"));
        String ns = cursorDoc.getString("ns");
        String collectionName = ns != null && ns.contains(".") ? ns.substring(ns.indexOf('.') + 1) : "";

        int batchCount = 1;
        while (cursorId != 0) {
            log.debug("Fetching cursor batch {} for '{}', cursorId={}", ++batchCount, ns, cursorId);
            try {
                Document getMore = db.runCommand(new Document("getMore", cursorId)
                        .append("collection", collectionName));
                Document nextCursor = getMore.get("cursor", Document.class);
                if (nextCursor == null) break;

                @SuppressWarnings("unchecked")
                List<Document> nextBatch = (List<Document>) nextCursor.get("nextBatch");
                if (nextBatch != null) results.addAll(nextBatch);

                cursorId = toLong(nextCursor.get("id"));
            } catch (MongoCommandException e) {
                log.warn("getMore failed for cursor {} on '{}': {} — stopping iteration",
                        cursorId, ns, e.getErrorMessage());
                break;
            }
        }

        log.debug("Cursor '{}' exhausted — {} total document(s) across {} batch(es)", ns, results.size(), batchCount);
        return results;
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return 0L;
    }
}
