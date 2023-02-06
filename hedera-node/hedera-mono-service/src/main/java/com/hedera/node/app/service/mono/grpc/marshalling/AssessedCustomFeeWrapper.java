/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.grpc.marshalling;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcAssessedCustomFee;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Arrays;
import java.util.Map;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * A simple wrapper around the data needed to construct a {@code FcAssessedCustomFee} We require
 * this for the scenarios where any of the effective payer accounts related to this custom fee are
 * being auto-created <b>in the same transaction</b>. {@code FcAssessedCustomFee} keeps track of the
 * effective payer accounts as a {@code long[]} using the num from the shard.realm.num format, which
 * we do not have at the point of marshalling for accounts which are to be auto created.
 *
 * <p>Therefore, we defer {@code FcAssessedCustomFee} creation <b>after</b> the transfers have
 * happened when we have a mapping of the aliases to the new account ids for all the auto-created
 * accounts.
 */
public final class AssessedCustomFeeWrapper {

    private final EntityId token;
    private final EntityId account;
    private final long units;
    private final AccountID[] effPayerAccounts;

    public AssessedCustomFeeWrapper(
            final EntityId account,
            final EntityId token,
            final long units,
            final AccountID[] effPayerAccounts) {
        this.account = account;
        this.token = token;
        this.units = units;
        this.effPayerAccounts = effPayerAccounts;
    }

    public AssessedCustomFeeWrapper(
            final EntityId account, final long units, final AccountID[] effPayerAccounts) {
        this.token = null;
        this.account = account;
        this.units = units;
        this.effPayerAccounts = effPayerAccounts;
    }

    public boolean isForHbar() {
        return token == null;
    }

    public EntityId token() {
        return token;
    }

    public FcAssessedCustomFee toFcAssessedCustomFee(
            final Map<ByteString, AccountID> aliasesToNewIds) {
        final var nums = new long[effPayerAccounts.length];
        for (int i = 0; i < effPayerAccounts.length; i++) {
            final var payer = effPayerAccounts[i];
            final var alias = payer.getAlias();
            if (alias != ByteString.EMPTY) {
                nums[i] = aliasesToNewIds.get(alias).getAccountNum();
            } else {
                nums[i] = payer.getAccountNum();
            }
        }
        return token == null
                ? new FcAssessedCustomFee(account, units, nums)
                : new FcAssessedCustomFee(account, token, units, nums);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(AssessedCustomFeeWrapper.class)
                .add("token", token == null ? "ℏ" : token)
                .add("account", account)
                .add("units", units)
                .add("effective payer accounts", Arrays.toString(effPayerAccounts))
                .toString();
    }
}
