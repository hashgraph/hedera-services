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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Objects;

/**
 * An implementation of {@link TransactionMetadata} for cases when an unknown error has occurred.
 */
public final class UnknownErrorTransactionMetadata implements TransactionMetadata {
    private final Throwable throwable;

    /**
     * Constructor of {@code UnknownErrorTransactionMetadata}
     *
     * @param th the {@link Throwable} that caused the error
     */
    public UnknownErrorTransactionMetadata(Throwable th) {
        this.throwable = Objects.requireNonNull(th);
    }

    /**
     * Returns the cause of the error
     *
     * @return the {@link Throwable} that caused the error
     */
    public Throwable cause() {
        return throwable;
    }

    @Override
    public ResponseCodeEnum status() {
        return ResponseCodeEnum.UNKNOWN;
    }

    @Override
    public TransactionBody getTxn() {
        return null;
    }

    @Override
    public List<HederaKey> getReqKeys() {
        return List.of();
    }

    @Override
    public void setStatus(ResponseCodeEnum status) {
        throw new UnsupportedOperationException(
                "This operation is not supported after an error occurred");
    }

    @Override
    public void addToReqKeys(HederaKey key) {
        throw new UnsupportedOperationException(
                "This operation is not supported after an error occurred");
    }
}
