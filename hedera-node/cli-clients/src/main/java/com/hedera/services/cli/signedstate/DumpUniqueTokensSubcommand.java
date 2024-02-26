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

package com.hedera.services.cli.signedstate;

import static com.hedera.services.cli.utils.ThingsToStrings.getMaybeStringifyByteString;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.google.common.collect.ComparisonChain;
import com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.NftNumPair;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.ThingsToStrings;
import com.hedera.services.cli.utils.Writer;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Dump all unique (serial-numbered) tokens, from a signed state file, to a text file, in deterministic order. */
@SuppressWarnings({"java:S106"})
// S106: "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
public class DumpUniqueTokensSubcommand {
    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path uniquesPath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        new DumpUniqueTokensSubcommand(state, uniquesPath, emitSummary, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path uniquesPath;

    @NonNull
    final Verbosity verbosity;

    @NonNull
    final EmitSummary emitSummary;

    DumpUniqueTokensSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path uniquesPath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        this.state = state;
        this.uniquesPath = uniquesPath;
        this.emitSummary = emitSummary;
        this.verbosity = verbosity;
    }

    void doit() {
        final var uniquesStore = state.getUniqueNFTTokens();
        System.out.printf(
                "=== %d unique tokens (%s) ===%n",
                uniquesStore.size(), uniquesStore.isVirtual() ? "virtual" : "merkle");
        final var uniques = gatherUniques(uniquesStore);
        System.out.printf("    %d unique tokens gathered%n", uniques.size());

        int reportSize;
        try (@NonNull final var writer = new Writer(uniquesPath)) {
            if (emitSummary == EmitSummary.YES) reportSummary(writer, uniques);
            reportOnUniques(writer, uniques);
            reportSize = writer.getSize();
        }

        System.out.printf("=== uniques report is %d bytes%n", reportSize);
    }

    record UniqueNFTId(long id, long serial) implements Comparable<UniqueNFTId> {

        static UniqueNFTId from(@NonNull final UniqueTokenKey ukey) {
            return new UniqueNFTId(ukey.getNum(), ukey.getTokenSerial());
        }

        static UniqueNFTId from(@NonNull final EntityNumPair enp) {
            return new UniqueNFTId(enp.getHiOrderAsLong(), enp.getLowOrderAsLong());
        }

        @Override
        public String toString() {
            return "%d%s%d".formatted(id, FIELD_SEPARATOR, serial);
        }

        @Override
        public int compareTo(UniqueNFTId o) {
            return ComparisonChain.start()
                    .compare(this.id, o.id)
                    .compare(this.serial, o.serial)
                    .result();
        }
    }

    @SuppressWarnings(
            "java:S6218") // "Equals/hashcode method should be overridden in records containing array fields" - this
    // record will never be compared or used as a key
    record UniqueNFT(
            EntityId owner,
            EntityId spender,
            @NonNull RichInstant creationTime,
            @NonNull byte[] metadata,
            @NonNull NftNumPair previous,
            @NonNull NftNumPair next) {

        static final byte[] EMPTY_BYTES = new byte[0];

        static UniqueNFT from(@NonNull final UniqueTokenValue utv) {
            return new UniqueNFT(
                    utv.getOwner(),
                    utv.getSpender(),
                    utv.getCreationTime(),
                    null != utv.getMetadata() ? utv.getMetadata() : EMPTY_BYTES,
                    utv.getPrev(),
                    utv.getNext());
        }

        static UniqueNFT from(@NonNull final MerkleUniqueToken mut) {
            return new UniqueNFT(
                    mut.getOwner(),
                    mut.getSpender(),
                    mut.getCreationTime(),
                    null != mut.getMetadata() ? mut.getMetadata() : EMPTY_BYTES,
                    mut.getPrev(),
                    mut.getNext());
        }
    }

    void reportSummary(@NonNull final Writer writer, @NonNull final Map<UniqueNFTId, UniqueNFT> uniques) {
        final var relatedEntityCounts = RelatedEntities.countRelatedEntities(uniques);
        writer.writeln("=== %7d unique tokens (%d owned by treasury accounts)"
                .formatted(uniques.size(), relatedEntityCounts.ownedByTreasury()));
        writer.writeln("    %7d null owners, %7d null or missing spenders"
                .formatted(
                        uniques.size()
                                - (relatedEntityCounts.ownersNotTreasury() + relatedEntityCounts.ownedByTreasury()),
                        uniques.size() - relatedEntityCounts.spenders()));
        writer.writeln("");
    }

