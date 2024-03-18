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

package com.hedera.node.app.service.contract.impl.state;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.CONTRACT_IS_TREASURY;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.CONTRACT_STILL_OWNS_NFTS;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.FAILURE_DURING_LAZY_ACCOUNT_CREATION;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INSUFFICIENT_CHILD_RECORDS;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_ALIAS_KEY;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.SELF_DESTRUCT_TO_SELF;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZero;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.maybeMissingNumberOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniUInt256;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static com.hedera.node.app.service.token.AliasUtils.extractEvmAddress;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy.UseTopLevelSigs;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.state.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * An implementation of {@link EvmFrameState} that uses {@link WritableKVState}s to manage
 * contract storage and bytecode, and a {@link HandleHederaNativeOperations} for additional influence over
 * the non-contract Hedera state in the current scope.
 *
 * <p>Almost every access requires a conversion from a PBJ type to a Besu type. At some
 * point it might be necessary to cache the converted values and invalidate them when
 * the state changes.
 * <p>
 * TODO - get a little further to clarify DI strategy, then bring back a code cache.
 */
public class DispatchingEvmFrameState implements EvmFrameState {
    public static final Key HOLLOW_ACCOUNT_KEY =
            Key.newBuilder().keyList(KeyList.DEFAULT).build();
    private static final String TOKEN_BYTECODE_PATTERN = "fefefefefefefefefefefefefefefefefefefefe";

    @SuppressWarnings("java:S6418")
    private static final String TOKEN_CALL_REDIRECT_CONTRACT_BINARY =
            "6080604052348015600f57600080fd5b506000610167905077618dc65efefefefefefefefefefefefefefefefefefefefe600052366000602037600080366018016008845af43d806000803e8160008114605857816000f35b816000fdfea2646970667358221220d8378feed472ba49a0005514ef7087017f707b45fb9bf56bb81bb93ff19a238b64736f6c634300080b0033";

    private final HederaNativeOperations nativeOperations;
    private final ContractStateStore contractStateStore;

