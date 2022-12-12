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

import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Metadata collected when scheduled transactions are handled as part of "pre-handle" needed for signature
 * verification. It contains {@link SigTransactionMetadata} to add the required keys for the
 * transaction that is being scheduled.
 * This extends {@link SigTransactionMetadata} to add the required keys for the transaction.
 */
public record ScheduleSigTransactionMetadata(@NonNull TransactionBody txnBody,
                                              @NonNull AccountID payer,
                                              ResponseCodeEnum status,
                                              List<HederaKey> requiredKeys,
                                              @NonNull TransactionMetadata scheduledMeta)
        implements ScheduleTransactionMetadata {
    public ScheduleSigTransactionMetadata {
        Objects.requireNonNull(txnBody);
        Objects.requireNonNull(payer);
        Objects.requireNonNull(scheduledMeta);
    }
}
