package com.hedera.services.sigs.metadata;

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
import com.hederahashgraph.api.proto.java.AccountID;

import java.util.Objects;
import java.util.Optional;

public class ScheduleSigningMetadata {
    private final byte[] txnBytes;
    private final Optional<JKey> adminKey;
    private final Optional<AccountID> designatedPayer;

    public ScheduleSigningMetadata(
            byte[] txnBytes,
            Optional<JKey> adminKey,
            Optional<AccountID> designatedPayer
    ) {
        this.txnBytes = txnBytes;
        this.adminKey = adminKey;
        this.designatedPayer = designatedPayer;
    }

    public static ScheduleSigningMetadata from(MerkleSchedule schedule) {
        return new ScheduleSigningMetadata(
                schedule.transactionBody(),
                schedule.adminKey(),
                Objects.equals(schedule.payer(), schedule.schedulingAccount())
                        ? Optional.empty()
                        : Optional.of(schedule.payer().toGrpcAccountId()));
    }

    public Optional<JKey> adminKey() {
        return adminKey;
    }

    public byte[] txnBytes() {
        return txnBytes;
    }

    public Optional<AccountID> overridePayer() {
        return designatedPayer;
    }
}
