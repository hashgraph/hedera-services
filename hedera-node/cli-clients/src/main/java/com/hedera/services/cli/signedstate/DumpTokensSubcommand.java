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

import static com.hedera.services.cli.utils.Formatters.getListFormatter;
import static com.hedera.services.cli.utils.Formatters.getNullableFormatter;
import static com.hedera.services.cli.utils.Formatters.getOptionalFormatter;
import static com.hedera.services.cli.utils.ThingsToStrings.quoteForCsv;
import static com.hedera.services.cli.utils.ThingsToStrings.toStructureSummaryOfJKey;

import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.enums.TokenSupplyType;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.DumpStateCommand.KeyDetails;
import com.hedera.services.cli.signedstate.DumpStateCommand.WithFeeSummary;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.ThingsToStrings;
import com.hedera.services.cli.utils.Writer;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        final var tokensStore = state.getFungibleTokenTypes();
        System.out.printf("=== %d token types ===%n", tokensStore.size());

        final var allTokens = gatherTokenTypes(tokensStore);
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

    record Token(
            @NonNull TokenType tokenType,
            @NonNull TokenSupplyType tokenSupplyType,
            long tokenTypeId, // this is the field `number` with setter/getter `getKey/setKey`
            @NonNull String symbol,
            @NonNull String name,
            @NonNull String memo,
            boolean deleted,
            boolean paused,
            long decimals,
            long maxSupply,
            long totalSupply,
            long lastUsedSerialNumber,
            long expiry,
            @NonNull Optional<Long> autoRenewPeriod,
            boolean accountsFrozenByDefault,
            boolean accountsKycGrantedByDefault,
            @Nullable EntityId treasury,
            @Nullable EntityId autoRenewAccount,
            @Nullable List<FcCustomFee> feeSchedule,
            @NonNull Optional<JKey> adminKey,
            @NonNull Optional<JKey> feeScheduleKey,
            @NonNull Optional<JKey> freezeKey,
            @NonNull Optional<JKey> kycKey,
            @NonNull Optional<JKey> pauseKey,
            @NonNull Optional<JKey> supplyKey,
            @NonNull Optional<JKey> wipeKey) {

        Token(@NonNull final MerkleToken token) {
            this(
                    token.tokenType(),
                    token.supplyType(),
                    token.getKey().longValue(),
                    token.symbol(),
                    token.name(),
                    token.memo(),
                    token.isDeleted(),
                    token.isPaused(),
                    token.decimals(),
                    token.maxSupply(),
                    token.totalSupply(),
                    token.getLastUsedSerialNumber(),
                    token.expiry(),
                    token.autoRenewPeriod() == -1L ? Optional.empty() : Optional.of(token.autoRenewPeriod()),
                    token.accountsAreFrozenByDefault(),
                    token.accountsKycGrantedByDefault(),
                    token.treasury(),
                    token.autoRenewAccount(),
                    token.customFeeSchedule(),
                    token.adminKey(),
                    token.feeScheduleKey(),
                    token.freezeKey(),
                    token.kycKey(),
                    token.pauseKey(),
                    token.supplyKey(),
                    token.wipeKey());
            Objects.requireNonNull(tokenType, "tokenType");
            Objects.requireNonNull(tokenSupplyType, "tokenSupplyType");
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(memo, "memo");
            Objects.requireNonNull(adminKey, "adminKey");
            Objects.requireNonNull(feeScheduleKey, "feeScheduleKey");
            Objects.requireNonNull(freezeKey, "freezeKey");
            Objects.requireNonNull(kycKey, "kycKey");
            Objects.requireNonNull(pauseKey, "pauseKey");
            Objects.requireNonNull(supplyKey, "supplyKey");
            Objects.requireNonNull(wipeKey, "wipeKey");
        }

        @NonNull
        String getKeyProfile() {
            final var adminKeyOk = jkeyPresentAndOk(adminKey);

            return getKeyDescription((c, ojk) -> {
                if (!jkeyPresentAndOk(ojk)) return "    ";
                if (!adminKeyOk) return c + "   ";
                if (c == 'A') return "A   ";
                if (jkeyDeepEqualsButBothNullIsFalse(ojk.get(), adminKey.get())) return c + "=A ";
                return c + "   ";
            });
        }

        String getKeyComplexity() {
            return getKeyDescription((c, ojk) -> {
                if (!jkeyPresentAndOk(ojk)) return "   ";
                if (jkeyIsComplex(ojk.get())) return c + "! ";
                return c + "  ";
            });
        }

        String getKeyStructure() {
            final var r = getKeyDescription((c, ojk) -> {
                if (!jkeyPresentAndOk(ojk)) return "";
                final var sb = new StringBuilder();
                final var b = toStructureSummaryOfJKey(sb, ojk.get());
                if (!b) return "";
                return c + ":" + sb + "; ";
            });
            return r.isEmpty() ? "" : r.substring(0, r.length() - 2);
        }

        // spotless:off
        @NonNull
        private static final Map<Character, Function<Token, Optional<JKey>>> KEYS = new TreeMap<>(Map.of(
                'A', Token::adminKey,
                'F', Token::feeScheduleKey,
                'K', Token::kycKey,
                'P', Token::pauseKey,
                'S', Token::supplyKey,
                'W', Token::wipeKey,
                'Z', Token::freezeKey));
        // spotless:on

        @NonNull
        private String getKeyDescription(@NonNull final BiFunction<Character, Optional<JKey>, String> map) {
            return KEYS.entrySet().stream()
                    .map(e -> map.apply(e.getKey(), e.getValue().apply(this)))
                    .collect(Collectors.joining());
        }
    }

    @NonNull
    EnumMap<TokenType, Map<Long, Token>> gatherTokenTypes(
            @NonNull final MerkleMapLike<EntityNum, MerkleToken> tokensStore) {

        final var allTokens = new EnumMap<TokenType, Map<Long, Token>>(TokenType.class);
        allTokens.put(TokenType.FUNGIBLE_COMMON, new HashMap<>());
        allTokens.put(TokenType.NON_FUNGIBLE_UNIQUE, new HashMap<>());

        tokensStore.forEachNode((en, mt) -> allTokens.get(mt.tokenType()).put(en.longValue(), new Token(mt)));
        return allTokens;
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

    // spotless:off
    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, Token>>> fieldFormatters = List.of(
            Pair.of("tokenType", getFieldFormatter(Token::tokenType, TokenType::name)),
            Pair.of("tokenSupplyType", getFieldFormatter(Token::tokenSupplyType, TokenSupplyType::name)),
            Pair.of("tokenTypeId", getFieldFormatter(Token::tokenTypeId, Object::toString)),
            Pair.of("symbol", getFieldFormatter(Token::symbol, csvQuote)),
            Pair.of("name", getFieldFormatter(Token::name, csvQuote)),
            Pair.of("memo", getFieldFormatter(Token::memo, csvQuote)),
            Pair.of("isDeleted", getFieldFormatter(Token::deleted, booleanFormatter)),
            Pair.of("isPaused", getFieldFormatter(Token::paused, booleanFormatter)),
            Pair.of("decimals", getFieldFormatter(Token::decimals, Object::toString)),
            Pair.of("maxSupply", getFieldFormatter(Token::maxSupply, Object::toString)),
            Pair.of("totalSupply", getFieldFormatter(Token::totalSupply, Object::toString)),
            Pair.of("lastUsedSerialNumber", getFieldFormatter(Token::lastUsedSerialNumber, Object::toString)),
            Pair.of("expiry", getFieldFormatter(Token::expiry, Object::toString)),
            Pair.of("autoRenewPeriod", getFieldFormatter(Token::autoRenewPeriod, getOptionalFormatter(Object::toString))),
            Pair.of("accountsFrozenByDefault", getFieldFormatter(Token::accountsFrozenByDefault, booleanFormatter)),
            Pair.of("accountsKycGrantedByDefault", getFieldFormatter(Token::accountsKycGrantedByDefault, booleanFormatter)),
            Pair.of("treasuryAccount", getFieldFormatter(Token::treasury, getNullableFormatter(ThingsToStrings::toStringOfEntityId))),
            Pair.of("autoRenewAccount", getFieldFormatter(Token::autoRenewAccount, getNullableFormatter(ThingsToStrings::toStringOfEntityId))),
            Pair.of("feeSchedule", getFieldFormatter(Token::feeSchedule,
                    getNullableFormatter(getListFormatter(ThingsToStrings::toStringOfFcCustomFee, SUBFIELD_SEPARATOR)))),
            Pair.of("adminKey", getFieldFormatter(Token::adminKey, getOptionalFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("feeScheduleKey", getFieldFormatter(Token::feeScheduleKey, getOptionalFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("frezeKey", getFieldFormatter(Token::freezeKey, getOptionalFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("kycKey", getFieldFormatter(Token::kycKey, getOptionalFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("pauseKey", getFieldFormatter(Token::pauseKey, getOptionalFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("supplyKey", getFieldFormatter(Token::supplyKey, getOptionalFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("wipeKey", getFieldFormatter(Token::wipeKey, getOptionalFormatter(ThingsToStrings::toStringOfJKey))));
    // spotless:on

    @NonNull
    static <T> BiConsumer<FieldBuilder, Token> getFieldFormatter(
            @NonNull final Function<Token, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final Token token,
            @NonNull final Function<Token, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(token)));
    }

    void formatToken(@NonNull final Writer writer, @NonNull final Token token) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, token));
        writer.writeln(fb);
    }

    @NonNull
    String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
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

        map.accept("key structures", Token::getKeyStructure);
        map.accept("key role profiles", Token::getKeyProfile);
        map.accept("key complexity", Token::getKeyComplexity);
    }

    static boolean jkeyDeepEqualsButBothNullIsFalse(final JKey left, final JKey right) {
        if (left == null || right == null) return false;
        return left.equals(right);
    }

    /** A "complex" key is a keylist with >1 key or a threshold key with >1 key. If a keylist has one key or if a
     * threshold key is 1-of-1 then the complexity is the complexity of the contained key.  Otherwise, it is not
     * complex. */
    static boolean jkeyIsComplex(final JKey jkey) {
        if (jkey == null) return false;
        if (jkey.isEmpty()) return false;
        if (!jkey.isValid()) return false;
        if (jkey.hasThresholdKey()) {
            final var jThresholdKey = jkey.getThresholdKey();
            final var th = jThresholdKey.getThreshold();
            final var n = jThresholdKey.getKeys().getKeysList().size();
            if (th == 1 && n == 1)
                return jkeyIsComplex(jThresholdKey.getKeys().getKeysList().get(0));
            return true;
        } else if (jkey.hasKeyList()) {
            final var n = jkey.getKeyList().getKeysList().size();
            if (n == 1) return jkeyIsComplex(jkey.getKeyList().getKeysList().get(0));
            return true;
        } else return false;
    }

    /** Returns true iff jkey is valid (thus not null and not empty) */
    static boolean jkeyPresentAndOk(@NonNull Optional<JKey> ojkey) {
        if (ojkey.isEmpty()) return false;
        if (ojkey.get().isEmpty()) return false;
        return ojkey.get().isValid();
    }

    void reportOnFees(
            @NonNull final Writer writer, @NonNull final String type, @NonNull final Map<Long, Token> tokens) {
        final var histogram = new HashMap<String, Integer>();
        for (@NonNull var token : tokens.values()) {
            final var fees = token.feeSchedule();
            if (null == fees) continue;
            final var feeProfile = fees.stream()
                    .map(ThingsToStrings::toSketchyStringOfFcCustomFee)
                    .sorted()
                    .collect(Collectors.joining(SUBFIELD_SEPARATOR));
            histogram.merge(feeProfile, 1, Integer::sum);
        }

        writer.writeln("=== %s fee schedules (%d distinct)%n".formatted(type, histogram.size()));
        histogram.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> writer.writeln("%7d: %s".formatted(e.getValue(), e.getKey())));
        writer.writeln("");
    }
}
