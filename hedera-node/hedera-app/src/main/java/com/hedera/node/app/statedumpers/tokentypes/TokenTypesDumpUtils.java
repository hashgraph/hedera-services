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

package com.hedera.node.app.statedumpers.tokentypes;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey;
import static com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings.quoteForCsv;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.tokentypes.BBMToken;
import com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
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
    static List<Pair<String, BiConsumer<FieldBuilder, BBMToken>>> fieldFormatters = List.of(
            Pair.of("tokenType", getFieldFormatter(BBMToken::tokenType, com.hedera.node.app.service.evm.store.tokens.TokenType::name)),
            Pair.of("tokenSupplyType", getFieldFormatter(BBMToken::tokenSupplyType, TokenSupplyType::name)),
            Pair.of("tokenTypeId", getFieldFormatter(BBMToken::tokenTypeId, Object::toString)),
            Pair.of("symbol", getFieldFormatter(BBMToken::symbol, csvQuote)),
            Pair.of("name", getFieldFormatter(BBMToken::name, csvQuote)),
            Pair.of("memo", getFieldFormatter(BBMToken::memo, csvQuote)),
            Pair.of("isDeleted", getFieldFormatter(BBMToken::deleted, booleanFormatter)),
            Pair.of("isPaused", getFieldFormatter(BBMToken::paused, booleanFormatter)),
            Pair.of("decimals", getFieldFormatter(BBMToken::decimals, Object::toString)),
            Pair.of("maxSupply", getFieldFormatter(BBMToken::maxSupply, Object::toString)),
            Pair.of("totalSupply", getFieldFormatter(BBMToken::totalSupply, Object::toString)),
            Pair.of("lastUsedSerialNumber", getFieldFormatter(BBMToken::lastUsedSerialNumber, Object::toString)),
            Pair.of("expiry", getFieldFormatter(BBMToken::expiry, Object::toString)),
            Pair.of("autoRenewPeriod", getFieldFormatter(BBMToken::autoRenewPeriod, getOptionalFormatter(Object::toString))),
            Pair.of("accountsFrozenByDefault", getFieldFormatter(BBMToken::accountsFrozenByDefault, booleanFormatter)),
            Pair.of("accountsKycGrantedByDefault", getFieldFormatter(BBMToken::accountsKycGrantedByDefault, booleanFormatter)),
            Pair.of("treasuryAccount", getFieldFormatter(BBMToken::treasury, getNullableFormatter(ThingsToStrings::toStringOfEntityId))),
            Pair.of("autoRenewAccount", getFieldFormatter(BBMToken::autoRenewAccount, getNullableFormatter(ThingsToStrings::toStringOfEntityId))),
            Pair.of("feeSchedule", getFieldFormatter(BBMToken::feeSchedule,
                    getNullableFormatter(getListFormatter(ThingsToStrings::toStringOfFcCustomFee, SUBFIELD_SEPARATOR)))),
            Pair.of("adminKey", getFieldFormatter(BBMToken::adminKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("feeScheduleKey", getFieldFormatter(BBMToken::feeScheduleKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("frezeKey", getFieldFormatter(BBMToken::freezeKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("kycKey", getFieldFormatter(BBMToken::kycKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("pauseKey", getFieldFormatter(BBMToken::pauseKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("supplyKey", getFieldFormatter(BBMToken::supplyKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("wipeKey", getFieldFormatter(BBMToken::wipeKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))));
    // spotless:on

    public static void dumpModTokenType(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<TokenID>, OnDiskValue<Token>> tokens,
            @NonNull final DumpCheckpoint checkpoint) {

        try (@NonNull final var writer = new Writer(path)) {
            final var allTokens = gatherTokensFromMod(tokens);
            dump(writer, allTokens);
            System.out.printf(
                    "=== mod tokens report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    private static Map<TokenType, Map<Long, BBMToken>> gatherTokensFromMod(
            @NonNull final VirtualMap<OnDiskKey<TokenID>, OnDiskValue<Token>> source) {
        final var r = new HashMap<TokenType, Map<Long, BBMToken>>();

        r.put(TokenType.FUNGIBLE_COMMON, new HashMap<>());
        r.put(TokenType.NON_FUNGIBLE_UNIQUE, new HashMap<>());

        final var threadCount = 8;
        final var allMappings = new ConcurrentLinkedQueue<Pair<TokenType, Map<Long, BBMToken>>>();
        try {

            VirtualMapLike.from(source)
                    .extractVirtualMapDataC(
                            getStaticThreadManager(),
                            p -> {
                                var tokenId = p.left().getKey();
                                var currentToken = p.right().getValue();
                                var tokenMap = new HashMap<Long, BBMToken>();
                                tokenMap.put(tokenId.tokenNum(), fromMod(currentToken));
                                allMappings.add(Pair.of(currentToken.tokenType(), tokenMap));
                            },
                            threadCount);

        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of token types virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        while (!allMappings.isEmpty()) {
            final var mapping = allMappings.poll();
            r.get(mapping.left()).putAll(mapping.value());
        }
        return r;
    }

    private static void dump(@NonNull Writer writer, @NonNull Map<TokenType, Map<Long, BBMToken>> allTokens) {
        reportSummary(writer, allTokens);

        reportOnTokens(writer, "fungible", allTokens.get(TokenType.FUNGIBLE_COMMON));
        reportOnTokens(writer, "non-fungible", allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE));

        reportOnKeyStructure(writer, "fungible", allTokens.get(TokenType.FUNGIBLE_COMMON));
        reportOnKeyStructure(writer, "non-fungible", allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE));

        reportOnFees(writer, "fungible", allTokens.get(TokenType.FUNGIBLE_COMMON));
        reportOnFees(writer, "non-fungible", allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE));
    }

    private static void reportSummary(@NonNull Writer writer, @NonNull Map<TokenType, Map<Long, BBMToken>> allTokens) {
        writer.writeln("=== %7d: fungible token types"
                .formatted(allTokens.get(TokenType.FUNGIBLE_COMMON).size()));
        writer.writeln("=== %7d: non-fungible token types"
                .formatted(allTokens.get(TokenType.NON_FUNGIBLE_UNIQUE).size()));
        writer.writeln("");
    }

    private static void reportOnTokens(
            @NonNull final Writer writer, @NonNull final String type, @NonNull final Map<Long, BBMToken> tokens) {
        writer.writeln("=== %s token types%n".formatted(type));
        writer.writeln(formatHeader());
        tokens.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> formatToken(writer, e.getValue()));
        writer.writeln("");
    }

    private static void reportOnKeyStructure(
            @NonNull final Writer writer, @NonNull final String type, @NonNull final Map<Long, BBMToken> tokens) {

        final BiConsumer<String, Function<BBMToken, String>> map = (title, fun) -> {
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

        map.accept("key structures", BBMToken::getKeyStructure);
        map.accept("key role profiles", BBMToken::getKeyProfile);
        map.accept("key complexity", BBMToken::getKeyComplexity);
    }

    private static void reportOnFees(
            @NonNull final Writer writer, @NonNull final String type, @NonNull final Map<Long, BBMToken> tokens) {
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
    static <T> BiConsumer<FieldBuilder, BBMToken> getFieldFormatter(
            @NonNull final Function<BBMToken, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMToken token,
            @NonNull final Function<BBMToken, T> fun,
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

    static void formatToken(@NonNull final Writer writer, @NonNull final BBMToken token) {
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

    private static BBMToken fromMod(@NonNull final Token token) {
        BBMToken tokenRes;

        tokenRes = new BBMToken(
                com.hedera.node.app.service.evm.store.tokens.TokenType.valueOf(
                        token.tokenType().protoName()),
                token.supplyType(),
                token.tokenId().tokenNum(),
                token.symbol(),
                token.name(),
                token.memo(),
                token.deleted(),
                token.paused(),
                token.decimals(),
                token.maxSupply(),
                token.totalSupply(),
                token.lastUsedSerialNumber(),
                token.expirationSecond(),
                token.autoRenewSeconds() == -1L ? Optional.empty() : Optional.of(token.autoRenewSeconds()),
                token.accountsFrozenByDefault(),
                token.accountsKycGrantedByDefault(),
                idFromMod(token.treasuryAccountId()),
                idFromMod(token.autoRenewAccountId()),
                customFeesFromMod(token.customFees()),
                Optional.of((JKey) fromPbjKey(token.adminKey()).orElse(null)),
                Optional.of((JKey) fromPbjKey(token.feeScheduleKey()).orElse(null)),
                Optional.of((JKey) fromPbjKey(token.freezeKey()).orElse(null)),
                Optional.of((JKey) fromPbjKey(token.kycKey()).orElse(null)),
                Optional.of((JKey) fromPbjKey(token.pauseKey()).orElse(null)),
                Optional.of((JKey) fromPbjKey(token.supplyKey()).orElse(null)),
                Optional.of((JKey) fromPbjKey(token.wipeKey()).orElse(null)));

        Objects.requireNonNull(tokenRes.tokenType(), "tokenType");
        Objects.requireNonNull(tokenRes.tokenSupplyType(), "tokenSupplyType");
        Objects.requireNonNull(tokenRes.symbol(), "symbol");
        Objects.requireNonNull(tokenRes.name(), "name");
        Objects.requireNonNull(tokenRes.memo(), "memo");
        Objects.requireNonNull(tokenRes.adminKey(), "adminKey");
        Objects.requireNonNull(tokenRes.feeScheduleKey(), "feeScheduleKey");
        Objects.requireNonNull(tokenRes.freezeKey(), "freezeKey");
        Objects.requireNonNull(tokenRes.kycKey(), "kycKey");
        Objects.requireNonNull(tokenRes.pauseKey(), "pauseKey");
        Objects.requireNonNull(tokenRes.supplyKey(), "supplyKey");
        Objects.requireNonNull(tokenRes.wipeKey(), "wipeKey");

        return tokenRes;
    }

    private static EntityId idFromMod(@Nullable final AccountID accountId) {
        return null == accountId ? EntityId.MISSING_ENTITY_ID : new EntityId(0L, 0L, accountId.accountNumOrThrow());
    }

    private static List<FcCustomFee> customFeesFromMod(List<CustomFee> customFees) {
        List<FcCustomFee> fcCustomFees = new ArrayList<>();
        customFees.stream().forEach(fee -> {
            var fcCustomFee = FcCustomFee.fromGrpc(PbjConverter.fromPbj(fee));
            fcCustomFees.add(fcCustomFee);
        });
        return fcCustomFees;
    }

    private static Optional<JKey> keyFromMod(@Nullable Key key) {
        try {
            return key == null ? Optional.empty() : Optional.ofNullable(JKey.mapKey(key));
        } catch (InvalidKeyException invalidKeyException) {
            // return invalid JKey
            return Optional.of(new JKey() {
                @Override
                public boolean isEmpty() {
                    return true;
                }

                @Override
                public boolean isValid() {
                    return false;
                }
            });
        }
    }
    // spotless:off
    @NonNull
    private static final Map<Character, Function<BBMToken, Optional<JKey>>> KEYS = new TreeMap<>(Map.of(
            'A', BBMToken::adminKey,
            'F', BBMToken::feeScheduleKey,
            'K', BBMToken::kycKey,
            'P', BBMToken::pauseKey,
            'S', BBMToken::supplyKey,
            'W', BBMToken::wipeKey,
            'Z', BBMToken::freezeKey));
}
