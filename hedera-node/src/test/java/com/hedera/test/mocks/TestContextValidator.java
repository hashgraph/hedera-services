package com.hedera.test.mocks;

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
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.binary.StringUtils;

public enum TestContextValidator implements OptionValidator {
	TEST_VALIDATOR;

	@Override
	public boolean hasGoodEncoding(Key key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean hasOnlyCryptoAccounts(TransferList accountAmounts) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isValidExpiry(Timestamp expiry) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isValidEntityMemo(String memo) {
		var maxUtf8Bytes = 100;
		return (null == memo) || (StringUtils.getBytesUtf8(memo).length <= maxUtf8Bytes);
	}

	@Override
	public boolean isValidTxnDuration(long duration) {
		long minDuration = PropertiesLoader.getTxMinDuration();
		long maxDuration = PropertiesLoader.getTxMaxDuration();

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
	public boolean isAcceptableLength(TransferList accountAmounts) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ResponseCodeEnum queryableTopicStatus(TopicID id, FCMap<MerkleEntityId, MerkleTopic> topics) {
		throw new UnsupportedOperationException();
	}
}
