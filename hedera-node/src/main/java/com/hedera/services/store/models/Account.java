package com.hedera.services.store.models;

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

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getMaxAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setAlreadyUsedAutomaticAssociationsTo;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setMaxAutomaticAssociationsTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;

/**
 * Encapsulates the state and operations of a Hedera account.
 * <p>
 * Operations are validated, and throw a {@link com.hedera.services.exceptions.InvalidTransactionException}
 * with response code capturing the failure when one occurs.
 *
 * <b>NOTE:</b> This implementation is incomplete, and includes
 * only the API needed to support the Hedera Token Service. The
 * memo field, for example, is not yet present.
 */
public class Account {
	private final Id id;

	private long expiry;
	private long balance;
	private boolean deleted = false;
	private boolean isSmartContract = false;
	private boolean isReceiverSigRequired = false;
	private CopyOnWriteIds associatedTokens;
	private long ownedNfts;
	private long autoRenewSecs;
	private JKey key;
	private String memo = "";
	private Id proxy;
	private int autoAssociationMetadata;

	/**
	 * Mutates the model account properties in the context of the contract update transition logic.
	 * The method also performs validation and can throw a {@link com.hedera.services.exceptions.InvalidTransactionException}
	 * with response code capturing the failure when one occurs.
	 * Subject of the update can be only:
	 * <li>Admin key</li>
	 * <li>Memo</li>
	 * <li>Proxy</li>
	 * <li>Expiration time</li>
	 * <li>AutoRenew period</li>
	 *
	 * @param newAdminKey        the new admin key
	 * @param newProxy           the new proxy
	 * @param newAutoRenewPeriod the new autoRenew period
	 * @param newExpirationTime  the new expiration time
	 * @param newMemo            the new memo
	 */
	public void updateFromGrpcContract(
			final Optional<JKey> newAdminKey,
			final Optional<AccountID> newProxy,
			final Optional<Duration> newAutoRenewPeriod,
			final Optional<Timestamp> newExpirationTime,
			final Optional<String> newMemo) {

		validateFalse(!affectsExpiryOnly(newAdminKey, newProxy, newAutoRenewPeriod, newMemo)
				&& this.getKey().hasContractID(), MODIFYING_IMMUTABLE_CONTRACT);
		validateFalse(reducesExpiry(newExpirationTime), EXPIRATION_REDUCTION_NOT_ALLOWED);

		newAdminKey.ifPresent(this::setKey);
		newMemo.ifPresent(this::setMemo);
		newProxy.ifPresent(p -> this.setProxy(Id.fromGrpcAccount(p)));
		newExpirationTime.ifPresent(e -> this.setExpiry(e.getSeconds()));
		newAutoRenewPeriod.ifPresent(a -> this.setAutoRenewSecs(a.getSeconds()));
	}

	private boolean affectsExpiryOnly(final Optional<JKey> newAdminKey,
									  final Optional<AccountID> newProxy,
									  final Optional<Duration> newAutoRenewPeriod,
									  final Optional<String> newMemo) {
		return newAdminKey.isEmpty() && newProxy.isEmpty() && newAutoRenewPeriod.isEmpty() && newMemo.isEmpty();
	}

	private boolean reducesExpiry(final Optional<Timestamp> newExpirationTime) {
		return newExpirationTime.isPresent() && newExpirationTime.get().getSeconds() < this.getExpiry();
	}

	public Account(Id id) {
		this.id = id;
	}

