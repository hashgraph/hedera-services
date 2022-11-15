package com.hedera.node.app.grpc;

import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.swirlds.common.metrics.Metrics;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utils.TestUtils;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MethodBaseTest {
    private final Metrics metrics = TestUtils.metrics();

    @Test
    void handleDelegatesToWorkflow(@Mock final StreamObserver<ByteBuffer> streamObserver) {
        final var requestBuffer = ByteBuffer.allocate(100);
        final AtomicBoolean called = new AtomicBoolean(false);
        final QueryWorkflow w = (s, r1, r2) -> {
            assertEquals(requestBuffer, r1);
            called.set(true);
        };

        final var method = new QueryMethod("testService", "testMethod", w, metrics);
        method.invoke(requestBuffer, streamObserver);
        assertTrue(called.get());
    }
}
