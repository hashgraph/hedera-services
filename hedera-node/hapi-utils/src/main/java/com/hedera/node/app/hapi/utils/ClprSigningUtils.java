// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.hapi.utils;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.clpr.ClprCompleteConnectorTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.StandardCharsets;

/** Signing payloads shared by CLPR handlers and clients. */
public final class ClprSigningUtils {
    private static final byte[] CONNECTOR_DOMAIN = "Hiero CLPR complete connector v1".getBytes(StandardCharsets.UTF_8);

    private ClprSigningUtils() {}

    /**
     * Returns Keccak-256 of the UTF-8 domain {@code Hiero CLPR complete connector v1}, followed by
     * the canonical protobuf encoding of the complete connector body with its signature cleared.
     * This binds every registration field, including the local contract, admin key, and locked stake.
     */
    public static byte[] connectorSignatureMessage(@NonNull final ClprCompleteConnectorTransactionBody op) {
        final var unsigned =
                requireNonNull(op).copyBuilder().signature(Bytes.EMPTY).build();
        final var body =
                ClprCompleteConnectorTransactionBody.PROTOBUF.toBytes(unsigned).toByteArray();
        final var preimage = new byte[CONNECTOR_DOMAIN.length + body.length];
        System.arraycopy(CONNECTOR_DOMAIN, 0, preimage, 0, CONNECTOR_DOMAIN.length);
        System.arraycopy(body, 0, preimage, CONNECTOR_DOMAIN.length, body.length);
        return MiscCryptoUtils.keccak256DigestOf(Bytes.wrap(preimage)).toByteArray();
    }
}
