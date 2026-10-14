// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.yahcli.commands.clpr;

import static com.hedera.node.app.hapi.utils.ClprSigningUtils.connectorSignatureMessage;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.clpr.ClprCompleteConnectorTransactionBody;
import com.hedera.hapi.node.clpr.ClprSignatureScheme;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.file.Path;
import org.hiero.base.crypto.CryptographyProvider;
import org.hiero.base.crypto.SignatureType;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ClprConnectorIdentityTest {
    @ParameterizedTest
    @EnumSource(
            value = ClprSignatureScheme.class,
            names = {"ED25519", "ECDSA_SECP256K1"})
    void generatedIdentitySignsResolvedReveal(final ClprSignatureScheme scheme, @TempDir final Path dir)
            throws Exception {
        final var generator = new GenerateConnectorIdentityCommand();
        generator.channelIdHex = "42".repeat(32);
        generator.signatureScheme = scheme.name();
        final var output = dir.resolve("connector.json");
        generator.outFile = output.toString();
        assertThat(generator.call()).isZero();
        final var identity = ClprArgs.readFlatJsonStringFields(output);
        assertThat(identity).doesNotContainKey("signature");
        final var publicKey = ClprArgs.parseHex(identity.get("publicKey"));
        final var op = ClprCompleteConnectorTransactionBody.newBuilder()
                .connectorId(Bytes.wrap(ClprArgs.parseHex(identity.get("connectorId"))))
                .channelId(Bytes.wrap(ClprArgs.parseHex(identity.get("channelId"))))
                .publicKey(Bytes.wrap(publicKey))
                .salt(Bytes.wrap(ClprArgs.parseHex(identity.get("salt"))))
                .signatureScheme(scheme)
                .connectorContract(ContractID.newBuilder().contractNum(1234).build())
                .adminKey(Key.newBuilder().ed25519(Bytes.wrap(new byte[32])).build())
                .lockedStake(200_000_000L)
                .build();
        final var signer =
                CompleteConnectorCommand.identitySigner(scheme.name(), ClprArgs.parseHex(identity.get("privateKey")));
        final var digest = connectorSignatureMessage(op);
        final var signature = signer.apply(digest);
        final var type = scheme == ClprSignatureScheme.ED25519 ? SignatureType.ED25519 : SignatureType.ECDSA_SECP256K1;
        assertThat(CryptographyProvider.getInstance().verifySync(digest, signature, publicKey, type))
                .isTrue();
        final var tampered = op.copyBuilder().lockedStake(200_000_001L).build();
        assertThat(CryptographyProvider.getInstance()
                        .verifySync(connectorSignatureMessage(tampered), signature, publicKey, type))
                .isFalse();
    }
}
