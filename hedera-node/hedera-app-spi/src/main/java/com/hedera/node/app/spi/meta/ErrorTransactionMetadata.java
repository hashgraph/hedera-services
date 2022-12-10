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
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** An implementation of {@link TransactionMetadata} for cases when an error has occurred. */
public final class ErrorTransactionMetadata implements TransactionMetadata {
    private final ResponseCodeEnum responseCode;
    private final Throwable throwable;
    private final TransactionBody txBody;
    private final AccountID payer;

    /**
     * Constructor of {@code ErrorTransactionMetadata}
     *
     * @param responseCode the {@link ResponseCodeEnum} of the error
     * @param throwable the {@link Throwable} that caused the error
     * @param txBody the {@link TransactionBody} if known, {@code null} otherwise
     * @param payer the {@link AccountID} of the payer if known, {@code null} otherwise
     * @throws NullPointerException if {@code responseCode} is {@code null}
     */
    public ErrorTransactionMetadata(
            @NonNull final ResponseCodeEnum responseCode,
            @NonNull final Throwable throwable,
            @Nullable final TransactionBody txBody,
            @Nullable final AccountID payer) {
        this.txBody = txBody;
        this.throwable = requireNonNull(throwable);
        this.responseCode = requireNonNull(responseCode);
        this.payer = payer;
    }

    /**
     * Returns the cause of the error
     *
     * @return the {@link Throwable} that caused the error
     */
    @Nullable
    public Throwable cause() {
        return throwable;
    }

    @NonNull
    @Override
    public ResponseCodeEnum status() {
        return responseCode;
    }

    @Nullable
    @Override
    public TransactionBody txnBody() {
        return txBody;
    }

    @NonNull
    @Override
    public List<HederaKey> requiredKeys() {
        return List.of();
    }

    @Nullable
    @Override
    public AccountID payer() {
        return payer;
    }
}
