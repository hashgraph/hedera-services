/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.statedumpers.legacy;

import static com.hedera.node.app.statedumpers.legacy.RichInstant.MISSING_INSTANT;

import com.google.common.base.MoreObjects;
import java.util.Objects;

public class TxnId {
    public static final int USER_TRANSACTION_NONCE = 0;

    private int nonce = USER_TRANSACTION_NONCE;
    private boolean scheduled = false;
    private EntityId payerAccount = EntityId.MISSING_ENTITY_ID;
    private RichInstant validStart = MISSING_INSTANT;

    public TxnId() {
        /* RuntimeConstructable */
    }

    public TxnId(final EntityId payerAccount, final RichInstant validStart, final boolean scheduled, final int nonce) {
        this.scheduled = scheduled;
        this.validStart = validStart;
        this.payerAccount = payerAccount;
        this.nonce = nonce;
    }

    public TxnId unscheduledWithNonce(final int nonce) {
        return new TxnId(payerAccount, validStart, false, nonce);
    }

    public TxnId withNonce(final int nonce) {
        return new TxnId(payerAccount, validStart, scheduled, nonce);
    }

    public EntityId getPayerAccount() {
        return payerAccount;
    }

    public RichInstant getValidStart() {
        return validStart;
    }

    /* --- Objects --- */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || TxnId.class != o.getClass()) {
            return false;
        }
        final var that = (TxnId) o;
        return this.scheduled == that.scheduled
                && Objects.equals(payerAccount, that.payerAccount)
                && Objects.equals(validStart, that.validStart)
                && this.nonce == that.nonce;
    }

    @Override
    public int hashCode() {
        return Objects.hash(payerAccount, validStart, scheduled, nonce);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("payer", payerAccount)
                .add("validStart", validStart)
                .add("scheduled", scheduled)
                .add("nonce", nonce)
                .toString();
    }

    public int getNonce() {
        return nonce;
    }

    public boolean isScheduled() {
        return scheduled;
    }
}
