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
package com.hedera.services.state.merkle;

import static com.hedera.services.state.merkle.internals.BitPackUtils.packedNums;
import static com.hedera.services.utils.EntityIdUtils.asRelationshipLiteral;

import com.google.common.base.MoreObjects;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;
import java.io.IOException;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

public class MerkleTokenRelStatus extends PartialMerkleLeaf
        implements Keyed<EntityNumPair>, MerkleLeaf {
    static final int RELEASE_090_VERSION = 1;
    static final int RELEASE_0180_PRE_SDK_VERSION = 2;
    static final int RELEASE_0180_VERSION = 3;
    static final int RELEASE_0250_VERSION = 4;
    static final int CURRENT_VERSION = RELEASE_0250_VERSION;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0xe487c7b8b4e7233fL;

    private long numbers;
    private long balance;
    private boolean frozen;
    private boolean kycGranted;
    private boolean automaticAssociation;
    // next and previous tokenIds of the account's association linked list
    private long next;
    private long prev;

    public MerkleTokenRelStatus() {
        /* RuntimeConstructable */
    }

    public MerkleTokenRelStatus(final Pair<AccountID, TokenID> grpcRel) {
        this.numbers =
                packedNums(grpcRel.getLeft().getAccountNum(), grpcRel.getRight().getTokenNum());
    }

    public MerkleTokenRelStatus(
            long balance, boolean frozen, boolean kycGranted, boolean automaticAssociation) {
        this.balance = balance;
        this.frozen = frozen;
        this.kycGranted = kycGranted;
        this.automaticAssociation = automaticAssociation;
    }

    public MerkleTokenRelStatus(
            long balance,
            boolean frozen,
            boolean kycGranted,
            boolean automaticAssociation,
            long numbers) {
        this.balance = balance;
        this.frozen = frozen;
        this.kycGranted = kycGranted;
        this.numbers = numbers;
        this.automaticAssociation = automaticAssociation;
    }

    private MerkleTokenRelStatus(MerkleTokenRelStatus that) {
        this.balance = that.balance;
        this.frozen = that.frozen;
        this.kycGranted = that.kycGranted;
        this.numbers = that.numbers;
        this.automaticAssociation = that.automaticAssociation;
        this.prev = that.prev;
        this.next = that.next;
    }

    /* --- MerkleLeaf --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        balance = in.readLong();
        frozen = in.readBoolean();
        kycGranted = in.readBoolean();
        if (version >= RELEASE_0180_PRE_SDK_VERSION) {
            automaticAssociation = in.readBoolean();
        }
        if (version >= RELEASE_0180_VERSION) {
            numbers = in.readLong();
        }
        if (version >= RELEASE_0250_VERSION) {
            next = in.readLong();
            prev = in.readLong();
        }
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(balance);
        out.writeBoolean(frozen);
        out.writeBoolean(kycGranted);
        out.writeBoolean(automaticAssociation);
        out.writeLong(numbers);
        out.writeLong(next);
        out.writeLong(prev);
    }

    /* --- Object --- */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || MerkleTokenRelStatus.class != o.getClass()) {
            return false;
        }

        var that = (MerkleTokenRelStatus) o;
        return this.balance == that.balance
                && this.frozen == that.frozen
                && this.kycGranted == that.kycGranted
                && this.numbers == that.numbers
                && this.automaticAssociation == that.automaticAssociation
                && this.next == that.next
                && this.prev == that.prev;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(balance)
                .append(frozen)
                .append(kycGranted)
                .append(automaticAssociation)
                .append(numbers)
                .append(next)
                .append(prev)
                .toHashCode();
    }

    /* --- Bean --- */
    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        throwIfImmutable("Cannot change this token relation's balance if it's immutable.");
        if (balance < 0) {
            throw new IllegalArgumentException(
                    String.format("Argument 'balance=%d' would negate %s!", balance, this));
        }
        this.balance = balance;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        throwIfImmutable("Cannot change this token relation's frozen status if it's immutable.");
        this.frozen = frozen;
    }

    public boolean isKycGranted() {
        return kycGranted;
    }

    public void setKycGranted(boolean kycGranted) {
        throwIfImmutable("Cannot change this token relation's grant kyc if it's immutable.");
        this.kycGranted = kycGranted;
    }

    public boolean isAutomaticAssociation() {
        return automaticAssociation;
    }

    public void setAutomaticAssociation(boolean automaticAssociation) {
        throwIfImmutable(
                "Cannot change this token relation's automaticAssociation if it's immutable.");
        this.automaticAssociation = automaticAssociation;
    }

    public long getRelatedTokenNum() {
        return getKey().getLowOrderAsLong();
    }

    /* --- FastCopyable --- */
    @Override
    public MerkleTokenRelStatus copy() {
        setImmutable(true);
        return new MerkleTokenRelStatus(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("balance", balance)
                .add("isFrozen", frozen)
                .add("hasKycGranted", kycGranted)
                .add("key", numbers + " <-> " + asRelationshipLiteral(numbers))
                .add("isAutomaticAssociation", automaticAssociation)
                .add("next", next)
                .add("prev", prev)
                .toString();
    }

    /* --- Keyed --- */
    @Override
    public EntityNumPair getKey() {
        return new EntityNumPair(numbers);
    }

    @Override
    public void setKey(EntityNumPair numbers) {
        this.numbers = numbers.value();
    }

    public long prevKey() {
        return prev;
    }

    public long nextKey() {
        return next;
    }

    public void setPrev(final long prev) {
        this.prev = prev;
    }

    public void setNext(final long next) {
        this.next = next;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return RELEASE_0180_VERSION;
    }
}
