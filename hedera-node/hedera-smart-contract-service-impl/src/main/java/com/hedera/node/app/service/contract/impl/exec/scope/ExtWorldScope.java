package com.hedera.node.app.service.contract.impl.exec.scope;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The "extended" world scope that a {@link ProxyWorldUpdater} needs to perform all required operations.
 */
public interface ExtWorldScope {
    /**
     * Creates a new {@link HandleExtWorldScope} that is a child of this {@link HandleExtWorldScope}.
     *
     * @return a nested {@link HandleExtWorldScope}
     */
    @NonNull ExtWorldScope begin();

    /**
     * Commits all changes made within this {@link HandleExtWorldScope} to the parent {@link HandleExtWorldScope}. For
     * everything except records, these changes will only affect state if every ancestor up to
     * and including the root {@link HandleExtWorldScope} is also committed. Records are a bit different,
     * as even if the root {@link HandleExtWorldScope} reverts, any records created within this
     * {@link HandleExtWorldScope} will still appear in state; but those with status {@code SUCCESS} will
     * have with their stateful effects cleared from the record and their status replaced with
     * {@code REVERTED_SUCCESS}.
     */
    void commit();

    /**
     * Reverts all changes and ends this session, with the possible exception of records, as
     * described above.
     */
    void revert();

    /**
     * Returns the {@link WritableStates} the {@code ContractService} can use to update
     * its own state within this {@link HandleExtWorldScope}.
     *
     * @return the contract state reflecting all changes made up to this {@link HandleExtWorldScope}
     */
    ContractStateStore getStore();

    /**
     * Returns what will be the next new entity number.
     *
     * @return the next entity number
     */
    long peekNextEntityNumber();

    /**
     * Reserves a new entity number for a contract being created.
     *
     * @return the reserved entity number
     */
    long useNextEntityNumber();

    /**
     * Returns the entropy available in this scope. See <a href="https://hips.hedera.com/hip/hip-351">HIP-351</a>
     * for details on how the Hedera node implements this.
     *
     * @return the available entropy
     */
    @NonNull Bytes entropy();

    /**
     * Returns the lazy creation cost within this scope.
     *
     * @return the lazy creation cost in gas
     */
    long lazyCreationCostInGas();

    /**
     * Given an amount in tinycents, return the equivalent value in tinybars at the
     * active exchange rate.
     *
     * @param tinycents the fee in tinycents
     * @return the equivalent cost in tinybars
     */
    long valueInTinybars(long tinycents);

    /**
     * Collects the given fee from the given account. The caller should have already
     * verified that the account exists and has sufficient balance to pay the fee, so
     * this method surfaces any problem by throwing an exception.
     *
     * @param payerId the account to collect the fee from
     * @param amount the amount to collect
     * @throws IllegalArgumentException if the collection fails for any reason
     */
    void collectFee(@NonNull AccountID payerId, final long amount);

    /**
     * Refunds the given {@code amount} of fees from the given {@code fromEntityNumber}.
     *
     * @param payerId the address of the account to refund the fees to
     * @param amount          the amount of fees to collect
     */
    void refundFee(@NonNull AccountID payerId, final long amount);

    /**
     * Attempts to charge the given {@code amount} of rent to the given {@code contractNumber}, with
     * preference to its auto-renew account (if any); falling back to charging the contract itself
     * if the auto-renew account does not exist or does not have sufficient balance.
     *
     * @param contractNumber         the number of the contract to charge
     * @param amount                 the amount to charge
     * @param itemizeStoragePayments whether to itemize storage payments in the record
     */
    void chargeStorageRent(long contractNumber, final long amount, final boolean itemizeStoragePayments);

    /**
     * Updates the storage metadata for the given contract.
     *
     * @param contractNumber the number of the contract
     * @param firstKey       the first key in the storage linked list, or {@code null} if the list is empty
     * @param slotsUsed      the number of storage slots used by the contract
     */
    void updateStorageMetadata(long contractNumber, @Nullable final Bytes firstKey, final int slotsUsed);

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
    void createContract(long number, final long parentNumber, final long nonce, @Nullable final Bytes evmAddress);

    /**
     * Deletes the contract whose alias is the given {@code evmAddress}, and also "unlinks" the alias.
     * Signing requirements are waived, and the record of this deletion should only be externalized if
     * the top-level HAPI transaction succeeds.
     *
     * <p>The record of this creation should only be externalized if the top-level HAPI transaction succeeds.
     *
     * @param evmAddress the EVM address of the contract to delete
     */
    void deleteAliasedContract(@NonNull Bytes evmAddress);

    /**
     * Convenience method to delete an unaliased contract with the given number.
     *
     * @param number the number of the contract to delete
     */
    void deleteUnaliasedContract(long number);

    /**
     * Returns a list of the account numbers that have been modified in this scope.
     *
     * @return the list of modified account numbers
     */
    List<Long> getModifiedAccountNumbers();

    /**
     * Returns a list of the contract numbers that have been created in this scope.
     *
     * @return the list of created contract numbers
     */
    List<ContractID> getCreatedContractIds();

    /**
     * Returns the contract nonces updated in this scope.
     *
     * @return the map of updated contract nonces
     */
    Map<ContractID, Long> getUpdatedContractNonces();

    /**
     * Returns number of slots used by the contract with the given number, ignoring any uncommitted
     * modifications already dispatched. If the contract did not exist before the transaction, returns
     * zero.
     *
     * @param contractNumber the contract number
     * @return the number of storage slots used by the contract, ignoring any uncommitted modifications
     */
    int getOriginalSlotsUsed(long contractNumber);
}
