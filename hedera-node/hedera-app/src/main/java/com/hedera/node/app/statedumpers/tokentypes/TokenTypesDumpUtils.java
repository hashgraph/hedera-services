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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.statedumpers.DumpCheckpoint;
import com.hedera.node.app.statedumpers.legacy.EntityId;
import com.hedera.node.app.statedumpers.legacy.FcCustomFee;
import com.hedera.node.app.statedumpers.legacy.JKey;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.statedumpers.utils.LegacyTypeUtils;
import com.hedera.node.app.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.hedera.pbj.runtime.ParseException;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    static Function<String, String> csvQuote = s -> ThingsToStrings.quoteForCsv(FIELD_SEPARATOR, s);
    // spotless:off
    @NonNull
    public static List<Pair<String, BiConsumer<FieldBuilder, BBMToken>>> tokenTypeFieldFormatters = List.of(
            Pair.of(
                    "tokenType",
                    getFieldFormatter(BBMToken::tokenType, com.hedera.node.app.hapi.utils.TokenType::name)),
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
            Pair.of(
                    "autoRenewPeriod",
                    getFieldFormatter(BBMToken::autoRenewPeriod, getOptionalFormatter(Object::toString))),
            Pair.of("accountsFrozenByDefault", getFieldFormatter(BBMToken::accountsFrozenByDefault, booleanFormatter)),
            Pair.of(
                    "accountsKycGrantedByDefault",
                    getFieldFormatter(BBMToken::accountsKycGrantedByDefault, booleanFormatter)),
            Pair.of(
                    "treasuryAccount",
                    getFieldFormatter(BBMToken::treasury, getNullableFormatter(ThingsToStrings::toStringOfEntityId))),
            Pair.of(
                    "autoRenewAccount",
                    getFieldFormatter(
                            BBMToken::autoRenewAccount, getNullableFormatter(ThingsToStrings::toStringOfEntityId))),
            Pair.of(
                    "feeSchedule",
                    getFieldFormatter(
                            BBMToken::feeSchedule,
                            getNullableFormatter(
                                    getListFormatter(ThingsToStrings::toStringOfFcCustomFee, SUBFIELD_SEPARATOR)))),
            Pair.of(
                    "adminKey",
                    getFieldFormatter(BBMToken::adminKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of(
                    "feeScheduleKey",
                    getFieldFormatter(
                            BBMToken::feeScheduleKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of(
                    "frezeKey",
                    getFieldFormatter(BBMToken::freezeKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of(
                    "kycKey",
                    getFieldFormatter(BBMToken::kycKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of(
                    "pauseKey",
                    getFieldFormatter(BBMToken::pauseKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of(
                    "supplyKey",
                    getFieldFormatter(BBMToken::supplyKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of(
                    "wipeKey",
                    getFieldFormatter(BBMToken::wipeKey, getOptionalJKeyFormatter(ThingsToStrings::toStringOfJKey))));

    public static void dumpModTokenType(
            @NonNull final Path path, @NonNull final VirtualMap tokens, @NonNull final DumpCheckpoint checkpoint) {

        try (@NonNull final var writer = new Writer(path)) {
            final var allTokens = gatherTokensFromMod(tokens);
            dump(writer, allTokens);
            System.out.printf(
                    "=== mod tokens report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    public static void dump(@NonNull Writer writer, @NonNull Map<TokenType, Map<Long, BBMToken>> allTokens) {
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

    public static void reportOnTokens(
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
    public static <T> BiConsumer<FieldBuilder, BBMToken> getFieldFormatter(
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

    public static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }

    public static <T> Function<List<T>, String> getListFormatter(
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
        tokenTypeFieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, token));
        writer.writeln(fb);
    }

    static <T> Function<Optional<T>, String> getOptionalFormatter(@NonNull final Function<T, String> formatter) {
        return ot -> ot.isPresent() ? formatter.apply(ot.get()) : "";
    }

    public static Function<Optional<JKey>, String> getOptionalJKeyFormatter(
            @NonNull final Function<JKey, String> formatter) {
        return ot -> {
            if (ot.isPresent()) {
                return ot.get().isValid() ? formatter.apply(ot.get()) : "<invalid-key>";
            }
            return "";
        };
    }

    @NonNull
    static String formatHeader() {
        return tokenTypeFieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    @NonNull
    private static Map<TokenType, Map<Long, BBMToken>> gatherTokensFromMod(@NonNull final VirtualMap source) {
        final var r = new HashMap<TokenType, Map<Long, BBMToken>>();

        r.put(TokenType.FUNGIBLE_COMMON, new HashMap<>());
        r.put(TokenType.NON_FUNGIBLE_UNIQUE, new HashMap<>());

        final var threadCount = 8;
        final var allMappings = new ConcurrentLinkedQueue<Pair<TokenType, Map<Long, BBMToken>>>();
        try {

            VirtualMapMigration.extractVirtualMapDataC(
                    getStaticThreadManager(),
                    source,
                    p -> {
                        final TokenID tokenId;
                        try {
                            tokenId = TokenID.PROTOBUF.parse(p.left());
                        } catch (final ParseException e) {
                            throw new RuntimeException("Failed to parse a token ID", e);
                        }
                        final Token currentToken;
                        try {
                            currentToken = Token.PROTOBUF.parse(p.right());
                        } catch (final ParseException e) {
                            throw new RuntimeException("Failed to parse a token", e);
                        }
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

    private static BBMToken fromMod(@NonNull final Token token) {
        final var adminKey = (JKey) LegacyTypeUtils.fromPbjKey(token.adminKey()).orElse(null);
        final var feeScheduleKey =
                (JKey) LegacyTypeUtils.fromPbjKey(token.feeScheduleKey()).orElse(null);
        final var freezeKey =
                (JKey) LegacyTypeUtils.fromPbjKey(token.freezeKey()).orElse(null);
        final var kycKey = (JKey) LegacyTypeUtils.fromPbjKey(token.kycKey()).orElse(null);
        final var pauseKey = (JKey) LegacyTypeUtils.fromPbjKey(token.pauseKey()).orElse(null);
        final var supplyKey =
                (JKey) LegacyTypeUtils.fromPbjKey(token.supplyKey()).orElse(null);
        final var wipeKey = (JKey) LegacyTypeUtils.fromPbjKey(token.wipeKey()).orElse(null);
        final BBMToken tokenRes = new BBMToken(
                com.hedera.node.app.hapi.utils.TokenType.valueOf(
                        token.tokenType().protoName()),
                token.supplyType(),
                token.tokenIdOrThrow().tokenNum(),
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
                token.treasuryAccountId() != null ? idFromMod(token.treasuryAccountId()) : null,
                token.autoRenewAccountId() != null ? idFromMod(token.autoRenewAccountId()) : null,
                customFeesFromMod(token.customFees()),
                adminKey == null ? Optional.empty() : Optional.of(adminKey),
                feeScheduleKey == null ? Optional.empty() : Optional.of(feeScheduleKey),
                freezeKey == null ? Optional.empty() : Optional.of(freezeKey),
                kycKey == null ? Optional.empty() : Optional.of(kycKey),
                pauseKey == null ? Optional.empty() : Optional.of(pauseKey),
                supplyKey == null ? Optional.empty() : Optional.of(supplyKey),
                wipeKey == null ? Optional.empty() : Optional.of(wipeKey));

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
            var fcCustomFee = FcCustomFee.fromGrpc(CommonPbjConverters.fromPbj(fee));
            fcCustomFees.add(fcCustomFee);
        });
        return fcCustomFees;
    }
}
