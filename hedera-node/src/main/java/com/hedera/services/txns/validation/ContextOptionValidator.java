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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.legacy.core.jproto.JKey.mapKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;

/**
 * Implements an {@link OptionValidator} that relies an injected instance
 * of the {@link TransactionContext} to determine whether various options are
 * permissible.
 *
 * @author Michael Tinker
 */
public class ContextOptionValidator implements OptionValidator {
	public static final Logger log = LogManager.getLogger(ContextOptionValidator.class);
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties properties;

	public ContextOptionValidator(TransactionContext txnCtx, GlobalDynamicProperties properties) {
		this.txnCtx = txnCtx;
		this.properties = properties;
	}

	@Override
	public boolean hasGoodEncoding(Key key) {
		try {
			mapKey(key);
			return true;
		} catch (Exception ignore) {
			return false;
		}
	}

	@Override
	public boolean isValidTxnDuration(long duration) {
		return duration >= properties.minTxnDuration() && duration <= properties.maxTxnDuration();
	}

	@Override
	public boolean isValidExpiry(Timestamp expiry) {
		Instant then = Instant.ofEpochSecond(expiry.getSeconds(), expiry.getNanos());
		return then.isAfter(txnCtx.consensusTime());
	}

	@Override
	public boolean isValidAutoRenewPeriod(Duration autoRenewPeriod) {
		long duration = autoRenewPeriod.getSeconds();

		if (duration < properties.minAutoRenewDuration() || duration > properties.maxAutoRenewDuration()) {
			return false;
		}
		return true;
	}

	@Override
	public boolean isAcceptableTransfersLength(TransferList accountAmounts) {
		return accountAmounts.getAccountAmountsCount() <= properties.maxTransferListSize();
	}

	@Override
	public ResponseCodeEnum isAcceptableTokenTransfersLength(List<TokenTransferList> tokenTransferLists) {
		int maxLen = properties.maxTokenTransferListSize();
		int tokenTransferListsSize = tokenTransferLists.size();

		if (tokenTransferListsSize > maxLen) {
			return TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
		}

		int count = 0;
		for (var tokenTransferList : tokenTransferLists) {
			int transferCounts = tokenTransferList.getTransfersCount();
			if (transferCounts == 0) {
				return EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
			}

			count += transferCounts;

			if (count > maxLen) {
				return TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
			}
		}

		return OK;
	}

	@Override
	public boolean isValidEntityMemo(@Nullable String memo) {
		return (null == memo) || (StringUtils.getBytesUtf8(memo).length <= properties.maxMemoUtf8Bytes());
	}

	@Override
	public ResponseCodeEnum queryableTopicStatus(TopicID id, FCMap<MerkleEntityId, MerkleTopic> topics) {
		MerkleTopic merkleTopic = topics.get(MerkleEntityId.fromTopicId(id));

		return Optional.ofNullable(merkleTopic)
				.map(t -> t.isDeleted() ? INVALID_TOPIC_ID : OK)
				.orElse(INVALID_TOPIC_ID);
	}

	@Override
	public ResponseCodeEnum tokenSymbolCheck(String symbol) {
		int numUtf8Bytes = StringUtils.getBytesUtf8(symbol).length;
		if (numUtf8Bytes == 0) {
			return MISSING_TOKEN_SYMBOL;
		}
		if (numUtf8Bytes > properties.maxTokenSymbolUtf8Bytes()) {
			return TOKEN_SYMBOL_TOO_LONG;
		}
		return OK;
	}

	@Override
	public ResponseCodeEnum tokenNameCheck(String name) {
		int numUtf8Bytes = StringUtils.getBytesUtf8(name).length;
		if (numUtf8Bytes == 0) {
			return MISSING_TOKEN_NAME;
		}
		if (numUtf8Bytes > properties.maxTokenNameUtf8Bytes()) {
			return TOKEN_NAME_TOO_LONG;
		}
		return OK;
	}

	/* Not applicable until auto-renew is implemented. */
	boolean isExpired(MerkleTopic merkleTopic) {
		Instant expiry = Instant.ofEpochSecond(
				merkleTopic.getExpirationTimestamp().getSeconds(),
				merkleTopic.getExpirationTimestamp().getNanos());
		return txnCtx.consensusTime().isAfter(expiry);
	}
}
