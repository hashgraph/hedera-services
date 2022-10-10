/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.store.models;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getMaxAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setAlreadyUsedAutomaticAssociationsTo;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setMaxAutomaticAssociationsTo;
import static com.hedera.services.store.contracts.WorldLedgers.ECDSA_KEY_ALIAS_PREFIX;
import static com.hedera.services.utils.EntityIdUtils.ECDSA_SECP256K1_ALIAS_SIZE;
import static com.hedera.services.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * Encapsulates the state and operations of a Hedera account.
 *
 * <p>Operations are validated, and throw a {@link
 * com.hedera.services.exceptions.InvalidTransactionException} with response code capturing the
 * failure when one occurs.
 *
 * <p><b>NOTE:</b> This implementation is incomplete, and includes only the API needed to support
 * the Hedera Token Service. The memo field, for example, is not yet present.
 */
public class Account {
    private final Id id;

    private long expiry;
    private long balance;
    private boolean deleted = false;
    private boolean isSmartContract = false;
    private boolean isReceiverSigRequired = false;
    private long ownedNfts;
    private long autoRenewSecs;
    private ByteString alias = ByteString.EMPTY;
    private JKey key;
    private String memo = "";
    private Id proxy;
    private int autoAssociationMetadata;
    private TreeMap<EntityNum, Long> cryptoAllowances;
    private TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances;
    private TreeSet<FcTokenAllowanceId> approveForAllNfts;
    private int numAssociations;
    private int numPositiveBalances;
    private int numTreasuryTitles;
    private long ethereumNonce;

    public Account(Id id) {
        this.id = id;
    }

    public void setExpiry(long expiry) {
        this.expiry = expiry;
    }

    public void initBalance(long balance) {
        this.balance = balance;
    }

    public void setEthereumNonce(long ethereumNonce) {
        this.ethereumNonce = ethereumNonce;
    }

    public void incrementEthereumNonce() {
        this.ethereumNonce++;
    }

