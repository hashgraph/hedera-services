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

package grpc;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.SessionContext;
import com.hedera.node.app.grpc.GrpcServiceBuilder;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.service.proto.java.ConsensusServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.NetworkServiceGrpc;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;
import io.helidon.grpc.server.GrpcRouting;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * This test generates and sends *real* GRPC transactions using the Google protobuf library and the
 * Helidon gRPC client. This is basically a smoke-test. Nothing is done with these transactions,
 * other than to make the call and verify that the call succeeds
 */
class GrpcTransactionTest extends GrpcTestBase {
    private IngestWorkflow ingestWorkflow;
    private QueryWorkflow queryWorkflow;

    private GrpcServiceClient consensusClient;
    private GrpcServiceClient cryptoClient;
    private GrpcServiceClient networkClient;

    @BeforeEach
    @Override
    void setUp() throws InterruptedException, UnknownHostException {
        super.setUp();

        final var channel =
                ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();

        consensusClient = GrpcServiceClient.create(
                channel,
                ClientServiceDescriptor.builder(ConsensusServiceGrpc.getServiceDescriptor())
                        .build());
        networkClient = GrpcServiceClient.create(
                channel,
                ClientServiceDescriptor.builder(NetworkServiceGrpc.getServiceDescriptor())
                        .build());
        cryptoClient = GrpcServiceClient.create(
                channel,
                ClientServiceDescriptor.builder(CryptoServiceGrpc.getServiceDescriptor())
                        .build());
    }

    @Override
    protected void configureRouting(GrpcRouting.Builder rb) {
        final var iw = new IngestWorkflow() {
            @Override
            public void submitTransaction(
                    @NonNull SessionContext session,
                    @NonNull ByteBuffer requestBuffer,
                    @NonNull ByteBuffer responseBuffer) {
                if (ingestWorkflow != null) {
                    ingestWorkflow.submitTransaction(session, requestBuffer, responseBuffer);
                }
            }
        };

        final var qw = new QueryWorkflow() {
            @Override
            public void handleQuery(
                    @NonNull SessionContext session,
                    @NonNull ByteBuffer requestBuffer,
                    @NonNull ByteBuffer responseBuffer) {
                if (queryWorkflow != null) {
                    queryWorkflow.handleQuery(session, requestBuffer, responseBuffer);
                }
            }
        };

        var serviceBuilder = new GrpcServiceBuilder("proto.ConsensusService", iw, qw)
                .query("getTopicInfo")
                .transaction("submitMessage");
        rb.register(serviceBuilder.build(metrics));

        serviceBuilder = new GrpcServiceBuilder("proto.CryptoService", iw, qw).query("getAccountRecords");
        rb.register(serviceBuilder.build(metrics));
    }

    @Test
    @DisplayName("Send a valid transaction and receive an OK response code")
    void sendGoodTransaction() {
        final var tx = createSubmitMessageTransaction(1001, "sendGoodTransaction");
        final TransactionResponse response = consensusClient.blockingUnary("submitMessage", tx);
        assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    }

    @Test
    @DisplayName("Send a transaction to a missing account and receive an error code")
    void sendBadTransaction() {
        ingestWorkflow = (session, requestBuffer, responseBuffer) -> {
            final var response = TransactionResponse.newBuilder()
                    .setNodeTransactionPrecheckCode(ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST)
                    .build();
            responseBuffer.put(response.toByteArray());
        };

        final var tx = createSubmitMessageTransaction(5000, "sendBadTransaction");
        final TransactionResponse response = consensusClient.blockingUnary("submitMessage", tx);
        assertEquals(ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST, response.getNodeTransactionPrecheckCode());
    }

    @Test
    @DisplayName("Send a valid transaction but the server fails unexpectedly")
    void sendTransactionAndServerFails() {
        ingestWorkflow = (session, requestBuffer, responseBuffer) -> {
            throw new RuntimeException("Something really unexpected and bad happened that should never" + " happen");
        };

        final var tx = createSubmitMessageTransaction(1001, "sendTransactionAndServerFails");
        final var e =
                assertThrows(StatusRuntimeException.class, () -> consensusClient.blockingUnary("submitMessage", tx));
        assertEquals(Status.UNKNOWN, e.getStatus());
    }

