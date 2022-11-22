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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** An implementation of {@link TransactionMetadata} for cases when an error has occurred. */
public final class ErrorTransactionMetadata implements TransactionMetadata {
    private final ResponseCodeEnum responseCode;
    private final Throwable throwable;
    private final TransactionBody txBody;

    /**
     * Constructor of {@code ErrorTransactionMetadata}
     *
     * @param responseCode the {@link ResponseCodeEnum} of the error
     * @param throwable the {@link Throwable} that caused the error
     * @param txBody the {@link TransactionBody} if known, {@code null} otherwise
     * @throws NullPointerException if {@code responseCode} is {@code null}
     */
    public ErrorTransactionMetadata(
            @Nonnull final ResponseCodeEnum responseCode,
            @Nullable final Throwable throwable,
            @Nullable final TransactionBody txBody) {
        this.txBody = txBody;
        this.throwable = requireNonNull(throwable);
        this.responseCode = requireNonNull(responseCode);
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

    @Nonnull
    @Override
    public ResponseCodeEnum status() {
        return responseCode;
    }

    @Nullable
    @Override
    public TransactionBody getTxn() {
        return txBody;
    }

    @Nonnull
    @Override
    public List<HederaKey> getReqKeys() {
        return List.of();
    }

    @Override
    public void setStatus(final ResponseCodeEnum status) {
        throw new UnsupportedOperationException(
                "This operation is not supported after an error occurred");
    }

    @Override
    public void addToReqKeys(final HederaKey key) {
        throw new UnsupportedOperationException(
                "This operation is not supported after an error occurred");
    }
}
