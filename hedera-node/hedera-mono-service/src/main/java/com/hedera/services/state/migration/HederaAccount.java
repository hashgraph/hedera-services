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
package com.hedera.services.state.migration;

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

public interface HederaAccount {
    boolean isImmutable();

    long getNftsOwned();

    void setNftsOwned(long nftsOwned);

    boolean isTokenTreasury();

    int getNumTreasuryTitles();

    void setNumTreasuryTitles(int treasuryTitles);

    String getMemo();

    void setMemo(String memo);

    boolean isSmartContract();

    void setSmartContract(boolean smartContract);

    ByteString getAlias();

    void setAlias(ByteString alias);

    long getEthereumNonce();

    void setEthereumNonce(long ethereumNonce);

    int getNumAssociations();

    void setNumAssociations(int numAssociations);

    int getNumPositiveBalances();

    void setNumPositiveBalances(int numPositiveBalances);

    long getHeadTokenId();

    void setHeadTokenId(long headTokenId);

    long getHeadNftTokenNum();

    void setHeadNftId(long headNftId);

    EntityNumPair getHeadNftKey();

    long getHeadNftSerialNum();

    void setHeadNftSerialNum(long headNftSerialNum);

    EntityNumPair getLatestAssociation();

    long getBalance();

    void setBalance(long balance) throws NegativeAccountBalanceException;

    void setBalanceUnchecked(long balance);

    boolean isReceiverSigRequired();

    void setReceiverSigRequired(boolean receiverSigRequired);

    JKey getAccountKey();

    void setAccountKey(JKey key);

    int number();

    void setEntityNum(final EntityNum num);

    default EntityNum getEntityNum() {
        return EntityNum.fromInt(number());
    }

    EntityId getProxy();

    void setProxy(EntityId proxy);

    long getAutoRenewSecs();

    void setAutoRenewSecs(long autoRenewSecs);

    boolean isDeleted();

    void setDeleted(boolean deleted);

    long getExpiry();

    void setExpiry(long expiry);

    int getMaxAutomaticAssociations();

    void setMaxAutomaticAssociations(int maxAutomaticAssociations);

    int getUsedAutoAssociations();

    void setUsedAutomaticAssociations(int usedAutoAssociations);

    int getNumContractKvPairs();

    void setNumContractKvPairs(int numContractKvPairs);

    ContractKey getFirstContractStorageKey();

    int[] getFirstUint256Key();

    void setFirstUint256StorageKey(int[] firstUint256Key);

    Map<EntityNum, Long> getCryptoAllowances();

    void setCryptoAllowances(SortedMap<EntityNum, Long> cryptoAllowances);

    Map<EntityNum, Long> getCryptoAllowancesUnsafe();

    void setCryptoAllowancesUnsafe(Map<EntityNum, Long> cryptoAllowances);

    Set<FcTokenAllowanceId> getApproveForAllNfts();

    void setApproveForAllNfts(Set<FcTokenAllowanceId> approveForAllNfts);

    Set<FcTokenAllowanceId> getApproveForAllNftsUnsafe();

    Map<FcTokenAllowanceId, Long> getFungibleTokenAllowances();

    void setFungibleTokenAllowances(SortedMap<FcTokenAllowanceId, Long> fungibleTokenAllowances);

    Map<FcTokenAllowanceId, Long> getFungibleTokenAllowancesUnsafe();

    void setFungibleTokenAllowancesUnsafe(Map<FcTokenAllowanceId, Long> fungibleTokenAllowances);

    boolean isDeclinedReward();

    void setDeclineReward(boolean declineReward);

    boolean hasBeenRewardedSinceLastStakeMetaChange();

    long totalStakeAtStartOfLastRewardedPeriod();

    void setStakeAtStartOfLastRewardedPeriod(long balanceAtStartOfLastRewardedPeriod);

    long getStakedToMe();

    void setStakedToMe(long stakedToMe);

    long totalStake();

    long getStakePeriodStart();

    void setStakePeriodStart(long stakePeriodStart);

    long getStakedId();

    void setStakedId(long stakedId);

    boolean mayHavePendingReward();

    long getStakedNodeAddressBookId();

    boolean hasAlias();

    boolean hasAutoRenewAccount();

    EntityId getAutoRenewAccount();

    void setAutoRenewAccount(EntityId autoRenewAccount);
}
