package com.hedera.node.app.service.schedule.impl;

import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

/**
 * A store for looking up schedules given {@link ScheduleID}.
 * If the scheduleID is valid and a schedule exists returns {@link ScheduleVirtualValue}.
 */
public class ScheduleStore {
    /** The underlying data storage class that holds the token data. */
    private final State<Long, ScheduleVirtualValue> schedulesById;

    /**
     * Create a new {@link ScheduleStore} instance.
     *
     * @param states The state to use.
     */
    public ScheduleStore(@NonNull final States states) {
        Objects.requireNonNull(states);
        this.schedulesById = states.get("SCHEDULES_BY_ID");
    }

    /**
     * Gets the schedule with the given {@link ScheduleID}.
     * If there is no schedule with given ID returns {@link Optional#empty()}.
     * @param id given id for the schedule
     * @return the schedule with the given id
     */
    public Optional<ScheduleMetadata> get(final ScheduleID id) {
        final var schedule = schedulesById.get(id.getScheduleNum());
        if (schedule.isEmpty()) {
            return Optional.empty();
        }
        final var value = schedule.get();
        return Optional.of(new ScheduleMetadata(
                value.adminKey(),
                value.ordinaryViewOfScheduledTxn(),
                value.hasExplicitPayer()
                        ? Optional.of(value.payer().toGrpcAccountId())
                        : Optional.empty()));
    }

    /**
     * Metadata about a schedule.
     *
     * @param adminKey admin key on the schedule
     * @param scheduledTxn scheduled transaction
     * @param designatedPayer payer for the schedule execution.If there is no explicit payer,
     *                        returns {@link Optional#empty()}.
     */
    public record ScheduleMetadata(
            Optional<? extends HederaKey> adminKey,
            TransactionBody scheduledTxn,
            Optional<AccountID> designatedPayer) {
    }
}