/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.test.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This test verifies gRPC calls made by users over the network. Since our gRPC code deals with bytes, and
 * all serialization and deserialization of protobuf objects happens in the workflows, these tests
 * can work on simple primitives such as Strings, making the tests easier to understand without
 * missing any possible cases.
 */
class GrpcUserQueryTest extends GrpcTestBase {
    private static final String SERVICE = "proto.TestService";
    private static final String METHOD = "testQuery";
    private static final String GOOD_RESPONSE = "All Good";
    private static final byte[] GOOD_RESPONSE_BYTES = GOOD_RESPONSE.getBytes(StandardCharsets.UTF_8);

    private static final QueryWorkflow GOOD_QUERY = (req, res) -> res.writeBytes(GOOD_RESPONSE_BYTES);
    private static final IngestWorkflow UNIMPLEMENTED_INGEST = (r, r2) -> fail("The Ingest should not be called");
    private static final QueryWorkflow UNIMPLEMENTED_QUERY =
            (r, r2) -> fail("The operator query workflow should not be called");

    private void setUp(@NonNull final QueryWorkflow query) {
        registerQuery(METHOD, UNIMPLEMENTED_INGEST, query, UNIMPLEMENTED_QUERY);
        startServer(false);
    }

    @Test
    @DisplayName("Call a query on a gRPC service endpoint that succeeds")
    void sendGoodQuery() {
        // Given a server with a service endpoint and a QueryWorkflow that returns a good response
        setUp(GOOD_QUERY);

        // When we call the service
        final var response = send(SERVICE, METHOD, "A Query");

        // Then the response is good, and no exception is thrown.
        assertEquals(GOOD_RESPONSE, response);
    }

    @Test
    @DisplayName("A query throwing a RuntimeException returns the UNKNOWN status code")
    void queryThrowingRuntimeExceptionReturnsUNKNOWNError() {
        // Given a server where the service will throw a RuntimeException
        setUp((req, res) -> {
            throw new RuntimeException("Failing with RuntimeException");
        });

        // When we invoke that service
        final var e = assertThrows(StatusRuntimeException.class, () -> send(SERVICE, METHOD, "A Query"));

        // Then the Status code will be UNKNOWN.
        assertEquals(Status.UNKNOWN, e.getStatus());
    }

    @Test
    @DisplayName("A query throwing an Error returns the UNKNOWN status code")
    @Disabled("This test needs to be investigated")
    void queryThrowingErrorReturnsUNKNOWNError() {
        // Given a server where the service will throw an Error
        setUp((req, res) -> {
            throw new Error("Whoops!");
        });

        // When we invoke that service
        final var e = assertThrows(StatusRuntimeException.class, () -> send(SERVICE, METHOD, "A Query"));

        // Then the Status code will be UNKNOWN.
        assertEquals(Status.UNKNOWN, e.getStatus());
    }

    public static Stream<Arguments> badStatusCodes() {
        return Arrays.stream(Status.Code.values()).filter(c -> c != Code.OK).map(Arguments::of);
    }

    @ParameterizedTest(name = "{0} Should Fail")
    @MethodSource("badStatusCodes")
    @DisplayName("Explicitly thrown StatusRuntimeException passes the code through to the response")
    void explicitlyThrowStatusRuntimeException(@NonNull final Status.Code code) {
        // Given a server where the service will throw a specific StatusRuntimeException
        setUp((req, res) -> {
            throw new StatusRuntimeException(code.toStatus());
        });

        // When we invoke that service
        final var e = assertThrows(StatusRuntimeException.class, () -> send(SERVICE, METHOD, "A Query"));

        // Then the Status code will match the exception
        assertEquals(code.toStatus(), e.getStatus());
    }

    @Test
    @DisplayName("Send a valid query to an unknown endpoint and get back UNIMPLEMENTED")
    void sendQueryToUnknownEndpoint() {
        // Given a client that knows about a method that DOES NOT EXIST on the server
        setUp(GOOD_QUERY);

        // When I call the service but with an unknown method
        final var e = assertThrows(StatusRuntimeException.class, () -> send(SERVICE, "unknown", "query"));

        // Then the resulting status code is UNIMPLEMENTED
        assertEquals(Status.UNIMPLEMENTED.getCode(), e.getStatus().getCode());
    }

    @Test
    @DisplayName("Send a valid query to an unknown service")
    void sendQueryToUnknownService() {
        // Given a client that knows about a service that DOES NOT exist on the server
        setUp(GOOD_QUERY);

        // When I call the unknown service
        final var e = assertThrows(StatusRuntimeException.class, () -> send("UnknownService", METHOD, "query"));

        // Then the resulting status code is UNIMPLEMENTED
        assertEquals(Status.UNIMPLEMENTED.getCode(), e.getStatus().getCode());
    }

    // Interestingly, I thought it should return INVALID_ARGUMENT, and I attempted to update the
    // NoopMarshaller to return INVALID_ARGUMENT by throwing a StatusRuntimeException. But the gRPC library we are
    // using DOES NOT special case for StatusRuntimeException thrown in the marshaller, and always returns
    // UNKNOWN to the client. So there is really no other response code possible for this case.
    @Test
    @DisplayName("Sending way too many bytes leads to UNKNOWN")
    void sendTooMuchData() {
        // Given a service
        setUp(GOOD_QUERY);

        // When I call a method on the service and pass too many bytes
        final var payload = randomString(1024 * 10);
        final var e = assertThrows(StatusRuntimeException.class, () -> send(SERVICE, METHOD, payload));

        // Then the resulting status code is UNKNOWN
        assertEquals(Status.UNKNOWN.getCode(), e.getStatus().getCode());
    }
}
