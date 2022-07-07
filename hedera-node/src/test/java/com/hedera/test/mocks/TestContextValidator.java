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
package com.hedera.test.mocks;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.merkle.map.MerkleMap;

public enum TestContextValidator implements OptionValidator {
    TEST_VALIDATOR;

    public static final long CONSENSUS_NOW = 1_234_567L;

    @Override
    public boolean hasGoodEncoding(Key key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValidExpiry(Timestamp expiry) {
        return expiry.getSeconds() > CONSENSUS_NOW;
    }

    @Override
    public boolean isThisNodeAccount(AccountID id) {
        return true;
    }

    @Override
    public boolean isValidTxnDuration(long duration) {
        long minDuration = 15;
        long maxDuration = 180;

        return duration >= minDuration && duration <= maxDuration;
    }

    @Override
    public boolean isAfterConsensusSecond(long now) {
        return now < 9999999L;
    }

    @Override
    public boolean isValidAutoRenewPeriod(Duration autoRenewPeriod) {
        long duration = autoRenewPeriod.getSeconds();
        long minDuration = 1L;
        long maxDuration = 1_000_000_000l;

        if (duration < minDuration || duration > maxDuration) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isAcceptableTransfersLength(TransferList accountAmounts) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JKey attemptDecodeOrThrow(final Key k) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResponseCodeEnum expiryStatusGiven(long balance, long expiry, boolean isContract) {
        return OK;
    }

    @Override
    public ResponseCodeEnum expiryStatusGiven(
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts,
            final AccountID id) {
        return id.getAccountNum() == 666_666L ? ACCOUNT_EXPIRED_AND_PENDING_REMOVAL : OK;
    }

    @Override
    public ResponseCodeEnum nftMetadataCheck(byte[] metadata) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResponseCodeEnum maxBatchSizeMintCheck(int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResponseCodeEnum maxBatchSizeWipeCheck(int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResponseCodeEnum maxBatchSizeBurnCheck(int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResponseCodeEnum maxNftTransfersLenCheck(int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResponseCodeEnum nftMaxQueryRangeCheck(long start, long end) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResponseCodeEnum queryableTopicStatus(
            TopicID id, MerkleMap<EntityNum, MerkleTopic> topics) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JKey attemptToDecodeOrThrow(Key key, ResponseCodeEnum code) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResponseCodeEnum tokenSymbolCheck(String symbol) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPermissibleTotalNfts(long proposedTotal) {
        return true;
    }

    @Override
    public ResponseCodeEnum tokenNameCheck(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResponseCodeEnum memoCheck(String cand) {
        return cand.length() <= 100 ? OK : MEMO_TOO_LONG;
    }

    @Override
    public ResponseCodeEnum rawMemoCheck(byte[] cand) {
        return cand.length <= 100 ? OK : MEMO_TOO_LONG;
    }

    @Override
    public ResponseCodeEnum rawMemoCheck(byte[] cand, boolean hasZeroByte) {
        return cand.length <= 100 ? OK : MEMO_TOO_LONG;
    }
}
