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
package com.hedera.services.txns.validation;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;

/**
 * Defines a type able to divine the validity of various options that can appear in HAPI gRPC
 * transactions.
 */
public interface OptionValidator {
    boolean hasGoodEncoding(Key key);

    boolean isValidExpiry(Timestamp expiry);

    boolean isThisNodeAccount(AccountID id);

    boolean isValidTxnDuration(long duration);

    boolean isAfterConsensusSecond(long now);

    boolean isValidAutoRenewPeriod(Duration autoRenewPeriod);

    boolean isAcceptableTransfersLength(TransferList accountAmounts);

    JKey attemptDecodeOrThrow(Key k);

    ResponseCodeEnum expiryStatusGiven(long balance, long expiry, boolean isContract);
    // This variant is to avoid unnecessary TransactionalLedger.get() calls
    ResponseCodeEnum expiryStatusGiven(
            TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts, AccountID id);

    ResponseCodeEnum memoCheck(String cand);

    ResponseCodeEnum rawMemoCheck(byte[] cand);

    ResponseCodeEnum rawMemoCheck(byte[] cand, boolean hasZeroByte);

    ResponseCodeEnum tokenNameCheck(String name);

    ResponseCodeEnum tokenSymbolCheck(String symbol);

    boolean isPermissibleTotalNfts(long proposedTotal);

    ResponseCodeEnum nftMetadataCheck(byte[] metadata);

    ResponseCodeEnum maxBatchSizeMintCheck(int length);

    ResponseCodeEnum maxBatchSizeWipeCheck(int length);

    ResponseCodeEnum maxBatchSizeBurnCheck(int length);

    ResponseCodeEnum maxNftTransfersLenCheck(int length);

    ResponseCodeEnum nftMaxQueryRangeCheck(long start, long end);

    ResponseCodeEnum queryableTopicStatus(TopicID id, MerkleMap<EntityNum, MerkleTopic> topics);

    JKey attemptToDecodeOrThrow(Key key, ResponseCodeEnum code);

    default ResponseCodeEnum queryableAccountStatus(AccountID id, AccountStorageAdapter accounts) {
        return queryableAccountStatus(EntityNum.fromAccountId(id), accounts);
    }

    default ResponseCodeEnum queryableAccountStatus(
            EntityNum entityNum, AccountStorageAdapter accounts) {
        return PureValidation.queryableAccountStatus(entityNum, accounts);
    }

    default ResponseCodeEnum queryableContractStatus(
            ContractID cid, AccountStorageAdapter contracts) {
        return PureValidation.queryableContractStatus(cid, contracts);
    }

    default ResponseCodeEnum queryableContractStatus(
            EntityNum cid, AccountStorageAdapter contracts) {
        return PureValidation.queryableContractStatus(cid, contracts);
    }

    default ResponseCodeEnum queryableFileStatus(FileID fid, StateView view) {
        return PureValidation.queryableFileStatus(fid, view);
    }

    default Instant asCoercedInstant(Timestamp when) {
        return PureValidation.asCoercedInstant(when);
    }

    default boolean isPlausibleAccount(AccountID id) {
        return id.getAccountNum() > 0 && id.getRealmNum() >= 0 && id.getShardNum() >= 0;
    }

    default boolean isPlausibleTxnFee(long amount) {
        return amount >= 0;
    }

    default ResponseCodeEnum chronologyStatus(TxnAccessor accessor, Instant consensusTime) {
        return PureValidation.chronologyStatus(
                consensusTime,
                asCoercedInstant(accessor.getTxnId().getTransactionValidStart()),
                accessor.getTxn().getTransactionValidDuration().getSeconds());
    }

    default ResponseCodeEnum chronologyStatusForTxn(
            Instant validAfter, long forSecs, Instant estimatedConsensusTime) {
        return PureValidation.chronologyStatus(estimatedConsensusTime, validAfter, forSecs);
    }

    /**
     * Validates if the stakedNodeId or stakedAccountId is set in the CryptoCreate, CryptoUpdate,
     * ContractCreate and ContractUpdate operations.
     *
     * @param idCase if stakedNodeId or stakedAccountId is set
     * @param stakedAccountId given stakedAccountId
     * @param stakedNodeId given stakedNodeId
     * @param accounts accounts merkle map
     * @param nodeInfo node info
     * @return true if valid, false otherwise
     */
    default boolean isValidStakedId(
            final String idCase,
            final AccountID stakedAccountId,
            final long stakedNodeId,
            final AccountStorageAdapter accounts,
            final NodeInfo nodeInfo) {
        return PureValidation.isValidStakedId(
                idCase, stakedAccountId, stakedNodeId, accounts, nodeInfo);
    }
}
