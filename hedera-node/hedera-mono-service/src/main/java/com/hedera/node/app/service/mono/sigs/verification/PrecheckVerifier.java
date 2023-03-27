/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.sigs.verification;

import static com.hedera.node.app.service.mono.keys.HederaKeyActivation.ONLY_IF_SIG_IS_VALID;
import static com.hedera.node.app.service.mono.keys.HederaKeyActivation.isActive;
import static com.hedera.node.app.service.mono.keys.HederaKeyActivation.pkToSigMapFrom;
import static com.hedera.node.app.service.mono.sigs.PlatformSigOps.createCryptoSigsFrom;
import static com.hedera.node.app.service.mono.sigs.sourcing.KeyType.ECDSA_SECP256K1;

import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.PlatformSigsCreationResult;
import com.hedera.node.app.service.mono.sigs.factories.ReusableBodySigningFactory;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Encapsulates logic to validate a transaction has the necessary signatures to pass precheck. In
 * particular,
 *
 * <ul>
 *   <li>All transactions must have a valid payer signature; and,
 *   <li>CryptoTransfer transactions identified as query payments must have valid signatures for all
 *       referenced accounts.
 * </ul>
 *
 * Note that this component verifies cryptographic signatures synchronously.
 */
@Singleton
public class PrecheckVerifier {
    private final SyncVerifier syncVerifier;
    private final PrecheckKeyReqs precheckKeyReqs;

    @Inject
    public PrecheckVerifier(SyncVerifier syncVerifier, PrecheckKeyReqs precheckKeyReqs) {
        this.syncVerifier = syncVerifier;
        this.precheckKeyReqs = precheckKeyReqs;
    }

    /**
     * Tests if a signed gRPC transaction has the necessary (valid) signatures to be allowed through
     * precheck.
     *
     * @param accessor convenience interface to the signed txn.
     * @return a flag giving the verdict on the precheck sigs for the txn.
     * @throws Exception if the txn doesn't reference valid keys or has malformed sigs.
     */
    public boolean hasNecessarySignatures(final SignedTxnAccessor accessor) throws Exception {
        try {
            final var reqKeys = precheckKeyReqs.getRequiredKeys(accessor.getTxn());
            // first key in reqKeys is always the payer key
            final var payerKey = reqKeys.get(0);
            if (payerKey.hasWildcardECDSAKey()) {
                // can change this algorithm to use the accessor.getSigMap()
                // and return immediately when we find the first match
                accessor.getPkToSigsFn().forEachUnusedSigWithFullPrefix((type, pubKey, sig) -> {
                    if (type.equals(ECDSA_SECP256K1)
                            && Arrays.equals(
                                    payerKey.getWildcardECDSAKey().getEvmAddress(),
                                    EthSigsUtils.recoverAddressFromPubKey(pubKey))) {
                        reqKeys.set(0, new JECDSASecp256k1Key(pubKey));
                    }
                });
            }
            final var availSigs = getAvailSigs(reqKeys, accessor);
            syncVerifier.verifySync(availSigs);
            final var sigsFn = pkToSigMapFrom(availSigs);
            for (final var key : reqKeys) {
                if (!isActive(key, sigsFn, ONLY_IF_SIG_IS_VALID)) {
                    return false;
                }
            }
            return true;
        } catch (final InvalidPayerAccountException ignore) {
            return false;
        }
    }

    private List<TransactionSignature> getAvailSigs(List<JKey> reqKeys, SignedTxnAccessor accessor) throws Exception {
        final var pkToSigFn = accessor.getPkToSigsFn();
        final var sigFactory = new ReusableBodySigningFactory(accessor);
        PlatformSigsCreationResult creationResult = createCryptoSigsFrom(reqKeys, pkToSigFn, sigFactory);
        if (creationResult.hasFailed()) {
            throw creationResult.getTerminatingEx();
        } else {
            return creationResult.getPlatformSigs();
        }
    }
}
