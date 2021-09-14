package com.hedera.services.store.models;

/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

/**
 * Represents the model of a {@link com.hedera.services.state.merkle.MerkleTopic}.
 * 
 * @author Yoan Sredkov
 */
public class Topic {
	private final static Logger log = LogManager.getLogger(Topic.class);
	
	private final Id id;
	private String memo;
	private JKey adminKey;
	private JKey submitKey;
	private Id autoRenewAccountId;
	private long autoRenewDurationSeconds;
	private boolean deleted;
	private RichInstant expirationTimestamp;
	
	private long sequenceNumber;
	private byte[] runningHash;
	
 	public Topic(final Id id) {
		this.id = id;
	}

	/**
	 * Creates a new {@link Topic} from the given body.
	 * Note: The created model is not added to state, and must be explicitly persisted via {@link com.hedera.services.store.TopicStore#persistNew(Topic)}
	 * @param body - the gRPC transaction body
	 * @param id - the id generated in the transition logic
	 * @param expirationTime - expiration time of the topic,
	 *               or when {@link com.hedera.services.txns.consensus.SubmitMessageTransitionLogic} will start failing.
	 * @return - the new topic
	 */
	public static Topic fromGrpcTopicCreate(ConsensusCreateTopicTransactionBody body, Id id, Instant expirationTime) {
		final var topic = new Topic(id);
		final var submitKey = body.hasSubmitKey() ? attemptDecodeOrThrow(body.getSubmitKey()) : null;
		final var adminKey = body.hasAdminKey() ? attemptDecodeOrThrow(body.getAdminKey()) : null;
		
		topic.setMemo(body.getMemo());
		topic.setDeleted(false);
		topic.setAutoRenewDurationSeconds(body.getAutoRenewPeriod().getSeconds());
		topic.setAutoRenewAccountId(body.hasAutoRenewAccount() ? Id.fromGrpcAccount(body.getAutoRenewAccount()) : null);
		topic.setSubmitKey(submitKey);
		topic.setAdminKey(adminKey);
		topic.setExpirationTimestamp(RichInstant.fromJava(expirationTime));
		return topic;
	}

	private static JKey attemptDecodeOrThrow(Key k) {
		try {
			return JKey.mapKey(k);
		} catch (DecoderException e) {
			log.error("DecoderException should have been hit in TopicCreateTransitionLogic.validatePreStateTransition().", e);
			throw new InvalidTransactionException(ResponseCodeEnum.BAD_ENCODING);
		}
	}

	public Id getId() {
		return id;
	}

	public String getMemo() {
		return memo;
	}

	public void setMemo(final String memo) {
		this.memo = memo;
	}

	public JKey getAdminKey() {
		return adminKey;
	}

	public void setAdminKey(final JKey adminKey) {
		this.adminKey = adminKey;
	}

	public JKey getSubmitKey() {
		return submitKey;
	}

	public void setSubmitKey(final JKey submitKey) {
		this.submitKey = submitKey;
	}

	public Id getAutoRenewAccountId() {
		return autoRenewAccountId;
	}

	public void setAutoRenewAccountId(final Id autoRenewAccountId) {
		this.autoRenewAccountId = autoRenewAccountId;
	}

	public long getAutoRenewDurationSeconds() {
		return autoRenewDurationSeconds;
	}

	public void setAutoRenewDurationSeconds(final long autoRenewDurationSeconds) {
		this.autoRenewDurationSeconds = autoRenewDurationSeconds;
	}

	public RichInstant getExpirationTimestamp() {
		return expirationTimestamp;
	}

	public void setExpirationTimestamp(final RichInstant expirationTimestamp) {
		this.expirationTimestamp = expirationTimestamp;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(final boolean deleted) {
		this.deleted = deleted;
	}

	public long getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(final long sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}
}
