package grpc;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.service.proto.java.ConsensusServiceGrpc;
import io.grpc.ManagedChannelBuilder;
import io.helidon.grpc.client.ClientServiceDescriptor;
import io.helidon.grpc.client.GrpcServiceClient;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrpcTest {
    @Test
    void sendTransaction() {
        final var desc = ClientServiceDescriptor
                .builder(ConsensusServiceGrpc.getServiceDescriptor())
                .build();

        final var channel = ManagedChannelBuilder
                .forAddress("192.168.86.20", 8080)
                .usePlaintext()
                .build();

        final var client = GrpcServiceClient.create(channel, desc);

        final var topicId = TopicID.newBuilder()
                .setShardNum(0)
                .setRealmNum(0)
                .setTopicNum(1234)
                .build();

        final var data = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setTopicID(topicId)
                .setMessage(ByteString.copyFrom("My message", StandardCharsets.UTF_8))
                .build();

        final var txId = TransactionID.newBuilder()
                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(2838283).setNanos(99902).build())
                .setAccountID(AccountID.newBuilder().setAccountNum(1001).build())
                .build();

        final var body = TransactionBody.newBuilder()
                .setTransactionID(txId)
                .setMemo("A Memo")
                .setTransactionFee(1_000_000)
                .setConsensusSubmitMessage(data)
                .build();

        final var signedTx = SignedTransaction.newBuilder()
                .setBodyBytes(body.toByteString())
                .build();

        final var tx = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTx.toByteString())
                .build();

        final TransactionResponse response = client.blockingUnary("submitMessage", tx);
        assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    }
}
