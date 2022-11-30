/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.sigs.factories;

import com.hedera.node.app.service.mono.sigs.utils.MiscCryptoUtils;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.swirlds.common.crypto.TransactionSignature;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ReusableBodySigningFactory implements TxnScopedPlatformSigFactory {
    private byte[] keccak256Digest;
    private TxnAccessor accessor;

    @Inject
    public ReusableBodySigningFactory() {}

    public ReusableBodySigningFactory(final TxnAccessor accessor) {
        this.accessor = accessor;
    }

    public void resetFor(final TxnAccessor accessor) {
        this.accessor = accessor;
        this.keccak256Digest = null;
    }

    @Override
    public TransactionSignature signBodyWithEd25519(final byte[] publicKey, final byte[] sigBytes) {
        return PlatformSigFactory.ed25519Sig(publicKey, sigBytes, accessor.getTxnBytes());
    }

    @Override
    public TransactionSignature signKeccak256DigestWithSecp256k1(
            final byte[] publicKey, final byte[] sigBytes) {
        if (keccak256Digest == null) {
            keccak256Digest = MiscCryptoUtils.keccak256DigestOf(accessor.getTxnBytes());
        }
        final var rawPublicKey = MiscCryptoUtils.decompressSecp256k1(publicKey);
        return PlatformSigFactory.ecdsaSecp256k1Sig(rawPublicKey, sigBytes, keccak256Digest);
    }

    /* --- Only used by unit tests --- */
    TxnAccessor getAccessor() {
        return accessor;
    }

    byte[] getKeccak256Digest() {
        return keccak256Digest;
    }
}
