/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.test.factories.accounts;

import static com.hedera.services.store.models.Id.MISSING_ID;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class MerkleAccountFactory {
    private int numKvPairs = 0;
    private KeyFactory keyFactory = KeyFactory.getDefaultInstance();
    private Optional<Long> balance = Optional.empty();
    private Optional<Long> lastAssociatedToken = Optional.empty();
    private Optional<Integer> associatedTokensCount = Optional.empty();
    private Optional<Integer> numPositiveBalances = Optional.empty();
    private Optional<Boolean> receiverSigRequired = Optional.empty();
    private Optional<JKey> accountKeys = Optional.empty();
    private Optional<Long> autoRenewPeriod = Optional.empty();
    private Optional<Boolean> deleted = Optional.empty();
    private Optional<Long> expirationTime = Optional.empty();
    private Optional<String> memo = Optional.empty();
    private Optional<Boolean> isSmartContract = Optional.empty();
    private Optional<AccountID> proxy = Optional.empty();
    private Optional<AccountID> autoRenewAccount = Optional.empty();
    private Optional<Integer> alreadyUsedAutoAssociations = Optional.empty();
    private Optional<Integer> maxAutoAssociations = Optional.empty();
    private Optional<ByteString> alias = Optional.empty();
    private EntityNum num = null;
    private Set<TokenID> associatedTokens = new HashSet<>();
    private TreeMap<EntityNum, Long> cryptoAllowances = new TreeMap<>();
    private TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances = new TreeMap<>();
    private TreeSet<FcTokenAllowanceId> approveForAllNftsAllowances = new TreeSet<>();
    private int[] firstUint256Key;
    private Optional<Long> stakedId = Optional.empty();
    private Optional<Long> stakedToMe = Optional.empty();
    private Optional<Long> stakePeriodStart = Optional.empty();
    private Optional<Boolean> declineReward = Optional.empty();
    private long nftsOwned = 0;
    private long headNftTokenNum = 0;
    private long headNftSerialNo = 0;

    public MerkleAccount get() {
        MerkleAccount value = new MerkleAccount();
        memo.ifPresent(value::setMemo);
        alias.ifPresent(value::setAlias);
        proxy.ifPresent(p -> value.setProxy(EntityId.fromGrpcAccountId(p)));
        balance.ifPresent(
                b -> {
                    try {
                        value.setBalance(b);
                    } catch (Exception ignore) {
                    }
                });
        deleted.ifPresent(value::setDeleted);
        accountKeys.ifPresent(value::setAccountKey);
        expirationTime.ifPresent(value::setExpiry);
        autoRenewPeriod.ifPresent(value::setAutoRenewSecs);
        isSmartContract.ifPresent(value::setSmartContract);
        receiverSigRequired.ifPresent(value::setReceiverSigRequired);
        maxAutoAssociations.ifPresent(value::setMaxAutomaticAssociations);
        alreadyUsedAutoAssociations.ifPresent(value::setUsedAutomaticAssociations);
        value.setNumContractKvPairs(numKvPairs);
        value.setCryptoAllowances(cryptoAllowances);
        value.setFungibleTokenAllowances(fungibleTokenAllowances);
        value.setApproveForAllNfts(approveForAllNftsAllowances);
        value.setNumAssociations(associatedTokensCount.orElse(0));
        value.setNumPositiveBalances(numPositiveBalances.orElse(0));
        value.setHeadTokenId(lastAssociatedToken.orElse(MISSING_ID.num()));
        value.setHeadNftId(headNftTokenNum);
        value.setHeadNftSerialNum(headNftSerialNo);
        stakedId.ifPresent(value::setStakedId);
        stakePeriodStart.ifPresent(value::setStakePeriodStart);
        declineReward.ifPresent(value::setDeclineReward);
        stakedToMe.ifPresent(value::setStakedToMe);
        autoRenewAccount.ifPresent(p -> value.setAutoRenewAccount(EntityId.fromGrpcAccountId(p)));
        value.setNftsOwned(nftsOwned);
        if (firstUint256Key != null) {
            value.setFirstUint256StorageKey(firstUint256Key);
        }
        if (num != null) {
            value.setKey(num);
        }
        return value;
    }

    private MerkleAccountFactory() {}

    public static MerkleAccountFactory newAccount() {
        return new MerkleAccountFactory();
    }

    public static MerkleAccountFactory newContract() {
        return new MerkleAccountFactory().isSmartContract(true);
    }

    public MerkleAccountFactory firstContractKey(final int[] uint256Key) {
        this.firstUint256Key = uint256Key;
        return this;
    }

    public MerkleAccountFactory number(final EntityNum num) {
        this.num = num;
        return this;
    }

    public MerkleAccountFactory stakedId(final long stakedId) {
        this.stakedId = Optional.of(stakedId);
        return this;
    }

    public MerkleAccountFactory stakedToMe(final long stakedToMe) {
        this.stakedToMe = Optional.of(stakedToMe);
        return this;
    }

    public MerkleAccountFactory declineReward(final boolean declineReward) {
        this.declineReward = Optional.of(declineReward);
        return this;
    }

    public MerkleAccountFactory stakePeriodStart(final long stakePeriodStart) {
        this.stakePeriodStart = Optional.of(stakePeriodStart);
        return this;
    }

    public MerkleAccountFactory numKvPairs(final int numKvPairs) {
        this.numKvPairs = numKvPairs;
        return this;
    }

    public MerkleAccountFactory proxy(final AccountID id) {
        proxy = Optional.of(id);
        return this;
    }

    public MerkleAccountFactory autoRenewAccount(final AccountID id) {
        autoRenewAccount = Optional.of(id);
        return this;
    }

    public MerkleAccountFactory balance(final long amount) {
        balance = Optional.of(amount);
        return this;
    }

    public MerkleAccountFactory alias(final ByteString bytes) {
        alias = Optional.of(bytes);
        return this;
    }

    public MerkleAccountFactory tokens(final TokenID... tokens) {
        associatedTokens.addAll(List.of(tokens));
        return this;
    }

    public MerkleAccountFactory receiverSigRequired(final boolean b) {
        receiverSigRequired = Optional.of(b);
        return this;
    }

    public MerkleAccountFactory keyFactory(final KeyFactory keyFactory) {
        this.keyFactory = keyFactory;
        return this;
    }

    public MerkleAccountFactory accountKeys(final KeyTree kt) throws Exception {
        return accountKeys(kt.asKey(keyFactory));
    }

    public MerkleAccountFactory accountKeys(final Key k) throws Exception {
        return accountKeys(JKey.mapKey(k));
    }

    public MerkleAccountFactory accountKeys(final JKey k) {
        accountKeys = Optional.of(k);
        return this;
    }

    public MerkleAccountFactory autoRenewPeriod(final long p) {
        autoRenewPeriod = Optional.of(p);
        return this;
    }

    public MerkleAccountFactory deleted(final boolean b) {
        deleted = Optional.of(b);
        return this;
    }

    public MerkleAccountFactory expirationTime(final long l) {
        expirationTime = Optional.of(l);
        return this;
    }

    public MerkleAccountFactory memo(final String s) {
        memo = Optional.of(s);
        return this;
    }

    public MerkleAccountFactory isSmartContract(final boolean b) {
        isSmartContract = Optional.of(b);
        return this;
    }

    public MerkleAccountFactory maxAutomaticAssociations(final int max) {
        maxAutoAssociations = Optional.of(max);
        return this;
    }

    public MerkleAccountFactory alreadyUsedAutomaticAssociations(final int count) {
        alreadyUsedAutoAssociations = Optional.of(count);
        return this;
    }

    public MerkleAccountFactory cryptoAllowances(final TreeMap<EntityNum, Long> allowances) {
        cryptoAllowances = allowances;
        return this;
    }

    public MerkleAccountFactory fungibleTokenAllowances(
            final TreeMap<FcTokenAllowanceId, Long> allowances) {
        fungibleTokenAllowances = allowances;
        return this;
    }

    public MerkleAccountFactory explicitNftAllowances(
            final TreeSet<FcTokenAllowanceId> allowances) {
        approveForAllNftsAllowances = allowances;
        return this;
    }

    public MerkleAccountFactory lastAssociatedToken(final long lastAssociatedToken) {
        this.lastAssociatedToken = Optional.of(lastAssociatedToken);
        return this;
    }

    public MerkleAccountFactory associatedTokensCount(final int associatedTokensCount) {
        this.associatedTokensCount = Optional.of(associatedTokensCount);
        return this;
    }

    public MerkleAccountFactory nftsOwned(final long n) {
        nftsOwned = n;
        return this;
    }

    public MerkleAccountFactory headNftSerialNo(final long n) {
        headNftSerialNo = n;
        return this;
    }

    public MerkleAccountFactory headNftTokenNum(final long n) {
        headNftTokenNum = n;
        return this;
    }
}
