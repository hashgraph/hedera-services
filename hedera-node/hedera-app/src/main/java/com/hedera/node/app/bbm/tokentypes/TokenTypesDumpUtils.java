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

package com.hedera.node.app.bbm.tokentypes;

import static com.hedera.node.app.bbm.utils.ThingsToStrings.quoteForCsv;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.bbm.DumpCheckpoint;
import com.hedera.node.app.bbm.utils.FieldBuilder;
import com.hedera.node.app.bbm.utils.ThingsToStrings;
import com.hedera.node.app.bbm.utils.Writer;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.base.utility.Pair;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TokenTypesDumpUtils {

    /** String that separates all fields in the CSV format */
    static final String FIELD_SEPARATOR = ";";
    /** String that separates sub-fields (e.g., in lists). */
    static final String SUBFIELD_SEPARATOR = ",";

    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";
    static Function<String, String> csvQuote = s -> quoteForCsv(FIELD_SEPARATOR, s);
    // spotless:off
    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, Token>>> fieldFormatters = List.of(
            Pair.of("tokenType", getFieldFormatter(Token::tokenType, com.hedera.node.app.service.evm.store.tokens.TokenType::name)),
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
            Pair.of("adminKey", getFieldFormatter(Token::adminKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("feeScheduleKey", getFieldFormatter(Token::feeScheduleKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("frezeKey", getFieldFormatter(Token::freezeKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("kycKey", getFieldFormatter(Token::kycKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("pauseKey", getFieldFormatter(Token::pauseKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("supplyKey", getFieldFormatter(Token::supplyKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("wipeKey", getFieldFormatter(Token::wipeKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))));
    // spotless:on

    public static void dumpModTokenType(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<TokenID>, OnDiskValue<com.hedera.hapi.node.state.token.Token>> tokens,
            @NonNull final DumpCheckpoint checkpoint) {

        try (@NonNull final var writer = new Writer(path)) {
            final var allTokens = gatherTokensFromMod(tokens, Token::fromMod);
            dump(writer, allTokens);
            System.out.printf(
                    "=== mod tokens report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    public static void dumpMonoTokenType(
            @NonNull final Path path,
            @NonNull final MerkleMap<EntityNum, MerkleToken> tokens,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var allTokens = gatherTokensFromMono(tokens);
            dump(writer, allTokens);
            System.out.printf(
                    "=== mono tokens report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    private static Map<TokenType, Map<Long, Token>> gatherTokensFromMono(
            @NonNull final MerkleMap<EntityNum, MerkleToken> source) {

        final var allTokens = new HashMap<TokenType, Map<Long, Token>>();

        allTokens.put(TokenType.FUNGIBLE_COMMON, new HashMap<>());
        allTokens.put(TokenType.NON_FUNGIBLE_UNIQUE, new HashMap<>());

        // todo check if it is possible to use multi threading with MerkleMaps like VirtualMaps
        MerkleMapLike.from(source).forEachNode((en, mt) -> allTokens
                .get(TokenType.fromProtobufOrdinal(mt.tokenType().ordinal()))
                .put(en.longValue(), Token.fromMono(mt)));
        return allTokens;
    }

    @NonNull
    private static Map<TokenType, Map<Long, Token>> gatherTokensFromMod(
            @NonNull final VirtualMap<OnDiskKey<TokenID>, OnDiskValue<com.hedera.hapi.node.state.token.Token>> source,
            @NonNull final Function<com.hedera.hapi.node.state.token.Token, Token> valueMapper) {
        final var r = new HashMap<TokenType, Map<Long, Token>>();

        r.put(TokenType.FUNGIBLE_COMMON, new HashMap<>());
        r.put(TokenType.NON_FUNGIBLE_UNIQUE, new HashMap<>());

        final var threadCount = 8;
        final var allMappings = new ConcurrentLinkedQueue<Pair<TokenType, Map<Long, Token>>>();
        try {

            VirtualMapLike.from(source)
                    .extractVirtualMapDataC(
                            getStaticThreadManager(),
                            p -> {
                                var tokenId = p.left().getKey();
                                var currentToken = p.right().getValue();
                                var tokenMap = new HashMap<Long, Token>();
                                tokenMap.put(tokenId.tokenNum(), valueMapper.apply(currentToken));
                                allMappings.add(Pair.of(currentToken.tokenType(), tokenMap));
                            },
                            threadCount);

        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of uniques virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        while (!allMappings.isEmpty()) {
            final var mapping = allMappings.poll();
            r.get(mapping.left()).putAll(mapping.value());
        }
        return r;
    }

    private static void dump(@NonNull Writer writer, @NonNull Map<TokenType, Map<Long, Token>> allTokens) {
        reportSummary(writer, allTokens);

        reportOnTokens(writer, "fungible", allTokens.get(TokenType.FUNGIBLE_COMMON));
        reportOnTokens(writer, "non-fungible", allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE));

        reportOnKeyStructure(writer, "fungible", allTokens.get(TokenType.FUNGIBLE_COMMON));
        reportOnKeyStructure(writer, "non-fungible", allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE));

        reportOnFees(writer, "fungible", allTokens.get(TokenType.FUNGIBLE_COMMON));
        reportOnFees(writer, "non-fungible", allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE));
    }

    private static void reportSummary(@NonNull Writer writer, @NonNull Map<TokenType, Map<Long, Token>> allTokens) {
        writer.writeln("=== %7d: fungible token types"
                .formatted(allTokens.get(TokenType.FUNGIBLE_COMMON).size()));
        writer.writeln("=== %7d: non-fungible token types"
                .formatted(allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE).size()));
        writer.writeln("");
    }

    private static void reportOnTokens(
            @NonNull final Writer writer, @NonNull final String type, @NonNull final Map<Long, Token> tokens) {
        writer.writeln("=== %s token types%n".formatted(type));
        writer.writeln(formatHeader());
        tokens.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> formatToken(writer, e.getValue()));
        writer.writeln("");
    }

    private static void reportOnKeyStructure(
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

    private static void reportOnFees(
            @NonNull final Writer writer, @NonNull final String type, @NonNull final Map<Long, Token> tokens) {
        final var histogram = new HashMap<String, Integer>();
        for (@NonNull var token : tokens.values()) {
            final var fees = token.feeSchedule();
            if (null == fees || fees.isEmpty()) continue;
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

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }

    static <T> Function<List<T>, String> getListFormatter(
            @NonNull final Function<T, String> formatter, @NonNull final String subfieldSeparator) {
        return lt -> {
            if (!lt.isEmpty()) {
                final var sb = new StringBuilder();
                for (@NonNull final var e : lt) {
                    final var v = formatter.apply(e);
                    sb.append(v);
                    sb.append(subfieldSeparator);
                }
                // Remove last subfield separator
                if (sb.length() >= subfieldSeparator.length()) sb.setLength(sb.length() - subfieldSeparator.length());
                return sb.toString();
            } else return "";
        };
    }

    static void formatToken(@NonNull final Writer writer, @NonNull final Token token) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, token));
        writer.writeln(fb);
    }

    static <T> Function<Optional<T>, String> getOptionalFormatter(@NonNull final Function<T, String> formatter) {
        return ot -> ot.isPresent() ? formatter.apply(ot.get()) : "";
    }

    static Function<Optional<JKey>, String> getOptionalJKeyFormatter(@NonNull final Function<JKey, String> formatter) {
        return ot -> {
            if (ot.isPresent()) {
                return ot.get().isValid() ? formatter.apply(ot.get()) : "<invalid-key>";
            }
            return "";
        };
    }

    @NonNull
    static String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    public static boolean jkeyPresentAndOk(@NonNull Optional<JKey> ojkey) {
        if (ojkey.isEmpty()) return false;
        if (ojkey.get().isEmpty()) return false;
        return ojkey.get().isValid();
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
}
