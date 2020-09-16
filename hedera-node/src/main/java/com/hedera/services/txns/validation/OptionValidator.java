package com.hedera.services.txns.validation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.swirlds.fcmap.FCMap;

import java.time.Instant;

/**
 * Defines a type able to divine the validity of various options
 * that can appear in HAPI gRPC transactions.
 *
 * @author Michael Tinker
 */

public interface OptionValidator {
	boolean hasGoodEncoding(Key key);

	boolean isValidExpiry(Timestamp expiry);

	boolean isValidEntityMemo(String memo);

	boolean isValidTxnDuration(long duration);

	boolean isValidAutoRenewPeriod(Duration autoRenewPeriod);

	boolean isAcceptableLength(TransferList accountAmounts);

	ResponseCodeEnum queryableTopicStatus(TopicID id, FCMap<MerkleEntityId, MerkleTopic> topics);

	default ResponseCodeEnum queryableAccountStatus(AccountID id, FCMap<MerkleEntityId, MerkleAccount> accounts) {
		return PureValidation.queryableAccountStatus(id, accounts);
	}

	default ResponseCodeEnum queryableContractStatus(ContractID cid, FCMap<MerkleEntityId, MerkleAccount> contracts) {
		return PureValidation.queryableContractStatus(cid, contracts);
	}

	default ResponseCodeEnum queryableFileStatus(FileID fid, StateView view) {
		return PureValidation.queryableFileStatus(fid, view);
	}

	default Instant asCoercedInstant(Timestamp when) {
		return PureValidation.asCoercedInstant(when);
	}

	default boolean isPlausibleAccount(AccountID id) {
		return id.getAccountNum() > 0 &&
				id.getRealmNum() >= 0 &&
				id.getShardNum() >= 0;
	}

	default boolean isPlausibleTxnFee(long amount) {
		return amount >= 0;
	}

	default ResponseCodeEnum chronologyStatus(SignedTxnAccessor accessor, Instant consensusTime) {
		return PureValidation.chronologyStatus(
				consensusTime,
				asCoercedInstant(accessor.getTxnId().getTransactionValidStart()),
				accessor.getTxn().getTransactionValidDuration().getSeconds());
	}

	default ResponseCodeEnum chronologyStatusForTxn(
			Instant validAfter,
			long forSecs,
			Instant estimatedConsensusTime) {
		return PureValidation.chronologyStatus(estimatedConsensusTime, validAfter, forSecs);
	}
}
