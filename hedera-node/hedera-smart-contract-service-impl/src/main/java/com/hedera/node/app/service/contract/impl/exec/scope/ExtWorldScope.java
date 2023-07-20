/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.scope;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.state.WritableContractsStore;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import javax.inject.Inject;

/**
 * A {@link ExtWorldScope} implementation based on a {@link HandleContext}.
 */
@TransactionScope
public class ExtWorldScope {
    private final HandleContext context;

    @Inject
    public ExtWorldScope(@NonNull final HandleContext context) {
        this.context = context;
    }

    /**
     * Creates a new {@link ExtWorldScope} that is a child of this {@link ExtWorldScope}.
     *
     * @return a nested {@link ExtWorldScope}
     */
    public @NonNull ExtWorldScope begin() {
        context.savepointStack().createSavepoint();
        return this;
    }

    /**
     * Commits all changes made within this {@link ExtWorldScope} to the parent {@link ExtWorldScope}. For
     * everything except records, these changes will only affect state if every ancestor up to
     * and including the root {@link ExtWorldScope} is also committed. Records are a bit different,
     * as even if the root {@link ExtWorldScope} reverts, any records created within this
     * {@link ExtWorldScope} will still appear in state; but those with status {@code SUCCESS} will
     * have with their stateful effects cleared from the record and their status replaced with
     * {@code REVERTED_SUCCESS}.
     */
    public void commit() {
        throw new UnsupportedOperationException();
    }

    /**
     * Reverts all changes and ends this session, with the possible exception of records, as
     * described above.
     */
    public void revert() {
        context.savepointStack().rollback();
    }

    /**
     * Returns the {@link WritableStates} the {@code ContractService} can use to update
     * its own state within this {@link ExtWorldScope}.
     *
     * @return the contract state reflecting all changes made up to this {@link ExtWorldScope}
     */
    public WritableContractsStore writableContractStore() {
        return context.writableStore(WritableContractsStore.class);
    }

    /**
     * Returns the account number of the Hedera account that is paying for the transaction.
     *
     * @return the payer account number
     */
    public long payerAccountNumber() {
        return context.payer().accountNumOrThrow();
    }

    /**
     * Returns what will be the next new entity number.
     *
     * @return the next entity number
     */
    public long peekNextEntityNumber() {
        throw new AssertionError("Not implemented");
    }

    /**
     * Reserves a new entity number for a contract being created.
     *
     * @return the reserved entity number
     */
    public long useNextEntityNumber() {
        throw new AssertionError("Not implemented");
    }

    /**
     * Returns the entropy available in this scope. See <a href="https://hips.hedera.com/hip/hip-351">HIP-351</a>
     * for details on how the Hedera node implements this.
     *
     * @return the available entropy
     */
    public @NonNull Bytes entropy() {
        throw new AssertionError("Not implemented");
    }

    /**
     * Returns the lazy creation cost within this scope.
     *
     * @return the lazy creation cost in gas
     */
    public long lazyCreationCostInGas() {
        return 0;
    }

    /**
     * Given an amount in tinycents, return the equivalent value in tinybars at the
     * active exchange rate.
     *
     * @param tinycents the fee in tinycents
     * @return the equivalent cost in tinybars
     */
    public long valueInTinybars(final long tinycents) {
        return 0;
    }

    /**
     * Collects the given fee from the given account. The caller should have already
     * verified that the account exists and has sufficient balance to pay the fee, so
     * this method surfaces any problem by throwing an exception.
     *
     * @param payerId the account to collect the fee from
     * @param amount the amount to collect
     * @throws IllegalArgumentException if the collection fails for any reason
     */
    public void collectFee(@NonNull final AccountID payerId, final long amount) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Refunds the given {@code amount} of fees from the given {@code fromEntityNumber}.
     *
     * @param payerId the address of the account to refund the fees to
     * @param amount          the amount of fees to collect
     */
    public void refundFee(@NonNull final AccountID payerId, final long amount) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Attempts to charge the given {@code amount} of rent to the given {@code contractNumber}, with
     * preference to its auto-renew account (if any); falling back to charging the contract itself
     * if the auto-renew account does not exist or does not have sufficient balance.
     *
     * @param contractNumber         the number of the contract to charge
     * @param amount                 the amount to charge
     * @param itemizeStoragePayments whether to itemize storage payments in the record
     */
    public void chargeStorageRent(final long contractNumber, final long amount, final boolean itemizeStoragePayments) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Updates the storage metadata for the given contract.
     *
     * @param contractNumber the number of the contract
     * @param firstKey       the first key in the storage linked list, or {@code null} if the list is empty
     * @param slotsUsed      the number of storage slots used by the contract
     */
    public void updateStorageMetadata(final long contractNumber, @Nullable final Bytes firstKey, final int slotsUsed) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Creates a new contract with the given entity number and EVM address; and also "links" the alias.
     *
     * <p>Any inheritable Hedera-native properties managed by the {@code TokenService} should be set on
     * the new contract based on the given model account.
     *
     * <p>The record of this creation should only be externalized if the top-level HAPI transaction succeeds.
     *
     * @param number       the number of the contract to create
     * @param parentNumber the number of the contract whose properties the new contract should inherit
     * @param nonce        the nonce of the contract to create
     * @param evmAddress   if not null, the EVM address to use as an alias of the created contract
     */
    public void createContract(
            final long number, final long parentNumber, final long nonce, @Nullable final Bytes evmAddress) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Deletes the contract whose alias is the given {@code evmAddress}, and also "unlinks" the alias.
     * Signing requirements are waived, and the record of this deletion should only be externalized if
     * the top-level HAPI transaction succeeds.
     *
     * <p>The record of this creation should only be externalized if the top-level HAPI transaction succeeds.
     *
     * @param evmAddress the EVM address of the contract to delete
     */
    public void deleteAliasedContract(@NonNull final Bytes evmAddress) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Convenience method to delete an unaliased contract with the given number.
     *
     * @param number the number of the contract to delete
     */
    public void deleteUnaliasedContract(final long number) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Returns a list of the account numbers that have been modified in this scope.
     *
     * @return the list of modified account numbers
     */
    public List<Long> getModifiedAccountNumbers() {
        throw new AssertionError("Not implemented");
    }

    /**
     * Returns number of slots used by the contract with the given number, ignoring any uncommitted
     * modifications already dispatched. If the contract did not exist before the transaction, returns
     * zero.
     *
     * @param contractNumber the contract number
     * @return the number of storage slots used by the contract, ignoring any uncommitted modifications
     */
    public int getOriginalSlotsUsed(final long contractNumber) {
        throw new AssertionError("Not implemented");
    }
}
