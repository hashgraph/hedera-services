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

package com.hedera.node.app.service.schedule.impl.codec;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.Schedule.Builder;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Utility class used for conversion of schedule virtual values to schedules.
 * @deprecated Since there should not be anymore ScheduleVirtualValue objects in state,
 * this class should no longer be required and will be removed in a future release
 */
@Deprecated(forRemoval = true)
public final class ScheduleServiceStateTranslator {
    private static final int ED25519_KEY_LENGTH = 32;

    private ScheduleServiceStateTranslator() {}

    /**
     * Convert schedule virtual value to schedule.
     *
     * @param virtualValue the virtual value
     * @return the schedule
     * @throws ParseException if there is an error parsing the TransactionBody message
     */
    public static Schedule convertScheduleVirtualValueToSchedule(
            @NonNull final com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue virtualValue)
            throws ParseException {
        final Builder scheduleBuilder = Schedule.newBuilder()
                .deleted(virtualValue.isDeleted())
                .executed(virtualValue.isExecuted())
                .waitForExpiry(virtualValue.waitForExpiryProvided());
        final Optional<String> optionalMemo = virtualValue.memo();

        if (optionalMemo.isPresent()) {
            scheduleBuilder.memo(optionalMemo.get());
        }

        if (virtualValue.getKey() != null) {
            scheduleBuilder.scheduleId(
                    ScheduleID.newBuilder().scheduleNum(virtualValue.getKey().getKeyAsLong()));
        }

        final EntityId scheduleAccount = virtualValue.schedulingAccount();
        if (scheduleAccount != null) {
            scheduleBuilder.schedulerAccountId(AccountID.newBuilder()
                    .accountNum(scheduleAccount.num())
                    .realmNum(scheduleAccount.realm())
                    .shardNum(scheduleAccount.shard()));
        }

        final EntityId payerAccount = virtualValue.payer();
        if (payerAccount != null) {
            scheduleBuilder.payerAccountId(AccountID.newBuilder()
                    .accountNum(payerAccount.num())
                    .realmNum(payerAccount.realm())
                    .shardNum(payerAccount.shard()));
        }

        final Optional<JKey> adminKey = virtualValue.adminKey();
        if (adminKey.isPresent()) {
            scheduleBuilder.adminKey(PbjConverter.asPbjKey(adminKey.get()));
        }

        final RichInstant validStart = virtualValue.schedulingTXValidStart();
        if (validStart != null) {
            scheduleBuilder.scheduleValidStart(
                    Timestamp.newBuilder().seconds(validStart.getSeconds()).nanos(validStart.getNanos()));
        }

        final RichInstant expirationTime = virtualValue.expirationTimeProvided();
        if (expirationTime != null) {
            scheduleBuilder.providedExpirationSecond(expirationTime.getSeconds());
        }

        final RichInstant calculatedExpirationTime = virtualValue.calculatedExpirationTime();
        if (calculatedExpirationTime != null) {
            scheduleBuilder.calculatedExpirationSecond(calculatedExpirationTime.getSeconds());
        }

        final Timestamp resolutionTime = getResolutionTime(virtualValue);
        if (resolutionTime != null) {
            scheduleBuilder.resolutionTime(resolutionTime);
        }

        final Optional<SchedulableTransactionBody> scheduledTxn = PbjConverter.toPbj(virtualValue.scheduledTxn());
        if (scheduledTxn.isPresent()) {
            scheduleBuilder.scheduledTransaction(scheduledTxn.get());
        }

        final byte[] body = virtualValue.bodyBytes();
        if (body.length > 0) {
            scheduleBuilder.originalCreateTransaction(TransactionBody.PROTOBUF.parse(BufferedData.wrap(body)));
        }

        final List<byte[]> keys = virtualValue.signatories();
        if (keys != null && !keys.isEmpty()) {
            final List<Key> keyList = new ArrayList<>();
            for (final byte[] key : keys) {
                final Bytes keyBytes = Bytes.wrap(key);
                final Key.Builder keyBuilder = Key.newBuilder();
                if (key.length == ED25519_KEY_LENGTH) {
                    keyList.add(keyBuilder.ed25519(keyBytes).build());
                } else {
                    keyList.add(keyBuilder.ecdsaSecp256k1(keyBytes).build());
                }
            }
            scheduleBuilder.signatories(keyList);
        }

        return scheduleBuilder.build();
    }

