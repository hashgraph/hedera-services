package com.hedera.test.mocks;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.binary.StringUtils;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

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
	public boolean isValidTxnDuration(long duration) {
		long minDuration = 15;
		long maxDuration = 180;

		return duration >= minDuration && duration <= maxDuration;
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
	public ResponseCodeEnum isAcceptableTokenTransfersLength(List<TokenTransferList> tokenTransferLists) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum queryableTopicStatus(TopicID id, FCMap<MerkleEntityId, MerkleTopic> topics) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum tokenSymbolCheck(String symbol) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum tokenNameCheck(String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum memoCheck(String cand) {
		return cand.length() <= 100 ? OK : MEMO_TOO_LONG;
	}
}
