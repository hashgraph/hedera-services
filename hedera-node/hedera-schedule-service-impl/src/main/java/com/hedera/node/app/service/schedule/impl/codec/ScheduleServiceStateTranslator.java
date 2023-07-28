/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.Schedule.Builder;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStoreImpl;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleUtility;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ScheduleServiceStateTranslator {

    public static Schedule convertScheduleVirtualValueToSchedule(
            @NonNull
                    final com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue
                            scheduleVirtualValue)
            throws IOException {
        /*
        @Nullable List<Key> signatories
         */
        Builder scheduleBuilder = Schedule.newBuilder()
                .deleted(scheduleVirtualValue.isDeleted())
                .executed(scheduleVirtualValue.isExecuted())
                .waitForExpiry(scheduleVirtualValue.waitForExpiryProvided());
        final var optionalMemo = scheduleVirtualValue.memo();

        if (optionalMemo != null) {
            scheduleBuilder.memo(optionalMemo.get());
        }

        if (scheduleVirtualValue.getKey() != null) {
            scheduleBuilder.id(ScheduleID.newBuilder()
                    .scheduleNum(scheduleVirtualValue.getKey().getKeyAsLong())
                    .build());
        }

        final var scheduleAccount = scheduleVirtualValue.schedulingAccount();
        if (scheduleAccount != null) {
            scheduleBuilder.schedulerAccount(AccountID.newBuilder()
                    .accountNum(scheduleAccount.num())
                    .realmNum(scheduleAccount.realm())
                    .shardNum(scheduleAccount.shard())
                    .build());
        }

        final var payerAccount = scheduleVirtualValue.payer();
        if (payerAccount != null) {
            scheduleBuilder.payerAccount(AccountID.newBuilder()
                    .accountNum(payerAccount.num())
                    .realmNum(payerAccount.realm())
                    .shardNum(payerAccount.shard())
                    .build());
        }

        final var adminKey = scheduleVirtualValue.adminKey();
        if (adminKey != null) {
            scheduleBuilder.adminKey(PbjConverter.asPbjKey(adminKey.get()));
        }

        final var validStart = scheduleVirtualValue.schedulingTXValidStart();
        if (validStart != null) {
            scheduleBuilder.scheduleValidStart(Timestamp.newBuilder()
                    .seconds(validStart.getSeconds())
                    .nanos(validStart.getNanos())
                    .build());
        }

        final var expirationTime = scheduleVirtualValue.expirationTimeProvided();
        if (expirationTime != null) {
            scheduleBuilder.expirationTimeProvided(Timestamp.newBuilder()
                    .seconds(expirationTime.getSeconds())
                    .nanos(expirationTime.getNanos())
                    .build());
        }

        final var calculatedExpirationTime = scheduleVirtualValue.calculatedExpirationTime();
        if (calculatedExpirationTime != null) {
            scheduleBuilder.calculatedExpirationTime(Timestamp.newBuilder()
                    .seconds(calculatedExpirationTime.getSeconds())
                    .nanos(calculatedExpirationTime.getNanos())
                    .build());
        }

        final var resolutionTime = scheduleVirtualValue.getResolutionTime();
        if (resolutionTime != null) {
            scheduleBuilder.resolutionTime(Timestamp.newBuilder()
                    .seconds(resolutionTime.getSeconds())
                    .nanos(resolutionTime.getNanos())
                    .build());
        }

        final var scheduledTxn = PbjConverter.fromPbj(scheduleVirtualValue.scheduledTxn());
        if (scheduledTxn != null) {
            scheduleBuilder.scheduledTransaction(scheduledTxn.get());
        }

        final var body = scheduleVirtualValue.bodyBytes();
        if (body.length > 0) {
            scheduleBuilder.originalCreateTransaction(TransactionBody.PROTOBUF.parse(BufferedData.wrap(body)));
        }

        var keys = scheduleVirtualValue.signatories();
        if (keys.size() > 0) {
            final List<Key> keyList = new ArrayList<>();
            for (byte[] key : keys) {
                if (key.length == 32) {
                    keyList.add(Key.newBuilder().ed25519(Bytes.wrap(key)).build());
                } else {
                    keyList.add(Key.newBuilder().ecdsaSecp256k1(Bytes.wrap(key)).build());
                }
            }
            scheduleBuilder.signatories(keyList);
        }

        return scheduleBuilder.build();
    }

    @NonNull
    public static com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue pbjToState(
            @NonNull ScheduleID scheduleID, @NonNull ReadableScheduleStoreImpl readableScheduleStore)
            throws InvalidKeyException {
        requireNonNull(scheduleID);
        requireNonNull(readableScheduleStore);
        final var optionalSchedule = readableScheduleStore.get(scheduleID);
        if (optionalSchedule == null) {
            new IllegalArgumentException("Schedule not found");
        }
        return pbjToState(optionalSchedule);
    }

    @NonNull
    public static List<com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue> pbjToState(
            long expiration, @NonNull ReadableScheduleStoreImpl readableScheduleStore) {
        requireNonNull(readableScheduleStore);
        final List<Schedule> optionalSchedules = readableScheduleStore.getByExpirationSecond(expiration);
        if (optionalSchedules == null || optionalSchedules.size() == 0) {
            new IllegalArgumentException("Schedule not found base on expirationTime");
        }

        List<com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue> schedules =
                new ArrayList<>();
        for (Schedule schedule : optionalSchedules) {
            schedules.add(pbjToState(schedule));
        }

        return schedules;
    }

    @NonNull
    public static List<com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue> pbjToState(
            @NonNull Schedule schedule, @NonNull ReadableScheduleStoreImpl readableScheduleStore)
            throws IllegalArgumentException {
        requireNonNull(schedule);
        requireNonNull(readableScheduleStore);
        final List<Schedule> optionalSchedules = readableScheduleStore.getByEquality(schedule);
        if (optionalSchedules == null || optionalSchedules.size() == 0) {
            throw new IllegalArgumentException("Schedule not found base on expirationTime");
        }

        List<com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue> schedules =
                new ArrayList<>();
        for (Schedule scheduleElm : optionalSchedules) {
            schedules.add(pbjToState(scheduleElm));
        }

        return schedules;
    }

    @NonNull
    public static com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue pbjToState(
            @NonNull Schedule schedule) {
        requireNonNull(schedule);
        requireNonNull(schedule.id());

        final var body =
                PbjConverter.asBytes(TransactionBody.PROTOBUF, requireNonNull(schedule.originalCreateTransaction()));
        com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue scheduleVirtualValue =
                new com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue();

        if (body.length > 0) {
            scheduleVirtualValue = scheduleVirtualValue.from(
                    body, schedule.calculatedExpirationTime().seconds());
        }

        if (schedule.resolutionTime() != null) {
            final var resolutionTime = ScheduleUtility.instantFromTimestamp(schedule.resolutionTime(), Instant.MAX);
            if (schedule.deleted()) {
                scheduleVirtualValue.markDeleted(resolutionTime);
            }
            if (schedule.executed()) {
                scheduleVirtualValue.markExecuted(resolutionTime);
            }
        }

        scheduleVirtualValue.setKey(EntityNumVirtualKey.fromLong(schedule.id().scheduleNum()));

        var keys = schedule.signatories();

        if (!keys.isEmpty()) {
            for (Key key : keys) {
                Bytes keyBytes =
                        key.hasEd25519() ? key.ed25519() : key.hasEcdsaSecp256k1() ? key.ecdsaSecp256k1() : null;
                if (keyBytes != null) {
                    scheduleVirtualValue.witnessValidSignature(keyBytes.toByteArray());
                }
            }
        }

        return scheduleVirtualValue;
    }
}
