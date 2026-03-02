package com.distributed.idgen.etcd;

import io.etcd.jetcd.Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("EtcdSnowflakeIdGenerator integration")
class EtcdSnowflakeIdGeneratorIntegrationTest {

    @Container
    static final GenericContainer<?> ETCD = new GenericContainer<>(DockerImageName.parse("quay.io/coreos/etcd:v3.5.15"))
            .withCommand(
                    "/usr/local/bin/etcd",
                    "--listen-client-urls=http://0.0.0.0:2379",
                    "--advertise-client-urls=http://127.0.0.1:2379")
            .withExposedPorts(2379)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));

    private Client client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("Should assign distinct node IDs and generate unique IDs using a real etcd server")
    void shouldAssignDistinctNodeIdsAndGenerateUniqueIds() {
        client = Client.builder()
                .endpoints("http://" + ETCD.getHost() + ":" + ETCD.getMappedPort(2379))
                .build();

        String basePath = "/integration/" + UUID.randomUUID() + "/";
        try (EtcdSnowflakeIdGenerator first = new EtcdSnowflakeIdGenerator(client, basePath);
             EtcdSnowflakeIdGenerator second = new EtcdSnowflakeIdGenerator(client, basePath)) {

            assertThat(first.getAssignedNodeId()).isEqualTo(0);
            assertThat(second.getAssignedNodeId()).isEqualTo(1);

            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 250; i++) {
                ids.add(first.generate());
                ids.add(second.generate());
            }

            assertThat(ids).hasSize(500);
        }
    }

    @Test
    @DisplayName("Should allow a revoked node ID to be claimed again after close")
    void shouldReuseRevokedNodeIdAfterClose() {
        client = Client.builder()
                .endpoints("http://" + ETCD.getHost() + ":" + ETCD.getMappedPort(2379))
                .build();

        String basePath = "/integration/reuse/" + UUID.randomUUID() + "/";

        int releasedId;
        try (EtcdSnowflakeIdGenerator generator = new EtcdSnowflakeIdGenerator(client, basePath)) {
            releasedId = generator.getAssignedNodeId();
            assertThat(releasedId).isEqualTo(0);
        }

        try (EtcdSnowflakeIdGenerator generator = new EtcdSnowflakeIdGenerator(client, basePath)) {
            assertThat(generator.getAssignedNodeId()).isEqualTo(releasedId);
        }
    }
}
