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

package com.hedera.node.app.service.contract.impl.state;

import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.aliasFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZero;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.ResultStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

/**
 * A {@link WorldUpdater} that delegates to a given {@link HandleHederaOperations} for state management.
 *
 * <p>For convenience, creates a {@link EvmFrameState} to manage the contract storage and
 * bytecode in the EVM frame via Besu types. <b>Important:</b> however, the {@link EvmFrameState}
 * does not itself provide any transactional semantics. The {@link HandleHederaOperations} alone has the
 * responsibility to {@code commit()} and {@code revert()} changes across all forms of
 * state as a transaction unit.
 *
 * <p><i>Note:</i> The {@code sbhRefund} field in the {@code mono-service} {@link WorldUpdater}
 * hierarchy is---as best I can tell---now always zero. So it does not appear here.
 */
public class ProxyWorldUpdater implements HederaWorldUpdater {
    private static final String CANNOT_CREATE = "Cannot create ";

    /**
     * The factory used to create new {@link EvmFrameState} instances; used once in the
     * constructor, and then again in {@link #updater()} if that is called.
     */
    private final EvmFrameStateFactory evmFrameStateFactory;
    /**
     * The parent {@link WorldUpdater}, or null if this is the root updater.
     */
    @Nullable
    private final WorldUpdater parent;
    /**
     * The {@link EvmFrameState} managing this {@code ProxyWorldUpdater}'s state.
     */
    protected final EvmFrameState evmFrameState;
    /**
     * The scope in which this {@code ProxyWorldUpdater} operates; stored in case we need to
     * create a "stacked" updater in a child scope via {@link #updater()}.
     */
    protected final HederaOperations hederaOperations;

    /**
     * Operations which are needed by system contracts.
     */
    protected final SystemContractOperations systemContractOperations;

    /**
     * If our {@code CreateOperation}s used the addresses prescribed by the {@code CREATE} and
     * {@code CREATE2} specs, they would not need Hedera state and thus not need to call into
     * their frame's {@link ProxyWorldUpdater}. Similarly, if a {@link ProxyWorldUpdater}
     * did not need any frame context to create a new account, it would not need any extra
     * "setup" help the {@code CreateOperation}.
     *
     * <p>However,
     * <ul>
     *     <li>The {@code CreateOperation} needs to call into the {@link ProxyWorldUpdater}
     *     because our {@code CREATE} address derives from the next Hedera entity number.</li>
     *     <li>To correctly create an account, the {@link ProxyWorldUpdater} must know the
     *     recipient address of the parent frame, as any children created in this frame
     *     will "inherit" many of their Hedera properties from the recipient.</li>
     * </ul>
     *
     * <p>So we need a little scratchpad to facilitate this data exchange with any create
     * operations executing in this {@link ProxyWorldUpdater}'s frame.
     */
    @Nullable
    protected PendingCreation pendingCreation;

