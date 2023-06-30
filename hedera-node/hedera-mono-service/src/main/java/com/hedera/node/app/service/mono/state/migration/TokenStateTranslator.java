/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.migration;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableTokenStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates between the legacy {@link com.hedera.node.app.service.mono.state.merkle.MerkleToken} and the {@link Token} and vise versa.
 */
public final class TokenStateTranslator {
    private TokenStateTranslator() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Translates the {@link com.hedera.node.app.service.mono.state.merkle.MerkleToken} to the {@link Token}.
     * @param token {@link com.hedera.node.app.service.mono.state.merkle.MerkleToken}
     * @return  {@link Token}
     */
    public static Token tokenFromMerkle(
            @NonNull final com.hedera.node.app.service.mono.state.merkle.MerkleToken token) {
        final var builder = Token.newBuilder()
                .tokenId(TokenID.newBuilder().tokenNum(token.getKey().longValue()))
                .name(token.name())
                .symbol(token.symbol())
                .decimals(token.decimals())
                .totalSupply(token.totalSupply())
                .treasuryAccountId(AccountID.newBuilder()
                        .accountNum(token.treasury().num())
                        .build())
                .lastUsedSerialNumber(token.getLastUsedSerialNumber())
                .deleted(token.isDeleted())
                .tokenType(fromMerkleType(token.tokenType()))
                .supplyType(fromMerkleSupplyType(token.supplyType()))
                .autoRenewAccountId(AccountID.newBuilder()
                        .accountNum(
                                token.autoRenewAccount() != null
                                        ? token.autoRenewAccount().num()
                                        : 0L))
                .autoRenewSecs(token.autoRenewPeriod())
                .expiry(token.expiry())
                .memo(token.memo())
                .maxSupply(token.maxSupply())
                .paused(token.isPaused())
                .accountsFrozenByDefault(token.accountsAreFrozenByDefault())
                .accountsKycGrantedByDefault(token.accountsKycGrantedByDefault())
                .customFees(convertMonoCustomFees(token.customFeeSchedule()));
        if (token.hasAdminKey()) {
            builder.adminKey(PbjConverter.asPbjKey(token.getAdminKey()));
        }
        if (token.hasKycKey()) {
            builder.kycKey(PbjConverter.asPbjKey(token.getKycKey()));
        }
        if (token.hasFreezeKey()) {
            builder.freezeKey(PbjConverter.asPbjKey(token.getFreezeKey()));
        }
        if (token.hasWipeKey()) {
            builder.wipeKey(PbjConverter.asPbjKey(token.getWipeKey()));
        }
        if (token.hasSupplyKey()) {
            builder.supplyKey(PbjConverter.asPbjKey(token.getSupplyKey()));
        }
        if (token.hasFeeScheduleKey()) {
            builder.feeScheduleKey(PbjConverter.asPbjKey(token.getFeeScheduleKey()));
        }
        if (token.hasPauseKey()) {
            builder.pauseKey(PbjConverter.asPbjKey(token.getPauseKey()));
        }
        return builder.build();
    }

    @NonNull
    static List<CustomFee> convertMonoCustomFees(
            @Nullable final List<com.hedera.node.app.service.mono.state.submerkle.FcCustomFee> monoCustomFees) {
        final List<CustomFee> customFees = new ArrayList<>();
        if (monoCustomFees != null) {
            for (var customFee : monoCustomFees) {
                customFees.add(PbjConverter.fromFcCustomFee(customFee));
            }
        }

        return customFees;
    }

    @NonNull
    static TokenType fromMerkleType(@NonNull com.hedera.node.app.service.evm.store.tokens.TokenType tokenType) {
        return (tokenType.equals(com.hedera.node.app.service.evm.store.tokens.TokenType.NON_FUNGIBLE_UNIQUE))
                ? TokenType.NON_FUNGIBLE_UNIQUE
                : TokenType.FUNGIBLE_COMMON;
    }

    @NonNull
    static TokenSupplyType fromMerkleSupplyType(
            @NonNull com.hedera.node.app.service.mono.state.enums.TokenSupplyType tokenSupplyType) {
        return (tokenSupplyType.equals(com.hedera.node.app.service.mono.state.enums.TokenSupplyType.INFINITE))
                ? TokenSupplyType.INFINITE
                : TokenSupplyType.FINITE;
    }

    @NonNull
    /***
     * Converts a {@link com.hedera.hapi.node.state.token.Token} to a {@link com.hedera.node.app.service.mono.state.merkle.MerkleAccount}
     * @param tokenId the {@link TokenID} of the token to convert
     * @param readableTokenStore the {@link com.hedera.node.app.service.token.ReadableTokenStore} to use to retrieve the token
     * @return the {@link com.hedera.node.app.service.mono.state.merkle.MerkleToken} corresponding to the tokenId
     */
    public static com.hedera.node.app.service.mono.state.merkle.MerkleToken merkleTokenFromToken(
            @NonNull TokenID tokenId, @NonNull ReadableTokenStore readableTokenStore) {
        requireNonNull(tokenId);
        requireNonNull(readableTokenStore);
        final var optionalToken = readableTokenStore.get(tokenId);
        if (optionalToken == null) {
            throw new IllegalArgumentException("Token not found");
        }
        return merkleTokenFromToken(optionalToken);
    }

