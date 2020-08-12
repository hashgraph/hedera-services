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
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.ledger.HederaLedger;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hedera.services.legacy.core.jproto.JKey.mapKey;

/**
 * Implements an {@link OptionValidator} that relies an injected instance
 * of the {@link TransactionContext} to determine whether various options are
 * permissible.
 *
 * @author Michael Tinker
 */
public class ContextOptionValidator implements OptionValidator {

	public static final Logger log = LogManager.getLogger(ContextOptionValidator.class);
	private final HederaLedger ledger;
	private final PropertySource properties;
	private final TransactionContext txnCtx;

	public ContextOptionValidator(HederaLedger ledger, PropertySource properties, TransactionContext txnCtx) {
		this.ledger = ledger;
		this.properties = properties;
		this.txnCtx = txnCtx;
	}

	@Override
	public boolean hasGoodEncoding(Key key) {
		try {
			mapKey(key);
			return true;
		} catch (Exception ignore) {
			log.warn(ignore.getMessage());
			return false;
		}
	}

	@Override
	public boolean isValidTxnDuration(long duration) {
		long minDuration = properties.getLongProperty("hedera.transaction.minValidDuration");
		long maxDuration = properties.getLongProperty("hedera.transaction.maxValidDuration");

		return duration >= minDuration && duration <= maxDuration;
	}

	@Override
	public boolean isValidExpiry(Timestamp expiry) {
		Instant then = Instant.ofEpochSecond(expiry.getSeconds(), expiry.getNanos());
		return then.isAfter(txnCtx.consensusTime());
	}

	@Override
	public boolean isValidAutoRenewPeriod(Duration autoRenewPeriod) {
		long duration = autoRenewPeriod.getSeconds();
		long minDuration = properties.getLongProperty("ledger.autoRenewPeriod.minDuration");
		long maxDuration = properties.getLongProperty("ledger.autoRenewPeriod.maxDuration");

		if (duration < minDuration || duration > maxDuration) {
			return false;
		}
		return true;
	}

	@Override
	public boolean isAcceptableLength(TransferList accountAmounts) {
		int maxLen = properties.getIntProperty("ledger.transfers.maxLen");

		return accountAmounts.getAccountAmountsCount() <= maxLen;
	}

	@Override
	public boolean hasOnlyCryptoAccounts(TransferList accountAmounts) {
		return accountAmounts.getAccountAmountsList()
				.stream()
				.map(AccountAmount::getAccountID)
				.noneMatch(ledger::isSmartContract);
	}

	@Override
	public boolean isValidEntityMemo(@Nullable String memo) {
		var maxUtf8Bytes = properties.getIntProperty("hedera.transaction.maxMemoUtf8Bytes");
		return (null == memo) || (StringUtils.getBytesUtf8(memo).length <= maxUtf8Bytes);
	}

	@Override
	public ResponseCodeEnum queryableTopicStatus(TopicID id, FCMap<MerkleEntityId, MerkleTopic> topics) {
		MerkleTopic merkleTopic = topics.get(MerkleEntityId.fromTopicId(id));

		return Optional.ofNullable(merkleTopic)
				.map(t -> t.isDeleted() ? INVALID_TOPIC_ID : OK)
				.orElse(INVALID_TOPIC_ID);
	}

	/* Not applicable until auto-renew is implemented. */
	boolean isExpired(MerkleTopic merkleTopic) {
		Instant expiry = Instant.ofEpochSecond(
				merkleTopic.getExpirationTimestamp().getSeconds(),
				merkleTopic.getExpirationTimestamp().getNanos());
		return txnCtx.consensusTime().isAfter(expiry);
	}
}
