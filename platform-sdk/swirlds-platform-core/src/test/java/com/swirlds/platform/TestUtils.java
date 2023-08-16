/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public class TestUtils {

    public static boolean equals(final EventImpl e1, final EventImpl e2) {
        if (e1.getCreatorId() != e2.getCreatorId()) {
            return false;
        }
        if (!e1.getConsensusTimestamp().equals(e2.getConsensusTimestamp())) {
            return false;
        }
        if (e1.getRoundCreated() != e2.getRoundCreated()) {
            return false;
        }
        if (e1.getRoundReceived() != e2.getRoundReceived()) {
            return false;
        }
        if (!Arrays.equals(e1.getBaseHash().getValue(), e2.getBaseHash().getValue())) {
            return false;
        }
        return true;
    }

    static EventImpl createTestEvent(@NonNull final NodeId creatorId, @Nullable final NodeId otherId) {
        Objects.requireNonNull(creatorId, "creatorId must not be null");
        final Instant startTime = Instant.ofEpochSecond(1554466913);
        final EventImpl e = new EventImpl(
                new BaseEventHashedData(
                        new BasicSoftwareVersion(1),
                        creatorId,
                        -1,
                        -1,
                        (byte[]) null,
                        (byte[]) null,
                        startTime,
                        new SwirldTransaction[0]),
                new BaseEventUnhashedData(otherId, new byte[] {0, 0, 0, 0}),
                null,
                null);
        CryptographyHolder.get().digestSync(e.getBaseEventHashedData());

        e.estimateTime(creatorId, startTime.getEpochSecond(), 0);
        return e;
    }
}
