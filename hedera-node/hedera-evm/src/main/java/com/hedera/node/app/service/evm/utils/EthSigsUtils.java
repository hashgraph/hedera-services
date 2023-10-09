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

package com.hedera.node.app.service.evm.utils;

import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.SECP256K1_EC_UNCOMPRESSED;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.sun.jna.ptr.LongByReference; // NOSONAR
import java.nio.ByteBuffer;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

public final class EthSigsUtils {

    private EthSigsUtils() {}

    public static byte[] recoverAddressFromPubKey(byte[] pubKeyBytes) {
        LibSecp256k1.secp256k1_pubkey pubKey = new LibSecp256k1.secp256k1_pubkey();
        var parseResult = LibSecp256k1.secp256k1_ec_pubkey_parse(CONTEXT, pubKey, pubKeyBytes, pubKeyBytes.length);
        if (parseResult == 1) {
            return recoverAddressFromPubKey(pubKey);
        } else {
            return new byte[0];
        }
    }

    public static Bytes recoverAddressFromPubKey(Bytes pubKeyBytes) {
        return Bytes.wrap(recoverAddressFromPubKey(pubKeyBytes.toByteArray()));
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
