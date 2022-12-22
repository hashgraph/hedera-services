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
package com.hedera.node.app.service.schedule.impl;

import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Schedules. If the scheduleID is valid and a schedule exists returns {@link
 * ScheduleVirtualValue}.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableScheduleStore {
    /** The underlying data storage class that holds the token data. */
    private final ReadableKVState<Long, ScheduleVirtualValue> schedulesById;

    /**
     * Create a new {@link ReadableScheduleStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableScheduleStore(@NonNull final ReadableStates states) {
        Objects.requireNonNull(states);
        this.schedulesById = states.get("SCHEDULES_BY_ID");
    }

    /**
     * Gets the schedule with the given {@link ScheduleID}. If there is no schedule with given ID
     * returns {@link Optional#empty()}.
     *
     * @param id given id for the schedule
     * @return the schedule with the given id
     */
    public Optional<ScheduleMetadata> get(final ScheduleID id) {
        final var schedule = schedulesById.get(id.getScheduleNum());
        return schedule == null
                ? Optional.empty()
                : Optional.of(new ScheduleMetadata(
                    schedule.adminKey(),
                    schedule.ordinaryViewOfScheduledTxn(),
                    schedule.hasExplicitPayer()
                            ? Optional.of(schedule.payer().toGrpcAccountId())
                            : Optional.empty()));
    }

    /**
     * Metadata about a schedule.
     *
     * @param adminKey admin key on the schedule
     * @param scheduledTxn scheduled transaction
     * @param designatedPayer payer for the schedule execution.If there is no explicit payer,
     *     returns {@link Optional#empty()}.
     */
    public record ScheduleMetadata(
            Optional<? extends HederaKey> adminKey,
            TransactionBody scheduledTxn,
            Optional<AccountID> designatedPayer) {}
}