    @Test
    @DisplayName("Send a valid transaction to an unknown endpoint")
    void sendTransactionToUnknownEndpoint() {
        // I never registered "createTopic" with the server end. The client understands it (because
        // it is
        // part of the actual protobuf service definition), but since I didn't register it with the
        // server,
        // I can verify the behavior should a client call a method on a service that the server
        // doesn't know about

        final var tx = createCreateTopicTransaction("sendTransactionToUnknownEndpoint");
        final var e =
                assertThrows(StatusRuntimeException.class, () -> consensusClient.blockingUnary("createTopic", tx));
        assertEquals(Status.UNIMPLEMENTED.getCode(), e.getStatus().getCode());
    }

    @Test
    @DisplayName("Send a valid transaction to an unknown service")
    void sendTransactionToUnknownService() {
        // I never registered Network service endpoints with the server end. The client understands
        // it (because it is
        // part of the actual protobuf service definition), but since I didn't register it with the
        // server, I can
        // verify the behavior should a client call a service that the server doesn't know about
        final var tx = createUncheckedSubmitTransaction();
        final var e =
                assertThrows(StatusRuntimeException.class, () -> networkClient.blockingUnary("uncheckedSubmit", tx));
        assertEquals(Status.UNIMPLEMENTED.getCode(), e.getStatus().getCode());
    }

    @Test
    @DisplayName("Send a valid query and receive a good response")
    void sendGoodQuery() {
        queryWorkflow = (session, requestBuffer, responseBuffer) -> {
            final var topicId = TopicID.newBuilder().setTopicNum(1001).build();
            final var response = Response.newBuilder()
                    .setConsensusGetTopicInfo(ConsensusGetTopicInfoResponse.newBuilder()
                            .setTopicID(topicId)
                            .setTopicInfo(ConsensusTopicInfo.newBuilder()
                                    .setMemo("Topic memo")
                                    .setSequenceNumber(1234))
                            .build())
                    .build();
            responseBuffer.put(response.toByteArray());
        };

        final var q = createGetTopicInfoQuery(1001);
        final Response response = consensusClient.blockingUnary("getTopicInfo", q);
        assertTrue(response.hasConsensusGetTopicInfo());

        final var info = response.getConsensusGetTopicInfo();
        assertEquals(1001, info.getTopicID().getTopicNum());
        assertEquals("Topic memo", info.getTopicInfo().getMemo());
        assertEquals(1234, info.getTopicInfo().getSequenceNumber());
    }

    @Test
    @DisplayName("Send a valid query but the server fails unexpectedly")
    void sendQueryAndServerFails() {
        queryWorkflow = (session, requestBuffer, responseBuffer) -> {
            throw new RuntimeException("Something really unexpected and bad happened that should never" + " happen");
        };

        final var q = createGetTopicInfoQuery(1001);
        final var e =
                assertThrows(StatusRuntimeException.class, () -> consensusClient.blockingUnary("getTopicInfo", q));
        assertEquals(Status.UNKNOWN, e.getStatus());
    }

    @Test
    @DisplayName("Send a valid query to an unknown endpoint")
    void sendQueryToUnknownEndpoint() {
        // I never registered "getLiveHash" with the server end. The client understands it (because
        // it is
        // part of the actual protobuf service definition), but since I didn't register it with the
        // server,
        // I can verify the behavior should a client call a method on a service that the server
        // doesn't know about
        final var q = createGetLiveHashQuery();
        final var e = assertThrows(StatusRuntimeException.class, () -> cryptoClient.blockingUnary("getLiveHash", q));
        assertEquals(Status.UNIMPLEMENTED.getCode(), e.getStatus().getCode());
    }

    @Test
    @DisplayName("Send a valid query to an unknown service")
    void sendQueryToUnknownService() {
        // I never registered Network service endpoints with the server end. The client understands
        // it (because it is
        // part of the actual protobuf service definition), but since I didn't register it with the
        // server, I can
        // verify the behavior should a client call a service that the server doesn't know about
        final var q = createGetExecutionTimeQuery();
        final var e =
                assertThrows(StatusRuntimeException.class, () -> networkClient.blockingUnary("getExecutionTime", q));
        assertEquals(Status.UNIMPLEMENTED.getCode(), e.getStatus().getCode());
    }
}
