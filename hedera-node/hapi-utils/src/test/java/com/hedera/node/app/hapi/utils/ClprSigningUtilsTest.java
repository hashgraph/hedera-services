// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.hapi.utils;

import static com.hedera.node.app.hapi.utils.ClprSigningUtils.connectorSignatureMessage;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.hedera.hapi.node.clpr.ClprCompleteConnectorTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ClprSigningUtilsTest {
    @Test
    void canonicalDigestMatchesProtobufClientAndIgnoresSignatureAndUnknownFields() throws Exception {
        final var proto = com.hederahashgraph.api.proto.java.ClprCompleteConnectorTransactionBody.newBuilder()
                .setConnectorId(ByteString.copyFrom(new byte[32]))
                .setPublicKey(ByteString.copyFrom(new byte[32]))
                .setSignatureScheme(com.hederahashgraph.api.proto.java.ClprSignatureScheme.ED25519)
                .setSalt(ByteString.copyFrom(new byte[32]))
                .setChannelId(ByteString.copyFrom(new byte[32]))
                .setConnectorContract(com.hederahashgraph.api.proto.java.ContractID.newBuilder()
                        .setContractNum(123))
                .setAdminKey(com.hederahashgraph.api.proto.java.Key.newBuilder()
                        .setEd25519(ByteString.copyFrom(new byte[32])))
                .setLockedStake(200_000_000L)
                .build();
        final var domain = "Hiero CLPR complete connector v1".getBytes(StandardCharsets.UTF_8);
        final var preimage =
                ByteString.copyFrom(domain).concat(proto.toByteString()).toByteArray();
        final var expected = MiscCryptoUtils.keccak256DigestOf(preimage);
        final var unsigned = ClprCompleteConnectorTransactionBody.PROTOBUF.parse(Bytes.wrap(proto.toByteArray()));
        assertThat(connectorSignatureMessage(unsigned)).isEqualTo(expected);

        final var unknown = UnknownFieldSet.newBuilder()
                .addField(100, UnknownFieldSet.Field.newBuilder().addVarint(42).build())
                .build();
        final var received = proto.toBuilder()
                .setSignature(ByteString.copyFrom(new byte[64]))
                .setUnknownFields(unknown)
                .setAdminKey(proto.getAdminKey().toBuilder().setUnknownFields(unknown))
                .build();
        final var parsed = ClprCompleteConnectorTransactionBody.PROTOBUF.parse(Bytes.wrap(received.toByteArray()));
        assertThat(connectorSignatureMessage(parsed)).isEqualTo(expected);
    }
}
