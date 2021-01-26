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
import com.hederahashgraph.api.proto.java.Key;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Set of properties used to describe unique instance of Scheduled Transaction
 */
public class CompositeKey {
    private final int txBytesHashCode;
    private final AccountID payer;
    private final Optional<Key> adminKey;
    private final Optional<String> entityMemo;

    public CompositeKey(int txBytesHashCode, AccountID payer, Optional<Key> adminKey, Optional<String> entityMemo) {
        this.txBytesHashCode = txBytesHashCode;
        this.payer = payer;
        this.adminKey = adminKey;
        this.entityMemo = entityMemo;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof CompositeKey))
            return false;
        CompositeKey other = (CompositeKey)o;

        boolean hashEquals = this.txBytesHashCode == other.txBytesHashCode;
        boolean payerEquals = Objects.equals(this.payer, other.payer);
        boolean adminKeyEquals = Objects.equals(this.adminKey, other.adminKey);
        boolean memoEquals = Objects.equals(this.entityMemo, other.entityMemo);

        return hashEquals && payerEquals && adminKeyEquals && memoEquals;
    }

    @Override
    public final int hashCode() {
        int result = txBytesHashCode;
        if (payer != null) {
            result = 31 * result + payer.hashCode();
        }
        return result;
    }

    public static CompositeKey fromMerkleSchedule(MerkleSchedule schedule) {
        return new CompositeKey(
                Arrays.hashCode(schedule.transactionBody()),
                schedule.payer().toGrpcAccountId(), Optional.empty(), schedule.memo()); // TODO admin
    }
}
