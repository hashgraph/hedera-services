/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.txns.token.process;

import static com.hedera.node.app.service.evm.store.tokens.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.models.Account;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.models.Token;
import com.hedera.node.app.service.mono.store.models.TokenRelationship;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Objects;

public class Dissociation {
    private final TokenRelationship dissociatingAccountRel;
    private final TokenRelationship dissociatedTokenTreasuryRel;

    private boolean modelsAreUpdated = false;
    private boolean expiredTokenTreasuryReceivedBalance = false;

    public static Dissociation loadFrom(final TypedTokenStore tokenStore, final Account account, final Id tokenId) {
        final var token = tokenStore.loadPossiblyDeletedOrAutoRemovedToken(tokenId);
        final var dissociatingAccountRel = tokenStore.loadTokenRelationship(token, account);
        if (token.isBelievedToHaveBeenAutoRemoved()) {
            return new Dissociation(dissociatingAccountRel, null);
        } else {
            final var treasury = token.getTreasury();
            final var dissociatedTokenTreasuryRel = tokenStore.loadPossiblyMissingTokenRelationship(token, treasury);
            return new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);
        }
    }

    public Dissociation(
            final TokenRelationship dissociatingAccountRel,
            @Nullable final TokenRelationship dissociatedTokenTreasuryRel) {
        Objects.requireNonNull(dissociatingAccountRel);

        this.dissociatingAccountRel = dissociatingAccountRel;
        this.dissociatedTokenTreasuryRel = dissociatedTokenTreasuryRel;
    }

    public TokenRelationship dissociatingAccountRel() {
        return dissociatingAccountRel;
    }

    public Id dissociatingAccountId() {
        return dissociatingAccountRel.getAccount().getId();
    }

    public Account dissociatingAccount() {
        return dissociatingAccountRel.getAccount();
    }

    public Id dissociatedTokenId() {
        return dissociatingAccountRel.getToken().getId();
    }

    public Token dissociatingToken() {
        return dissociatingAccountRel.getToken();
    }

    public TokenRelationship dissociatedTokenTreasuryRel() {
        return dissociatedTokenTreasuryRel;
    }

    /**
     * Updates the model of the account-token relationship (and possibly the model of the
     * treasury-token relationship) with the results of the dissociation logic, using the given
     * validator to check for an expired token.
     *
     * <p>There are several cases:
     *
     * <ol>
     *   <li>If the token is deleted or auto-removed, any fungible units or NFTs owned by the
     *       dissociating account simply "disappear" (generating a non-zero-sum token transfer list
     *       in the record).
     *   <li>If the token is "detached" (i.e., expired but still within its grace period), then any
     *       fungible units owned by the dissociating account are transferred back to the treasury
     *       immediately; but if the token is non-fungible, the dissociation is still rejected with
     *       {@code ACCOUNT_STILL_OWNS_NFTS}.
     *   <li>Otherwise, the dissociating account's relationship is removed if and only if it does
     *       not own any fungible units or NFTs of the token.
     * </ol>
     *
     */
    public void updateModelRelsSubjectTo() {
        Objects.requireNonNull(dissociatingAccountRel);
        final var token = dissociatingAccountRel.getToken();
        if (token.isDeleted() || token.isBelievedToHaveBeenAutoRemoved()) {
            updateModelsForDissociationFromDeletedOrRemovedToken();
        } else {
            updateModelsForDissociationFromActiveToken();
        }
        dissociatingAccountRel.markAsDestroyed();
        modelsAreUpdated = true;
    }

    private void updateModelsForDissociationFromDeletedOrRemovedToken() {
        final var disappearingUnits = dissociatingAccountRel.getBalance();
        dissociatingAccountRel.setBalance(0L);

        final var token = dissociatingAccountRel.getToken();
        if (token.getType() == NON_FUNGIBLE_UNIQUE) {
            final var account = dissociatingAccountRel.getAccount();
            final var curOwnedNfts = account.getOwnedNfts();
            account.setOwnedNfts(curOwnedNfts - disappearingUnits);
        }
    }

    private void updateModelsForDissociationFromActiveToken() {
        Objects.requireNonNull(dissociatedTokenTreasuryRel);
        final var token = dissociatingAccountRel.getToken();
        final var isAccountTreasuryOfDissociatedToken =
                dissociatingAccountId().equals(token.getTreasury().getId());
        validateFalse(isAccountTreasuryOfDissociatedToken, ACCOUNT_IS_TREASURY);
        validateFalse(dissociatingAccountRel.isFrozen(), ACCOUNT_FROZEN_FOR_TOKEN);

        final var balance = dissociatingAccountRel.getBalance();
        if (balance > 0L) {
            if (token.getType() == NON_FUNGIBLE_UNIQUE) {
                throw new InvalidTransactionException(ACCOUNT_STILL_OWNS_NFTS);
            } else {
                throw new InvalidTransactionException(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
            }
        }
    }

    public void addUpdatedModelRelsTo(final List<TokenRelationship> accumulator) {
        if (!modelsAreUpdated) {
            throw new IllegalStateException("Cannot reveal changed relationships before update");
        }
        if (expiredTokenTreasuryReceivedBalance) {
            final var treasuryId = dissociatedTokenTreasuryRel.getAccount().getId();
            if (Id.ID_COMPARATOR.compare(dissociatingAccountId(), treasuryId) < 0) {
                accumulator.add(dissociatingAccountRel);
                accumulator.add(dissociatedTokenTreasuryRel);
            } else {
                accumulator.add(dissociatedTokenTreasuryRel);
                accumulator.add(dissociatingAccountRel);
            }
        } else {
            accumulator.add(dissociatingAccountRel);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(Dissociation.class)
                .add(
                        "dissociatingAccountId",
                        dissociatingAccountRel.getAccount().getId())
                .add("dissociatedTokenId", dissociatingAccountRel.getToken().getId())
                .add(
                        "dissociatedTokenTreasuryId",
                        dissociatedTokenTreasuryRel.getAccount().getId())
                .add("expiredTokenTreasuryReceivedBalance", expiredTokenTreasuryReceivedBalance)
                .toString();
    }
}
