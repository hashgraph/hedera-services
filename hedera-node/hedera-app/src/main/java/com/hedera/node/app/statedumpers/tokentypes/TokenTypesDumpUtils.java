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
import static com.hedera.node.app.service.mono.statedumpers.tokentypes.TokenTypesDumpUtils.dump;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
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
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualMap;
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

public class TokenTypesDumpUtils {
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

    private static BBMToken fromMod(@NonNull final Token token) {
        BBMToken tokenRes = null;
        final var adminKey = (JKey) fromPbjKey(token.adminKey()).orElse(null);
        final var feeScheduleKey = (JKey) fromPbjKey(token.feeScheduleKey()).orElse(null);
        final var freezeKey = (JKey) fromPbjKey(token.freezeKey()).orElse(null);
        final var kycKey = (JKey) fromPbjKey(token.kycKey()).orElse(null);
        final var pauseKey = (JKey) fromPbjKey(token.pauseKey()).orElse(null);
        final var supplyKey = (JKey) fromPbjKey(token.supplyKey()).orElse(null);
        final var wipeKey = (JKey) fromPbjKey(token.wipeKey()).orElse(null);
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
            var fcCustomFee = FcCustomFee.fromGrpc(PbjConverter.fromPbj(fee));
            fcCustomFees.add(fcCustomFee);
        });
        return fcCustomFees;
    }
}
