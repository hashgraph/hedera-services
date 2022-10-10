/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.crypto;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.fetchOwnerAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.updateSpender;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.validateAllowanceLimitsOn;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;

public class ApproveAllowanceLogic {
    private final AccountStore accountStore;
    private final TypedTokenStore tokenStore;
    private final GlobalDynamicProperties dynamicProperties;
    private final Map<Long, Account> entitiesChanged;
    private final Map<NftId, UniqueToken> nftsTouched;

    @Inject
    public ApproveAllowanceLogic(
            final AccountStore accountStore,
            final TypedTokenStore tokenStore,
            final GlobalDynamicProperties dynamicProperties) {
        this.accountStore = accountStore;
        this.tokenStore = tokenStore;
        this.dynamicProperties = dynamicProperties;
        this.entitiesChanged = new TreeMap<>();
        this.nftsTouched = new TreeMap<>();
    }

    public void approveAllowance(
            final List<CryptoAllowance> cryptoAllowances,
            final List<TokenAllowance> tokenAllowances,
            final List<NftAllowance> nftAllowances,
            final AccountID payer) {
        entitiesChanged.clear();
        nftsTouched.clear();

        /* --- Use models --- */
        final Id payerId = Id.fromGrpcAccount(payer);
        final var payerAccount = accountStore.loadAccount(payerId);

        /* --- Do the business logic --- */
        applyCryptoAllowances(cryptoAllowances, payerAccount);
        applyFungibleTokenAllowances(tokenAllowances, payerAccount);
        applyNftAllowances(nftAllowances, payerAccount);

        /* --- Persist the entities --- */
        for (final var nft : nftsTouched.values()) {
            tokenStore.persistNft(nft);
        }
        for (final var entry : entitiesChanged.entrySet()) {
            accountStore.commitAccount(entry.getValue());
        }
    }

    /**
     * Applies all changes needed for Crypto allowances from the transaction. If the spender already
     * has an allowance, the allowance value will be replaced with values from transaction
     *
     * @param cryptoAllowances
     * @param payerAccount
     */
    private void applyCryptoAllowances(
            final List<CryptoAllowance> cryptoAllowances, final Account payerAccount) {
        if (cryptoAllowances.isEmpty()) {
            return;
        }
        for (final var allowance : cryptoAllowances) {
            final var owner = allowance.getOwner();
            final var accountToApprove =
                    fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
            final var cryptoMap = accountToApprove.getMutableCryptoAllowances();

            final var spender = Id.fromGrpcAccount(allowance.getSpender());
            accountStore.loadAccountOrFailWith(spender, INVALID_ALLOWANCE_SPENDER_ID);

            final var amount = allowance.getAmount();

            if (cryptoMap.containsKey(spender.asEntityNum()) && amount == 0) {
                removeEntity(cryptoMap, spender, accountToApprove);
            }
            if (amount > 0) {
                cryptoMap.put(spender.asEntityNum(), amount);
                validateAllowanceLimitsOn(
                        accountToApprove, dynamicProperties.maxAllowanceLimitPerAccount());
                entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
            }
        }
    }

    private void removeEntity(
            final Map<EntityNum, Long> cryptoMap,
            final Id spender,
            final Account accountToApprove) {
        cryptoMap.remove(spender.asEntityNum());
        accountToApprove.setCryptoAllowances(cryptoMap);
        entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
    }

    /**
     * Applies all changes needed for fungible token allowances from the transaction.If the key
     * {token, spender} already has an allowance, the allowance value will be replaced with values
     * from transaction
     *
     * @param tokenAllowances
     * @param payerAccount
     */
    private void applyFungibleTokenAllowances(
            final List<TokenAllowance> tokenAllowances, final Account payerAccount) {
        if (tokenAllowances.isEmpty()) {
            return;
        }
        for (var allowance : tokenAllowances) {
            final var owner = allowance.getOwner();
            final var accountToApprove =
                    fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
            final var tokensMap = accountToApprove.getMutableFungibleTokenAllowances();

            final var spender = Id.fromGrpcAccount(allowance.getSpender());
            accountStore.loadAccountOrFailWith(spender, INVALID_ALLOWANCE_SPENDER_ID);

            final var amount = allowance.getAmount();
            final var tokenId = allowance.getTokenId();

            final var key =
                    FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId), spender.asEntityNum());
            if (tokensMap.containsKey(key) && amount == 0) {
                removeTokenEntity(key, tokensMap, accountToApprove);
            }
            if (amount > 0) {
                tokensMap.put(key, amount);
                validateAllowanceLimitsOn(
                        accountToApprove, dynamicProperties.maxAllowanceLimitPerAccount());
                entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
            }
        }
    }

    /**
     * Applies all changes needed for NFT allowances from the transaction. If the key{tokenNum,
     * spenderNum} doesn't exist in the map the allowance will be inserted. If the key exists,
     * existing allowance values will be replaced with new allowances given in operation
     *
     * @param nftAllowances
     * @param payerAccount
     */
    protected void applyNftAllowances(
            final List<NftAllowance> nftAllowances, final Account payerAccount) {
        if (nftAllowances.isEmpty()) {
            return;
        }
        for (var allowance : nftAllowances) {
            final var owner = allowance.getOwner();
            final var approvingAccount =
                    fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
            final var spenderId = Id.fromGrpcAccount(allowance.getSpender());
            accountStore.loadAccountOrFailWith(spenderId, INVALID_ALLOWANCE_SPENDER_ID);

            final var tokenId = Id.fromGrpcToken(allowance.getTokenId());
            if (allowance.hasApprovedForAll()) {
                final var approveForAllNfts = approvingAccount.getMutableApprovedForAllNfts();
                final var key =
                        FcTokenAllowanceId.from(tokenId.asEntityNum(), spenderId.asEntityNum());
                if (allowance.getApprovedForAll().getValue()) {
                    approveForAllNfts.add(key);
                } else {
                    approveForAllNfts.remove(key);
                }
                validateAllowanceLimitsOn(
                        approvingAccount, dynamicProperties.maxAllowanceLimitPerAccount());
            }

            final var nfts =
                    updateSpender(
                            tokenStore,
                            approvingAccount.getId(),
                            spenderId,
                            tokenId,
                            allowance.getSerialNumbersList());
            for (final var nft : nfts) {
                nftsTouched.put(nft.getNftId(), nft);
            }
            entitiesChanged.put(approvingAccount.getId().num(), approvingAccount);
        }
    }

    private void removeTokenEntity(
            final FcTokenAllowanceId key,
            final Map<FcTokenAllowanceId, Long> tokensMap,
            final Account accountToApprove) {
        tokensMap.remove(key);
        accountToApprove.setFungibleTokenAllowances(tokensMap);
        entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
    }
}
