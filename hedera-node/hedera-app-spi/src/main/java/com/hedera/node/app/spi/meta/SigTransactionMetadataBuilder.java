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
package com.hedera.node.app.spi.meta;

import com.hedera.node.app.spi.AccountKeyLookup;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Builds {@link SigTransactionMetadata} by collecting information that is needed when transactions
 * are handled as part of "pre-handle" needed for signature verification.
 *
 * <p>NOTE : This class is designed to be subclassed For e.g., we need a {@link TransactionMetadata}
 * with an inner {@link TransactionMetadata} for schedule transactions.
 */
public class SigTransactionMetadataBuilder
        extends TransactionMetadataBuilder<SigTransactionMetadataBuilder> {
    public SigTransactionMetadataBuilder(@NonNull AccountKeyLookup keyLookup) {
        super(keyLookup);
    }

    /**
     * Creates and returns a new {@link SigTransactionMetadata} based on the values configured in
     * this builder.
     *
     * @return a new {@link SigTransactionMetadata}
     */
    @NonNull
    public TransactionMetadata build() {
        Objects.requireNonNull(txn, "Transaction body is required to build SigTransactionMetadata");
        Objects.requireNonNull(payer, "Payer is required to build SigTransactionMetadata");
        return new SigTransactionMetadata(txn, payer, status, payerKey, requiredNonPayerKeys);
    }

    @Override
    public SigTransactionMetadataBuilder self() {
        return this;
    }
}
