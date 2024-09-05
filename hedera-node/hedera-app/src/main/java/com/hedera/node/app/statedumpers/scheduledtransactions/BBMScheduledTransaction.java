/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.statedumpers.scheduledtransactions;

import com.hedera.node.app.statedumpers.legacy.EntityId;
import com.hedera.node.app.statedumpers.legacy.JKey;
import com.hedera.node.app.statedumpers.legacy.RichInstant;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("java:S6218") // "Equals/hashcode methods should be overridden in records containing array fields"
public record BBMScheduledTransaction(
        long number,
        @NonNull Optional<JKey> adminKey,
        @Nullable String memo,
        boolean deleted,
        boolean executed,
        boolean calculatedWaitForExpiry,
        boolean waitForExpiryProvided,
        @Nullable EntityId payer,
        @NonNull EntityId schedulingAccount,
        @NonNull RichInstant schedulingTXValidStart,
        @Nullable RichInstant expirationTimeProvided,
        @Nullable RichInstant calculatedExpirationTime,
        @Nullable RichInstant resolutionTime,
        @NonNull byte[] bodyBytes,
        @Nullable TransactionBody ordinaryScheduledTxn,
        @Nullable SchedulableTransactionBody scheduledTxn,
        @Nullable List<byte[]> signatories) {}
