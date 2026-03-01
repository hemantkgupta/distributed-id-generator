package com.distributed.idgen.etcd;

import com.distributed.idgen.common.IdGenerationException;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Txn;
import io.etcd.jetcd.kv.TxnResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class EtcdSnowflakeIdGeneratorTest {

    private Client client;
    private Lease lease;
    private KV kv;

    @BeforeEach
    void setUp() {
        client = mock(Client.class);
        lease = mock(Lease.class);
        kv = mock(KV.class);

        when(client.getLeaseClient()).thenReturn(lease);
        when(client.getKVClient()).thenReturn(kv);
    }

    @Test
    void testNodeAssignmentAndGeneration() throws Exception {
        // Mock Lease Grant
        LeaseGrantResponse leaseGrantResponse = mock(LeaseGrantResponse.class);
        when(leaseGrantResponse.getID()).thenReturn(12345L);
        when(lease.grant(anyLong())).thenReturn(CompletableFuture.completedFuture(leaseGrantResponse));

        // Mock Txn
        Txn txn = mock(Txn.class);
        when(kv.txn()).thenReturn(txn);
        when(txn.If(any(io.etcd.jetcd.op.Cmp.class))).thenReturn(txn);
        when(txn.Then(any(io.etcd.jetcd.op.Op.class))).thenReturn(txn);
        
        // Mock successful transaction on first try (assigning ID 0)
        TxnResponse txnResponseSuccess = mock(TxnResponse.class);
        when(txnResponseSuccess.isSucceeded()).thenReturn(true);
        when(txn.commit()).thenReturn(CompletableFuture.completedFuture(txnResponseSuccess));

        // Create the generator
        try (EtcdSnowflakeIdGenerator generator = new EtcdSnowflakeIdGenerator(client, "/test/node-assign/")) {
            // Validate node ID assignment
            assertThat(generator.getAssignedNodeId()).isEqualTo(0);

            // Generate an ID (datacenter=0, worker=0 for ID 0)
            long id1 = generator.generate();
            long id2 = generator.generate();

            assertThat(id1).isGreaterThan(0);
            assertThat(id2).isGreaterThan(id1);
            
            // Verify keepAlive was called
            verify(lease).keepAlive(eq(12345L), any(StreamObserver.class));
        }

        // Verify revoke is called on close
        verify(lease).revoke(12345L);
    }
    
    @Test
    void testAssignmentFailsWhenAllIdsTaken() throws Exception {
        // Mock Lease Grant
        LeaseGrantResponse leaseGrantResponse = mock(LeaseGrantResponse.class);
        when(leaseGrantResponse.getID()).thenReturn(9999L);
        when(lease.grant(anyLong())).thenReturn(CompletableFuture.completedFuture(leaseGrantResponse));

        // Mock Txn to ALWAYS fail (simulating 1024 taken IDs)
        Txn txn = mock(Txn.class);
        when(kv.txn()).thenReturn(txn);
        when(txn.If(any(io.etcd.jetcd.op.Cmp.class))).thenReturn(txn);
        when(txn.Then(any(io.etcd.jetcd.op.Op.class))).thenReturn(txn);
        
        TxnResponse txnResponseFail = mock(TxnResponse.class);
        when(txnResponseFail.isSucceeded()).thenReturn(false);
        when(txn.commit()).thenReturn(CompletableFuture.completedFuture(txnResponseFail));

        IdGenerationException exception = assertThrows(IdGenerationException.class, () -> {
            new EtcdSnowflakeIdGenerator(client, "/test/node-assign/");
        });

        assertThat(exception.getMessage()).contains("No available ETCD Snowflake IDs");
        
        // Assert it tried 1024 times
        verify(txn, times(1024)).commit();
    }
}
