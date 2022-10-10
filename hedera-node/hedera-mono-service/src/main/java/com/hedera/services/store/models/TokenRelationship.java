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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;

import com.google.common.base.MoreObjects;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Encapsulates the state and operations of a Hedera account-token relationship.
 *
 * <p>Operations are validated, and throw a {@link
 * com.hedera.services.exceptions.InvalidTransactionException} with response code capturing the
 * failure when one occurs.
 *
 * <p><b>NOTE:</b> Some operations will likely be moved to specializations of this class as NFTs are
 * fully supported. For example, a {@link TokenRelationship#getBalanceChange()} signature only makes
 * sense for a token of type {@code FUNGIBLE_COMMON}; the analogous signature for a {@code
 * NON_FUNGIBLE_UNIQUE} is {@code getOwnershipChanges())}, returning a type that is structurally
 * equivalent to a {@code Pair<long[], long[]>} of acquired and relinquished serial numbers.
 */
public class TokenRelationship {
    private final Token token;
    private final Account account;

    private long balance;
    private boolean frozen;
    private boolean kycGranted;
    private boolean destroyed = false;
    private boolean notYetPersisted = true;
    private boolean automaticAssociation = false;

    private long balanceChange = 0L;

    public TokenRelationship(Token token, Account account) {
        this.token = token;
        this.account = account;
    }

    public FcTokenAssociation asAutoAssociation() {
        return new FcTokenAssociation(token.getId().num(), account.getId().num());
    }

    public long getBalance() {
        return balance;
    }

    /**
     * Set the balance of this relationship's token that the account holds at the beginning of a
     * user transaction. (In particular, does <b>not</b> change the return value of {@link
     * TokenRelationship#getBalanceChange()}.)
     *
     * @param balance the initial balance in the relationship
     */
    public void initBalance(long balance) {
        this.balance = balance;
    }

    /**
     * Update the balance of this relationship token held by the account.
     *
     * <p>This <b>does</b> change the return value of {@link TokenRelationship#getBalanceChange()}.
     *
     * @param balance the updated balance of the relationship
     */
    public void setBalance(long balance) {
        if (!token.isDeleted()) {
            validateTrue(isTokenFrozenFor(), ACCOUNT_FROZEN_FOR_TOKEN);
            validateTrue(isTokenKycGrantedFor(), ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN);
        }

        balanceChange += (balance - this.balance);
        this.balance = balance;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    /**
     * Modifies the state of the "Frozen" property to either true (freezes the relation between the
     * account and the token) or false (unfreezes the relation between the account and the token).
     *
     * <p>Before the property modification, the method performs validation, that the respective
     * token has a freeze key.
     *
     * @param freeze the new state of the property
     */
    public void changeFrozenState(boolean freeze) {
        validateTrue(token.hasFreezeKey(), TOKEN_HAS_NO_FREEZE_KEY);
        this.frozen = freeze;
    }

    public boolean isKycGranted() {
        return kycGranted;
    }

    public void setKycGranted(boolean kycGranted) {
        this.kycGranted = kycGranted;
    }

    /**
     * Modifies the state of the KYC property to either true (granted) or false (revoked).
     *
     * <p>Before the property modification, the method performs validation, that the respective
     * token has a KYC key.
     *
     * @param isGranted the new state of the property
     */
    public void changeKycState(boolean isGranted) {
        validateTrue(token.hasKycKey(), TOKEN_HAS_NO_KYC_KEY);
        this.kycGranted = isGranted;
    }

    public long getBalanceChange() {
        return balanceChange;
    }

    public Token getToken() {
        return token;
    }

    public Account getAccount() {
        return account;
    }

    boolean hasInvolvedIds(Id tokenId, Id accountId) {
        return account.getId().equals(accountId) && token.getId().equals(tokenId);
    }

    public boolean isNotYetPersisted() {
        return notYetPersisted;
    }

    public void markAsPersisted() {
        notYetPersisted = false;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void markAsDestroyed() {
        validateFalse(notYetPersisted, FAIL_INVALID);
        destroyed = true;
    }

    public boolean hasChangesForRecord() {
        return balanceChange != 0 && (hasCommonRepresentation() || token.isDeleted());
    }

    public boolean hasCommonRepresentation() {
        return token.getType() == TokenType.FUNGIBLE_COMMON;
    }

    public boolean hasUniqueRepresentation() {
        return token.getType() == TokenType.NON_FUNGIBLE_UNIQUE;
    }

    public boolean isAutomaticAssociation() {
        return automaticAssociation;
    }

    public void setAutomaticAssociation(final boolean automaticAssociation) {
        this.automaticAssociation = automaticAssociation;
    }

    private boolean isTokenFrozenFor() {
        return !token.hasFreezeKey() || !frozen;
    }

    private boolean isTokenKycGrantedFor() {
        return !token.hasKycKey() || kycGranted;
    }

    /* The object methods below are only overridden to improve
    readability of unit tests; model objects are not used in hash-based
    collections, so the performance of these methods doesn't matter. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !obj.getClass().equals(TokenRelationship.class)) {
            return false;
        }

        final var that = (TokenRelationship) obj;
        return new EqualsBuilder()
                .append(notYetPersisted, that.notYetPersisted)
                .append(account, that.account)
                .append(balance, that.balance)
                .append(balanceChange, that.balanceChange)
                .append(frozen, that.frozen)
                .append(kycGranted, that.kycGranted)
                .append(automaticAssociation, that.automaticAssociation)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(TokenRelationship.class)
                .add("notYetPersisted", notYetPersisted)
                .add("account", account)
                .add("token", token)
                .add("balance", balance)
                .add("balanceChange", balanceChange)
                .add("frozen", frozen)
                .add("kycGranted", kycGranted)
                .add("isAutomaticAssociation", automaticAssociation)
                .toString();
    }
}
