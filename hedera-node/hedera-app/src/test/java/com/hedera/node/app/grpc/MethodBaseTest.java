/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.node.app.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.swirlds.common.metrics.Metrics;
import io.grpc.stub.StreamObserver;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utils.TestUtils;

@ExtendWith(MockitoExtension.class)
class MethodBaseTest {
    private final Metrics metrics = TestUtils.metrics();

    @Test
    void handleDelegatesToWorkflow(@Mock final StreamObserver<ByteBuffer> streamObserver) {
        final var requestBuffer = ByteBuffer.allocate(100);
        final AtomicBoolean called = new AtomicBoolean(false);
        final QueryWorkflow w =
                (s, r1, r2) -> {
                    assertEquals(requestBuffer, r1);
                    called.set(true);
                };

        final var method = new QueryMethod("testService", "testMethod", w, metrics);
        method.invoke(requestBuffer, streamObserver);
        assertTrue(called.get());
    }
}
