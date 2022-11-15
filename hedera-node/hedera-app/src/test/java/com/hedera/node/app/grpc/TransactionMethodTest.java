package com.hedera.node.app.grpc;

import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.swirlds.common.metrics.Metrics;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utils.TestUtils;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TransactionMethodTest {
    private final IngestWorkflow ingestWorkflow = (session, requestBuffer, responseBuffer) -> { };
    private final Metrics metrics = TestUtils.metrics();

    @Test
    void nullServiceNameThrows() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new TransactionMethod(null, "testMethod", ingestWorkflow, metrics));
    }

    @Test
    void nullMethodNameThrows() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new TransactionMethod("testService", null, ingestWorkflow, metrics));
    }

    @Test
    void nullWorkflowThrows() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new TransactionMethod("testService", "testMethod", null, metrics));
    }

    @Test
    void nullMetricsThrows() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new TransactionMethod("testService", "testMethod", ingestWorkflow, null));
    }

    @Test
    void handleDelegatesToWorkflow(@Mock final StreamObserver<ByteBuffer> streamObserver) {
        final var requestBuffer = ByteBuffer.allocate(100);
        final AtomicBoolean called = new AtomicBoolean(false);
        final IngestWorkflow w = (s, r1, r2) -> {
            assertEquals(requestBuffer, r1);
            called.set(true);
        };

        final var method = new TransactionMethod("testService", "testMethod", w, metrics);
        method.invoke(requestBuffer, streamObserver);
        assertTrue(called.get());
    }
}
