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

package com.hedera.node.app.signature;

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.state.HederaState;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

public interface SignaturePreparer {

    /**
     * Prepares the signature data for a single key (usually the payer's key).
     *
     * <p>Please note: The parameter list is preliminary and very likely to change once we implement
     * the real {@link SignaturePreparer}.
     *
     * @param state the {@link HederaState} that should be used to read the state
     * @param txBodyBytes the {@code byte[]} of the {@link
     *     com.hederahashgraph.api.proto.java.TransactionBody}
     * @param signatureMap the {@link SignatureMap} that is included in the transaction
     * @param accountID the {@link AccountID} for which the signature data needs to be prepared
     * @return the {@link TransactionSignature} with all data required to verify the signature
     */
    @NonNull
    TransactionSignature prepareSignature(
            @NonNull HederaState state,
            @NonNull byte[] txBodyBytes,
            @NonNull SignatureMap signatureMap,
            @NonNull AccountID accountID);

    /**
     * Prepares the signature data for a list of keys.
     *
     * <p>Please note: The parameter list is preliminary and very likely to change once we implement
     * the real {@link SignaturePreparer}.
     *
     * @param state the {@link HederaState} that should be used to read the state
     * @param txBodyBytes the {@code byte[]} of the {@link
     *     com.hederahashgraph.api.proto.java.TransactionBody}
     * @param signatureMap the {@link SignatureMap} that is included in the transaction
     * @param keys the list of {@link HederaKey}s for which the signature data needs to be prepared
     * @return the {@link TransactionSignature} with all data required to verify the signature
     */
    @NonNull
    List<TransactionSignature> prepareSignatures(
            @NonNull HederaState state,
            @NonNull byte[] txBodyBytes,
            @NonNull SignatureMap signatureMap,
            @NonNull List<HederaKey> keys);
}
