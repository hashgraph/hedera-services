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

package com.hedera.node.app.service.contract.impl.hevm;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.service.contract.impl.state.PendingCreation;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

/**
 * A {@link WorldUpdater} extension with additional methods for Hedera-specific operations.
 */
public interface HederaWorldUpdater extends WorldUpdater {
    /**
     * Returns the {@link HederaEvmAccount} for the given account id, or null if no
     * such account (or contract).
     *
     * @param accountId the id of the account to get
     * @return the account, or null if no such account
     */
    @Nullable
    HederaEvmAccount getHederaAccount(@NonNull AccountID accountId);

    /**
     * Returns the {@link HederaEvmAccount} for the given contract id, or null if no
     * such contract (or account).
     *
     * @param contractId the id of the contract to get
     * @return the account, or null if no such account
     */
    @Nullable
    HederaEvmAccount getHederaAccount(@NonNull ContractID contractId);

    /**
     * Returns the {@code 0.0.X} Hedera contract id for the given address, including when
     * the address is pending creation.
     *
     * @param address the address to get the id for
     * @return the id of the account at the given address
     * @throws IllegalArgumentException if the address has no corresponding contract id
     */
    ContractID getHederaContractId(@NonNull Address address);

    /**
     * Collects the given fee from the given account. The caller should have already
     * verified that the account exists and has sufficient balance to pay the fee, so
     * this method surfaces any problem by throwing an exception.
     *
     * @param payerId the id of the account to collect the fee from
     * @param amount      the amount to collect
     * @throws IllegalArgumentException if the collection fails for any reason
     */
    void collectFee(@NonNull AccountID payerId, long amount);

    /**
     * Refunds the given fee to the given account. The caller should have already
     * verified that the account exists, so this method surfaces any problem by
     * throwing an exception.
     *
     * @param payerId the id of the account to refund the fee to
     * @param amount the amount to refund
     */
    void refundFee(@NonNull AccountID payerId, long amount);

    /**
     * Tries to transfer the given amount from a sending contract to the recipient. The sender
     * has already authorized this action, in the sense that it is the address that has initiated
     * either a message call with value or a {@code selfdestruct}. The recipient, however, must
     * still be checked for authorization based on the Hedera concept of receiver signature
     * requirements.
     *
     * <p>Returns true if the receiver authorization and transfer succeeded, false otherwise.
     *
     * @param sendingContract the sender of the transfer, already authorized
     * @param recipient       the recipient of the transfer, not yet authorized
     * @param amount          the amount to transfer
     * @param delegateCall    whether this transfer is done via code executed by a delegate call
     * @return a optional with the reason to halt if the transfer failed, or empty if it succeeded
     */
    Optional<ExceptionalHaltReason> tryTransferFromContract(
            @NonNull Address sendingContract, @NonNull Address recipient, long amount, boolean delegateCall);

    /**
     * Attempts to lazy-create the account at the given address and decrement the gas remaining in the frame
     * (since lazy creation has significant gas costs above the standard EVM fee schedule).
     *
     * @param recipient the address of the account to create
     * @param frame the frame in which the account is being created
     * @return an optional with the reason to halt if the creation failed, or empty if it succeeded
     */
    Optional<ExceptionalHaltReason> tryLazyCreation(@NonNull Address recipient, @NonNull MessageFrame frame);

    /**
     * Attempts to track the given deletion of an account with the designated beneficiary, returning an optional
     * {@link ExceptionalHaltReason} to indicate whether the deletion could be successfully tracked.
     *
     * @param deleted     the address of the account being deleted
     * @param beneficiary the address of the beneficiary of the deletion
     * @return an optional {@link ExceptionalHaltReason} with the reason deletion could not be tracked
     */
    Optional<ExceptionalHaltReason> tryTrackingDeletion(@NonNull Address deleted, @NonNull Address beneficiary);

    /**
     * Given the possibly zero address of the origin of a {@code CONTRACT_CREATION} message,
     * sets up the {@link PendingCreation} this {@link ProxyWorldUpdater} will use to complete
     * the creation of the new account in {@link ProxyWorldUpdater#createAccount(Address, long, Wei)};
     * returns the "long-zero" address to be assigned to the new account.
     *
     * @param origin the address of the origin of a {@code CONTRACT_CREATION} message, zero if a top-level message
     * @return the "long-zero" address to be assigned to the new account
     */
    Address setupCreate(@NonNull Address origin);

    /**
     * Given the possibly zero address of the origin of a {@code CONTRACT_CREATION} message,
     * and either the canonical {@code CREATE1} address, or the EIP-1014 address computed by an
     * in-progress {@code CREATE2} operation, sets up the {@link PendingCreation} this
     * {@link ProxyWorldUpdater} will use to complete the creation of the new account in
     * {@link ProxyWorldUpdater#createAccount(Address, long, Wei)}.
     *
     * <p>Does not return anything, as the {@code CREATE2} address is already known.
     *
     * @param origin the address of the origin of a {@code CONTRACT_CREATION} message, zero if a top-level message
     * @param alias    the EIP-1014 address computed by an in-progress {@code CREATE2} operation
     */
    void setupAliasedCreate(@NonNull Address origin, @NonNull Address alias);

    /**
     * Returns whether this address refers to a hollow account (i.e. a lazy-created account that
     * has not yet been completed as either an EOA with a cryptographic key, or a contract created
     * with CREATE2.)
     *
     * @param address the address to check
     * @return whether the address refers to a hollow account
     */
    boolean isHollowAccount(@NonNull Address address);

    /**
     * Finalizes the creation of a hollow account as a contract created via CREATE2. This step doesn't
     * exist in Besu because there contracts are just normal accounts with code; but in Hedera, there
     * are a few other properties that need to be set to "convert" an account into a contract.
     *
     * @param alias the hollow account to be finalized as a contract
     */
    void finalizeHollowAccount(@NonNull Address alias);

    /**
     * Returns all storage updates that would be committed by this updater, necessary for constructing
     * a {@link com.hedera.hapi.streams.SidecarType#CONTRACT_STATE_CHANGE} sidecar.
     *
     * @return the full list of account-scoped storage changes
     */
    @NonNull
    List<StorageAccesses> pendingStorageUpdates();
}