    @Nullable
    private static Timestamp getResolutionTime(@NonNull final ScheduleVirtualValue virtualValue) {
        if (virtualValue.isDeleted()) return PbjConverter.toPbj(virtualValue.deletionTime());
        else if (virtualValue.isExecuted()) return PbjConverter.toPbj(virtualValue.executionTime());
        else return null;
    }

    /**
     * Migrates the state of the schedule service from the scheduleId to the schedule virtual value
     * using the readableStore and the ScheduleId.
     *
     * @param scheduleID the schedule id
     * @param readableScheduleStore the readable schedule store
     * @return the schedule virtual value
     */
    @NonNull
    public static com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue pbjToState(
            @NonNull final ScheduleID scheduleID, @NonNull final ReadableScheduleStore readableScheduleStore) {
        Objects.requireNonNull(scheduleID);
        Objects.requireNonNull(readableScheduleStore);
        final Schedule optionalSchedule = readableScheduleStore.get(scheduleID);
        if (optionalSchedule == null) {
            throw new IllegalArgumentException("Schedule not found");
        }
        return pbjToState(optionalSchedule);
    }

    /**
     * Converts a {@link Schedule} object to a {@link ScheduleVirtualValue}
     * *
     * @param schedule the schedule
     * @return the schedule virtual value
     */
    @NonNull
    public static com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue pbjToState(
            @NonNull final Schedule schedule) {
        Objects.requireNonNull(schedule);
        Objects.requireNonNull(schedule.scheduleId());

        final byte[] body = PbjConverter.asBytes(
                TransactionBody.PROTOBUF, Objects.requireNonNull(schedule.originalCreateTransaction()));
        com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue scheduleVirtualValue =
                new com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue();

        if (body.length > 0 && schedule.calculatedExpirationSecond() != Schedule.DEFAULT.calculatedExpirationSecond()) {
            scheduleVirtualValue = ScheduleVirtualValue.from(body, schedule.calculatedExpirationSecond());
        }

        if (schedule.resolutionTime() != null) {
            final Instant resolutionTime = instantFromTimestamp(schedule.resolutionTime(), Instant.MAX);
            if (schedule.deleted()) {
                scheduleVirtualValue.markDeleted(resolutionTime);
            }
            if (schedule.executed()) {
                scheduleVirtualValue.markExecuted(resolutionTime);
            }
        }

        scheduleVirtualValue.setKey(
                EntityNumVirtualKey.fromLong(schedule.scheduleId().scheduleNum()));

        final List<Key> keys = schedule.signatories();

        if (keys != null && !keys.isEmpty()) {
            for (final Key key : keys) {
                final Bytes keyBytes = getKeyBytes(key);
                if (keyBytes != null) {
                    scheduleVirtualValue.witnessValidSignature(keyBytes.toByteArray());
                }
            }
        }

        return scheduleVirtualValue;
    }

    @Nullable
    private static Bytes getKeyBytes(@NonNull final Key key) {
        if (key.hasEd25519()) return key.ed25519();
        else if (key.hasEcdsaSecp256k1()) return key.ecdsaSecp256k1();
        else return null;
    }

    @NonNull
    private static Instant instantFromTimestamp(
            @Nullable final Timestamp timestampValue, @NonNull final Instant defaultValue) {
        return timestampValue != null
                ? Instant.ofEpochSecond(timestampValue.seconds(), timestampValue.nanos())
                : defaultValue;
    }
}
