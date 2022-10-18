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
package com.hedera.services.sigs.metadata;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;

public record ScheduleSigningMetadata(
        Optional<JKey> adminKey,
        TransactionBody scheduledTxn,
        Optional<AccountID> designatedPayer) {
    public static ScheduleSigningMetadata from(ScheduleVirtualValue schedule) {
        return new ScheduleSigningMetadata(
                schedule.adminKey(),
                schedule.ordinaryViewOfScheduledTxn(),
                schedule.hasExplicitPayer()
                        ? Optional.of(schedule.payer().toGrpcAccountId())
                        : Optional.empty());
    }
}
