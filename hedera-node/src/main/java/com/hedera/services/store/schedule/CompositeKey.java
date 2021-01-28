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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;

import java.util.Arrays;
import java.util.Objects;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;

/**
 * Set of properties used to describe unique instance of Scheduled Transaction
 */
public class CompositeKey {

    public static final JKey UNUSED_KEY = null;
    private static final String EMPTY_MEMO = null;

    private final int txBytesHashCode;
    private final EntityId payer;
    private final JKey adminKey;
    private final String entityMemo;

    public CompositeKey(int txBytesHashCode, EntityId payer, JKey adminKey, String entityMemo) {
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

        return this.txBytesHashCode == other.txBytesHashCode &&
                Objects.equals(this.entityMemo, other.entityMemo) &&
                Objects.equals(this.payer, other.payer) &&
                equalUpToDecodability(this.adminKey, other.adminKey);
    }

    @Override
    public final int hashCode() {
        int result = txBytesHashCode;
        if (payer != null) {
            result = 31 * result + payer.hashCode();
        }
        if (adminKey != null) {
            result = 31 * result + adminKey.hashCode();
        }
        if (entityMemo != null) {
            result = 31 * result + entityMemo.hashCode();
        }
        return result;
    }

    public static CompositeKey fromMerkleSchedule(MerkleSchedule schedule) {
        var adminKey = schedule.adminKey().isPresent() ? schedule.adminKey().get() : UNUSED_KEY;
        var memo = schedule.memo().isPresent() ? schedule.memo().get() : EMPTY_MEMO;
        return new CompositeKey(
                Arrays.hashCode(schedule.transactionBody()),
                schedule.payer(),
                adminKey,
                memo
        );
    }
}