    @NonNull
    public static com.hedera.node.app.service.mono.state.merkle.MerkleToken merkleTokenFromToken(@NonNull Token token) {
        requireNonNull(token);
        com.hedera.node.app.service.mono.state.merkle.MerkleToken merkleToken =
                new com.hedera.node.app.service.mono.state.merkle.MerkleToken();
        merkleToken.setKey(
                EntityNum.fromLong(token.tokenIdOrElse(TokenID.DEFAULT).tokenNum()));
        merkleToken.setName(token.name());
        merkleToken.setSymbol(token.symbol());
        merkleToken.setDecimals(token.decimals());
        merkleToken.setTotalSupply(token.totalSupply());
        merkleToken.setTreasury(EntityId.fromNum(
                token.treasuryAccountIdOrElse(AccountID.DEFAULT).accountNumOrElse(0L)));
        merkleToken.setLastUsedSerialNumber(token.lastUsedSerialNumber());
        merkleToken.setDeleted(token.deleted());
        merkleToken.setTokenType(toMerkleType(token.tokenType()));
        merkleToken.setSupplyType(toMerkleSupplyType(token.supplyType()));
        final var autoRenewAccountNumber =
                token.autoRenewAccountIdOrElse(AccountID.DEFAULT).accountNumOrElse(0L);
        merkleToken.setAutoRenewAccount(
                (autoRenewAccountNumber > 0) ? new EntityId(0, 0, autoRenewAccountNumber) : null);
        merkleToken.setAutoRenewPeriod(token.autoRenewSecs());
        merkleToken.setExpiry(token.expiry());
        merkleToken.setMemo(token.memo());
        merkleToken.setMaxSupply(token.maxSupply());
        merkleToken.setPaused(token.paused());
        merkleToken.setAccountsFrozenByDefault(token.accountsFrozenByDefault());
        merkleToken.setAccountsKycGrantedByDefault(token.accountsKycGrantedByDefault());
        merkleToken.setAdminKey((JKey) PbjConverter.fromPbjKeyUnchecked(token.adminKeyOrElse((Key.DEFAULT)))
                .orElse(null));
        merkleToken.setKycKey((JKey) PbjConverter.fromPbjKeyUnchecked(token.kycKeyOrElse((Key.DEFAULT)))
                .orElse(null));
        merkleToken.setFreezeKey((JKey) PbjConverter.fromPbjKeyUnchecked(token.freezeKeyOrElse((Key.DEFAULT)))
                .orElse(null));
        merkleToken.setWipeKey((JKey) PbjConverter.fromPbjKeyUnchecked(token.wipeKeyOrElse((Key.DEFAULT)))
                .orElse(null));
        merkleToken.setSupplyKey((JKey) PbjConverter.fromPbjKeyUnchecked(token.supplyKeyOrElse((Key.DEFAULT)))
                .orElse(null));
        merkleToken.setFeeScheduleKey((JKey) PbjConverter.fromPbjKeyUnchecked(token.feeScheduleKeyOrElse((Key.DEFAULT)))
                .orElse(null));
        merkleToken.setPauseKey((JKey) PbjConverter.fromPbjKeyUnchecked(token.pauseKeyOrElse((Key.DEFAULT)))
                .orElse(null));
        merkleToken.setFeeSchedule(convertCustomFees(token.customFees()));
        return merkleToken;
    }

    @NonNull
    static com.hedera.node.app.service.evm.store.tokens.TokenType toMerkleType(@NonNull TokenType tokenType) {
        return (tokenType.equals(TokenType.NON_FUNGIBLE_UNIQUE))
                ? com.hedera.node.app.service.evm.store.tokens.TokenType.NON_FUNGIBLE_UNIQUE
                : com.hedera.node.app.service.evm.store.tokens.TokenType.FUNGIBLE_COMMON;
    }

    @NonNull
    static com.hedera.node.app.service.mono.state.enums.TokenSupplyType toMerkleSupplyType(
            @NonNull TokenSupplyType tokenSupplyType) {
        return (tokenSupplyType.equals(TokenSupplyType.INFINITE))
                ? com.hedera.node.app.service.mono.state.enums.TokenSupplyType.INFINITE
                : com.hedera.node.app.service.mono.state.enums.TokenSupplyType.FINITE;
    }

    @NonNull
    static List<com.hedera.node.app.service.mono.state.submerkle.FcCustomFee> convertCustomFees(
            @Nullable final List<CustomFee> customFees) {
        final List<com.hedera.node.app.service.mono.state.submerkle.FcCustomFee> monoCustomFees = new ArrayList<>();
        if (customFees != null) {
            for (var customFee : customFees) {
                if (customFee != null) {
                    monoCustomFees.add(FcCustomFee.fromGrpc(PbjConverter.fromPbj(customFee)));
                }
            }
        }

        return monoCustomFees;
    }
}
