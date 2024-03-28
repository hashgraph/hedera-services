/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ScheduleStoreUtility {
    private ScheduleStoreUtility() {}

    // @todo('7773') This requires rebuilding the equality virtual map on migration,
    //      because it's different from ScheduleVirtualValue (and must be, due to PBJ shift)
    @SuppressWarnings("UnstableApiUsage")
    public static Bytes calculateBytesHash(@NonNull final Schedule scheduleToHash) {
        Objects.requireNonNull(scheduleToHash);
        final Hasher hasher = Hashing.sha256().newHasher();
        if (scheduleToHash.memo() != null) {
            hasher.putString(scheduleToHash.memo(), StandardCharsets.UTF_8);
        }
        if (scheduleToHash.adminKey() != null) {
            addToHash(hasher, scheduleToHash.adminKey());
        }
        // @note We should check scheduler here, but mono doesn't, so we cannot either, yet.
        if (scheduleToHash.scheduledTransaction() != null) {
            addToHash(hasher, scheduleToHash.scheduledTransaction());
        }
        // @todo('9447') This should be modified to use calculated expiration once
        //               differential testing completes
        hasher.putLong(scheduleToHash.providedExpirationSecond());
        hasher.putBoolean(scheduleToHash.waitForExpiry());
        return Bytes.wrap(hasher.hash().asBytes());
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void addToHash(final Hasher hasher, final Key keyToAdd) {
        final byte[] keyBytes = Key.PROTOBUF.toBytes(keyToAdd).toByteArray();
        hasher.putInt(keyBytes.length);
        hasher.putBytes(keyBytes);
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void addToHash(final Hasher hasher, final SchedulableTransactionBody transactionToAdd) {
        final byte[] bytes =
                SchedulableTransactionBody.PROTOBUF.toBytes(transactionToAdd).toByteArray();
        hasher.putInt(bytes.length);
        hasher.putBytes(bytes);
    }
}
