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
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.Collections;
import java.util.List;

/**
 * Metadata collected when transactions are handled as part of "pre-handle". This happens with
 * multiple background threads. Any state read or computed as part of this pre-handle, including any
 * errors, are captured in the TransactionMetadata. This is then made available to the transaction
 * during the "handle" phase as part of the HandleContext.
 */
public class TransactionMetadata {
    private final boolean failed;
    private final Transaction tx;
    private final JKey payerSig;
    private final List<JKey> otherSigs;

    public TransactionMetadata(
            final Transaction tx,
            final boolean failed,
            final JKey payerSig,
            final List<JKey> otherSigs) {
        this.tx = tx;
        this.failed = failed;
        this.payerSig = payerSig;
        this.otherSigs = otherSigs;
    }

    public TransactionMetadata(final Transaction tx, final boolean failed) {
        this.tx = tx;
        this.failed = failed;
        this.payerSig = null;
        this.otherSigs = Collections.emptyList();
    }

    public TransactionMetadata(
            final Transaction tx, final boolean failed, final List<JKey> otherSigs) {
        this(tx, failed, null, otherSigs);
    }

    public Transaction transaction() {
        return tx;
    }

    public boolean failed() {
        return failed;
    }

    public JKey getPayerSig() {
        return payerSig;
    }

    public List<JKey> getOthersSigs() {
        return otherSigs;
    }
}
