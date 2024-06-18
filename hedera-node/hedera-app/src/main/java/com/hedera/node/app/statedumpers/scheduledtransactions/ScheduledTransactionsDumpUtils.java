/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.statedumpers.scheduledtransactions;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.mono.statedumpers.associations.BBMTokenAssociation.entityIdFrom;
import static com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.ScheduledTransactionsDumpUtils.reportOnScheduledTransactionsByEquality;
import static com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.ScheduledTransactionsDumpUtils.reportOnScheduledTransactionsByExpiry;
import static com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.ScheduledTransactionsDumpUtils.reportOnScheduledTransactionsById;
import static com.hedera.node.app.service.schedule.impl.handlers.HandlerUtility.childAsOrdinary;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.BBMScheduledEqualityValue;
import com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.BBMScheduledId;
import com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.BBMScheduledSecondValue;
import com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.BBMScheduledTransaction;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.swirlds.base.utility.Pair;
import com.swirlds.platform.state.merkle.disk.OnDiskKey;
import com.swirlds.platform.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ScheduledTransactionsDumpUtils {

    public static void dumpModScheduledTransactions(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<ScheduleID>, OnDiskValue<Schedule>> scheduledTransactions,
            @NonNull final VirtualMap<OnDiskKey<ProtoBytes>, OnDiskValue<ScheduleList>> byEquality,
            @NonNull final VirtualMap<OnDiskKey<ProtoLong>, OnDiskValue<ScheduleList>> byExpiry) {
        try (@NonNull final var writer = new Writer(path)) {
            System.out.printf("=== Dumping schedule transactions %n ======");

            final var byId = gatherModScheduledTransactionsById(scheduledTransactions);
            reportOnScheduledTransactionsById(writer, byId);
            System.out.println(
                    "Size of byId in State : " + scheduledTransactions.size() + " and gathered : " + byId.size());

            // Not sure how to compare Equality Virtual map in mono and mod
            final var byExpiryDump = gatherModScheduledTransactionsByExpiry(byExpiry);
            reportOnScheduledTransactionsByExpiry(writer, byExpiryDump);
            System.out.println(
                    "Size of byExpiry in State : " + byExpiry.size() + " and gathered : " + byExpiryDump.size());

            try {
                final var byEqualityDump = gatherModScheduledTransactionsByEquality(byEquality);
                reportOnScheduledTransactionsByEquality(writer, byEqualityDump);
                System.out.println("Size of byEquality in State : " + byEquality.size() + " and gathered : "
                        + byEqualityDump.size());
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error in gathering byEqualityDump");
            }
        }
    }

    private static List<BBMScheduledEqualityValue> gatherModScheduledTransactionsByEquality(
            final VirtualMap<OnDiskKey<ProtoBytes>, OnDiskValue<ScheduleList>> source) {
        final List<BBMScheduledEqualityValue> r = new ArrayList<>();
        final var scheduledTransactions = new ConcurrentLinkedQueue<BBMScheduledEqualityValue>();

        try {
            VirtualMapLike.from(source)
                    .extractVirtualMapDataC(
                            getStaticThreadManager(),
                            p -> scheduledTransactions.add(
                                    fromMod(p.key().getKey(), p.value().getValue())),
                            1);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of scheduledTransactions by equality virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        while (!scheduledTransactions.isEmpty()) {
            final var mapping = scheduledTransactions.poll();
            r.add(mapping);
        }
        return r;
    }

    private static Map<BBMScheduledId, BBMScheduledSecondValue> gatherModScheduledTransactionsByExpiry(
            final VirtualMap<OnDiskKey<ProtoLong>, OnDiskValue<ScheduleList>> source) {
        final var r = new HashMap<BBMScheduledId, BBMScheduledSecondValue>();
        final var scheduledTransactions = new ConcurrentLinkedQueue<Pair<BBMScheduledId, BBMScheduledSecondValue>>();

        try {
            VirtualMapLike.from(source)
                    .extractVirtualMapDataC(
                            getStaticThreadManager(),
                            p -> scheduledTransactions.add(Pair.of(
                                    new BBMScheduledId(p.key().getKey().value()),
                                    fromMod(p.key().getKey(), p.value().getValue()))),
                            8);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of scheduledTransactions virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        while (!scheduledTransactions.isEmpty()) {
            final var mapping = scheduledTransactions.poll();
            r.put(mapping.key(), mapping.value());
        }
        return r;
    }

    @NonNull
    private static Map<BBMScheduledId, BBMScheduledTransaction> gatherModScheduledTransactionsById(
            VirtualMap<OnDiskKey<ScheduleID>, OnDiskValue<Schedule>> source) {
        final var r = new HashMap<BBMScheduledId, BBMScheduledTransaction>();
        final var scheduledTransactions = new ConcurrentLinkedQueue<Pair<BBMScheduledId, BBMScheduledTransaction>>();

        try {
            VirtualMapLike.from(source)
                    .extractVirtualMapDataC(
                            getStaticThreadManager(),
                            p -> scheduledTransactions.add(Pair.of(
                                    fromMod(p.key().getKey()), fromMod(p.value().getValue()))),
                            8);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of scheduledTransactions virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        while (!scheduledTransactions.isEmpty()) {
            final var mapping = scheduledTransactions.poll();
            r.put(mapping.key(), mapping.value());
        }
        return r;
    }

    static BBMScheduledSecondValue fromMod(final ProtoLong expiry, @NonNull final ScheduleList value) {
        final var newMap = new TreeMap<Instant, List<Long>>();
        final var longsList = value.schedules().stream()
                .map(a -> a.scheduleId().scheduleNum())
                .toList();
        newMap.put(Instant.ofEpochSecond(expiry.value()), longsList);
        return new BBMScheduledSecondValue(newMap);
    }

    static BBMScheduledEqualityValue fromMod(final ProtoBytes hash, @NonNull final ScheduleList value) {
        final var newMap = new TreeMap<String, Long>();
        final var longsList = value.schedules().stream()
                .map(a -> a.scheduleId().scheduleNum())
                .toList();
        newMap.put(hash.value().asUtf8String(), longsList.get(0));
        return new BBMScheduledEqualityValue(newMap);
    }

    static BBMScheduledTransaction fromMod(@NonNull final Schedule value) {
        Optional<JKey> adminKey;
        try {
            adminKey = value.adminKey() != null ? Optional.of(JKey.mapKey(value.adminKey())) : Optional.empty();
        } catch (InvalidKeyException e) {
            adminKey = Optional.empty();
        }

        return new BBMScheduledTransaction(
                value.scheduleId().scheduleNum(),
                adminKey,
                value.memo(),
                value.deleted(),
                value.executed(),
                // calculatedWaitForExpiry is the same as waitForExpiryProvided;
                // see ScheduleVirtualValue::from` - to.calculatedWaitForExpiry = to.waitForExpiryProvided;
                value.waitForExpiry(),
                value.waitForExpiry(),
                entityIdFrom(value.payerAccountId().accountNum()),
                entityIdFrom(value.schedulerAccountId().accountNum()),
                RichInstant.fromJava(Instant.ofEpochSecond(
                        value.scheduleValidStart().seconds(),
                        value.scheduleValidStart().nanos())),
                RichInstant.fromJava(Instant.ofEpochSecond(value.providedExpirationSecond())),
                RichInstant.fromJava(Instant.ofEpochSecond(value.calculatedExpirationSecond())),
                RichInstant.fromJava(Instant.ofEpochSecond(
                        value.resolutionTime().seconds(), value.resolutionTime().nanos())),
                fromPbj(value.originalCreateTransaction()).toByteArray(),
                fromPbj(childAsOrdinary(value)),
                fromPbj(value.scheduledTransaction()),
                value.signatories().stream().map(k -> toPrimitiveKey(k)).toList());
    }

    static BBMScheduledId fromMod(@NonNull final ScheduleID scheduleID) {
        return new BBMScheduledId(scheduleID.scheduleNum());
    }

    static byte[] toPrimitiveKey(com.hedera.hapi.node.base.Key key) {
        if (key.hasEd25519()) {
            return key.ed25519().toByteArray();
        } else if (key.hasEcdsaSecp256k1()) {
            return key.ecdsaSecp256k1().toByteArray();
        } else {
            return new byte[] {};
        }
    }
}
