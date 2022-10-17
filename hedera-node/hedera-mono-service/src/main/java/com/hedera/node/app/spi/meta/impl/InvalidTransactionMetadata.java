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
package com.hedera.node.app.spi.meta.impl;

import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.Collections;
import java.util.List;

/** Transaction metadata returned in case of any failure during pre-handle. */
public class InvalidTransactionMetadata implements TransactionMetadata {
    protected ResponseCodeEnum status;
    protected Transaction txn;

    public InvalidTransactionMetadata(final Transaction txn, final ResponseCodeEnum failure) {
        this.txn = txn;
        this.status = failure;
    }

    @Override
    public boolean failed() {
        return true;
    }

    @Override
    public ResponseCodeEnum failureStatus() {
        return status;
    }

    @Override
    public Transaction getTxn() {
        return txn;
    }

    @Override
    public List<JKey> getReqKeys() {
        return Collections.emptyList();
    }
}