    public ProxyWorldUpdater(
            @NonNull final HederaOperations hederaOperations,
            @NonNull final SystemContractOperations systemContractOperations,
            @NonNull final EvmFrameStateFactory evmFrameStateFactory,
            @Nullable final WorldUpdater parent) {
        this.parent = parent;
        this.hederaOperations = requireNonNull(hederaOperations);
        this.systemContractOperations = requireNonNull(systemContractOperations);
        this.evmFrameStateFactory = requireNonNull(evmFrameStateFactory);
        this.evmFrameState = evmFrameStateFactory.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable HederaEvmAccount getHederaAccount(@NonNull AccountID accountId) {
        final Address address;
        if (accountId.hasAlias()) {
            address = pbjToBesuAddress(accountId.aliasOrThrow());
        } else {
            try {
                address = evmFrameState.getAddress(accountId.accountNumOrElse(0L));
            } catch (IllegalArgumentException ignore) {
                return null;
            }
        }
        return address == null ? null : (HederaEvmAccount) get(address);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContractID getHederaContractId(@NonNull final Address address) {
        // As an important special case, return the pending creation's contract ID if its address matches
        if (pendingCreation != null && pendingCreation.address().equals(requireNonNull(address))) {
            return ContractID.newBuilder().contractNum(pendingCreation.number()).build();
        }
        final HederaEvmAccount account = (HederaEvmAccount) get(address);
        if (account == null) {
            throw new IllegalArgumentException("No contract pending or extant at " + address);
        }
        return account.hederaContractId();
    }

    @Override
    public @NonNull Bytes entropy() {
        return pbjToTuweniBytes(hederaOperations.entropy());
    }

    @Override
    public @Nullable HederaEvmAccount getHederaAccount(@NonNull final ContractID contractId) {
        requireNonNull(contractId);
        final Address address;
        if (contractId.hasEvmAddress()) {
            address = pbjToBesuAddress(contractId.evmAddressOrThrow());
        } else {
            try {
                address = evmFrameState.getAddress(contractId.contractNumOrElse(0L));
            } catch (IllegalArgumentException ignore) {
                return null;
            }
        }
        return address == null ? null : (HederaEvmAccount) get(address);
    }

    @Override
    public void collectFee(@NonNull final AccountID payerId, final long amount) {
        requireNonNull(payerId);
        hederaOperations.collectFee(payerId, amount);
    }

    @Override
    public void refundFee(@NonNull final AccountID payerId, final long amount) {
        requireNonNull(payerId);
        hederaOperations.refundFee(payerId, amount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ExceptionalHaltReason> tryTransfer(
            @NonNull final Address from, @NonNull final Address to, final long amount, final boolean delegateCall) {
        return evmFrameState.tryTransfer(from, to, amount, delegateCall);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ExceptionalHaltReason> tryLazyCreation(
            @NonNull final Address recipient, @NonNull final MessageFrame frame) {
        final var gasCost = hederaOperations.lazyCreationCostInGas();
        if (gasCost > frame.getRemainingGas()) {
            return Optional.of(INSUFFICIENT_GAS);
        }
        final var maybeHaltReason = evmFrameState.tryLazyCreation(recipient);
        if (maybeHaltReason.isPresent()) {
            return maybeHaltReason;
        }
        frame.decrementRemainingGas(gasCost);
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isHollowAccount(@NonNull final Address address) {
        return evmFrameState.isHollowAccount(address);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Address setupTopLevelCreate(@NonNull ContractCreateTransactionBody body) {
        setupPendingCreation(null, requireNonNull(body), null);
        return requireNonNull(pendingCreation).address();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setupTopLevelLazyCreate(@NonNull final Address alias) {
        setupPendingCreation(null, null, requireNonNull(alias));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setupAliasedTopLevelCreate(@NonNull ContractCreateTransactionBody body, @NonNull Address alias) {
        setupPendingCreation(null, requireNonNull(body), requireNonNull(alias));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Address setupInternalCreate(@NonNull final Address origin) {
        setupPendingCreation(origin, null, null);
        return requireNonNull(pendingCreation).address();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setupInternalAliasedCreate(@NonNull final Address origin, @NonNull final Address alias) {
        setupPendingCreation(origin, null, requireNonNull(alias));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finalizeHollowAccount(@NonNull final Address alias) {
        evmFrameState.finalizeHollowAccount(alias);
    }

    @Override
    public @NonNull List<StorageAccesses> pendingStorageUpdates() {
        return evmFrameState.getStorageChanges();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ExceptionalHaltReason> tryTrackingDeletion(
            @NonNull final Address deleted, @NonNull final Address beneficiary) {
        return evmFrameState.tryTrackingDeletion(deleted, beneficiary);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Account get(@NonNull final Address address) {
        return evmFrameState.getAccount(address);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EvmAccount getAccount(@NonNull final Address address) {
        return evmFrameState.getMutableAccount(address);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EvmAccount createAccount(@NonNull final Address address, final long nonce, @NonNull final Wei balance) {
        if (pendingCreation == null) {
            throw new IllegalStateException(CANNOT_CREATE + address + " without a pending creation");
        }
        final var number = getValidatedCreationNumber(address, balance, pendingCreation);
        if (pendingCreation.isHapiCreation()) {
            hederaOperations.createContract(
                    number, requireNonNull(pendingCreation.body()), pendingCreation.aliasIfApplicable());
        } else {
            hederaOperations.createContract(
                    number, pendingCreation.parentNumber(), pendingCreation.aliasIfApplicable());
        }
        return evmFrameState.getMutableAccount(pendingCreation.address());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAccount(@NonNull final Address address) {
        if (isLongZero(address)) {
            hederaOperations.deleteUnaliasedContract(numberOfLongZero(address));
        } else {
            hederaOperations.deleteAliasedContract(aliasFrom(address));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void revert() {
        // It might seem like we should have a call to evmFrameState.revert() here; but remember the
        // EvmFrameState is just a convenience wrapper around the Scope to let us use Besu types, and
        // ultimately the Scope is the one tracking and managing all changes
        hederaOperations.revert();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("java:S125")
    public void commit() {
        // It might seem like we should have a call to evmFrameState.commit() here; but remember the
        // EvmFrameState is just a mutable view of the scope's state that lets us use Besu types
        hederaOperations.commit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Optional<WorldUpdater> parentUpdater() {
        return Optional.ofNullable(parent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ProxyWorldUpdater updater() {
        final var child =
                new ProxyWorldUpdater(hederaOperations.begin(), systemContractOperations, evmFrameStateFactory, this);
        // Hand off any pending creation to the child updater; this a bit of a hack, but
        // lets the TransactionProcessor client code "flow" as naturally as possible,
        // without need to defer setting up creation until the initial frame is built
        // (since its updater will be a child of the RootProxyWorldUpdater)
        if (this.pendingCreation != null) {
            child.pendingCreation = this.pendingCreation;
        }
        return child;
    }

    /**
     * Returns the accounts that have been touched (i.e., created or maybe mutated but <i>not</i> deleted)
     * within the scope of this updater.
     *
     * <p>We may not actually need this, as Besu only uses it in
     * {@code AbstractMessageProcessor.clearAccumulatedStateBesidesGasAndOutput()}, which seems to deal
     * with side-effects of an Ethereum consensus bug.
     *
     * @return the accounts that have been touched
     */
    @Override
    public @NonNull Collection<? extends Account> getTouchedAccounts() {
        final var modifiedNumbers = hederaOperations.getModifiedAccountNumbers();
        final List<Account> touched = new ArrayList<>();
        for (final var number : modifiedNumbers) {
            // Returns null if the account has been deleted
            final var address = evmFrameState.getAddress(number);
            if (address != null) {
                touched.add(evmFrameState.getAccount(address));
            }
        }
        return touched;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Collection<Address> getDeletedAccountAddresses() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void externalizeSystemContractResults(
            @NonNull final ContractFunctionResult result, final ResultStatus status) {
        systemContractOperations.externalizeResult(result, status);
    }

    private long getValidatedCreationNumber(
            @NonNull final Address address,
            @NonNull final Wei balance,
            @NonNull final PendingCreation knownPendingCreation) {
        if (!balance.isZero()) {
            throw new IllegalStateException(CANNOT_CREATE + address + " with non-zero balance " + balance);
        }
        final var pendingAddress = knownPendingCreation.address();
        if (!requireNonNull(address).equals(pendingAddress)) {
            throw new IllegalStateException(CANNOT_CREATE + address + " with " + pendingAddress + " pending");
        }
        final var pendingNumber = hederaOperations.peekNextEntityNumber();
        if (pendingNumber != knownPendingCreation.number()) {
            throw new IllegalStateException(CANNOT_CREATE + address + " with number " + pendingNumber + " ("
                    + knownPendingCreation.number() + ") pending");
        }
        return pendingNumber;
    }

    private void setupPendingCreation(
            @Nullable final Address origin,
            @Nullable final ContractCreateTransactionBody body,
            @Nullable final Address alias) {
        final var number = hederaOperations.peekNextEntityNumber();
        pendingCreation = new PendingCreation(
                alias == null ? asLongZeroAddress(number) : alias,
                number,
                origin != null ? evmFrameState.getIdNumber(origin) : MISSING_ENTITY_NUMBER,
                body);
    }

    // Visible for testing
    public @Nullable PendingCreation getPendingCreation() {
        return pendingCreation;
    }
}