    record RelatedEntities(long ownersNotTreasury, long ownedByTreasury, long spenders) {
        @NonNull
        static RelatedEntities countRelatedEntities(@NonNull final Map<UniqueNFTId, UniqueNFT> uniques) {
            final var cs = new long[3];
            uniques.values().forEach(unique -> {
                if (null != unique.owner && !unique.owner.equals(EntityId.MISSING_ENTITY_ID)) cs[0]++;
                if (null != unique.owner && unique.owner.equals(EntityId.MISSING_ENTITY_ID)) cs[1]++;
                if (null != unique.spender && !unique.spender.equals(EntityId.MISSING_ENTITY_ID)) cs[2]++;
            });
            return new RelatedEntities(cs[0], cs[1], cs[2]);
        }
    }

    /** String that separates all fields in the CSV format */
    static final String FIELD_SEPARATOR = ";";

    void reportOnUniques(@NonNull final Writer writer, @NonNull final Map<UniqueNFTId, UniqueNFT> uniques) {
        writer.writeln(formatHeader());
        uniques.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatUnique(writer, e.getKey(), e.getValue()));
        writer.writeln("");
    }

    @NonNull
    String formatHeader() {
        return "nftId,nftSerial,"
                + fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    // spotless:off
    @NonNull static List<Pair<String,BiConsumer<FieldBuilder,UniqueNFT>>> fieldFormatters = List.of(
            Pair.of("owner", getFieldFormatter(UniqueNFT::owner, ThingsToStrings::toStringOfEntityId)),
            Pair.of("spender",getFieldFormatter(UniqueNFT::spender, ThingsToStrings::toStringOfEntityId)),
            Pair.of("creationTime", getFieldFormatter(UniqueNFT::creationTime, ThingsToStrings::toStringOfRichInstant)),
            Pair.of("metadata", getFieldFormatter(UniqueNFT::metadata, getMaybeStringifyByteString(FIELD_SEPARATOR))),
            Pair.of("prev", getFieldFormatter(UniqueNFT::previous, Object::toString)),
            Pair.of("next", getFieldFormatter(UniqueNFT::next, Object::toString))
    );
    // spotless:on

    @NonNull
    static <T> BiConsumer<FieldBuilder, UniqueNFT> getFieldFormatter(
            @NonNull final Function<UniqueNFT, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> formatField(fb, u, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final UniqueNFT unique,
            @NonNull final Function<UniqueNFT, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(unique)));
    }

    void formatUnique(@NonNull final Writer writer, @NonNull final UniqueNFTId id, @NonNull final UniqueNFT unique) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fb.append(id.toString());
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, unique));
        writer.writeln(fb);
    }

    @NonNull
    Map<UniqueNFTId, UniqueNFT> gatherUniques(@NonNull final UniqueTokenMapAdapter uniquesStore) {
        final var r = new HashMap<UniqueNFTId, UniqueNFT>();
        if (uniquesStore.isVirtual()) {
            // world of VirtualMapLike<UniqueTokenKey, UniqueTokenValue>
            final var threadCount = 8; // Good enough for my laptop, why not?
            final var keys = new ConcurrentLinkedQueue<UniqueNFTId>();
            final var values = new ConcurrentLinkedQueue<UniqueNFT>();
            try {
                uniquesStore
                        .virtualMap()
                        .extractVirtualMapDataC(
                                getStaticThreadManager(),
                                p -> {
                                    keys.add(UniqueNFTId.from(p.left()));
                                    values.add(UniqueNFT.from(p.right()));
                                },
                                threadCount);
            } catch (final InterruptedException ex) {
                System.err.println("*** Traversal of uniques virtual map interrupted!");
                Thread.currentThread().interrupt();
            }
            // Consider in the future: Use another thread to pull things off the queue as they're put on by the
            // virtual map traversal
            while (!keys.isEmpty()) {
                r.put(keys.poll(), values.poll());
            }
        } else {
            // world of MerkleMap<EntityNumPair, MerkleUniqueToken>
            uniquesStore
                    .merkleMap()
                    .getIndex()
                    .forEach((key, value) -> r.put(UniqueNFTId.from(key), UniqueNFT.from(value)));
        }
        return r;
    }
}
