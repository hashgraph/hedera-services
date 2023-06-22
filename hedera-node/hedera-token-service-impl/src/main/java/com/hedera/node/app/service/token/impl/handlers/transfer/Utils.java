/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.asBytes;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.SECP256K1_EC_UNCOMPRESSED;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class Utils {
    /**
     * Attempts to parse a {@code Key} from given alias {@code ByteString}. If the Key is of type
     * Ed25519 or ECDSA(secp256k1), returns true if it is a valid key; and false otherwise.
     *
     * @param alias given alias byte string
     * @return whether it parses to a valid primitive key
     */
    public static boolean isSerializedProtoKey(final Bytes alias) {
        try (final var bais = new ByteArrayInputStream(Objects.requireNonNull(asBytes(alias)))) {
            final var stream = new ReadableStreamingData(bais);
            stream.limit(bais.available());
            final var key = Key.PROTOBUF.parse(stream);
            if (key.hasEcdsaSecp256k1()) {
                return isValid(key);
            } else if (key.hasEd25519()) {
                return isValid(key);
            } else {
                return false;
            }
        } catch (final IOException e) {
            return false;
        }
    }

    public static Key asKeyFromAlias(Bytes alias) {
        try (final var bais = new ByteArrayInputStream(Objects.requireNonNull(asBytes(alias)))) {
            final var stream = new ReadableStreamingData(bais);
            return Key.PROTOBUF.parse(stream);
        } catch (final IOException e) {
            throw new HandleException(ResponseCodeEnum.INVALID_ALIAS_KEY);
        }
    }
    public static byte[] recoverAddressFromPubKey(byte[] pubKeyBytes) {
        LibSecp256k1.secp256k1_pubkey pubKey = new LibSecp256k1.secp256k1_pubkey();
        var parseResult = LibSecp256k1.secp256k1_ec_pubkey_parse(CONTEXT, pubKey, pubKeyBytes, pubKeyBytes.length);
        if (parseResult == 1) {
            return recoverAddressFromPubKey(pubKey);
        } else {
            return new byte[0];
        }
    }

    public static byte[] recoverAddressFromPubKey(LibSecp256k1.secp256k1_pubkey pubKey) {
        final ByteBuffer recoveredFullKey = ByteBuffer.allocate(65);
        final LongByReference fullKeySize = new LongByReference(recoveredFullKey.limit());
        LibSecp256k1.secp256k1_ec_pubkey_serialize(
                CONTEXT, recoveredFullKey, fullKeySize, pubKey, SECP256K1_EC_UNCOMPRESSED);

        recoveredFullKey.get(); // read and discard - recoveryId is not part of the account hash
        var preHash = new byte[64];
        recoveredFullKey.get(preHash, 0, 64);
        var keyHash = new Keccak.Digest256().digest(preHash);
        var address = new byte[20];
        System.arraycopy(keyHash, 12, address, 0, 20);
        return address;
    }
}
}
