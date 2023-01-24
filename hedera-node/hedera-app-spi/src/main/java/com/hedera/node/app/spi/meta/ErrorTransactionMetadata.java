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
import java.util.Map;
import java.util.Set;

/**
 * An implementation of {@link TransactionMetadata} for cases when an error has occurred.
 *
 * @param txnBody the {@link TransactionBody} if known, {@code null} otherwise
 * @param status the {@link ResponseCodeEnum} of the error
 * @param cause Returns the cause of the error
 */
public record ErrorTransactionMetadata(
        @Nullable TransactionBody txnBody,
        @NonNull ResponseCodeEnum status,
        @NonNull Throwable cause)
        implements TransactionMetadata {
    /**
     * Constructor of {@code ErrorTransactionMetadata}
     *
     * @param status the {@link ResponseCodeEnum} of the error
     * @param cause the {@link Throwable} that caused the error
     * @param txnBody the {@link TransactionBody} if known, {@code null} otherwise
     * @throws NullPointerException if {@code responseCode} is {@code null}
     */
    public ErrorTransactionMetadata {
        requireNonNull(cause);
        requireNonNull(status);
    }

    @NonNull
    @Override
    public List<HederaKey> requiredNonPayerKeys() {
        return List.of();
    }

    @Nullable
    @Override
    public AccountID payer() {
        return null; // FUTURE: change this to the payer injected in PreHandleWorkflow#dispatch
        // method.
    }

    @Nullable
    @Override
    public HederaKey payerKey() {
        return null;
    }

    @NonNull
    @Override
    public List<ReadKeys> readKeys() {
        return List.of();
    }
}
