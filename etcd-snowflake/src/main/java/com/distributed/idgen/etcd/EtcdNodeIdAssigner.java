package com.distributed.idgen.etcd;

import com.distributed.idgen.common.IdGenerationException;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.kv.TxnResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.grpc.stub.StreamObserver;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Assigns a unique 10-bit worker ID (0-1023) to this JVM by negotiating
 * an exclusive claim in ETCD using short-lived leases.
 */
public class EtcdNodeIdAssigner implements AutoCloseable {

    private final Client client;
    private final String basePath;
    private final String instanceId;
    private final int maxId;
    
    private long leaseId;
    private int assignedId = -1;

    public EtcdNodeIdAssigner(Client client, String basePath, int maxId) {
        this.client = client;
        this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";
        this.maxId = maxId;
        this.instanceId = UUID.randomUUID().toString();
    }

    /**
     * Attempts to acquire a unique worker ID from 0 to maxId-1.
     * @param ttlSeconds TTL for the lease in ETCD.
     * @return The strictly assigned ID.
     * @throws IdGenerationException if no IDs are available or ETCD is unreachable.
     */
    public int assignWorkerId(long ttlSeconds) {
        try {
            Lease leaseClient = client.getLeaseClient();
            KV kvClient = client.getKVClient();

            // Grant a new lease
            this.leaseId = leaseClient.grant(ttlSeconds).get().getID();

            // Setup keep alive
            leaseClient.keepAlive(leaseId, new StreamObserver<LeaseKeepAliveResponse>() {
                @Override
                public void onNext(LeaseKeepAliveResponse value) {
                    // Lease successfully renewed
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("ETCD lease keep-alive failed. Node ID may be lost: " + t.getMessage());
                }

                @Override
                public void onCompleted() {
                    System.err.println("ETCD lease keep-alive completed unexpectedly.");
                }
            });

            // Loop and try to claim an ID
            for (int i = 0; i < maxId; i++) {
                ByteSequence key = ByteSequence.from((basePath + i).getBytes(StandardCharsets.UTF_8));
                ByteSequence value = ByteSequence.from(instanceId.getBytes(StandardCharsets.UTF_8));

                // Transaction: If key does not exist, put value bound to lease
                TxnResponse response = kvClient.txn()
                        .If(new Cmp(key, Cmp.Op.EQUAL, CmpTarget.version(0)))
                        .Then(Op.put(key, value, io.etcd.jetcd.options.PutOption.newBuilder().withLeaseId(leaseId).build()))
                        .commit()
                        .get();

                if (response.isSucceeded()) {
                    this.assignedId = i;
                    return i;
                }
            }

            throw new IdGenerationException("No available ETCD Snowflake IDs out of max " + maxId);

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new IdGenerationException("Failed to acquire worker ID from ETCD", e);
        }
    }

    public int getAssignedId() {
        if (assignedId == -1) {
            throw new IllegalStateException("Worker ID has not been assigned yet.");
        }
        return assignedId;
    }

    @Override
    public void close() {
        if (leaseId != 0) {
            try {
                client.getLeaseClient().revoke(leaseId).get();
            } catch (Exception e) {
                // Ignore on shutdown
            }
        }
    }
}
