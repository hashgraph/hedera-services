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

import com.google.common.collect.ComparisonChain;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Map;

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
        System.out.printf("=== %d unique tokens (%s) ===%n", 0, "virtual");
        final var uniques = gatherUniques();
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
    void reportSummary(@NonNull final Writer writer, @NonNull final Map<UniqueNFTId, Nft> uniques) {
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
        static RelatedEntities countRelatedEntities(@NonNull final Map<UniqueNFTId, Nft> uniques) {
            final var cs = new long[3];
            uniques.values().forEach(unique -> {});
            return new RelatedEntities(cs[0], cs[1], cs[2]);
        }
    }

    /** String that separates all fields in the CSV format */
    static final String FIELD_SEPARATOR = ";";

    void reportOnUniques(@NonNull final Writer writer, @NonNull final Map<UniqueNFTId, Nft> uniques) {
        writer.writeln(formatHeader());
        writer.writeln("");
    }

    @NonNull
    String formatHeader() {
        return "nftId,nftSerial,";
    }

    // spotless:off

    void formatUnique(@NonNull final Writer writer, @NonNull final UniqueNFTId id, @NonNull final Nft unique) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fb.append(id.toString());
        writer.writeln(fb);
    }

    @NonNull
    Map<UniqueNFTId, Nft> gatherUniques() {
        return Map.of();
    }
}
