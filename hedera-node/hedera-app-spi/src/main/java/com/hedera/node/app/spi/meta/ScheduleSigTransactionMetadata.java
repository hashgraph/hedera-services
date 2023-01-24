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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Metadata collected when scheduled transactions are handled as part of "pre-handle" needed for
 * signature verification. It contains {@link SigTransactionMetadata} to add the required keys for
 * the transaction that is being scheduled.
 *
 * @param txnBody given schedule transaction body
 * @param payer payer for the top-level transaction
 * @param status status of the top-level transaction
 * @param payerKey payer key for the top-level transaction
 * @param readKeys keys that were read during pre-handle
 * @param requiredNonPayerKeys required non-payer keys for the top-level transaction
 * @param scheduledMeta metadata for the scheduled transaction
 */
public record ScheduleSigTransactionMetadata(
        @NonNull TransactionBody txnBody,
        @NonNull AccountID payer,
        @NonNull ResponseCodeEnum status,
        @Nullable HederaKey payerKey,
        @NonNull List<HederaKey> requiredNonPayerKeys,
        @NonNull List<ReadKeys> readKeys,
        @NonNull TransactionMetadata scheduledMeta)
        implements ScheduleTransactionMetadata {
    public ScheduleSigTransactionMetadata {
        requireNonNull(txnBody);
        requireNonNull(payer);
        requireNonNull(status);
        requireNonNull(requiredNonPayerKeys);
        requireNonNull(readKeys);
        requireNonNull(scheduledMeta);
    }
}
