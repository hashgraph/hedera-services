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
package com.hedera.services.grpc.marshalling;

import com.google.protobuf.ByteString;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public final class AssessedCustomFeeWrapper {

    private final EntityId token;
    private final EntityId account;
    private final long units;
    private final AccountID[] effPayerAccounts;

    public AssessedCustomFeeWrapper(
            EntityId account, EntityId token, long units, AccountID[] effPayerAccounts) {
        this.account = account;
        this.token = token;
        this.units = units;
        this.effPayerAccounts = effPayerAccounts;
    }

    public AssessedCustomFeeWrapper(EntityId account, long units, AccountID[] effPayerAccounts) {
        this.token = null;
        this.account = account;
        this.units = units;
        this.effPayerAccounts = effPayerAccounts;
    }

    FcAssessedCustomFee toFcAssessedCustomFee(Map<ByteString, AccountID> aliasesToNewIds) {
        final var nums = new long[effPayerAccounts.length];
        for (int i = 0; i < effPayerAccounts.length; i++) {
            final var payer = effPayerAccounts[i];
            final var alias = payer.getAlias();
            if (alias != ByteString.EMPTY && aliasesToNewIds.containsKey(alias)) {
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AssessedCustomFeeWrapper that = (AssessedCustomFeeWrapper) o;
        return units == that.units
                && Objects.equals(token, that.token)
                && Objects.equals(account, that.account)
                && Arrays.equals(effPayerAccounts, that.effPayerAccounts);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(token, account, units);
        result = 31 * result + Arrays.hashCode(effPayerAccounts);
        return result;
    }

    @Override
    public String toString() {
        return "fixme";
    }
}
