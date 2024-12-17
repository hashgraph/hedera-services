/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import java.util.Map;

/**
 * Context for the current CryptoTransfer transaction.
 * Each CryptoTransfer transaction goes through different steps in handling. The output of one step will
 * be needed as input to other steps. For example, in the first step we resolve all the aliases in the transaction body.
 * The resolutions are needed in further steps to is IDs instead of aliases.
 * It also has helper function to create accounts from alias.
 * This class stores all the needed information that is shared between steps in handling a CryptoTransfer transaction.
 * The lifecycle of this class is the same as the lifecycle of a CryptoTransfer transaction.
 */
public interface TransferContext {
    /**
     * Looks up alias from accountID in form of alias and return the account ID with account number if found.
     * Return null otherwise.
     * @param aliasedId the account ID with the account number associated with alias
     * @return the account ID with account number if found, null otherwise
     */
    AccountID getFromAlias(AccountID aliasedId);

    /**
     * Creates an account from the given alias. This is called when the account associated with alias
     * is not found in the account store.
     *
     * @param alias                  the alias of the account
     * @param reqMaxAutoAssociations the maximum number of auto-associations allowed for the account
     */
    void createFromAlias(Bytes alias, int reqMaxAutoAssociations);

    /**
     * Returns the number of auto-creation of accounts in current transfer.
     * @return the number of auto-creation of accounts
     */
    int numOfAutoCreations();

    /**
     * Returns the number of lazy-creation of accounts in current transfer.
     * @return the number of lazy-creation of accounts
     */
    int numOfLazyCreations();

    /**
     * Returns the resolved accounts with alias and its account ID.
     * @return the resolved accounts with alias and its account ID
     */
    Map<Bytes, AccountID> resolutions();

    /**
     * Charges extra fee to the HAPI payer account in the current transfer context with the given amount.
     * @param amount the amount to charge
     */
    void chargeExtraFeeToHapiPayer(long amount);

    /**
     * Returns the handle context of the current transfer context.
     * @return the handle context of the current transfer context
     */
    HandleContext getHandleContext();

    /* ------------------- Needed for building records ------------------- */

    /**
     * Adds the token association that is created by auto association to the list of automatic associations.
     * This information is needed while building the records at the end of the transaction handling.
     * @param newAssociation the token association that is created by auto association
     */
    void addToAutomaticAssociations(TokenAssociation newAssociation);

    /**
     * Adds the assessed custom fee to the list of assessed custom fees. This information is needed while building the
     * records at the end of the transaction handling.
     * @param assessedCustomFee the assessed custom fee
     */
    void addToAssessedCustomFee(AssessedCustomFee assessedCustomFee);

    /**
     * Returns the custom fees assessed so far in this transfer context.
     *
     * @return the custom fees assessed so far in this transfer context
     */
    List<AssessedCustomFee> getAssessedCustomFees();

    /**
     * Indicates if this transfer context enforces mono-service
     * restrictions on whether auto-created accounts can pay custom fees
     * in the same transaction where they are created.
     *
     * <p>The default is true for mono-service fidelity. But many (quite
     * complicated!) unit tests were written before this was enforced; and
     * they need to be able to turn it off.
     *
     * @return whether certain restrictions on custom fees are enforced
     */
    boolean isEnforceMonoServiceRestrictionsOnAutoCreationCustomFeePayments();

    /**
     * Validates hbar allowances for the top-level operation in this transfer context.
     */
    // @Future Remove this, only needed for diff testing and has no logical priority.
    void validateHbarAllowances();
}
