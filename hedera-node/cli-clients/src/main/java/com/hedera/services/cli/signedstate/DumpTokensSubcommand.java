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

import static com.hedera.services.cli.utils.ThingsToStrings.quoteForCsv;

import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.DumpStateCommand.KeyDetails;
import com.hedera.services.cli.signedstate.DumpStateCommand.WithFeeSummary;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Dump all fungible and non-fungible token types, from a signed state file, to a text file, in deterministic order. */
@SuppressWarnings({"java:S106", "java:S5411", "java:S4276", "java:S1192"})
// S106: "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
// S5411: "Avoid using boxed "Boolean" types directly in boolean expressions" - this rule is almost always undesirable
// S4276: "Functional Interfaces should be as specialised as possible" - would make sense if the specialized functional
//        interfaces used the same method name as the unspecialized version, but due to an unaccountable Java design
//        mistake _they do not!_
// S1192: "Define a constant instead of duplicating this literal ..." - doesn't improve readability or usability or
//        maintainability of this command line program
public class DumpTokensSubcommand {

    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path tokensPath,
            @NonNull final EnumSet<KeyDetails> keyDetails,
            @NonNull final WithFeeSummary withFeeSummary,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        new DumpTokensSubcommand(state, tokensPath, keyDetails, withFeeSummary, emitSummary, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path tokensPath;

    @NonNull
    final Verbosity verbosity;

    @NonNull
    final EnumSet<KeyDetails> keyDetails;

    @NonNull
    final WithFeeSummary withFeeSummary;

    @NonNull
    final EmitSummary emitSummary;

    DumpTokensSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path tokensPath,
            @NonNull final EnumSet<KeyDetails> keyDetails,
            @NonNull final WithFeeSummary withFeeSummary,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        this.state = state;
        this.tokensPath = tokensPath;
        this.keyDetails = keyDetails;
        this.withFeeSummary = withFeeSummary;
        this.emitSummary = emitSummary;
        this.verbosity = verbosity;
    }

    void doit() {
        System.out.printf("=== %d token types ===%n", 0);

        final var allTokens = gatherTokenTypes();
        System.out.printf(
                "=== %d fungible token types, %d non-fungible token types%n",
                allTokens.get(TokenType.FUNGIBLE_COMMON).size(),
                allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE).size());

        int reportSize;
        try (@NonNull final var writer = new Writer(tokensPath)) {
            if (emitSummary == EmitSummary.YES) reportSummary(writer, allTokens);
            reportOnTokens(writer, "fungible", allTokens.get(TokenType.FUNGIBLE_COMMON));
            reportOnTokens(writer, "non-fungible", allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE));
            if (keyDetails.contains(KeyDetails.STRUCTURE)) {
                reportOnKeyStructure(writer, "fungible", allTokens.get(TokenType.FUNGIBLE_COMMON));
                reportOnKeyStructure(writer, "non-fungible", allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE));
            }
            if (withFeeSummary == WithFeeSummary.YES) {
                reportOnFees(writer, "fungible", allTokens.get(TokenType.FUNGIBLE_COMMON));
                reportOnFees(writer, "non-fungible", allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE));
            }
            reportSize = writer.getSize();
        }

        System.out.printf("=== tokens report is %d bytes%n", reportSize);
    }

    @NonNull
    EnumMap<TokenType, Map<Long, Token>> gatherTokenTypes() {
        return new EnumMap<>(TokenType.class);
    }

    void reportSummary(@NonNull Writer writer, @NonNull EnumMap<TokenType, Map<Long, Token>> allTokens) {
        writer.writeln("=== %7d: fungible token types"
                .formatted(allTokens.get(TokenType.FUNGIBLE_COMMON).size()));
        writer.writeln("=== %7d: non-fungible token types"
                .formatted(allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE).size()));
        writer.writeln("");
    }

    /** String that separates all fields in the CSV format */
    static final String FIELD_SEPARATOR = ";";

    /** String that separates sub-fields (e.g., in lists). */
    static final String SUBFIELD_SEPARATOR = ",";

    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";
    static Function<String, String> csvQuote = s -> quoteForCsv(FIELD_SEPARATOR, s);

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final Token token,
            @NonNull final Function<Token, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(token)));
    }

    void formatToken(@NonNull final Writer writer, @NonNull final Token token) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        writer.writeln(fb);
    }

    @NonNull
    String formatHeader() {
        return "";
    }

    void reportOnTokens(
            @NonNull final Writer writer, @NonNull final String type, @NonNull final Map<Long, Token> tokens) {
        writer.writeln("=== %s token types%n".formatted(type));
        writer.writeln(formatHeader());
        tokens.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> formatToken(writer, e.getValue()));
        writer.writeln("");
    }

    void reportOnKeyStructure(
            @NonNull final Writer writer, @NonNull final String type, @NonNull final Map<Long, Token> tokens) {

        final BiConsumer<String, Function<Token, String>> map = (title, fun) -> {
            final var histogram = new HashMap<String, Integer>();

            for (@NonNull var e : tokens.entrySet()) {
                histogram.merge(fun.apply(e.getValue()), 1, Integer::sum);
            }

            writer.writeln("=== %s %s (%d distinct)%n".formatted(type, title, histogram.size()));
            histogram.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEachOrdered(e -> writer.writeln("%7d: %s".formatted(e.getValue(), e.getKey())));
            writer.writeln("");
        };
    }

    void reportOnFees(
            @NonNull final Writer writer, @NonNull final String type, @NonNull final Map<Long, Token> tokens) {
        final var histogram = new HashMap<String, Integer>();

        writer.writeln("=== %s fee schedules (%d distinct)%n".formatted(type, histogram.size()));
        histogram.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> writer.writeln("%7d: %s".formatted(e.getValue(), e.getKey())));
        writer.writeln("");
    }
}
