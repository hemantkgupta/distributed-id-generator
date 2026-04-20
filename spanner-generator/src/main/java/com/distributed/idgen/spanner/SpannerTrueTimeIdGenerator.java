package com.distributed.idgen.spanner;

import com.distributed.idgen.common.IdGenerationException;
import com.distributed.idgen.common.IdGenerator;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Value;
import com.google.common.annotations.VisibleForTesting;

import java.time.Instant;
import java.util.UUID;

/**
 * Generates globally strictly-chronological unique IDs using Google Cloud Spanner's 
 * TrueTime architecture.
 *
 * It generates IDs by executing a blind write to an append-only table using the
 * built-in {@code PENDING_COMMIT_TIMESTAMP()} Spanner feature to get an authoritative
 * point-in-time that is perfectly ordered and reconciled against global atomic clocks
 * and GPS receivers.
 */
public class SpannerTrueTimeIdGenerator implements IdGenerator<String> {

    private final DatabaseClient dbClient;
    private final String appendingTableName;
    private final String timestampColumnName;
    private final String uniqueNodeId;

    /**
     * Initializes the Spanner generator.
     * Table structure must match something like:
     * {@code CREATE TABLE TrueTimeIds ( InsertTime TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true) ) PRIMARY KEY (InsertTime)}
     *
     * @param dbClient            The configured Google Cloud Spanner client.
     * @param appendingTableName  The name of the Spanner table.
     * @param timestampColumnName The name of the TIMESTAMP column enabled for PENDING_COMMIT_TIMESTAMP.
     * @param uniqueNodeId        A short string appended to identical timestamps to avoid primary key collisions 
     *                            if multiple nodes commit on the exact same microsecond.
     */
    public SpannerTrueTimeIdGenerator(DatabaseClient dbClient, String appendingTableName, String timestampColumnName, String uniqueNodeId) {
        this.dbClient = dbClient;
        this.appendingTableName = appendingTableName;
        this.timestampColumnName = timestampColumnName;
        this.uniqueNodeId = uniqueNodeId;
    }

    /**
     * Helper constructor that auto-generates a UUID suffix for the node component.
     */
    public SpannerTrueTimeIdGenerator(DatabaseClient dbClient, String appendingTableName, String timestampColumnName) {
        this(dbClient, appendingTableName, timestampColumnName, UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public String generate() {
        try {
            // Write to the database requesting the commit timestamp
            Timestamp trueTimeTimestamp = dbClient.writeAtLeastOnce(java.util.Collections.singleton(
                    Mutation.newInsertBuilder(appendingTableName)
                            .set(timestampColumnName).to(Value.COMMIT_TIMESTAMP)
                            // We need to write a string suffix into the primary key row too (if it's composite), 
                            // or just rely on Spanner treating the commit timestamp as the PK if frequency is low enough.
                            // Assuming composite primary key: (InsertTime, NodeSuffix) for high throughput.
                            .set("NodeSuffix").to(uniqueNodeId)
                            .build()
            ));

            // Format into string: 2024-10-18T10:15:30.123456000Z-<nodeSuffix>
            return formatId(trueTimeTimestamp, uniqueNodeId);

        } catch (Exception e) {
            throw new IdGenerationException("Failed to acquire TrueTime Commit Timestamp from Spanner", e);
        }
    }

    @VisibleForTesting
    static String formatId(Timestamp timestamp, String nodeSuffix) {
        // Convert to standard RFC 3339 string layout natively via Spanner's Timestamp
        return timestamp.toString() + "-" + nodeSuffix;
    }

    /**
     * Parses the Instant back out from a generated string ID.
     */
    public static Instant extractInstant(String generatedId) {
        int delim = generatedId.lastIndexOf("-");
        if (delim == -1) {
            throw new IllegalArgumentException("Invalid Spanner TrueTime ID format");
        }
        String timestampStr = generatedId.substring(0, delim);
        return Timestamp.parseTimestamp(timestampStr).toDate().toInstant();
    }

    @Override
    public String strategyName() {
        return "Google Spanner (TrueTime Chronological String)";
    }
}
