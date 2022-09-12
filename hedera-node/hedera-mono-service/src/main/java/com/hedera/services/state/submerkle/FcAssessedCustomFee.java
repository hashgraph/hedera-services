/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.AssessedCustomFee;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Process self serializable object that represents an assessed custom fee, which may or may not
 * result in a balance change, depending on if the payer can afford it. This is useful for setting
 * custom fees balance changes in {@link ExpirableTxnRecord}.
 */
public class FcAssessedCustomFee implements SelfSerializable {
    static final long[] UNKNOWN_EFFECTIVE_PAYER_ACCOUNT_NUMS = new long[0];

    static final int RELEASE_0170_VERSION = 1;
    static final int RELEASE_0171_VERSION = 2;

    static final int CURRENT_VERSION = RELEASE_0171_VERSION;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0xd8b56ce46e56a466L;

    private EntityId token;
    private EntityId account;
    private long units;
    private long[] effPayerAccountNums = UNKNOWN_EFFECTIVE_PAYER_ACCOUNT_NUMS;

    public FcAssessedCustomFee() {
        /* For RuntimeConstructable */
    }

    private FcAssessedCustomFee(
            final EntityId token, final AccountAmount aa, final long[] effPayerAccountNums) {
        this.token = token;
        this.account = EntityId.fromGrpcAccountId(aa.getAccountID());
        this.units = aa.getAmount();
        this.effPayerAccountNums = effPayerAccountNums;
    }

    public FcAssessedCustomFee(
            final EntityId account, final long amount, final long[] effPayerAccountNums) {
        this.token = null;
        this.account = account;
        this.units = amount;
        this.effPayerAccountNums = effPayerAccountNums;
    }

    public FcAssessedCustomFee(
            final EntityId account,
            final EntityId token,
            final long amount,
            final long[] effPayerAccountNums) {
        this.token = token;
        this.account = account;
        this.units = amount;
        this.effPayerAccountNums = effPayerAccountNums;
    }

    public boolean isForHbar() {
        return token == null;
    }

    public long units() {
        return units;
    }

    public EntityId token() {
        return token;
    }

    public EntityId account() {
        return account;
    }

    public long[] effPayerAccountNums() {
        return effPayerAccountNums;
    }

    public static FcAssessedCustomFee assessedHbarFeeFrom(
            final AccountAmount aa, final long[] effPayerAccountNums) {
        return new FcAssessedCustomFee(null, aa, effPayerAccountNums);
    }

    public static FcAssessedCustomFee assessedHtsFeeFrom(
            final EntityId token, final AccountAmount aa, final long[] effPayerAccountNums) {
        return new FcAssessedCustomFee(token, aa, effPayerAccountNums);
    }

    @Override
    public boolean equals(final Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(FcAssessedCustomFee.class)
                .add("token", token == null ? "ℏ" : token)
                .add("account", account)
                .add("units", units)
                .add("effective payer accounts", Arrays.toString(effPayerAccountNums))
                .toString();
    }

    /* --- Helpers --- */
    public AssessedCustomFee toGrpc() {
        var grpc =
                AssessedCustomFee.newBuilder()
                        .setFeeCollectorAccountId(account.toGrpcAccountId())
                        .setAmount(units);
        for (int i = 0; i < effPayerAccountNums.length; i++) {
            grpc.addEffectivePayerAccountId(
                    AccountID.newBuilder().setAccountNum(effPayerAccountNums[i]));
        }
        if (isForHbar()) {
            return grpc.build();
        }
        return grpc.setTokenId(token.toGrpcTokenId()).build();
    }

    public static FcAssessedCustomFee fromGrpc(AssessedCustomFee assessedFee) {
        final var aa =
                AccountAmount.newBuilder()
                        .setAccountID(assessedFee.getFeeCollectorAccountId())
                        .setAmount(assessedFee.getAmount())
                        .build();
        int n = assessedFee.getEffectivePayerAccountIdCount();
        long[] effPayerAccountNums = n > 0 ? new long[n] : UNKNOWN_EFFECTIVE_PAYER_ACCOUNT_NUMS;
        for (int i = 0; i < n; i++) {
            effPayerAccountNums[i] = assessedFee.getEffectivePayerAccountId(i).getAccountNum();
        }
        if (assessedFee.hasTokenId()) {
            return assessedHtsFeeFrom(
                    EntityId.fromGrpcTokenId(assessedFee.getTokenId()), aa, effPayerAccountNums);
        }
        return assessedHbarFeeFrom(aa, effPayerAccountNums);
    }

    /* ----- SelfSerializable methods ------ */
    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        account = in.readSerializable();
        token = in.readSerializable();
        units = in.readLong();
        if (version >= CURRENT_VERSION) {
            effPayerAccountNums = in.readLongArray(Integer.MAX_VALUE);
        } else {
            effPayerAccountNums = UNKNOWN_EFFECTIVE_PAYER_ACCOUNT_NUMS;
        }
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(account, true);
        out.writeSerializable(token, true);
        out.writeLong(units);
        out.writeLongArray(effPayerAccountNums);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }
}
