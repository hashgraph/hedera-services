package com.hedera.services.store.schedule;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.state.merkle.MerkleSchedule;
import com.hederahashgraph.api.proto.java.AccountID;

import java.util.Arrays;

public class CompositeKey {
    private final int hash;
    private final AccountID id;

    public CompositeKey(int hash, AccountID id) {
        this.hash = hash;
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof CompositeKey))
            return false;
        CompositeKey other = (CompositeKey)o;

        boolean hashEquals = this.hash == other.hash;
        boolean idEquals = this.id.equals(other.id);

        return hashEquals && idEquals;
    }

    @Override
    public final int hashCode() {
        int result = hash;
        if (id != null) {
            result = 31 * result + id.hashCode();
        }
        return result;
    }

    public static CompositeKey fromMerkleSchedule(MerkleSchedule schedule) {
        return new CompositeKey(Arrays.hashCode(schedule.transactionBody()), schedule.payer().toGrpcAccountId());
    }
}
