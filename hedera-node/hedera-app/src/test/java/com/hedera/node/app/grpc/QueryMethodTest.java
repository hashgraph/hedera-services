/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.metrics.Metrics;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import utils.TestUtils;

@ExtendWith(MockitoExtension.class)
class QueryMethodTest {
    private final QueryWorkflow queryWorkflow = (session, requestBuffer, responseBuffer) -> {};
    private final Metrics metrics = TestUtils.metrics();

    @Test
    void nullServiceNameThrows() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new QueryMethod(null, "testMethod", queryWorkflow, metrics));
    }

    @Test
    void nullMethodNameThrows() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new QueryMethod("testService", null, queryWorkflow, metrics));
    }

    @Test
    void nullWorkflowThrows() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new QueryMethod("testService", "testMethod", null, metrics));
    }

    @Test
    void nullMetricsThrows() {
        //noinspection ConstantConditions
        assertThrows(
                NullPointerException.class, () -> new QueryMethod("testService", "testMethod", queryWorkflow, null));
    }

    @Test
    void handleDelegatesToWorkflow(@Mock final StreamObserver<BufferedData> streamObserver) {
        final var requestBuffer = BufferedData.allocate(100);
        final AtomicBoolean called = new AtomicBoolean(false);
        final QueryWorkflow w = (s, r1, r2) -> {
            assertEquals(PbjConverter.asBytes(requestBuffer), PbjConverter.asBytes(r1));
            called.set(true);
        };

        final var method = new QueryMethod("testService", "testMethod", w, metrics);
        method.invoke(requestBuffer, streamObserver);
        assertTrue(called.get());
    }

    @Test
    void unexpectedExceptionFromHandler(@Mock final StreamObserver<BufferedData> streamObserver) {
        final var requestBuffer = BufferedData.allocate(100);
        final QueryWorkflow w = (s, r1, r2) -> {
            throw new RuntimeException("Unexpected!");
        };
        final var method = new QueryMethod("testService", "testMethod", w, metrics);
        method.invoke(requestBuffer, streamObserver);
        Mockito.verify(streamObserver).onError(Mockito.any());
    }
}