    public DispatchingEvmFrameState(
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final ContractStateStore contractStateStore) {
        this.nativeOperations = requireNonNull(nativeOperations);
        this.contractStateStore = requireNonNull(contractStateStore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStorageValue(
            @Nullable final ContractID contractID, @NonNull final UInt256 key, @NonNull final UInt256 value) {
        final var slotKey = new SlotKey(contractID, tuweniToPbjBytes(requireNonNull(key)));
        final var oldSlotValue = contractStateStore.getSlotValue(slotKey);
        // Ensure we don't change any prev/next keys until the base commit
        final var slotValue = new SlotValue(
                tuweniToPbjBytes(requireNonNull(value)),
                oldSlotValue == null ? com.hedera.pbj.runtime.io.buffer.Bytes.EMPTY : oldSlotValue.previousKey(),
                oldSlotValue == null ? com.hedera.pbj.runtime.io.buffer.Bytes.EMPTY : oldSlotValue.nextKey());
        // We don't call remove() here when the new value is zero, again because we
        // want to preserve the prev/next key information until the base commit; only
        // then will we remove the zeroed out slot from the K/V state
        contractStateStore.putSlot(slotKey, slotValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull UInt256 getStorageValue(final ContractID contractID, @NonNull final UInt256 key) {
        final var slotKey = new SlotKey(contractID, tuweniToPbjBytes(requireNonNull(key)));
        return valueOrZero(contractStateStore.getSlotValue(slotKey));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull UInt256 getOriginalStorageValue(final ContractID contractID, @NonNull final UInt256 key) {
        final var slotKey = new SlotKey(contractID, tuweniToPbjBytes(requireNonNull(key)));
        return valueOrZero(contractStateStore.getOriginalSlotValue(slotKey));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull List<StorageAccesses> getStorageChanges() {
        final Map<ContractID, List<StorageAccess>> modifications = new TreeMap<>(HapiUtils.CONTRACT_ID_COMPARATOR);
        contractStateStore.getModifiedSlotKeys().forEach(slotKey -> modifications
                .computeIfAbsent(slotKey.contractID(), k -> new ArrayList<>())
                .add(StorageAccess.newWrite(
                        pbjToTuweniUInt256(slotKey.key()),
                        valueOrZero(contractStateStore.getOriginalSlotValue(slotKey)),
                        valueOrZero(contractStateStore.getSlotValue(slotKey)))));
        final List<StorageAccesses> allChanges = new ArrayList<>();
        modifications.forEach(
                (number, storageAccesses) -> allChanges.add(new StorageAccesses(number, storageAccesses)));
        return allChanges;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getKvStateSize() {
        return contractStateStore.getNumSlots();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull RentFactors getRentFactorsFor(final ContractID contractID) {
        final var account = validatedAccount(contractID);
        return new RentFactors(account.contractKvPairsNumber(), account.expirationSecond());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Bytes getCode(final ContractID contractID) {
        final var numberedBytecode = contractStateStore.getBytecode(contractID);
        if (numberedBytecode == null) {
            return Bytes.EMPTY;
        } else {
            final var code = numberedBytecode.code();
            return code == null ? Bytes.EMPTY : pbjToTuweniBytes(code);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Hash getCodeHash(final ContractID contractID) {
        final var numberedBytecode = contractStateStore.getBytecode(contractID);
        if (numberedBytecode == null) {
            return Hash.EMPTY;
        } else {
            return CodeFactory.createCode(pbjToTuweniBytes(numberedBytecode.code()), 0, false)
                    .getCodeHash();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Bytes getTokenRedirectCode(@NonNull final Address address) {
        return proxyBytecodeFor(address);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Hash getTokenRedirectCodeHash(@NonNull final Address address) {
        return CodeFactory.createCode(proxyBytecodeFor(address), 0, false).getCodeHash();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getNonce(final AccountID accountID) {
        return validatedAccount(accountID).ethereumNonce();
    }

    @Override
    public com.hedera.hapi.node.state.token.Account getNativeAccount(final AccountID accountID) {
        return validatedAccount(accountID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumTreasuryTitles(final AccountID accountID) {
        return validatedAccount(accountID).numberTreasuryTitles();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isContract(final AccountID accountID) {
        return validatedAccount(accountID).smartContract();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumPositiveTokenBalances(final AccountID accountID) {
        return validatedAccount(accountID).numberPositiveBalances();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCode(final ContractID contractID, @NonNull final Bytes code) {
        contractStateStore.putBytecode(contractID, new Bytecode(tuweniToPbjBytes(requireNonNull(code))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNonce(final long number, final long nonce) {
        nativeOperations.setNonce(number, nonce);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Wei getBalance(AccountID accountID) {
        return Wei.of(validatedAccount(accountID).tinybarBalance());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getIdNumber(@NonNull Address address) {
        final var number = maybeMissingNumberOf(address, nativeOperations);
        if (number == MISSING_ENTITY_NUMBER) {
            throw new IllegalArgumentException("Address " + address + " has no associated Hedera id");
        }
        return number;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Address getAddress(final long number) {
        final AccountID accountID = AccountID.newBuilder().accountNum(number).build();
        final var account = nativeOperations.getAccount(accountID);
        if (account == null) {
            final var token = nativeOperations.getToken(number);
            if (token != null) {
                // If the token is deleted or expired, the system contract executed by the redirect
                // bytecode will fail with a more meaningful error message, so don't check that here
                return asLongZeroAddress(number);
            } else {
                throw new IllegalArgumentException("No account or token has number " + number);
            }
        }

        if (account.deleted()) {
            return null;
        }

        final var evmAddress = extractEvmAddress(account.alias());
        return evmAddress == null ? asLongZeroAddress(number) : pbjToBesuAddress(evmAddress);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Address getAddress(final AccountID accountID) {
        final var account = nativeOperations.getAccount(accountID);
        if (account == null) {
            throw new IllegalArgumentException("No account has id " + accountID);
        }

        if (account.deleted()) {
            return null;
        }

        final var evmAddress = extractEvmAddress(account.alias());
        return evmAddress == null
                ? asLongZeroAddress(account.accountIdOrThrow().accountNum())
                : pbjToBesuAddress(evmAddress);
    }

    @Override
    public boolean isHollowAccount(@NonNull final Address address) {
        final var number = maybeMissingNumberOf(address, nativeOperations);
        if (number == MISSING_ENTITY_NUMBER) {
            return false;
        }
        final AccountID accountID = AccountID.newBuilder().accountNum(number).build();
        final var account = nativeOperations.getAccount(accountID);
        if (account == null) {
            return false;
        }
        return HOLLOW_ACCOUNT_KEY.equals(account.key());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finalizeHollowAccount(@NonNull final Address address) {
        nativeOperations.finalizeHollowAccountAsContract(tuweniToPbjBytes(address));
    }

    @Override
    public long numBytecodesInState() {
        return contractStateStore.getNumBytecodes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ExceptionalHaltReason> tryTransfer(
            @NonNull final Address sendingContract,
            @NonNull final Address recipient,
            final long amount,
            final boolean delegateCall) {
        final var from = (ProxyEvmAccount) getAccount(sendingContract);
        if (from == null) {
            return Optional.of(INVALID_SOLIDITY_ADDRESS);
        }
        final var to = getAccount(recipient);
        if (to == null) {
            return Optional.of(INVALID_SOLIDITY_ADDRESS);
        } else if (to instanceof TokenEvmAccount) {
            return Optional.of(ILLEGAL_STATE_CHANGE);
        }
        // Note we can still use top-level signatures to meet receiver signature requirements
        final var status = nativeOperations.transferWithReceiverSigCheck(
                amount,
                from.hederaId(),
                ((ProxyEvmAccount) to).hederaId(),
                new ActiveContractVerificationStrategy(
                        from.hederaContractId(),
                        tuweniToPbjBytes(from.getAddress()),
                        delegateCall,
                        UseTopLevelSigs.YES));
        if (status != OK) {
            if (status == INVALID_SIGNATURE) {
                return Optional.of(CustomExceptionalHaltReason.INVALID_SIGNATURE);
            } else {
                throw new IllegalStateException("Transfer from 0.0." + from.accountID
                        + " to 0.0." + ((ProxyEvmAccount) to).accountID
                        + " failed with status " + status + " despite valid preconditions");
            }
        } else {
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ExceptionalHaltReason> tryLazyCreation(@NonNull final Address address) {
        if (isLongZero(address)) {
            return Optional.of(INVALID_ALIAS_KEY);
        }
        final var number = maybeMissingNumberOf(address, nativeOperations);
        if (number != MISSING_ENTITY_NUMBER) {
            AccountID accountID = AccountID.newBuilder().accountNum(number).build();
            final var account = nativeOperations.getAccount(accountID);
            if (account != null) {
                if (account.expiredAndPendingRemoval()) {
                    return Optional.of(FAILURE_DURING_LAZY_ACCOUNT_CREATION);
                } else {
                    throw new IllegalArgumentException(
                            "Unexpired account 0.0." + number + " already exists at address " + address);
                }
            }
        }
        final var status = nativeOperations.createHollowAccount(tuweniToPbjBytes(address));
        if (status != SUCCESS) {
            return status == MAX_CHILD_RECORDS_EXCEEDED
                    ? Optional.of(INSUFFICIENT_CHILD_RECORDS)
                    : Optional.of(FAILURE_DURING_LAZY_ACCOUNT_CREATION);
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ExceptionalHaltReason> tryTrackingSelfDestructBeneficiary(
            @NonNull final Address deleted, @NonNull final Address beneficiary, @NonNull final MessageFrame frame) {
        requireNonNull(deleted);
        requireNonNull(beneficiary);
        requireNonNull(frame);
        if (deleted.equals(beneficiary)) {
            return Optional.of(SELF_DESTRUCT_TO_SELF);
        }
        final var beneficiaryAccount = getAccount(beneficiary);
        if (beneficiaryAccount == null || beneficiaryAccount instanceof TokenEvmAccount) {
            return Optional.of(INVALID_SOLIDITY_ADDRESS);
        }
        // Token addresses don't have bytecode that could run a selfdestruct, so this cast is safe
        final var deletedAccount = (ProxyEvmAccount) requireNonNull(getAccount(deleted));
        if (deletedAccount.numTreasuryTitles() > 0) {
            return Optional.of(CONTRACT_IS_TREASURY);
        }
        if (deletedAccount.numPositiveTokenBalances() > 0) {
            return Optional.of(CONTRACT_STILL_OWNS_NFTS);
        }
        nativeOperations.trackSelfDestructBeneficiary(
                deletedAccount.hederaId(), ((ProxyEvmAccount) beneficiaryAccount).hederaId(), frame);
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Account getAccount(@NonNull final Address address) {
        return getMutableAccount(address);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable MutableAccount getMutableAccount(@NonNull final Address address) {
        final var number = maybeMissingNumberOf(address, nativeOperations);
        if (number == MISSING_ENTITY_NUMBER) {
            return null;
        }
        final AccountID accountID = AccountID.newBuilder().accountNum(number).build();
        final var account = nativeOperations.getAccount(accountID);
        if (account == null) {
            final var token = nativeOperations.getToken(number);
            if (token != null) {
                // If the token is deleted or expired, the system contract executed by the redirect
                // bytecode will fail with a more meaningful error message, so don't check that here
                return new TokenEvmAccount(address, this);
            }
            return null;
        }
        if (account.deleted() || account.expiredAndPendingRemoval() || isNotPriority(address, account)) {
            return null;
        }
        return new ProxyEvmAccount(account.accountId(), this);
    }

    private Bytes proxyBytecodeFor(final Address address) {
        return Bytes.fromHexString(
                TOKEN_CALL_REDIRECT_CONTRACT_BINARY.replace(TOKEN_BYTECODE_PATTERN, address.toUnprefixedHexString()));
    }

    private boolean isNotPriority(
            final Address address, final @NonNull com.hedera.hapi.node.state.token.Account account) {
        requireNonNull(account);
        final var maybeEvmAddress = extractEvmAddress(account.alias());
        return maybeEvmAddress != null && !address.equals(pbjToBesuAddress(maybeEvmAddress));
    }

    private com.hedera.hapi.node.state.token.Account validatedAccount(final AccountID accountID) {
        final var account = nativeOperations.getAccount(accountID);
        if (account == null) {
            throw new IllegalArgumentException("No account has id " + accountID);
        }
        return account;
    }

    private com.hedera.hapi.node.state.token.Account validatedAccount(final ContractID contractID) {
        final var account = nativeOperations.getAccount(contractID);
        if (account == null) {
            throw new IllegalArgumentException("No account found for contract ID " + contractID);
        }
        return account;
    }

    private UInt256 valueOrZero(@Nullable final SlotValue slotValue) {
        return (slotValue == null) ? UInt256.ZERO : pbjToTuweniUInt256(slotValue.value());
    }
}
