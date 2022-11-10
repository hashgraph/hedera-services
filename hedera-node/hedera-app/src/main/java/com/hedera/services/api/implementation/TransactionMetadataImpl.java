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
package com.hedera.services.api.implementation;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;

/**
 * A simple implementation of {@link TransactionMetadata} that is setup with the attributes directly
 */
public class TransactionMetadataImpl implements TransactionMetadata {

    private final TransactionBody transactionBody;
    private final ResponseCodeEnum status;

    /**
     * Constructor of {@code TransactionMetadataImpl} which {@code status} is {@link
     * ResponseCodeEnum#OK}
     *
     * @param transactionBody the {@link TransactionBody}
     * @throws NullPointerException if {@code transactionBody} is {@code null}
     */
    public TransactionMetadataImpl(TransactionBody transactionBody) {
        this(transactionBody, ResponseCodeEnum.OK);
    }

    /**
     * Constructor of {@code TransactionMetadataImpl}
     *
     * @param transactionBody the {@link TransactionBody}
     * @param status the status, a {@link ResponseCodeEnum}
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    protected TransactionMetadataImpl(TransactionBody transactionBody, ResponseCodeEnum status) {
        this.transactionBody = requireNonNull(transactionBody);
        this.status = requireNonNull(status);
    }

    @Override
    public ResponseCodeEnum status() {
        return status;
    }

    @Override
    public TransactionBody getTxn() {
        return transactionBody;
    }

    @Override
    public List<HederaKey> getReqKeys() {
        return List.of();
    }
}
