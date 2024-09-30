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

import static com.hedera.node.app.statedumpers.utils.ThingsToStrings.toStructureSummaryOfJKey;

import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.node.app.hapi.utils.TokenType;
import com.hedera.node.app.statedumpers.legacy.EntityId;
import com.hedera.node.app.statedumpers.legacy.FcCustomFee;
import com.hedera.node.app.statedumpers.legacy.JKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
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
            final var b = toStructureSummaryOfJKey(sb, ojk.get());
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

    public static boolean jkeyPresentAndOk(@NonNull Optional<JKey> ojkey) {
        if (ojkey.isEmpty()) return false;
        if (ojkey.get().isEmpty()) return false;
        return ojkey.get().isValid();
    }
}
