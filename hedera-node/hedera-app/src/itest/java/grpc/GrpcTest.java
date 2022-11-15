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
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.service.proto.java.ConsensusServiceGrpc;
import io.grpc.ManagedChannelBuilder;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GrpcTest {
    @Test
    void sendTransaction() {
        final var desc =
                ClientServiceDescriptor.builder(ConsensusServiceGrpc.getServiceDescriptor())
                        .build();

        final var channel =
                ManagedChannelBuilder.forAddress("192.168.86.20", 8080).usePlaintext().build();

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
