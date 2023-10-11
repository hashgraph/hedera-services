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

package com.hedera.node.app.service.contract.impl.test.state;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.CONTRACT_IS_TREASURY;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.CONTRACT_STILL_OWNS_NFTS;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.FAILURE_DURING_LAZY_ACCOUNT_CREATION;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniUInt256;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.contract.impl.state.DispatchingEvmFrameState;
import com.hedera.node.app.service.contract.impl.state.ProxyEvmAccount;
import com.hedera.node.app.service.contract.impl.state.RentFactors;
import com.hedera.node.app.service.contract.impl.state.StorageAccess;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import com.hedera.node.app.service.contract.impl.state.TokenEvmAccount;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedHashSet;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchingEvmFrameStateTest {
    private static final int REDIRECT_CODE_FIXED_PREFIX_LEN =
            "6080604052348015600f57600080fd5b506000610167905077618dc65e".length();
    private static final int NUM_KV_SLOTS = 42;
    private static final long EXPIRY = 1_234_567L;
    private static final long ACCOUNT_NUM = 0x9abcdefabcdefbbbL;
    private static final long BENEFICIARY_NUM = 0xdefdefL;
    private static final long TOKEN_NUM = 0xffffffffffffL;
    private static final Bytes SOME_OTHER_ALIAS = Bytes.wrap("<PRETEND>");
    private static final Address EVM_ADDRESS = Address.fromHexString("abcabcabcabcabcabeeeeeee9abcdefabcdefbbb");
    private static final Address LONG_ZERO_ADDRESS = Address.fromHexString("0000000000000000000000009abcdefabcdefbbb");
    private static final Address BENEFICIARY_ADDRESS =
            Address.fromHexString("0000000000000000000000000000000000defdef");
    private static final Address TOKEN_ADDRESS = Address.fromHexString("0000000000000000000000000000ffffffffffff");
    private static final Bytes SOME_PRETEND_CODE = Bytes.wrap("<NOT-REALLY-CODE>");
    private static final Bytecode SOME_PRETEND_BYTECODE =
            Bytecode.newBuilder().code(SOME_PRETEND_CODE).build();
    private static final Hash SOME_PRETEND_CODE_HASH = CodeFactory.createCode(
                    pbjToTuweniBytes(SOME_PRETEND_CODE), 0, false)
            .getCodeHash();
    private static final Bytes A_STORAGE_KEY = Bytes.wrap(Bytes32.random().toArrayUnsafe());
    private static final Bytes B_STORAGE_KEY = Bytes.wrap(Bytes32.random().toArrayUnsafe());
    private static final Bytes C_STORAGE_KEY = Bytes.wrap(Bytes32.random().toArrayUnsafe());
    private static final Bytes A_STORAGE_VALUE = Bytes.wrap(Bytes32.random().toArrayUnsafe());
    private static final SlotKey A_SLOT_KEY =
            SlotKey.newBuilder().contractNumber(ACCOUNT_NUM).key(A_STORAGE_KEY).build();
    private static final SlotValue A_SLOT_VALUE = SlotValue.newBuilder()
            .previousKey(B_STORAGE_KEY)
            .value(A_STORAGE_VALUE)
            .nextKey(C_STORAGE_KEY)
            .build();

    @Mock
    private HederaNativeOperations nativeOperations;

    @Mock
    private ContractStateStore contractStateStore;

    private DispatchingEvmFrameState subject;

    @BeforeEach
    void setUp() {
        subject = new DispatchingEvmFrameState(nativeOperations, contractStateStore);
    }

    @Test
    void dispatchesToSetNonce() {
        subject.setNonce(ACCOUNT_NUM, 1234);

        verify(nativeOperations).setNonce(ACCOUNT_NUM, 1234);
    }

    @Test
    void dispatchesToNumBytecodes() {
        given(contractStateStore.getNumBytecodes()).willReturn(1234L);

        assertEquals(1234L, subject.numBytecodesInState());
    }

    @Test
    void extFrameScopeesToFinalizeHollowAccount() {
        subject.finalizeHollowAccount(EVM_ADDRESS);

        verify(nativeOperations).finalizeHollowAccountAsContract(tuweniToPbjBytes(EVM_ADDRESS));
    }

    @Test
    void extFrameScopeesToSetCode() {
        final var expectedCode = Bytecode.newBuilder().code(SOME_PRETEND_CODE).build();

        subject.setCode(ACCOUNT_NUM, pbjToTuweniBytes(SOME_PRETEND_CODE));

        verify(contractStateStore).putBytecode(new EntityNumber(ACCOUNT_NUM), expectedCode);
    }

    @Test
    void getsExtantStorageValues() {
        given(contractStateStore.getSlotValue(A_SLOT_KEY)).willReturn(A_SLOT_VALUE);

        final var expectedWord = pbjToTuweniUInt256(A_STORAGE_VALUE);
        final var actualWord = subject.getStorageValue(ACCOUNT_NUM, pbjToTuweniUInt256(A_STORAGE_KEY));

        assertEquals(expectedWord, actualWord);
    }

    @Test
    void getsOriginalStorageValues() {
        given(contractStateStore.getOriginalSlotValue(A_SLOT_KEY)).willReturn(A_SLOT_VALUE);

        final var expectedWord = pbjToTuweniUInt256(A_STORAGE_VALUE);
        final var actualWord = subject.getOriginalStorageValue(ACCOUNT_NUM, pbjToTuweniUInt256(A_STORAGE_KEY));

        assertEquals(expectedWord, actualWord);
    }

    @Test
    void summarizesModificationsAsExpected() {
        final List<StorageAccesses> expected = List.of(
                new StorageAccesses(
                        1L,
                        List.of(
                                StorageAccess.newWrite(UInt256.ONE, UInt256.ONE, UInt256.MAX_VALUE),
                                StorageAccess.newWrite(UInt256.MAX_VALUE, UInt256.MIN_VALUE, UInt256.ONE))),
                new StorageAccesses(
                        2L,
                        List.of(
                                StorageAccess.newWrite(UInt256.MAX_VALUE, UInt256.MIN_VALUE, UInt256.ONE),
                                StorageAccess.newWrite(UInt256.ONE, UInt256.ONE, UInt256.MAX_VALUE))));

        final var modifiedKeys = List.of(
                new SlotKey(1L, tuweniToPbjBytes(UInt256.ONE)),
                new SlotKey(1L, tuweniToPbjBytes(UInt256.MAX_VALUE)),
                new SlotKey(2L, tuweniToPbjBytes(UInt256.MAX_VALUE)),
                new SlotKey(2L, tuweniToPbjBytes(UInt256.ONE)));
        given(contractStateStore.getModifiedSlotKeys()).willReturn(new LinkedHashSet<>(modifiedKeys));
        final var iter = modifiedKeys.iterator();
        givenOrigAndNewValues(iter.next(), UInt256.ONE, UInt256.MAX_VALUE);
        givenOrigAndNewValues(iter.next(), UInt256.MIN_VALUE, UInt256.ONE);
        givenOrigAndNewValues(iter.next(), UInt256.MIN_VALUE, UInt256.ONE);
        givenOrigAndNewValues(iter.next(), UInt256.ONE, UInt256.MAX_VALUE);

        final var actual = subject.getStorageChanges();

        assertEquals(expected, actual);
    }

    private void givenOrigAndNewValues(
            @NonNull final SlotKey key, @NonNull final UInt256 origValue, @NonNull final UInt256 newValue) {
        given(contractStateStore.getOriginalSlotValue(key))
                .willReturn(SlotValue.newBuilder()
                        .value(tuweniToPbjBytes(origValue))
                        .build());
        given(contractStateStore.getSlotValue(key))
                .willReturn(
                        SlotValue.newBuilder().value(tuweniToPbjBytes(newValue)).build());
    }

    @Test
    void overwritesNewStorageSlotAsExpected() {
        final var newSlotValue = A_SLOT_VALUE
                .copyBuilder()
                .previousKey(Bytes.EMPTY)
                .nextKey(Bytes.EMPTY)
                .build();

        subject.setStorageValue(ACCOUNT_NUM, pbjToTuweniUInt256(A_STORAGE_KEY), pbjToTuweniUInt256(A_STORAGE_VALUE));

        verify(contractStateStore).putSlot(A_SLOT_KEY, newSlotValue);
    }

    @Test
    void preservesPrevNextPointersForStorageSlotUpdate() {
        final var oldSlotValue = A_SLOT_VALUE
                .copyBuilder()
                .previousKey(Bytes.fromHex("1234"))
                .nextKey(Bytes.fromHex("5678"))
                .build();
        final var newSlotValue = A_SLOT_VALUE
                .copyBuilder()
                .previousKey(Bytes.fromHex("1234"))
                .nextKey(Bytes.fromHex("5678"))
                .build();

        given(contractStateStore.getSlotValue(A_SLOT_KEY)).willReturn(oldSlotValue);
        subject.setStorageValue(ACCOUNT_NUM, pbjToTuweniUInt256(A_STORAGE_KEY), pbjToTuweniUInt256(A_STORAGE_VALUE));

        verify(contractStateStore).putSlot(A_SLOT_KEY, newSlotValue);
    }

    @Test
    void getsZeroWordForMissingSlotKey() {
        final var actualWord = subject.getStorageValue(ACCOUNT_NUM, pbjToTuweniUInt256(A_STORAGE_KEY));

        assertSame(UInt256.ZERO, actualWord);
    }

    @Test
    void getsZeroWordForEmptySlotValue() {
        given(contractStateStore.getSlotValue(A_SLOT_KEY)).willReturn(SlotValue.DEFAULT);

        final var actualWord = subject.getStorageValue(ACCOUNT_NUM, pbjToTuweniUInt256(A_STORAGE_KEY));

        assertSame(UInt256.ZERO, actualWord);
    }

    @Test
    void getsExtantCode() {
        givenWellKnownBytecode();

        final var actualCode = subject.getCode(ACCOUNT_NUM);

        assertEquals(pbjToTuweniBytes(SOME_PRETEND_CODE), actualCode);
    }

    @Test
    void interpolatesTokenCodeByAddress() {
        final var actualCode = subject.getTokenRedirectCode(TOKEN_ADDRESS);

        assertEquals(
                TOKEN_ADDRESS.toUnprefixedHexString(),
                actualCode
                        .toUnprefixedHexString()
                        // EVM 20-byte address is 40 hex chars
                        .substring(REDIRECT_CODE_FIXED_PREFIX_LEN, REDIRECT_CODE_FIXED_PREFIX_LEN + 40));
    }

    @Test
    void hashesInterpolatesTokenCode() {
        final var code = subject.getTokenRedirectCode(TOKEN_ADDRESS);
        final var expectedHash = Hash.hash(code);

        assertEquals(expectedHash, subject.getTokenRedirectCodeHash(TOKEN_ADDRESS));
    }

    @Test
    void getsEmptyCodeForMissing() {
        final var actualCode = subject.getCode(ACCOUNT_NUM);

        assertSame(org.apache.tuweni.bytes.Bytes.EMPTY, actualCode);
    }

    @Test
    void getsEmptyCodeForNull() {
        given(contractStateStore.getBytecode(new EntityNumber(ACCOUNT_NUM))).willReturn(new Bytecode(null));

        final var actualCode = subject.getCode(ACCOUNT_NUM);

        assertSame(org.apache.tuweni.bytes.Bytes.EMPTY, actualCode);
    }

    @Test
    void getsExtantCodeHash() {
        givenWellKnownBytecode();

        final var actualCodeHash = subject.getCodeHash(ACCOUNT_NUM);

        assertEquals(SOME_PRETEND_CODE_HASH, actualCodeHash);
    }

    @Test
    void getsEmptyCodeHashForMissing() {
        final var actualCodeHash = subject.getCodeHash(ACCOUNT_NUM);

        assertSame(Hash.EMPTY, actualCodeHash);
    }

    @Test
    void throwsOnMissingAddressWhenGettingHederaIdNumber() {
        given(nativeOperations.resolveAlias(tuweniToPbjBytes(EVM_ADDRESS))).willReturn(MISSING_ENTITY_NUMBER);
        assertThrows(IllegalArgumentException.class, () -> subject.getIdNumber(EVM_ADDRESS));
    }

    @Test
    void returnsResolvedNumberForEvmAddress() {
        given(nativeOperations.resolveAlias(tuweniToPbjBytes(EVM_ADDRESS))).willReturn(ACCOUNT_NUM);
        assertEquals(ACCOUNT_NUM, subject.getIdNumber(EVM_ADDRESS));
    }

    @Test
    void returnsLongZeroAddressWithoutAnAlias() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM));
        assertEquals(LONG_ZERO_ADDRESS, subject.getAddress(ACCOUNT_NUM));
    }

    @Test
    void returnsExpectedRentFactors() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM));
        final var expected = new RentFactors(NUM_KV_SLOTS, EXPIRY);
        assertEquals(expected, subject.getRentFactorsFor(ACCOUNT_NUM));
    }

    @Test
    void returnsNullWithDeletedAccount() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).deleted(true));
        assertNull(subject.getAddress(ACCOUNT_NUM));
    }

    @Test
    void returnsLongZeroAddressWithNonAddressAlias() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM, SOME_OTHER_ALIAS));
        assertEquals(LONG_ZERO_ADDRESS, subject.getAddress(ACCOUNT_NUM));
    }

    @Test
    void returnsAliasIfPresent() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM, Bytes.wrap(EVM_ADDRESS.toArrayUnsafe())));
        assertEquals(EVM_ADDRESS, subject.getAddress(ACCOUNT_NUM));
    }

    @Test
    void throwsIfAccountMissing() {
        assertThrows(IllegalArgumentException.class, () -> subject.getAddress(ACCOUNT_NUM));
    }

    @Test
    void returnsNonceIfPresent() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).ethereumNonce(1234));
        assertEquals(1234, subject.getNonce(ACCOUNT_NUM));
    }

    @Test
    void returnsNumTreasuryTitlesIfPresent() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).numberTreasuryTitles(1234));
        assertEquals(1234, subject.getNumTreasuryTitles(ACCOUNT_NUM));
    }

    @Test
    void returnsNumNonZeroBalancesIfPresent() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).numberPositiveBalances(1234));
        assertEquals(1234, subject.getNumPositiveTokenBalances(ACCOUNT_NUM));
    }

    @Test
    void returnsWhetherAnAccountIsContract() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).smartContract(true));
        assertTrue(subject.isContract(ACCOUNT_NUM));
    }

    @Test
    void returnsBalanceIfPresent() {
        final var value = Wei.of(1234);
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).tinybarBalance(1234));
        assertEquals(value, subject.getBalance(ACCOUNT_NUM));
    }

    @Test
    void returnsNullForMissing() {
        assertNull(subject.getAccount(LONG_ZERO_ADDRESS));
    }

    @Test
    void returnsNullForDeleted() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).deleted(true));
        assertNull(subject.getAccount(LONG_ZERO_ADDRESS));
    }

    @Test
    void returnsNullForExpired() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).expiredAndPendingRemoval(true));
        assertNull(subject.getAccount(LONG_ZERO_ADDRESS));
    }

    @Test
    void missingAccountsCannotBeBeneficiaries() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).expiredAndPendingRemoval(true));

        final var reasonToHaltDeletion = subject.tryTrackingDeletion(EVM_ADDRESS, LONG_ZERO_ADDRESS);

        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(INVALID_SOLIDITY_ADDRESS, reasonToHaltDeletion.get());
    }

    @Test
    void missingAccountsCannotTransferFunds() {
        final var reasonToHaltDeletion = subject.tryTransfer(EVM_ADDRESS, LONG_ZERO_ADDRESS, 123L, true);
        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(INVALID_SOLIDITY_ADDRESS, reasonToHaltDeletion.get());
    }

    @Test
    void cannotTransferToMissingAccount() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).smartContract(true));
        final var reasonToHaltDeletion = subject.tryTransfer(LONG_ZERO_ADDRESS, EVM_ADDRESS, 123L, true);
        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(INVALID_SOLIDITY_ADDRESS, reasonToHaltDeletion.get());
    }

    @Test
    void cannotTransferToTokenAccount() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).smartContract(true));
        givenWellKnownToken();
        final var reasonToHaltDeletion = subject.tryTransfer(LONG_ZERO_ADDRESS, TOKEN_ADDRESS, 123L, true);
        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(ILLEGAL_STATE_CHANGE, reasonToHaltDeletion.get());
    }

    @Test
    void cannotLazyCreateOverExpiredAccount() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).expiredAndPendingRemoval(true));
        given(nativeOperations.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe())))
                .willReturn(ACCOUNT_NUM);

        final var reasonLazyCreationFailed = subject.tryLazyCreation(EVM_ADDRESS);

        assertTrue(reasonLazyCreationFailed.isPresent());
        assertEquals(FAILURE_DURING_LAZY_ACCOUNT_CREATION, reasonLazyCreationFailed.get());
    }

    @Test
    void noHaltIfLazyCreationOk() {
        given(nativeOperations.createHollowAccount(tuweniToPbjBytes(EVM_ADDRESS)))
                .willReturn(ResponseCodeEnum.OK);
        final var reasonLazyCreationFailed = subject.tryLazyCreation(EVM_ADDRESS);

        assertTrue(reasonLazyCreationFailed.isEmpty());
    }

    @Test
    void translatesMaxAccountsCreated() {
        given(nativeOperations.createHollowAccount(tuweniToPbjBytes(EVM_ADDRESS)))
                .willReturn(ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
        final var reasonLazyCreationFailed = subject.tryLazyCreation(EVM_ADDRESS);

        assertTrue(reasonLazyCreationFailed.isPresent());
        assertEquals(FAILURE_DURING_LAZY_ACCOUNT_CREATION, reasonLazyCreationFailed.get());
    }

    @Test
    void throwsOnLazyCreateOfLongZeroAddress() {
        assertThrows(IllegalArgumentException.class, () -> subject.tryLazyCreation(LONG_ZERO_ADDRESS));
    }

    @Test
    void throwsOnLazyCreateOfNonExpiredAccount() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM));
        given(nativeOperations.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe())))
                .willReturn(ACCOUNT_NUM);

        assertThrows(IllegalArgumentException.class, () -> subject.tryLazyCreation(EVM_ADDRESS));
    }

    @Test
    void transferDelegationUsesExpectedVerifierForNonDelegate() {
        final var captor = ArgumentCaptor.forClass(VerificationStrategy.class);
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).smartContract(true));
        givenWellKnownAccount(BENEFICIARY_NUM, accountWith(BENEFICIARY_NUM));
        given(nativeOperations.transferWithReceiverSigCheck(
                        eq(123L), eq(ACCOUNT_NUM), eq(BENEFICIARY_NUM), captor.capture()))
                .willReturn(OK);
        final var reasonToHaltDeletion = subject.tryTransfer(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS, 123L, false);
        assertTrue(reasonToHaltDeletion.isEmpty());
        final var strategy = assertInstanceOf(ActiveContractVerificationStrategy.class, captor.getValue());
        assertEquals(ACCOUNT_NUM, strategy.getActiveNumber());
        assertEquals(tuweniToPbjBytes(LONG_ZERO_ADDRESS), strategy.getActiveAddress());
        assertFalse(strategy.requiresDelegatePermission());
    }

    @Test
    void transferDelegationReportsInvalidSignature() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).smartContract(true));
        givenWellKnownAccount(BENEFICIARY_NUM, accountWith(BENEFICIARY_NUM));
        given(nativeOperations.transferWithReceiverSigCheck(eq(123L), eq(ACCOUNT_NUM), eq(BENEFICIARY_NUM), any()))
                .willReturn(INVALID_SIGNATURE);
        final var reasonToHaltDeletion = subject.tryTransfer(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS, 123L, false);
        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(CustomExceptionalHaltReason.INVALID_SIGNATURE, reasonToHaltDeletion.get());
    }

    @Test
    void transferDelegationThrowsOnApparentlyImpossibleFailureMode() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).smartContract(true));
        givenWellKnownAccount(BENEFICIARY_NUM, accountWith(BENEFICIARY_NUM));
        given(nativeOperations.transferWithReceiverSigCheck(eq(123L), eq(ACCOUNT_NUM), eq(BENEFICIARY_NUM), any()))
                .willReturn(INSUFFICIENT_ACCOUNT_BALANCE);
        assertThrows(
                IllegalStateException.class,
                () -> subject.tryTransfer(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS, 123L, false));
    }

    @Test
    void deletedAccountCannotBeTokenTreasury() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).numberTreasuryTitles(1));
        givenWellKnownAccount(BENEFICIARY_NUM, accountWith(BENEFICIARY_NUM));

        final var reasonToHaltDeletion = subject.tryTrackingDeletion(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS);

        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(CONTRACT_IS_TREASURY, reasonToHaltDeletion.get());
    }

    @Test
    void deletedAccountCannotHaveTokenBalances() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).numberPositiveBalances(1));
        givenWellKnownAccount(BENEFICIARY_NUM, accountWith(BENEFICIARY_NUM));

        final var reasonToHaltDeletion = subject.tryTrackingDeletion(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS);

        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(CONTRACT_STILL_OWNS_NFTS, reasonToHaltDeletion.get());
    }

    @Test
    void deletionsAreTracked() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM));
        givenWellKnownAccount(BENEFICIARY_NUM, accountWith(BENEFICIARY_NUM));

        final var reasonToHaltDeletion = subject.tryTrackingDeletion(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS);

        assertTrue(reasonToHaltDeletion.isEmpty());
        verify(nativeOperations).trackDeletion(ACCOUNT_NUM, BENEFICIARY_NUM);
    }

    @Test
    void beneficiaryCannotBeSelf() {
        final var reasonToHaltDeletion = subject.tryTrackingDeletion(EVM_ADDRESS, EVM_ADDRESS);

        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(CustomExceptionalHaltReason.SELF_DESTRUCT_TO_SELF, reasonToHaltDeletion.get());
    }

    @Test
    void tokenAccountsCannotBeBeneficiaries() {
        givenWellKnownToken();

        final var reasonToHaltDeletion = subject.tryTrackingDeletion(EVM_ADDRESS, TOKEN_ADDRESS);

        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(INVALID_SOLIDITY_ADDRESS, reasonToHaltDeletion.get());
    }

    @Test
    void senderAccountMustBeC() {
        givenWellKnownToken();

        final var reasonToHaltDeletion = subject.tryTrackingDeletion(EVM_ADDRESS, TOKEN_ADDRESS);

        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(INVALID_SOLIDITY_ADDRESS, reasonToHaltDeletion.get());
    }

    @Test
    void returnsProxyAccountForNormal() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM));
        assertInstanceOf(ProxyEvmAccount.class, subject.getAccount(LONG_ZERO_ADDRESS));
    }

    @Test
    void returnsNullForMissingAlias() {
        assertNull(subject.getAccount(EVM_ADDRESS));
    }

    @Test
    void missingAliasIsNotHollow() {
        assertFalse(subject.isHollowAccount(EVM_ADDRESS));
    }

    @Test
    void missingAccountIsNotHollow() {
        given(nativeOperations.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe())))
                .willReturn(ACCOUNT_NUM);
        assertFalse(subject.isHollowAccount(EVM_ADDRESS));
    }

    @Test
    void extantAccountIsHollowOnlyIfHasAnEmptyKey() {
        given(nativeOperations.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe())))
                .willReturn(ACCOUNT_NUM);
        givenWellKnownAccount(accountWith(ACCOUNT_NUM)
                .key(Key.newBuilder().keyList(KeyList.DEFAULT).build()));
        assertTrue(subject.isHollowAccount(EVM_ADDRESS));
    }

    @Test
    void usesResolvedNumberFromDispatch() {
        given(nativeOperations.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe())))
                .willReturn(ACCOUNT_NUM);
        givenWellKnownAccount(accountWith(ACCOUNT_NUM));
        assertInstanceOf(ProxyEvmAccount.class, subject.getAccount(EVM_ADDRESS));
    }

    @Test
    void returnsNullForAliasedReferencedByLongZero() {
        final var alias = Bytes.wrap(EVM_ADDRESS.toArrayUnsafe());
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).alias(alias));
        assertNull(subject.getAccount(LONG_ZERO_ADDRESS));
    }

    @Test
    void choosesTokenAccountIfApplicable() {
        givenWellKnownToken();
        final var account = subject.getAccount(TOKEN_ADDRESS);
        assertInstanceOf(TokenEvmAccount.class, account);
    }

    @Test
    void getAccountDelegatesToGetMutableAccount() {
        final var mockSubject = mock(DispatchingEvmFrameState.class);
        final var mockAccount = mock(TokenEvmAccount.class);

        given(mockSubject.getMutableAccount(TOKEN_ADDRESS)).willReturn(mockAccount);
        doCallRealMethod().when(mockSubject).getAccount(TOKEN_ADDRESS);

        final var account = mockSubject.getAccount(TOKEN_ADDRESS);

        assertSame(mockAccount, account);
    }

    @Test
    void delegatesSizeOfKvState() {
        given(contractStateStore.getNumSlots()).willReturn(123L);
        assertEquals(123L, subject.getKvStateSize());
    }

    private void givenWellKnownBytecode() {
        given(contractStateStore.getBytecode(new EntityNumber(ACCOUNT_NUM))).willReturn(SOME_PRETEND_BYTECODE);
    }

    private void givenWellKnownAccount(final Account.Builder builder) {
        givenWellKnownAccount(ACCOUNT_NUM, builder);
    }

    private void givenWellKnownAccount(final long number, final Account.Builder builder) {
        given(nativeOperations.getAccount(number)).willReturn(builder.build());
    }

    private void givenWellKnownToken() {
        given(nativeOperations.getToken(TOKEN_NUM))
                .willReturn(Token.newBuilder().build());
    }

    private Account.Builder accountWith(final long num, final Bytes alias) {
        return accountWith(num).alias(alias);
    }

    private Account.Builder accountWith(final long num) {
        return Account.newBuilder()
                .accountId(AccountID.newBuilder().accountNum(num))
                .expirationSecond(EXPIRY)
                .contractKvPairsNumber(NUM_KV_SLOTS);
    }
}
