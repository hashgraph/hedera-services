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
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.state.submerkle.RichInstant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Instant;

import static com.hedera.services.state.merkle.MerkleTopic.RUNNING_HASH_BYTE_ARRAY_SIZE;
import static com.hedera.services.state.merkle.MerkleTopic.RUNNING_HASH_VERSION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;

/**
 * Represents the model of a {@link com.hedera.services.state.merkle.MerkleTopic}.
 *
 * @author Yoan Sredkov
 */
public class Topic {
	private final Logger log = LogManager.getLogger(Topic.class);
	
	private final Id id;
	private String memo;
	private JKey adminKey;
	private JKey submitKey;
	private Id autoRenewAccountId;
	private long autoRenewDurationSeconds;
	private boolean deleted;
	private boolean isNew;
	private boolean hasUpdatedHashAndSeqNo;
	private RichInstant expirationTimestamp;
	private byte[] runningHash;
	private long sequenceNumber;

	public Topic(final Id id) {
		this.id = id;
	}

	/**
	 * Creates a new {@link Topic} from the given body.
	 * Note: The created model is not added to state, and must be explicitly persisted via {@link com.hedera.services.store.TopicStore#persistNew(Topic)}
	 *
	 * @param id
	 * 		- the id generated in the transition logic
	 * @param submitKey
	 * 		- the key which permits submitting messages
	 * @param adminKey
	 * 		- the adminKey of the topic
	 * @param autoRenewAccount
	 * 		- the account which pays for the automatic renewal of the topic
	 * @param memo
	 * 		- memo of the topic
	 * @param autoRenewPeriod
	 * 		- the period of automatic renewal
	 * @param expirationTime
	 * 		- expiration time of the topic,
	 * 		or when {@link com.hedera.services.txns.consensus.SubmitMessageTransitionLogic} will start failing.
	 * @return - the new topic
	 */
	public static Topic fromGrpcTopicCreate(
			Id id,
			@Nullable JKey submitKey,
			@Nullable JKey adminKey,
			@Nullable Account autoRenewAccount,
			String memo,
			long autoRenewPeriod,
			Instant expirationTime) {
		final var topic = new Topic(id);

		topic.setMemo(memo);
		topic.setDeleted(false);
		topic.setAutoRenewDurationSeconds(autoRenewPeriod);
		topic.setAutoRenewAccountId(autoRenewAccount != null ? autoRenewAccount.getId() : null);
		topic.setSubmitKey(submitKey);
		topic.setAdminKey(adminKey);
		topic.setExpirationTimestamp(RichInstant.fromJava(expirationTime));
		topic.setNew(true);
		return topic;
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

	public boolean isNew() {
		return isNew;
	}

	public void setNew(final boolean aNew) {
		isNew = aNew;
	}

	public byte[] getRunningHash() {
		return (runningHash != null) ? runningHash : new byte[RUNNING_HASH_BYTE_ARRAY_SIZE];
	}
	
	public void setRunningHash(final byte[] runningHash) {
		this.runningHash = runningHash;
	}

	/**
	 * Updates the {@link Topic#runningHash} and the {@link Topic#sequenceNumber} properties of the topic.
	 * Those changes have to be explicitly persisted
	 * via {@link com.hedera.services.store.TopicStore#persistTopic(Topic)}.
	 *
	 * @param payer
	 * 		- the account ID of the submitting member
	 * @param message
	 * 		- the actual message as bytes
	 * @param topicID
	 * 		- the ID of the topic
	 * @param consensusTime
	 * 		- consensus timestamp of the transaction
	 * @return
	 */
	public void updateRunningHashAndSeqNo(
			final Id payer,
			@Nullable byte[] message,
			@Nullable Id topicID,
			@Nullable Instant consensusTime
	) {
		try {
			if (null == message) {
				message = new byte[0];
			}
			if (null == topicID) {
				topicID = Id.DEFAULT;
			}
			if (null == consensusTime) {
				consensusTime = Instant.ofEpochSecond(0);
			}
			var boas = new ByteArrayOutputStream();
			var out = new ObjectOutputStream(boas);
			out.writeObject(getRunningHash());
			out.writeLong(RUNNING_HASH_VERSION);
			out.writeLong(payer.getShard());
			out.writeLong(payer.getRealm());
			out.writeLong(payer.getNum());
			out.writeLong(topicID.getShard());
			out.writeLong(topicID.getRealm());
			out.writeLong(topicID.getNum());
			out.writeLong(consensusTime.getEpochSecond());
			out.writeInt(consensusTime.getNano());
			++sequenceNumber;
			out.writeLong(sequenceNumber);
			out.writeObject(CommonUtils.noThrowSha384HashOf(message));
			out.flush();
			runningHash = CommonUtils.noThrowSha384HashOf(boas.toByteArray());
			setHasUpdatedHashAndSeqNo(true);
		} catch (IOException ioe) {
			log.error("Updating topic running hash failed.", ioe);
			throw new InvalidTransactionException(INVALID_TRANSACTION);
		}
	}

	public boolean hasUpdatedHashAndSeqNo() {
		return hasUpdatedHashAndSeqNo;
	}

	public void setHasUpdatedHashAndSeqNo(final boolean hasUpdatedHashAndSeqNo) {
		this.hasUpdatedHashAndSeqNo = hasUpdatedHashAndSeqNo;
	}
}
