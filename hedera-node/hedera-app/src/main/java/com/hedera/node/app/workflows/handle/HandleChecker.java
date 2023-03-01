/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.workflows.handle;

import com.hedera.node.app.meta.ValidTransactionMetadata;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaState;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

public class HandleChecker {

    private final SignaturePreparer signaturePreparer;
    private final Cryptography cryptography;

    @Inject
    public HandleChecker(@NonNull final SignaturePreparer signaturePreparer, @NonNull final Cryptography cryptography) {
        this.signaturePreparer = signaturePreparer;
        this.cryptography = cryptography;
    }

    public List<TransactionSignature> determineSignatures(
            @NonNull final HederaState state,
            @NonNull final ValidTransactionMetadata metadata,
            @NonNull final PreHandleContext context) {
        final var signatures = new ArrayList<TransactionSignature>();
        final var notValidatedKeys = new ArrayList<HederaKey>();

        if (metadata.payerSignature() != null && Objects.equals(context.getPayerKey(), metadata.payerKey())) {
            signatures.add(metadata.payerSignature());
        } else {
            notValidatedKeys.add(metadata.payerKey());
        }

        context.getRequiredNonPayerKeys()
                .forEach(key -> {
                    var signature = metadata.otherSignatures().get(key);
                    if (signature != null) {
                        signatures.add(signature);
                    } else {
                        notValidatedKeys.add(key);
                    }
                });

        final var notValidatedSignatures = notValidatedKeys.stream()
                .map(key -> signaturePreparer
                        .prepareSignature(state, metadata.txnBodyBytes(), metadata.signatureMap(), key))
                .toList();
        cryptography.verifyAsync(notValidatedSignatures);

        signatures.addAll(notValidatedSignatures);
        return signatures;
    }
}
