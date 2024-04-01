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

import static com.hedera.node.app.service.mono.statedumpers.associations.BBMTokenAssociation.entityIdFrom;
import static com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.ScheduledTransactionsDumpUtils.reportOnScheduledTransactionsById;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.BBMScheduledId;
import com.hedera.node.app.service.mono.statedumpers.scheduledtransactions.BBMScheduledTransaction;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ScheduledTransactionsDumpUtils {

    public static void dumpModScheduledTransactions(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<ScheduleID>, OnDiskValue<Schedule>> scheduledTransactions,
            @NonNull final VirtualMap<OnDiskKey<ProtoBytes>, OnDiskValue<ScheduleList>> byEquality,
            @NonNull final VirtualMap<OnDiskKey<ProtoLong>, OnDiskValue<ScheduleList>> byExpiry,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableScheduledTransactionsById = gatherModScheduledTransactionsById(scheduledTransactions);
            reportOnScheduledTransactionsById(writer, dumpableScheduledTransactionsById);
            System.out.printf(
                    "=== mod scheduled transactions report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
            //            final var byEqualityDump = gatherModScheduledTransactionsByEquality(byEquality);
            //            reportOnScheduledTransactionsByEquality(writer, byEqualityDump);
            System.out.printf(
                    "=== mod scheduled transactions by equality report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
            //            final var byExpiryDump = gatherModScheduledTransactionsByExpiry(byExpiry);
            //            reportOnScheduledTransactionsByExpiry(writer, byExpiryDump);
            System.out.printf(
                    "=== mod scheduled transactions by expiry report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    //    private static Map<BBMScheduledId, BBMScheduledSecondValue> gatherModScheduledTransactionsByExpiry(
    //            final VirtualMap<OnDiskKey<ProtoLong>, OnDiskValue<ScheduleList>> source) {
    //        final var r = new HashMap<BBMScheduledId, BBMScheduledSecondValue>();
    //        final var scheduledTransactions =
    //                new ConcurrentLinkedQueue<Pair<BBMScheduledId, BBMScheduledSecondValue>>();
    //
    //        try {
    //            VirtualMapLike.from(source)
    //                    .extractVirtualMapDataC(
    //                            getStaticThreadManager(),
    //                            p -> {
    //                                try {
    //                                    scheduledTransactions.add(Pair.of(
    //                                            new BBMScheduledId(p.key().getKey().value()),
    //                                            fromMod(p.value().getValue())));
    //                                } catch (InvalidKeyException e) {
    //                                    throw new RuntimeException(e);
    //                                }
    //                            },
    //                            8);
    //        } catch (final InterruptedException ex) {
    //            System.err.println("*** Traversal of scheduledTransactions virtual map interrupted!");
    //            Thread.currentThread().interrupt();
    //        }
    //        while (!scheduledTransactions.isEmpty()) {
    //            final var mapping = scheduledTransactions.poll();
    //            r.put(mapping.key(), mapping.value());
    //        }
    //        return r;
    //    }

    @NonNull
    private static Map<BBMScheduledId, BBMScheduledTransaction> gatherModScheduledTransactionsById(
            VirtualMap<OnDiskKey<ScheduleID>, OnDiskValue<Schedule>> source) {
        final var r = new HashMap<BBMScheduledId, BBMScheduledTransaction>();
        final var scheduledTransactions = new ConcurrentLinkedQueue<Pair<BBMScheduledId, BBMScheduledTransaction>>();

        try {
            VirtualMapLike.from(source)
                    .extractVirtualMapDataC(
                            getStaticThreadManager(),
                            p -> {
                                try {
                                    scheduledTransactions.add(Pair.of(
                                            fromMod(p.key().getKey()),
                                            fromMod(p.value().getValue())));
                                } catch (InvalidKeyException e) {
                                    throw new RuntimeException(e);
                                }
                            },
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

    //    @NonNull
    //    private static Map<BBMScheduledId, BBMScheduledEqualityValue> gatherModScheduledTransactionsByEquality(
    //            VirtualMap<OnDiskKey<ProtoBytes>, OnDiskValue<ScheduleList>> source) {
    //        final var r = new HashMap<BBMScheduledId, BBMScheduledEqualityValue>();
    //        final var scheduledTransactions =
    //                new ConcurrentLinkedQueue<Pair<BBMScheduledId, BBMScheduledEqualityValue>>();
    //
    //        try {
    //            VirtualMapLike.from(source)
    //                    .extractVirtualMapDataC(
    //                            getStaticThreadManager(),
    //                            p -> {
    //                                try {
    //                                    scheduledTransactions.add(Pair.of(
    //                                            fromMod(p.key().getKey()),
    //                                            fromMod(p.value().getValue())));
    //                                } catch (InvalidKeyException e) {
    //                                    throw new RuntimeException(e);
    //                                }
    //                            },
    //                            8);
    //        } catch (final InterruptedException ex) {
    //            System.err.println("*** Traversal of scheduledTransactions virtual map interrupted!");
    //            Thread.currentThread().interrupt();
    //        }
    //        while (!scheduledTransactions.isEmpty()) {
    //            final var mapping = scheduledTransactions.poll();
    //            r.put(mapping.key(), mapping.value());
    //        }
    //        return r;
    //    }

    static BBMScheduledTransaction fromMod(@NonNull final Schedule value) throws InvalidKeyException {
        return new BBMScheduledTransaction(
                value.scheduleId().scheduleNum(),
                value.adminKey() != null ? Optional.of(JKey.mapKey(value.adminKey())) : Optional.empty(),
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
                PbjConverter.fromPbj(value.originalCreateTransaction()).toByteArray(),
                PbjConverter.fromPbj(value.originalCreateTransaction()),
                PbjConverter.fromPbj(value.scheduledTransaction()),
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
