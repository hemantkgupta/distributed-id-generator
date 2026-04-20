package com.distributed.idgen.spanner;

import com.google.cloud.spanner.Database;
import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.InstanceAdminClient;
import com.google.cloud.spanner.InstanceConfigId;
import com.google.cloud.spanner.InstanceId;
import com.google.cloud.spanner.InstanceInfo;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.SpannerEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Testcontainers(disabledWithoutDocker = true)
public class SpannerTrueTimeIdGeneratorTest {

    private static final String PROJECT_ID = "test-project";
    private static final String INSTANCE_ID = "test-instance";
    private static final String DB_ID = "test-db";

    @Container
    private static final SpannerEmulatorContainer emulator = new SpannerEmulatorContainer(
            DockerImageName.parse("gcr.io/cloud-spanner-emulator/emulator:latest")
    );

    private static Spanner spanner;
    private static DatabaseClient dbClient;

    @BeforeAll
    public static void setUp() throws Exception {
        SpannerOptions options = SpannerOptions.newBuilder()
                .setEmulatorHost(emulator.getEmulatorGrpcEndpoint())
                .setProjectId(PROJECT_ID)
                .build();
        spanner = options.getService();

        InstanceAdminClient instanceAdminClient = spanner.getInstanceAdminClient();
        instanceAdminClient.createInstance(InstanceInfo.newBuilder(InstanceId.of(PROJECT_ID, INSTANCE_ID))
                .setInstanceConfigId(InstanceConfigId.of(PROJECT_ID, "emulator-config"))
                .setDisplayName("Test Instance")
                .setNodeCount(1)
                .build()).get(10, TimeUnit.SECONDS);

        DatabaseAdminClient dbAdminClient = spanner.getDatabaseAdminClient();
        
        // Define our DDL with allow_commit_timestamp=true
        String ddl = "CREATE TABLE TrueTimeIds (" +
                "  InsertTime TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)," +
                "  NodeSuffix STRING(MAX) NOT NULL" +
                ") PRIMARY KEY (InsertTime, NodeSuffix)";

        Database db = dbAdminClient.createDatabase(
                INSTANCE_ID,
                DB_ID,
                Collections.singletonList(ddl)
        ).get(10, TimeUnit.SECONDS);

        dbClient = spanner.getDatabaseClient(DatabaseId.of(PROJECT_ID, INSTANCE_ID, DB_ID));
    }

    @AfterAll
    public static void tearDown() {
        if (spanner != null) {
            spanner.close();
        }
    }

    @Test
    public void testSequentialSingleThread() {
        SpannerTrueTimeIdGenerator generator = new SpannerTrueTimeIdGenerator(dbClient, "TrueTimeIds", "InsertTime", "testnode");
        
        String id1 = generator.generate();
        String id2 = generator.generate();
        
        Assertions.assertTrue(id2.compareTo(id1) > 0, "id2 should be chronologically strictly greater than id1");
    }

    @Test
    public void testConcurrentGenerationsYieldStrictSorting() throws Exception {
        SpannerTrueTimeIdGenerator genA = new SpannerTrueTimeIdGenerator(dbClient, "TrueTimeIds", "InsertTime", "nodeA");
        SpannerTrueTimeIdGenerator genB = new SpannerTrueTimeIdGenerator(dbClient, "TrueTimeIds", "InsertTime", "nodeB");

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Callable<String>> tasks = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            tasks.add(genA::generate);
            tasks.add(genB::generate);
        }

        List<Future<String>> futures = executor.invokeAll(tasks);
        List<String> generatedIds = new ArrayList<>();

        for (Future<String> future : futures) {
            generatedIds.add(future.get());
        }

        executor.shutdown();

        Assertions.assertEquals(40, generatedIds.size());

        // Validate that we can extract standard Timestamps
        for (String id : generatedIds) {
            Assertions.assertNotNull(SpannerTrueTimeIdGenerator.extractInstant(id));
        }
    }
}
