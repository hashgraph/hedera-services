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

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;

public class TransactionMetadataImpl implements TransactionMetadata {

    private final TransactionBody transactionBody;
    private final ResponseCodeEnum status;

    public TransactionMetadataImpl(TransactionBody transactionBody) {
        this(transactionBody, ResponseCodeEnum.OK);
    }

    protected TransactionMetadataImpl(TransactionBody transactionBody, ResponseCodeEnum status) {
        this.transactionBody = transactionBody;
        this.status = status;
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
