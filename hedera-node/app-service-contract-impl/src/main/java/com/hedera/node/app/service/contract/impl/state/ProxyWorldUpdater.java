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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.aliasFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZero;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.maybeMissingNumberOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.meta.bni.Scope;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

/**
 * A {@link WorldUpdater} that delegates to a given {@link Scope} for state management.
 *
 * <p>For convenience, creates a {@link EvmFrameState} to manage the contract storage and
 * bytecode in the EVM frame via Besu types. <b>Important:</b> however, the {@link EvmFrameState}
 * does not itself provide any transactional semantics. The {@link Scope} alone has the
 * responsibility to {@code commit()} and {@code revert()} changes across all forms of
 * state as a transaction unit.
 *
 * <p><i>Note:</i> The {@code sbhRefund} field in the {@code mono-service} {@link WorldUpdater}
 * hierarchy is---as best I can tell---now always zero. So it does not appear here.
 */
public class ProxyWorldUpdater implements WorldUpdater {
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
    protected final Scope scope;

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
    private PendingCreation pendingCreation;

    public ProxyWorldUpdater(
            @NonNull final Scope scope,
            @NonNull final EvmFrameStateFactory evmFrameStateFactory,
            @Nullable final WorldUpdater parent) {
        this.parent = parent;
        this.scope = requireNonNull(scope);
        this.evmFrameStateFactory = requireNonNull(evmFrameStateFactory);
        this.evmFrameState = evmFrameStateFactory.createIn(scope);
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
     * Given the possibly zero address of the recipient of a {@code CONTRACT_CREATION} message,
     * sets up the {@link PendingCreation} this {@link ProxyWorldUpdater} will use to complete
     * the creation of the new account in {@link ProxyWorldUpdater#createAccount(Address, long, Wei)};
     * returns the "long-zero" address to be assigned to the new account.
     *
     * @param origin the address of the recipient of a {@code CONTRACT_CREATION} message, zero if a top-level message
     * @return the "long-zero" address to be assigned to the new account
     */
    public Address setupCreate(@NonNull final Address origin) {
        setupPendingCreation(origin, null);
        return requireNonNull(pendingCreation).address();
    }

    /**
     * Given the possibly zero address of the recipient of a {@code CONTRACT_CREATION} message,
     * and the EIP-1014 address computed by an in-progress {@code CREATE2} operation, sets up the
     * {@link PendingCreation} this {@link ProxyWorldUpdater} will use to complete the creation of
     * the new account in {@link ProxyWorldUpdater#createAccount(Address, long, Wei)}.
     *
     * <p>Does not return anything, as the {@code CREATE2} address is already known.
     *
     * @param origin the address of the recipient of a {@code CONTRACT_CREATION} message, zero if a top-level message
     * @param alias the EIP-1014 address computed by an in-progress {@code CREATE2} operation
     */
    public void setupCreate2(@NonNull final Address origin, @NonNull final Address alias) {
        setupPendingCreation(origin, alias);
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
        scope.dispatch()
                .createContract(number, pendingCreation.parentNumber(), nonce, pendingCreation.aliasIfApplicable());
        return evmFrameState.getMutableAccount(pendingCreation.address());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAccount(@NonNull final Address address) {
        if (isLongZero(address)) {
            scope.dispatch().deleteUnaliasedContract(numberOfLongZero(address));
        } else {
            scope.dispatch().deleteAliasedContract(aliasFrom(address));
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
        scope.revert();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("java:S125")
    public void commit() {
        // It might seem like we should have a call to evmFrameState.commit() here; but remember the
        // EvmFrameState is just a convenience wrapper around the Scope to let us use Besu types, and
        // ultimately the Scope is the one tracking and managing all changes
        scope.commit();
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
    public @NonNull WorldUpdater updater() {
        return new ProxyWorldUpdater(scope, evmFrameStateFactory, this);
    }

    /**
     * Returns the accounts that have been touched (i.e., created or maybe mutated but <i>not</i> deleted)
     * within the scope of this updater.
     *
     * <p>We may not need this; only used in Besu by
     * {@code AbstractMessageProcessor.clearAccumulatedStateBesidesGasAndOutput()}, which seems to be in
     * response to unwinding side-effects of an Ethereum consensus bug.
     *
     * <p>TODO - revisit whether this is needed.
     *
     * @return the accounts that have been touched
     */
    @Override
    public @NonNull Collection<? extends Account> getTouchedAccounts() {
        final var modifiedNumbers = scope.dispatch().getModifiedAccountNumbers();
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

    @Override
    public @NonNull Collection<Address> getDeletedAccountAddresses() {
        throw new UnsupportedOperationException();
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
        final var pendingNumber = scope.dispatch().useNextEntityNumber();
        if (pendingNumber != knownPendingCreation.number()) {
            throw new IllegalStateException(CANNOT_CREATE + address + " with number " + pendingNumber + " ("
                    + knownPendingCreation.number() + ") pending");
        }
        return pendingNumber;
    }

    private void setupPendingCreation(@NonNull final Address origin, @Nullable final Address alias) {
        final var number = scope.dispatch().peekNextEntityNumber();
        final long parentNumber = Address.ZERO.equals(requireNonNull(origin))
                ? scope.payerAccountNumber()
                : maybeMissingNumberOf(origin, scope.dispatch());
        if (parentNumber == MISSING_ENTITY_NUMBER) {
            throw new IllegalStateException("Claimed origin " + origin + " has no Hedera account number");
        }
        pendingCreation = new PendingCreation(alias == null ? asLongZeroAddress(number) : alias, number, parentNumber);
    }
}