    public long getEthereumNonce() {
        return ethereumNonce;
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

    public int getNumTreasuryTitles() {
        return numTreasuryTitles;
    }

    public void setNumTreasuryTitles(int numTreasuryTitles) {
        this.numTreasuryTitles = numTreasuryTitles;
    }

    public void incrementNumTreasuryTitles() {
        numTreasuryTitles++;
    }

    public void decrementNumTreasuryTitles() {
        validateTrue(numTreasuryTitles > 0, FAIL_INVALID);
        numTreasuryTitles--;
    }

    public Address canonicalAddress() {
        if (alias.isEmpty()) {
            return id.asEvmAddress();
        } else {
            if (alias.size() == EVM_ADDRESS_SIZE) {
                return Address.wrap(Bytes.wrap(alias.toByteArray()));
            } else if (alias.size() == ECDSA_SECP256K1_ALIAS_SIZE
                    && alias.startsWith(ECDSA_KEY_ALIAS_PREFIX)) {
                var addressBytes =
                        EthTxSigs.recoverAddressFromPubKey(alias.substring(2).toByteArray());
                return addressBytes == null
                        ? id.asEvmAddress()
                        : Address.wrap(Bytes.wrap(addressBytes));
            } else {
                return id.asEvmAddress();
            }
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
        autoAssociationMetadata =
                setMaxAutomaticAssociationsTo(autoAssociationMetadata, maxAutomaticAssociations);
    }

    public void setAlreadyUsedAutomaticAssociations(int alreadyUsedCount) {
        validateTrue(
                isValidAlreadyUsedCount(alreadyUsedCount), NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
        autoAssociationMetadata =
                setAlreadyUsedAutomaticAssociationsTo(autoAssociationMetadata, alreadyUsedCount);
    }

    public void incrementUsedAutomaticAssociations() {
        var count = getAlreadyUsedAutomaticAssociations();
        setAlreadyUsedAutomaticAssociations(++count);
    }

    public void decrementUsedAutomaticAssociations() {
        var count = getAlreadyUsedAutomaticAssociations();
        setAlreadyUsedAutomaticAssociations(--count);
    }

    public int getNumAssociations() {
        return numAssociations;
    }

    public void setNumAssociations(final int numAssociations) {
        if (numAssociations < 0) {
            // not possible
            this.numAssociations = 0;
        } else {
            this.numAssociations = numAssociations;
        }
    }

    public int getNumPositiveBalances() {
        return numPositiveBalances;
    }

    public void setNumPositiveBalances(final int numPositiveBalances) {
        if (numPositiveBalances < 0) {
            // not possible
            this.numPositiveBalances = 0;
        } else {
            this.numPositiveBalances = numPositiveBalances;
        }
    }

    /**
     * Associated the given list of Tokens to this account.
     *
     * @param tokens List of tokens to be associated to the Account
     * @param tokenStore TypedTokenStore to validate if existing relationship with the tokens to be
     *     associated with
     * @param isAutomaticAssociation whether these associations count against the max
     *     auto-associations limit
     * @param shouldEnableRelationship whether the new relationships should be enabled
     *     unconditionally, no matter KYC and freeze settings
     * @param dynamicProperties GlobalDynamicProperties to fetch the token associations limit and
     *     enforce it.
     * @return the new token relationships formed by this association
     */
    public List<TokenRelationship> associateWith(
            final List<Token> tokens,
            final TypedTokenStore tokenStore,
            final boolean isAutomaticAssociation,
            final boolean shouldEnableRelationship,
            final GlobalDynamicProperties dynamicProperties) {
        final var proposedTotalAssociations = tokens.size() + numAssociations;
        validateFalse(
                exceedsTokenAssociationLimit(dynamicProperties, proposedTotalAssociations),
                TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);
        final List<TokenRelationship> newModelRels = new ArrayList<>();
        for (final var token : tokens) {
            validateFalse(
                    tokenStore.hasAssociation(token, this), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
            if (isAutomaticAssociation) {
                incrementUsedAutomaticAssociations();
            }
            final var newRel =
                    shouldEnableRelationship
                            ? token.newEnabledRelationship(this)
                            : token.newRelationshipWith(this, false);
            numAssociations++;
            newModelRels.add(newRel);
        }
        return newModelRels;
    }

    /**
     * Applies the given list of {@link Dissociation}s, validating that this account is indeed
     * associated to each involved token.
     *
     * @param dissociations the dissociations to perform
     * @param validator validator to check if the dissociating token has expired
     */
    public void dissociateUsing(
            final List<Dissociation> dissociations, final OptionValidator validator) {
        for (final var dissociation : dissociations) {
            validateTrue(id.equals(dissociation.dissociatingAccountId()), FAIL_INVALID);
            dissociation.updateModelRelsSubjectTo(validator);
            final var pastRel = dissociation.dissociatingAccountRel();
            if (pastRel.isAutomaticAssociation()) {
                decrementUsedAutomaticAssociations();
            }
            if (pastRel.getBalanceChange() != 0) {
                numPositiveBalances--;
            }
            numAssociations--;
        }
    }

    public Id getId() {
        return id;
    }

    private boolean isValidAlreadyUsedCount(int alreadyUsedCount) {
        return alreadyUsedCount >= 0 && alreadyUsedCount <= getMaxAutomaticAssociations();
    }

    private boolean exceedsTokenAssociationLimit(
            GlobalDynamicProperties dynamicProperties, int totalAssociations) {
        return dynamicProperties.areTokenAssociationsLimited()
                && totalAssociations > dynamicProperties.maxTokensPerAccount();
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
        return MoreObjects.toStringHelper(Account.class)
                .add("id", id)
                .add("expiry", expiry)
                .add("balance", balance)
                .add("deleted", deleted)
                .add("ownedNfts", ownedNfts)
                .add("alreadyUsedAutoAssociations", getAlreadyUsedAutomaticAssociations())
                .add("maxAutoAssociations", getMaxAutomaticAssociations())
                .add("alias", getAlias().toStringUtf8())
                .add("cryptoAllowances", cryptoAllowances)
                .add("fungibleTokenAllowances", fungibleTokenAllowances)
                .add("approveForAllNfts", approveForAllNfts)
                .add("numAssociations", numAssociations)
                .add("numPositiveBalances", numPositiveBalances)
                .add("ethereumNonce", ethereumNonce)
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
        return cryptoAllowances == null ? Collections.emptyMap() : cryptoAllowances;
    }

    public SortedMap<EntityNum, Long> getMutableCryptoAllowances() {
        if (cryptoAllowances == null) {
            cryptoAllowances = new TreeMap<>();
        }
        return cryptoAllowances;
    }

    public void setCryptoAllowances(final Map<EntityNum, Long> cryptoAllowances) {
        this.cryptoAllowances = new TreeMap<>(cryptoAllowances);
    }

    public Map<FcTokenAllowanceId, Long> getFungibleTokenAllowances() {
        return fungibleTokenAllowances == null ? Collections.emptyMap() : fungibleTokenAllowances;
    }

    public SortedMap<FcTokenAllowanceId, Long> getMutableFungibleTokenAllowances() {
        if (fungibleTokenAllowances == null) {
            fungibleTokenAllowances = new TreeMap<>();
        }
        return fungibleTokenAllowances;
    }

    public void setFungibleTokenAllowances(
            final Map<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
        this.fungibleTokenAllowances = new TreeMap<>(fungibleTokenAllowances);
    }

    public Set<FcTokenAllowanceId> getApprovedForAllNftsAllowances() {
        return approveForAllNfts == null ? Collections.emptySet() : approveForAllNfts;
    }

    public SortedSet<FcTokenAllowanceId> getMutableApprovedForAllNfts() {
        if (approveForAllNfts == null) {
            approveForAllNfts = new TreeSet<>();
        }
        return approveForAllNfts;
    }

    public void setApproveForAllNfts(final Set<FcTokenAllowanceId> approveForAllNfts) {
        this.approveForAllNfts = new TreeSet<>(approveForAllNfts);
    }

    public int getTotalAllowances() {
        return cryptoAllowances.size() + fungibleTokenAllowances.size() + approveForAllNfts.size();
    }
}
