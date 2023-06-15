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

package com.hedera.node.app.spi.meta.bni;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A strategy interface to allow a dispatcher to optionally set the verification status of a
 * "simple" {@link com.hedera.hapi.node.base.Key.KeyOneOfType#CONTRACT_ID},
 * {@link com.hedera.hapi.node.base.Key.KeyOneOfType#DELEGATABLE_CONTRACT_ID}, or
 * even cryptographic key.
 *
 * <p>The strategy has the option to delegate back to the cryptographic verifications
 * already computed by the app in pre-handle and/or handle workflows by returning
 * {@link Decision#DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION}.
 *
 * <p>Because it possible for the {@code tokenTransfer()} system contract to need to amend
 * its dispatched transaction based on the results of signature verifications, the strategy
 * also has the option to return an amended transaction body when an .
 */
public interface VerificationStrategy {
    enum Decision {
        VALID,
        INVALID,
        DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION
    }

    enum KeyRole {
        TOKEN_ADMIN,
        TOKEN_TREASURY,
        TOKEN_AUTO_RENEW_ACCOUNT,
        TOKEN_FEE_COLLECTOR,
        OTHER
    }

    /**
     * Returns a decision on whether to verify the signature of a transaction, given a key and
     * the role that its "parent" key structure plays in the transaction.
     *
     * <p>The {@link KeyRole} is necessary to allow the contract service to implement the "legacy" key
     * activations that are currently allow-listed on mainnet via the {@code contracts.keys.legacyActivations}
     * property.
     *
     * @param key the key to verify
     * @param keyRole the role that the key plays in the transaction
     * @return a decision on whether to verify the signature, or delegate back to the crypto engine results
     */
    Decision maybeVerifySignature(@NonNull Key key, @NonNull KeyRole keyRole);

    /**
     * Given a {@link CryptoTransferTransactionBody} and list of account numbers that were judged to have an
     * invalid signature, may return an amended transaction body that will be dispatched instead of the original.
     *
     * @param transfer the original CryptoTransfer
     * @param invalidSignerNumbers a list of account numbers that were judged to have an invalid signature
     * @return an amended CryptoTransfer, or null if no amendment is necessary
     */
    @Nullable
    CryptoTransferTransactionBody maybeAmendTransfer(
            @NonNull CryptoTransferTransactionBody transfer, List<Long> invalidSignerNumbers);
}
