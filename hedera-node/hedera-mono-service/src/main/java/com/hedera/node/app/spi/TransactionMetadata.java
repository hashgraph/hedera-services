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
package com.hedera.node.app.spi;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.Collections;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Metadata collected when transactions are handled as part of "pre-handle". This happens with
 * multiple background threads. Any state read or computed as part of this pre-handle, including any
 * errors, are captured in the TransactionMetadata. This is then made available to the transaction
 * during the "handle" phase as part of the HandleContext.
 */
public class TransactionMetadata {
    private ResponseCodeEnum failureStatus = OK;
    private final Transaction tx;
    private final JKey payerSig;
    private final List<JKey> otherSigs;

    public TransactionMetadata(
            final Transaction tx,
            final JKey payerSig,
            final List<JKey> otherSigs) {
        this.tx = tx;
        this.payerSig = payerSig;
        this.otherSigs = otherSigs;
    }

    public TransactionMetadata(final Transaction tx, final ResponseCodeEnum failureStatus) {
        this.tx = tx;
        this.failureStatus = failureStatus;
        this.payerSig = null;
        this.otherSigs = Collections.emptyList();
    }

    public Transaction transaction() {
        return tx;
    }

    public boolean failed() {
        return !failureStatus.equals(OK);
    }

    public ResponseCodeEnum failureStatus() {
        return failureStatus;
    }

    public JKey getPayerSig() {
        return payerSig;
    }

    public List<JKey> getOthersSigs() {
        return otherSigs;
    }
}
