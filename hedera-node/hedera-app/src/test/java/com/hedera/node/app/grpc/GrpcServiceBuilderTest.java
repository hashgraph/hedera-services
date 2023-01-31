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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.swirlds.common.metrics.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import utils.TestUtils;

class GrpcServiceBuilderTest {
    private final QueryWorkflow queryWorkflow = (session, requestBuffer, responseBuffer) -> {};
    private final IngestWorkflow ingestWorkflow = (session, requestBuffer, responseBuffer) -> {};

    private GrpcServiceBuilder builder;
    private final Metrics metrics = TestUtils.metrics();

    @BeforeEach
    void setUp() {
        builder = new GrpcServiceBuilder("TestService", ingestWorkflow, queryWorkflow);
    }

    @Test
    @DisplayName("The ingestWorkflow cannot be null")
    void ingestWorkflowIsNull() {
        //noinspection ConstantConditions
        assertThrows(
                NullPointerException.class,
                () -> new GrpcServiceBuilder("TestService", null, queryWorkflow));
    }

    @Test
    @DisplayName("The queryWorkflow cannot be null")
    void queryWorkflowIsNull() {
        //noinspection ConstantConditions
        assertThrows(
                NullPointerException.class,
                () -> new GrpcServiceBuilder("TestService", ingestWorkflow, null));
    }

    @Test
    @DisplayName("The 'service' cannot be null")
    void serviceIsNull() {
        //noinspection ConstantConditions
        assertThrows(
                NullPointerException.class,
                () -> new GrpcServiceBuilder(null, ingestWorkflow, queryWorkflow));
    }

    @ParameterizedTest()
    @ValueSource(strings = {"", " ", "\t", "\n", "\r", "\r\n", "  \n  "})
    @DisplayName("The 'service' cannot be blank")
    void serviceIsBlank(final String value) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GrpcServiceBuilder(value, ingestWorkflow, queryWorkflow));
    }

    @Test
    @DisplayName("Cannot call 'transaction' with null")
    void transactionIsNull() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> builder.transaction(null));
    }

    @ParameterizedTest()
    @ValueSource(strings = {"", " ", "\t", "\n", "\r", "\r\n", "  \n  "})
    @DisplayName("Cannot call 'transaction' with blank")
    void transactionIsBlank(final String value) {
        assertThrows(IllegalArgumentException.class, () -> builder.transaction(value));
    }

    @Test
    @DisplayName("Cannot call 'query' with null")
    void queryIsNull() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> builder.query(null));
    }

    @ParameterizedTest()
    @ValueSource(strings = {"", " ", "\t", "\n", "\r", "\r\n", "  \n  "})
    @DisplayName("Cannot call 'query' with blank")
    void queryIsBlank(final String value) {
        assertThrows(IllegalArgumentException.class, () -> builder.query(value));
    }

    /**
     * A builder with no transactions and queries still creates and returns a {@link
     * io.helidon.grpc.server.ServiceDescriptor}.
     */
    @Test
    @DisplayName("The build method will return a ServiceDescriptor")
    void serviceDescriptorIsNotNullOnNoopBuilder() {
        assertNotNull(builder.build(metrics));
    }

    /**
     * A {@link GrpcServiceBuilder} may define transactions which will be created on the {@link
     * io.helidon.grpc.server.ServiceDescriptor}.
     */
    @Test
    @DisplayName(
            "The built ServiceDescriptor includes a method with the name of the defined"
                    + " transaction")
    void singleTransaction() {
        final var sd = builder.transaction("txA").build(metrics);

        assertNotNull(sd.method("txA"));
    }

    /**
     * A {@link GrpcServiceBuilder} may define transactions which will be created on the {@link
     * io.helidon.grpc.server.ServiceDescriptor}.
     */
    @Test
    @DisplayName("The built ServiceDescriptor includes all methods defined by the builder")
    void multipleTransactionsAndQueries() {
        final var sd =
                builder.transaction("txA")
                        .transaction("txB")
                        .query("qA")
                        .query("qB")
                        .transaction("txC")
                        .query("qC")
                        .transaction("txD")
                        .build(metrics);

        assertNotNull(sd.method("txA"));
        assertNotNull(sd.method("txB"));
        assertNotNull(sd.method("txC"));
        assertNotNull(sd.method("txD"));
        assertNotNull(sd.method("qA"));
        assertNotNull(sd.method("qB"));
        assertNotNull(sd.method("qC"));
    }
}
