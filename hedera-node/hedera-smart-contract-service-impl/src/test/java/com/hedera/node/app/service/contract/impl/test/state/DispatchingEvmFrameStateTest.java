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
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_RECEIVER_SIGNATURE;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_VALUE_TRANSFER;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.MISSING_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.SELFDESTRUCT_TO_SELF;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.TOKEN_HOLDER_SELFDESTRUCT;
import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.TOKEN_TREASURY_SELFDESTRUCT;
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
import com.hedera.node.app.service.contract.impl.state.DispatchingEvmFrameState;
import com.hedera.node.app.service.contract.impl.state.ProxyEvmAccount;
import com.hedera.node.app.service.contract.impl.state.TokenEvmAccount;
import com.hedera.node.app.spi.meta.bni.ActiveContractVerificationStrategy;
import com.hedera.node.app.spi.meta.bni.Dispatch;
import com.hedera.node.app.spi.meta.bni.VerificationStrategy;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
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
    private Dispatch dispatch;

    @Mock
    private WritableKVState<SlotKey, SlotValue> storage;

    @Mock
    private WritableKVState<EntityNumber, Bytecode> bytecode;

    private DispatchingEvmFrameState subject;

    @BeforeEach
    void setUp() {
        subject = new DispatchingEvmFrameState(dispatch, storage, bytecode);
    }

    @Test
    void dispatchesToSetNonce() {
        subject.setNonce(ACCOUNT_NUM, 1234);

        verify(dispatch).setNonce(ACCOUNT_NUM, 1234);
    }

    @Test
    void dispatchesToFinalizeHollowAccount() {
        subject.finalizeHollowAccount(EVM_ADDRESS);

        verify(dispatch).finalizeHollowAccountAsContract(tuweniToPbjBytes(EVM_ADDRESS));
    }

    @Test
    void dispatchesToSetCode() {
        final var expectedCode = Bytecode.newBuilder().code(SOME_PRETEND_CODE).build();

        subject.setCode(ACCOUNT_NUM, pbjToTuweniBytes(SOME_PRETEND_CODE));

        verify(bytecode).put(new EntityNumber(ACCOUNT_NUM), expectedCode);
    }

    @Test
    void getsExtantStorageValues() {
        given(storage.get(A_SLOT_KEY)).willReturn(A_SLOT_VALUE);

        final var expectedWord = pbjToTuweniUInt256(A_STORAGE_VALUE);
        final var actualWord = subject.getStorageValue(ACCOUNT_NUM, pbjToTuweniUInt256(A_STORAGE_KEY));

        assertEquals(expectedWord, actualWord);
    }

    @Test
    void overwritesNewStorageSlotAsExpected() {
        final var newSlotValue = A_SLOT_VALUE
                .copyBuilder()
                .previousKey(Bytes.EMPTY)
                .nextKey(Bytes.EMPTY)
                .build();

        subject.setStorageValue(ACCOUNT_NUM, pbjToTuweniUInt256(A_STORAGE_KEY), pbjToTuweniUInt256(A_STORAGE_VALUE));

        verify(storage).put(A_SLOT_KEY, newSlotValue);
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

        given(storage.get(A_SLOT_KEY)).willReturn(oldSlotValue);
        subject.setStorageValue(ACCOUNT_NUM, pbjToTuweniUInt256(A_STORAGE_KEY), pbjToTuweniUInt256(A_STORAGE_VALUE));

        verify(storage).put(A_SLOT_KEY, newSlotValue);
    }

    @Test
    void getsZeroWordForMissingSlotKey() {
        final var actualWord = subject.getStorageValue(ACCOUNT_NUM, pbjToTuweniUInt256(A_STORAGE_KEY));

        assertSame(UInt256.ZERO, actualWord);
    }

    @Test
    void getsZeroWordForEmptySlotValue() {
        given(storage.get(A_SLOT_KEY)).willReturn(SlotValue.DEFAULT);

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
        given(bytecode.get(new EntityNumber(ACCOUNT_NUM))).willReturn(new Bytecode(null));

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
    void returnsLongZeroAddressWithoutAnAlias() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM));
        assertEquals(LONG_ZERO_ADDRESS, subject.getAddress(ACCOUNT_NUM));
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
        assertEquals(MISSING_ADDRESS, reasonToHaltDeletion.get());
    }

    @Test
    void missingAccountsCannotPayFees() {
        assertThrows(IllegalArgumentException.class, () -> subject.collectFee(EVM_ADDRESS, 123L));
    }

    @Test
    void delegatesFeeCollection() {
        given(dispatch.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe()))).willReturn(new EntityNumber(ACCOUNT_NUM));

        subject.collectFee(EVM_ADDRESS, 123L);

        verify(dispatch).collectFee(ACCOUNT_NUM, 123L);
    }

    @Test
    void missingAccountsCannotGetRefunds() {
        assertThrows(IllegalArgumentException.class, () -> subject.refundFee(EVM_ADDRESS, 123L));
    }

    @Test
    void delegatesFeeRefunding() {
        given(dispatch.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe()))).willReturn(new EntityNumber(ACCOUNT_NUM));

        subject.refundFee(EVM_ADDRESS, 123L);

        verify(dispatch).refundFee(ACCOUNT_NUM, 123L);
    }

    @Test
    void missingAccountsCannotTransferFunds() {
        final var reasonToHaltDeletion = subject.tryTransferFromContract(EVM_ADDRESS, LONG_ZERO_ADDRESS, 123L, true);
        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(MISSING_ADDRESS, reasonToHaltDeletion.get());
    }

    @Test
    void nonContractAccountsShouldNeverBeTransferringFunds() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM));
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.tryTransferFromContract(LONG_ZERO_ADDRESS, EVM_ADDRESS, 123L, true));
    }

    @Test
    void cannotTransferToMissingAccount() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).smartContract(true));
        final var reasonToHaltDeletion = subject.tryTransferFromContract(LONG_ZERO_ADDRESS, EVM_ADDRESS, 123L, true);
        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(MISSING_ADDRESS, reasonToHaltDeletion.get());
    }

    @Test
    void cannotTransferToTokenAccount() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).smartContract(true));
        givenWellKnownToken();
        final var reasonToHaltDeletion = subject.tryTransferFromContract(LONG_ZERO_ADDRESS, TOKEN_ADDRESS, 123L, true);
        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(ILLEGAL_STATE_CHANGE, reasonToHaltDeletion.get());
    }

    @Test
    void cannotLazyCreateOverExpiredAccount() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).expiredAndPendingRemoval(true));
        given(dispatch.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe()))).willReturn(new EntityNumber(ACCOUNT_NUM));

        final var reasonLazyCreationFailed = subject.tryLazyCreation(EVM_ADDRESS);

        assertTrue(reasonLazyCreationFailed.isPresent());
        assertEquals(INVALID_VALUE_TRANSFER, reasonLazyCreationFailed.get());
    }

    @Test
    void translatesMaxChildRecordsExceeded() {
        given(dispatch.createHollowAccount(tuweniToPbjBytes(EVM_ADDRESS)))
                .willReturn(ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
        final var reasonLazyCreationFailed = subject.tryLazyCreation(EVM_ADDRESS);

        assertTrue(reasonLazyCreationFailed.isPresent());
        assertEquals(CustomExceptionalHaltReason.TOO_MANY_CHILD_RECORDS, reasonLazyCreationFailed.get());
    }

    @Test
    void noHaltIfLazyCreationOk() {
        given(dispatch.createHollowAccount(tuweniToPbjBytes(EVM_ADDRESS))).willReturn(ResponseCodeEnum.OK);
        final var reasonLazyCreationFailed = subject.tryLazyCreation(EVM_ADDRESS);

        assertTrue(reasonLazyCreationFailed.isEmpty());
    }

    @Test
    void translatesMaxAccountsCreated() {
        given(dispatch.createHollowAccount(tuweniToPbjBytes(EVM_ADDRESS)))
                .willReturn(ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
        final var reasonLazyCreationFailed = subject.tryLazyCreation(EVM_ADDRESS);

        assertTrue(reasonLazyCreationFailed.isPresent());
        assertEquals(CustomExceptionalHaltReason.ACCOUNTS_LIMIT_REACHED, reasonLazyCreationFailed.get());
    }

    @Test
    void throwsOnUnexpectedFailureMode() {
        given(dispatch.createHollowAccount(tuweniToPbjBytes(EVM_ADDRESS)))
                .willReturn(ResponseCodeEnum.INVALID_ALIAS_KEY);
        assertThrows(IllegalStateException.class, () -> subject.tryLazyCreation(EVM_ADDRESS));
    }

    @Test
    void throwsOnLazyCreateOfLongZeroAddress() {
        assertThrows(IllegalArgumentException.class, () -> subject.tryLazyCreation(LONG_ZERO_ADDRESS));
    }

    @Test
    void throwsOnLazyCreateOfNonExpiredAccount() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM));
        given(dispatch.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe()))).willReturn(new EntityNumber(ACCOUNT_NUM));

        assertThrows(IllegalArgumentException.class, () -> subject.tryLazyCreation(EVM_ADDRESS));
    }

    @Test
    void transferDelegationUsesExpectedVerifierForNonDelegate() {
        final var captor = ArgumentCaptor.forClass(VerificationStrategy.class);
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).smartContract(true));
        givenWellKnownAccount(BENEFICIARY_NUM, accountWith(BENEFICIARY_NUM));
        given(dispatch.transferWithReceiverSigCheck(eq(123L), eq(ACCOUNT_NUM), eq(BENEFICIARY_NUM), captor.capture()))
                .willReturn(OK);
        final var reasonToHaltDeletion =
                subject.tryTransferFromContract(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS, 123L, false);
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
        given(dispatch.transferWithReceiverSigCheck(eq(123L), eq(ACCOUNT_NUM), eq(BENEFICIARY_NUM), any()))
                .willReturn(INVALID_SIGNATURE);
        final var reasonToHaltDeletion =
                subject.tryTransferFromContract(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS, 123L, false);
        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(INVALID_RECEIVER_SIGNATURE, reasonToHaltDeletion.get());
    }

    @Test
    void transferDelegationThrowsOnApparentlyImpossibleFailureMode() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).smartContract(true));
        givenWellKnownAccount(BENEFICIARY_NUM, accountWith(BENEFICIARY_NUM));
        given(dispatch.transferWithReceiverSigCheck(eq(123L), eq(ACCOUNT_NUM), eq(BENEFICIARY_NUM), any()))
                .willReturn(INSUFFICIENT_ACCOUNT_BALANCE);
        assertThrows(
                IllegalStateException.class,
                () -> subject.tryTransferFromContract(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS, 123L, false));
    }

    @Test
    void deletedAccountCannotBeTokenTreasury() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).numberTreasuryTitles(1));
        givenWellKnownAccount(BENEFICIARY_NUM, accountWith(BENEFICIARY_NUM));

        final var reasonToHaltDeletion = subject.tryTrackingDeletion(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS);

        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(TOKEN_TREASURY_SELFDESTRUCT, reasonToHaltDeletion.get());
    }

    @Test
    void deletedAccountCannotHaveTokenBalances() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).numberPositiveBalances(1));
        givenWellKnownAccount(BENEFICIARY_NUM, accountWith(BENEFICIARY_NUM));

        final var reasonToHaltDeletion = subject.tryTrackingDeletion(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS);

        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(TOKEN_HOLDER_SELFDESTRUCT, reasonToHaltDeletion.get());
    }

    @Test
    void deletionsAreTracked() {
        givenWellKnownAccount(accountWith(ACCOUNT_NUM));
        givenWellKnownAccount(BENEFICIARY_NUM, accountWith(BENEFICIARY_NUM));

        final var reasonToHaltDeletion = subject.tryTrackingDeletion(LONG_ZERO_ADDRESS, BENEFICIARY_ADDRESS);

        assertTrue(reasonToHaltDeletion.isEmpty());
        verify(dispatch).trackDeletion(ACCOUNT_NUM, BENEFICIARY_NUM);
    }

    @Test
    void beneficiaryCannotBeSelf() {
        final var reasonToHaltDeletion = subject.tryTrackingDeletion(EVM_ADDRESS, EVM_ADDRESS);

        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(SELFDESTRUCT_TO_SELF, reasonToHaltDeletion.get());
    }

    @Test
    void tokenAccountsCannotBeBeneficiaries() {
        givenWellKnownToken();

        final var reasonToHaltDeletion = subject.tryTrackingDeletion(EVM_ADDRESS, TOKEN_ADDRESS);

        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(MISSING_ADDRESS, reasonToHaltDeletion.get());
    }

    @Test
    void senderAccountMustBeC() {
        givenWellKnownToken();

        final var reasonToHaltDeletion = subject.tryTrackingDeletion(EVM_ADDRESS, TOKEN_ADDRESS);

        assertTrue(reasonToHaltDeletion.isPresent());
        assertEquals(MISSING_ADDRESS, reasonToHaltDeletion.get());
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
        given(dispatch.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe()))).willReturn(new EntityNumber(ACCOUNT_NUM));
        assertFalse(subject.isHollowAccount(EVM_ADDRESS));
    }

    @Test
    void extantAccountIsHollowOnlyIfHasAnEmptyKey() {
        given(dispatch.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe()))).willReturn(new EntityNumber(ACCOUNT_NUM));
        givenWellKnownAccount(accountWith(ACCOUNT_NUM).key(Key.newBuilder().keyList(KeyList.DEFAULT)));
        assertTrue(subject.isHollowAccount(EVM_ADDRESS));
    }

    @Test
    void usesResolvedNumberFromDispatch() {
        given(dispatch.resolveAlias(Bytes.wrap(EVM_ADDRESS.toArrayUnsafe()))).willReturn(new EntityNumber(ACCOUNT_NUM));
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
        given(storage.size()).willReturn(123L);
        assertEquals(123L, subject.getKvStateSize());
    }

    private void givenWellKnownBytecode() {
        given(bytecode.get(new EntityNumber(ACCOUNT_NUM))).willReturn(SOME_PRETEND_BYTECODE);
    }

    private void givenWellKnownAccount(final Account.Builder builder) {
        givenWellKnownAccount(ACCOUNT_NUM, builder);
    }

    private void givenWellKnownAccount(final long number, final Account.Builder builder) {
        given(dispatch.getAccount(number)).willReturn(builder.build());
    }

    private void givenWellKnownToken() {
        given(dispatch.getToken(TOKEN_NUM)).willReturn(Token.newBuilder().build());
    }

    private Account.Builder accountWith(final long num, final Bytes alias) {
        return accountWith(num).alias(alias);
    }

    private Account.Builder accountWith(final long num) {
        return Account.newBuilder().accountNumber(num);
    }
}
