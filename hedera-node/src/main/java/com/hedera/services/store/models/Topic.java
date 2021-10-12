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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Optional;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.state.submerkle.RichInstant.fromGrpc;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;

/**
 * Represents the model of a {@link com.hedera.services.state.merkle.MerkleTopic}.
 *
 * @author Yoan Sredkov
 */
public class Topic {

	private final Id id;
	private String memo;
	private JKey adminKey;
	private JKey submitKey;
	private Id autoRenewAccountId;
	private long autoRenewDurationSeconds;
	private boolean deleted;
	private boolean isNew;
	private RichInstant expirationTimestamp;

	private long sequenceNumber;

	public Topic(final Id id) {
		this.id = id;
	}

	/**
	 * Creates a new {@link Topic} from the given body.
	 * Note: The created model is not added to state, and must be explicitly persisted via {@link com.hedera.services.store.TopicStore#persistNew(Topic)}
	 *
	 * @param id               the id generated in the transition logic
	 * @param submitKey        the key which permits submitting messages
	 * @param adminKey         the adminKey for the topic
	 * @param autoRenewAccount the account which pays for the automatic renewal of the topic
	 * @param memo             the memo for the token entity
	 * @param autoRenewPeriod  the period of automatic renewal
	 * @param expirationTime   expiration time of the topic,
	 *                         or when {@link com.hedera.services.txns.consensus.SubmitMessageTransitionLogic} will start failing.
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

	/**
	 * Updates the topic with the given gRPC data.
	 *
	 * @param newExpirationTime                  the optional new expiration time of the topic
	 * @param newAdminKey                        the decoded admin key
	 * @param newSubmitKey                       the submit key
	 * @param newMemo                            the new optional memo
	 * @param newAutoRenewPeriod                 the new optional autorenew period
	 * @param newAutoRenewAccount                the new optional autorenew account
	 * @param transactionHasAutoRenewAccount     a boolean whether the transaction carries a new autorenew account
	 * @param transactionRemovesAutoRenewAccount a boolean which flags the removal of the current autorenew account
	 */
	public void update(final Optional<Timestamp> newExpirationTime,
					   final Optional<Key> newAdminKey,
					   final Optional<Key> newSubmitKey,
					   final Optional<String> newMemo,
					   final Optional<Duration> newAutoRenewPeriod,
					   final Optional<Account> newAutoRenewAccount,
					   final boolean transactionHasAutoRenewAccount,
					   final boolean transactionRemovesAutoRenewAccount
	) {

		if (newExpirationTime.isPresent()) {
			final var currentExpiryIsAfterNewExpiry =
					this.hasExpirationTimestamp() && this.getExpirationTimestamp().isAfter(fromGrpc(newExpirationTime.get()));
			validateFalse(currentExpiryIsAfterNewExpiry, EXPIRATION_REDUCTION_NOT_ALLOWED);
		}
		if (newAdminKey.isPresent() && !transactionHasAutoRenewAccount) {
			validateFalse(this.hasAutoRenewAccountId() && transactionRemovesAutoRenewAccount, AUTORENEW_ACCOUNT_NOT_ALLOWED);
		}
		if (newAutoRenewAccount.isPresent() && !transactionRemovesAutoRenewAccount) {
			validateFalse(!this.hasAdminKey() || newAdminKey.isEmpty(), AUTORENEW_ACCOUNT_NOT_ALLOWED);
			validateFalse(newAutoRenewAccount.get().isSmartContract(), INVALID_AUTORENEW_ACCOUNT);
		}
		newAdminKey.ifPresent(ak -> this.setAdminKey(asFcKeyUnchecked(ak)));
		newSubmitKey.ifPresent(sk -> this.setSubmitKey(asFcKeyUnchecked(sk)));
		newMemo.ifPresent(this::setMemo);
		newExpirationTime.ifPresent(t -> setExpirationTimestamp(fromGrpc(t)));
		newAutoRenewPeriod.ifPresent(p -> setAutoRenewDurationSeconds(p.getSeconds()));
		newAutoRenewAccount.ifPresent(na -> setAutoRenewAccountId(transactionRemovesAutoRenewAccount ? null : na.getId()));
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

	public boolean hasAdminKey() {
		return adminKey != null;
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

	public boolean hasAutoRenewAccountId() {
		return autoRenewAccountId != null;
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

	public boolean hasExpirationTimestamp() {
		return expirationTimestamp != null;
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
}
