/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.services.utils.EntityIdUtils.asAccount;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Objects;

public class TxnId implements SelfSerializable {
    public static final int USER_TRANSACTION_NONCE = 0;

    static final int RELEASE_0210_VERSION = 4;
    private static final int CURRENT_VERSION = RELEASE_0210_VERSION;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0x61a52dfb3a18d9bL;

    private int nonce = USER_TRANSACTION_NONCE;
    private boolean scheduled = false;
    private EntityId payerAccount = MISSING_ENTITY_ID;
    private RichInstant validStart = MISSING_INSTANT;

    public TxnId() {
        /* RuntimeConstructable */
    }

    public TxnId(
            final EntityId payerAccount,
            final RichInstant validStart,
            final boolean scheduled,
            final int nonce) {
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

    /* --- SelfSerializable --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        payerAccount = in.readSerializable(true, EntityId::new);
        validStart = RichInstant.from(in);
        scheduled = in.readBoolean();
        // Added in 0.21
        final var hasNonUserNonce = in.readBoolean();
        if (hasNonUserNonce) {
            nonce = in.readInt();
        } else {
            nonce = USER_TRANSACTION_NONCE;
        }
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(payerAccount, true);
        validStart.serialize(out);
        out.writeBoolean(scheduled);
        if (nonce != USER_TRANSACTION_NONCE) {
            out.writeBoolean(true);
            out.writeInt(nonce);
        } else {
            out.writeBoolean(false);
        }
    }

    /* --- Objects --- */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || TxnId.class != o.getClass()) {
            return false;
        }
        var that = (TxnId) o;
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

    /* --- Helpers --- */
    public static TxnId fromGrpc(final TransactionID grpc) {
        return new TxnId(
                EntityId.fromGrpcAccountId(grpc.getAccountID()),
                RichInstant.fromGrpc(grpc.getTransactionValidStart()),
                grpc.getScheduled(),
                grpc.getNonce());
    }

    public TransactionID toGrpc() {
        var grpc = TransactionID.newBuilder().setAccountID(asAccount(payerAccount));

        if (!validStart.isMissing()) {
            grpc.setTransactionValidStart(validStart.toGrpc());
        }
        grpc.setScheduled(scheduled);
        grpc.setNonce(nonce);

        return grpc.build();
    }
}
