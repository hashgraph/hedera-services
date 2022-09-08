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
package com.hedera.services.sigs.verification;

import static com.hedera.services.keys.HederaKeyActivation.ONLY_IF_SIG_IS_VALID;
import static com.hedera.services.keys.HederaKeyActivation.isActive;
import static com.hedera.services.keys.HederaKeyActivation.pkToSigMapFrom;
import static com.hedera.services.sigs.PlatformSigOps.createCryptoSigsFrom;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.PlatformSigsCreationResult;
import com.hedera.services.sigs.factories.ReusableBodySigningFactory;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.swirlds.common.crypto.TransactionSignature;
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

    private List<TransactionSignature> getAvailSigs(List<JKey> reqKeys, SignedTxnAccessor accessor)
            throws Exception {
        final var pkToSigFn = accessor.getPkToSigsFn();
        final var sigFactory = new ReusableBodySigningFactory(accessor);
        PlatformSigsCreationResult creationResult =
                createCryptoSigsFrom(reqKeys, pkToSigFn, sigFactory);
        if (creationResult.hasFailed()) {
            throw creationResult.getTerminatingEx();
        } else {
            return creationResult.getPlatformSigs();
        }
    }
}