	public void setAssociatedTokens(CopyOnWriteIds associatedTokens) {
		this.associatedTokens = associatedTokens;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public void initBalance(long balance) {
		this.balance = balance;
	}

	public long getOwnedNfts() {
		return ownedNfts;
	}

	public void setOwnedNfts(long ownedNfts) {
		this.ownedNfts = ownedNfts;
	}

	public void incrementOwnedNfts() {
		this.ownedNfts++;
	}

	public void setAutoAssociationMetadata(int autoAssociationMetadata) {
		this.autoAssociationMetadata = autoAssociationMetadata;
	}

	public int getAutoAssociationMetadata() {
		return autoAssociationMetadata;
	}

	public int getMaxAutomaticAssociations() {
		return getMaxAutomaticAssociationsFrom(autoAssociationMetadata);
	}

	public int getAlreadyUsedAutomaticAssociations() {
		return getAlreadyUsedAutomaticAssociationsFrom(autoAssociationMetadata);
	}

	public void setMaxAutomaticAssociations(int maxAutomaticAssociations) {
		autoAssociationMetadata = setMaxAutomaticAssociationsTo(autoAssociationMetadata, maxAutomaticAssociations);
	}

	public void setAlreadyUsedAutomaticAssociations(int alreadyUsedCount) {
		validateTrue(isValidAlreadyUsedCount(alreadyUsedCount), NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
		autoAssociationMetadata = setAlreadyUsedAutomaticAssociationsTo(autoAssociationMetadata, alreadyUsedCount);
	}

	public void incrementUsedAutomaticAssocitions() {
		var count = getAlreadyUsedAutomaticAssociations();
		setAlreadyUsedAutomaticAssociations(++count);
	}

	public void decrementUsedAutomaticAssocitions() {
		var count = getAlreadyUsedAutomaticAssociations();
		setAlreadyUsedAutomaticAssociations(--count);
	}

	public void associateWith(List<Token> tokens, int maxAllowed, boolean automaticAssociation) {
		final var alreadyAssociated = associatedTokens.size();
		final var proposedNewAssociations = tokens.size() + alreadyAssociated;
		validateTrue(proposedNewAssociations <= maxAllowed, TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		final Set<Id> uniqueIds = new HashSet<>();
		for (var token : tokens) {
			final var id = token.getId();
			validateFalse(associatedTokens.contains(id), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
			if (automaticAssociation) {
				incrementUsedAutomaticAssocitions();
			}
			uniqueIds.add(id);
		}

		associatedTokens.addAllIds(uniqueIds);
	}

	/**
	 * Applies the given list of {@link Dissociation}s, validating that this account is
	 * indeed associated to each involved token.
	 *
	 * @param dissociations the dissociations to perform.
	 * @param validator     the validator to use for each dissociation
	 */
	public void dissociateUsing(List<Dissociation> dissociations, OptionValidator validator) {
		final Set<Id> dissociatedTokenIds = new HashSet<>();
		for (var dissociation : dissociations) {
			validateTrue(id.equals(dissociation.dissociatingAccountId()), FAIL_INVALID);
			dissociation.updateModelRelsSubjectTo(validator);
			dissociatedTokenIds.add(dissociation.dissociatedTokenId());
			if (dissociation.dissociatingAccountRel().isAutomaticAssociation()) {
				decrementUsedAutomaticAssocitions();
			}
		}
		associatedTokens.removeAllIds(dissociatedTokenIds);
	}

	public Id getId() {
		return id;
	}

	public CopyOnWriteIds getAssociatedTokens() {
		return associatedTokens;
	}

	public boolean isAssociatedWith(Id token) {
		return associatedTokens.contains(token);
	}

	private boolean isValidAlreadyUsedCount(int alreadyUsedCount) {
		return alreadyUsedCount >= 0 && alreadyUsedCount <= getMaxAutomaticAssociations();
	}

	/* NOTE: The object methods below are only overridden to improve
	readability of unit tests; this model object is not used in hash-based
	collections, so the performance of these methods doesn't matter. */

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		final var assocTokenRepr = Optional.ofNullable(associatedTokens)
				.map(CopyOnWriteIds::toReadableIdList)
				.orElse("<N/A>");
		return MoreObjects.toStringHelper(Account.class)
				.add("id", id)
				.add("expiry", expiry)
				.add("balance", balance)
				.add("deleted", deleted)
				.add("tokens", assocTokenRepr)
				.add("ownedNfts", ownedNfts)
				.add("alreadyUsedAutoAssociations", getAlreadyUsedAutomaticAssociations())
				.add("maxAutoAssociations", getMaxAutomaticAssociations())
				.toString();
	}

	public long getExpiry() {
		return expiry;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(final boolean deleted) {
		this.deleted = deleted;
	}

	public boolean isSmartContract() {
		return isSmartContract;
	}

	public void setSmartContract(boolean val) {
		this.isSmartContract = val;
	}

	public boolean isReceiverSigRequired() {
		return this.isReceiverSigRequired;
	}

	public void setReceiverSigRequired(boolean isReceiverSigRequired) {
		this.isReceiverSigRequired = isReceiverSigRequired;
	}

	public long getBalance() {
		return balance;
	}

	public long getAutoRenewSecs() {
		return autoRenewSecs;
	}

	public void setAutoRenewSecs(final long autoRenewSecs) {
		this.autoRenewSecs = autoRenewSecs;
	}

	public JKey getKey() {
		return key;
	}

	public void setKey(final JKey key) {
		this.key = key;
	}

	public String getMemo() {
		return memo;
	}

	public void setMemo(final String memo) {
		this.memo = memo;
	}

	public Id getProxy() {
		return proxy;
	}

	public void setProxy(final Id proxy) {
		this.proxy = proxy;
	}
}
