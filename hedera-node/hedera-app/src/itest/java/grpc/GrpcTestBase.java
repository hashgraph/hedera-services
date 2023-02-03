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

import com.google.protobuf.ByteString;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.DefaultMetrics;
import com.swirlds.common.metrics.platform.DefaultMetricsFactory;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class GrpcTestBase {
    private static final ScheduledExecutorService METRIC_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor();

    private GrpcServer grpcServer;

    protected Metrics metrics;
    protected String host;
    protected int port;

    @BeforeEach
    void setUp() throws InterruptedException, UnknownHostException {
        final var latch = new CountDownLatch(1);
        metrics = new DefaultMetrics(METRIC_EXECUTOR, new DefaultMetricsFactory());
        final var config = GrpcServerConfiguration.builder().port(0).build();
        final var routingBuilder = GrpcRouting.builder();
        configureRouting(routingBuilder);
        grpcServer = GrpcServer.create(config, routingBuilder.build());

        // Block until the startup has completed, so all tests can run knowing the server
        // is set up and ready to go.
        grpcServer.start().thenAccept(server -> latch.countDown());
        latch.await();

        // Get the host and port dynamically now that the server is running.
        host = InetAddress.getLocalHost().getHostName();
        port = grpcServer.port();
    }

    @AfterEach
    void tearDown() {
        grpcServer.shutdown();
    }

    protected abstract void configureRouting(GrpcRouting.Builder rb);

    protected Transaction createSubmitMessageTransaction(int topicId, String msg) {
        final var data =
                ConsensusSubmitMessageTransactionBody.newBuilder()
                        .setTopicID(TopicID.newBuilder().setTopicNum(topicId).build())
                        .setMessage(ByteString.copyFrom(msg, StandardCharsets.UTF_8))
                        .build();

        return createTransaction(bodyBuilder -> bodyBuilder.setConsensusSubmitMessage(data));
    }

    protected Transaction createCreateTopicTransaction(String memo) {
        final var data = ConsensusCreateTopicTransactionBody.newBuilder().setMemo(memo).build();

        return createTransaction(bodyBuilder -> bodyBuilder.setConsensusCreateTopic(data));
    }

    protected Transaction createUncheckedSubmitTransaction() {
        final var data =
                UncheckedSubmitBody.newBuilder().setTransactionBytes(ByteString.EMPTY).build();

        return createTransaction(bodyBuilder -> bodyBuilder.setUncheckedSubmit(data));
    }

    protected Transaction createTransaction(Consumer<TransactionBody.Builder> txBodyBuilder) {
        final var txId =
                TransactionID.newBuilder()
                        .setTransactionValidStart(
                                Timestamp.newBuilder().setSeconds(2838283).setNanos(99902).build())
                        .setAccountID(AccountID.newBuilder().setAccountNum(1001).build())
                        .build();

        final var bodyBuilder =
                TransactionBody.newBuilder()
                        .setTransactionID(txId)
                        .setMemo("A Memo")
                        .setTransactionFee(1_000_000);
        txBodyBuilder.accept(bodyBuilder);
        final var body = bodyBuilder.build();

        final var signedTx =
                SignedTransaction.newBuilder().setBodyBytes(body.toByteString()).build();

        return Transaction.newBuilder().setSignedTransactionBytes(signedTx.toByteString()).build();
    }

    protected Query createGetTopicInfoQuery(int topicId) {
        final var data =
                ConsensusGetTopicInfoQuery.newBuilder()
                        .setTopicID(TopicID.newBuilder().setTopicNum(topicId).build())
                        .build();

        return Query.newBuilder().setConsensusGetTopicInfo(data).build();
    }

    protected Query createGetExecutionTimeQuery() {
        final var data = NetworkGetExecutionTimeQuery.newBuilder().build();

        return Query.newBuilder().setNetworkGetExecutionTime(data).build();
    }

    protected Query createGetLiveHashQuery() {
        final var data = CryptoGetLiveHashQuery.newBuilder().build();

        return Query.newBuilder().setCryptoGetLiveHash(data).build();
    }
}
