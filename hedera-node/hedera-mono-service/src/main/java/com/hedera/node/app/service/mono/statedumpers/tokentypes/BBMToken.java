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

package com.hedera.node.app.service.mono.statedumpers.tokentypes;

import static com.hedera.node.app.service.mono.statedumpers.tokentypes.TokenTypesDumpUtils.jkeyDeepEqualsButBothNullIsFalse;
import static com.hedera.node.app.service.mono.statedumpers.tokentypes.TokenTypesDumpUtils.jkeyIsComplex;
import static com.hedera.node.app.service.mono.statedumpers.tokentypes.TokenTypesDumpUtils.jkeyPresentAndOk;

import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public record BBMToken(
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

    static BBMToken fromMono(@NonNull final MerkleToken token) {
        var tokenRes = new BBMToken(
                token.tokenType(),
                supplyTypeFromMono(token.supplyType()),
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
        Objects.requireNonNull(tokenRes.tokenType, "tokenType");
        Objects.requireNonNull(tokenRes.tokenSupplyType, "tokenSupplyType");
        Objects.requireNonNull(tokenRes.symbol, "symbol");
        Objects.requireNonNull(tokenRes.name, "name");
        Objects.requireNonNull(tokenRes.memo, "memo");
        Objects.requireNonNull(tokenRes.adminKey, "adminKey");
        Objects.requireNonNull(tokenRes.feeScheduleKey, "feeScheduleKey");
        Objects.requireNonNull(tokenRes.freezeKey, "freezeKey");
        Objects.requireNonNull(tokenRes.kycKey, "kycKey");
        Objects.requireNonNull(tokenRes.pauseKey, "pauseKey");
        Objects.requireNonNull(tokenRes.supplyKey, "supplyKey");
        Objects.requireNonNull(tokenRes.wipeKey, "wipeKey");

        return tokenRes;
    }

    static TokenSupplyType supplyTypeFromMono(
            @NonNull com.hedera.node.app.service.mono.state.enums.TokenSupplyType tokenSupplyType) {
        return (tokenSupplyType.equals(com.hedera.node.app.service.mono.state.enums.TokenSupplyType.INFINITE))
                ? TokenSupplyType.INFINITE
                : TokenSupplyType.FINITE;
    }

    @NonNull
    public String getKeyProfile() {
        final var adminKeyOk = jkeyPresentAndOk(adminKey);

        return getKeyDescription((c, ojk) -> {
            if (!jkeyPresentAndOk(ojk)) return "    ";
            if (!adminKeyOk) return c + "   ";
            if (c == 'A') return "A   ";
            if (jkeyDeepEqualsButBothNullIsFalse(ojk.get(), adminKey.get())) return c + "=A ";
            return c + "   ";
        });
    }

    public String getKeyComplexity() {
        return getKeyDescription((c, ojk) -> {
            if (!jkeyPresentAndOk(ojk)) return "   ";
            if (jkeyIsComplex(ojk.get())) return c + "! ";
            return c + "  ";
        });
    }

    public String getKeyStructure() {
        final var r = getKeyDescription((c, ojk) -> {
            if (!jkeyPresentAndOk(ojk)) return "";
            final var sb = new StringBuilder();
            final var b = ThingsToStrings.toStructureSummaryOfJKey(sb, ojk.get());
            if (!b) return "";
            return c + ":" + sb + "; ";
        });
        return r.isEmpty() ? "" : r.substring(0, r.length() - 2);
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
    // spotless:on

    @NonNull
    private String getKeyDescription(@NonNull final BiFunction<Character, Optional<JKey>, String> map) {
        return KEYS.entrySet().stream()
                .map(e -> map.apply(e.getKey(), e.getValue().apply(this)))
                .collect(Collectors.joining());
    }
}
