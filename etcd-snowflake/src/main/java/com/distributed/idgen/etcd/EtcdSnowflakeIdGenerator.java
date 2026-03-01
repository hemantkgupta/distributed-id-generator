package com.distributed.idgen.etcd;

import com.distributed.idgen.common.IdGenerationException;
import com.distributed.idgen.common.IdGenerator;
import com.distributed.idgen.snowflake.SnowflakeIdGenerator;
import io.etcd.jetcd.Client;

/**
 * ETCD-backed Snowflake 64-bit ID generator.
 * 
 * Automates the hardest part of Snowflake IDs: guaranteeing that no two
 * instances accidentally share the same `datacenterId` and `workerId`
 * hardware coordinates.
 */
public class EtcdSnowflakeIdGenerator implements IdGenerator<Long>, AutoCloseable {

    private final EtcdNodeIdAssigner assigner;
    private final SnowflakeIdGenerator underlyingGenerator;
    private final int totalNodeId;

    /**
     * Creates an ETCD backed snowflake generator.
     * @param client The configured Jetcd ETCD client.
     * @param basePath The prefix path for keys, e.g. "/services/idgen/nodes/"
     */
    public EtcdSnowflakeIdGenerator(Client client, String basePath) {
        // max 1024 unique node combinations in Snowflake (10-bit)
        this.assigner = new EtcdNodeIdAssigner(client, basePath, 1024);
        
        // Claim the ID and start the background heartbeat with a 10s TTL
        this.totalNodeId = assigner.assignWorkerId(10);
        
        // Split the 10-bit assigned ID into 5-bit datacenter and 5-bit worker
        long datacenterId = (totalNodeId >> 5) & 31;
        long workerId = totalNodeId & 31;

        this.underlyingGenerator = new SnowflakeIdGenerator(datacenterId, workerId);
    }

    @Override
    public Long generate() {
        try {
            return underlyingGenerator.generate();
        } catch (Exception e) {
            throw new IdGenerationException("Failed to generate ETCD-backed Snowflake ID", e);
        }
    }

    @Override
    public String strategyName() {
        return "ETCD Snowflake (64-bit, Dynamic Coordinates)";
    }
    
    public int getAssignedNodeId() {
        return totalNodeId;
    }

    @Override
    public void close() {
        assigner.close();
    }
}
