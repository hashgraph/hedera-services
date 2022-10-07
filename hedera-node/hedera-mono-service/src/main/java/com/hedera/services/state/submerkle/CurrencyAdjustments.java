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

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.utils.MiscUtils.readableTransferList;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CurrencyAdjustments implements SelfSerializable {
    static final int PRE_0240_VERSION = 1;
    static final int RELEASE_0240_VERSION = 2;
    static final int CURRENT_VERSION = RELEASE_0240_VERSION;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0xd8b06bd46e12a466L;

    private static final long[] NO_ADJUSTMENTS = new long[0];
    static final int MAX_NUM_ADJUSTMENTS = 25;

    long[] hbars = NO_ADJUSTMENTS;
    long[] accountNums = NO_ADJUSTMENTS;

    public CurrencyAdjustments() {
        /* For RuntimeConstructable */
    }

    public CurrencyAdjustments(long[] amounts, long[] parties) {
        hbars = amounts;
        accountNums = parties;
    }

    public boolean isEmpty() {
        return hbars.length == 0;
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
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        if (version < RELEASE_0240_VERSION) {
            final var accountIds =
                    in.readSerializableList(MAX_NUM_ADJUSTMENTS, true, EntityId::new);
            accountNums = accountIds.stream().mapToLong(EntityId::num).toArray();
        } else {
            accountNums = in.readLongArray(MAX_NUM_ADJUSTMENTS);
        }

        hbars = in.readLongArray(MAX_NUM_ADJUSTMENTS);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLongArray(accountNums);
        out.writeLongArray(hbars);
    }

    /* ---- Object --- */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || CurrencyAdjustments.class != o.getClass()) {
            return false;
        }

        CurrencyAdjustments that = (CurrencyAdjustments) o;
        return Arrays.equals(accountNums, that.accountNums) && Arrays.equals(hbars, that.hbars);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(RUNTIME_CONSTRUCTABLE_ID);
        result = result * 31 + Integer.hashCode(CURRENT_VERSION);
        result = result * 31 + Arrays.hashCode(accountNums);
        return result * 31 + Arrays.hashCode(hbars);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("readable", readableTransferList(toGrpc()))
                .toString();
    }

    /* --- Helpers --- */
    public TransferList toGrpc() {
        var grpc = TransferList.newBuilder();
        grpc.addAllAccountAmounts(asAccountAmountsList());
        return grpc.build();
    }

    public void addToGrpc(final TokenTransferList.Builder builder) {
        for (int i = 0; i < hbars.length; i++) {
            builder.addTransfers(adjustAt(i));
        }
    }

    public List<AccountAmount> asAccountAmountsList() {
        final List<AccountAmount> changes = new ArrayList<>();
        for (int i = 0; i < hbars.length; i++) {
            changes.add(adjustAt(i));
        }
        return changes;
    }

    private AccountAmount adjustAt(final int i) {
        return AccountAmount.newBuilder()
                .setAmount(hbars[i])
                .setAccountID(STATIC_PROPERTIES.scopedAccountWith(accountNums[i]))
                .build();
    }

    public static CurrencyAdjustments fromChanges(
            final long[] balanceChanges, final long[] changedAccounts) {
        final var n = balanceChanges.length;
        final var m = changedAccounts.length;

        if (n > 0 && m > 0 && n == m) {
            return new CurrencyAdjustments(balanceChanges, changedAccounts);
        }
        return new CurrencyAdjustments();
    }

    public static CurrencyAdjustments fromGrpc(List<AccountAmount> adjustments) {
        final var pojo = new CurrencyAdjustments();
        final int n = adjustments.size();
        if (n > 0) {
            final var amounts = new long[n];
            final long[] accounts = new long[n];
            for (var i = 0; i < n; i++) {
                final var adjustment = adjustments.get(i);
                amounts[i] = adjustment.getAmount();
                accounts[i] = adjustment.getAccountID().getAccountNum();
            }
            pojo.hbars = amounts;
            pojo.accountNums = accounts;
        }
        return pojo;
    }

    public long[] getHbars() {
        return hbars;
    }

    public long[] getAccountNums() {
        return accountNums;
    }
}
