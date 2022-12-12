/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
 * Metadata collected when schedule transactions are handled as part of "pre-handle" needed for signature
 * verification. It builds {@link SigTransactionMetadata} for the transaction that is being scheduled,
 * in addition to all fields in {@link SigTransactionMetadata}.
 */
public class ScheduleSigTransactionMetadataBuilder extends
        SigTransactionMetadataBuilder<ScheduleSigTransactionMetadataBuilder>{
    private TransactionMetadata scheduledTxnMeta;

    public ScheduleSigTransactionMetadataBuilder(@NonNull final AccountKeyLookup keyLookup) {
        super(keyLookup);
    }

    public ScheduleSigTransactionMetadataBuilder scheduledMeta(final TransactionMetadata meta) {
        this.scheduledTxnMeta = meta;
        return this;
    }

    /**
     * Creates and returns a new {@link ScheduleSigTransactionMetadata} based on the values configured in
     * this builder.
     *
     * @return a new {@link ScheduleSigTransactionMetadata} object
     */
    @Override
    @NonNull
    public ScheduleTransactionMetadata build(){
        Objects.requireNonNull(txn, "Transaction body is required to build ScheduleSigTransactionMetadata");
        Objects.requireNonNull(payer, "Payer is required to build ScheduleSigTransactionMetadata");
        Objects.requireNonNull(scheduledTxnMeta, "Scheduled transaction metadata is required to " +
                "build ScheduleSigTransactionMetadata");
        return new ScheduleSigTransactionMetadata(txn, payer, status, requiredKeys, scheduledTxnMeta);
    }
}
