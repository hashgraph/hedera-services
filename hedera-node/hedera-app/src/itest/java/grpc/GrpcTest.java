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
package grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.node.app.grpc.GrpcServiceBuilder;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.ingest.IngestWorkflowImpl;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflowImpl;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.service.proto.java.ConsensusServiceGrpc;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import io.grpc.ManagedChannelBuilder;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrpcTest {

    private GrpcServer grpcServer;
    private IngestWorkflow ingestWorkflow = new IngestWorkflowImpl();
    private QueryWorkflow queryWorkflow = new QueryWorkflowImpl();

    @BeforeEach
    void setUp() {
        final var metrics =
                new DefaultMetrics(
                        Executors.newSingleThreadScheduledExecutor(), new DefaultMetricsFactory());

        grpcServer =
                GrpcServer.create(
                        GrpcServerConfiguration.builder().port(0).build(),
                        GrpcRouting.builder()
                                .register(
                                        new GrpcServiceBuilder(
                                                        "proto.ConsensusService",
                                                        ingestWorkflow,
                                                        queryWorkflow)
                                                .transaction("createTopic")
                                                .transaction("updateTopic")
                                                .transaction("deleteTopic")
                                                .query("getTopicInfo")
                                                .transaction("submitMessage")
                                                .build(metrics))
                                .build());

        grpcServer.start();
    }

    @Test
    void sendTransaction() throws UnknownHostException {
        final var desc =
                ClientServiceDescriptor.builder(ConsensusServiceGrpc.getServiceDescriptor())
                        .build();

        final var channel =
                ManagedChannelBuilder.forAddress(
                                InetAddress.getLocalHost().getHostName(), grpcServer.port())
                        .usePlaintext()
                        .build();

        final var client = GrpcServiceClient.create(channel, desc);

        final var topicId =
                TopicID.newBuilder().setShardNum(0).setRealmNum(0).setTopicNum(1234).build();

        final var data =
                ConsensusSubmitMessageTransactionBody.newBuilder()
                        .setTopicID(topicId)
                        .setMessage(ByteString.copyFrom("My message", StandardCharsets.UTF_8))
                        .build();

        final var txId =
                TransactionID.newBuilder()
                        .setTransactionValidStart(
                                Timestamp.newBuilder().setSeconds(2838283).setNanos(99902).build())
                        .setAccountID(AccountID.newBuilder().setAccountNum(1001).build())
                        .build();

        final var body =
                TransactionBody.newBuilder()
                        .setTransactionID(txId)
                        .setMemo("A Memo")
                        .setTransactionFee(1_000_000)
                        .setConsensusSubmitMessage(data)
                        .build();

        final var signedTx =
                SignedTransaction.newBuilder().setBodyBytes(body.toByteString()).build();

        final var tx =
                Transaction.newBuilder().setSignedTransactionBytes(signedTx.toByteString()).build();

        final TransactionResponse response = client.blockingUnary("submitMessage", tx);
        assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    }
}
