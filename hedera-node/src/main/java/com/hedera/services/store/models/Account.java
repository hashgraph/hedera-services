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
import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getMaxAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setAlreadyUsedAutomaticAssociationsTo;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setMaxAutomaticAssociationsTo;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.aggregateNftAllowances;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
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
	private ByteString alias = ByteString.EMPTY;
	private JKey key;
	private String memo = "";
	private Id proxy;
	private int autoAssociationMetadata;
	private Map<EntityNum, Long> cryptoAllowances = Collections.emptyMap();
	private Map<FcTokenAllowanceId, Long> fungibleTokenAllowances = Collections.emptyMap();
	private Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowances = Collections.emptyMap();

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

	public Address canonicalAddress() {
		if (alias.isEmpty()) {
			return id.asEvmAddress();
		} else {
			return Address.wrap(Bytes.wrap(alias.toByteArray()));
		}
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

	public void associateWith(List<Token> tokens, boolean automaticAssociation) {
		final Set<Id> uniqueIds = new HashSet<>();
		for (var token : tokens) {
			final var tokenId = token.getId();
			validateFalse(associatedTokens.contains(tokenId), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
			if (automaticAssociation) {
				incrementUsedAutomaticAssocitions();
			}
			uniqueIds.add(tokenId);
		}

		associatedTokens.addAllIds(uniqueIds);
	}

	/**
	 * Applies the given list of {@link Dissociation}s, validating that this account is
	 * indeed associated to each involved token.
	 *
	 * @param dissociations
	 * 		the dissociations to perform.
	 * @param validator
	 * 		the validator to use for each dissociation
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
				.add("alias", getAlias().toStringUtf8())
				.add("cryptoAllowances", cryptoAllowances)
				.add("fungibleTokenAllowances", fungibleTokenAllowances)
				.add("nftAllowances", nftAllowances)
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

	public ByteString getAlias() {
		return alias;
	}

	public void setAlias(final ByteString alias) {
		this.alias = alias;
	}

	public Map<EntityNum, Long> getCryptoAllowances() {
		return cryptoAllowances;
	}

	public void setCryptoAllowances(final Map<EntityNum, Long> cryptoAllowances) {
		this.cryptoAllowances = new TreeMap<>(cryptoAllowances);
	}

	public Map<FcTokenAllowanceId, Long> getFungibleTokenAllowances() {
		return fungibleTokenAllowances;
	}

	public void setFungibleTokenAllowances(
			final Map<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
		this.fungibleTokenAllowances = new TreeMap<>(fungibleTokenAllowances);
	}

	public Map<FcTokenAllowanceId, FcTokenAllowance> getNftAllowances() {
		return nftAllowances;
	}

	public void setNftAllowances(
			final Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowances) {
		this.nftAllowances = new TreeMap<>(nftAllowances);
	}

	public int getTotalAllowances() {
		// each serial number of an NFT is considered as an allowance.
		// So for Nft allowances aggregated amount is considered for limit calculation.
		return cryptoAllowances.size() + fungibleTokenAllowances.size() + aggregateNftAllowances(nftAllowances);
	}
}
