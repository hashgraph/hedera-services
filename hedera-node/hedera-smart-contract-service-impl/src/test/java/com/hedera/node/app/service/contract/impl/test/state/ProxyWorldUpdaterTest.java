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

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_RECEIVER_SIGNATURE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.*;
import static org.hyperledger.besu.datatypes.Address.ALTBN128_ADD;
import static org.hyperledger.besu.datatypes.Address.ZERO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.state.*;
import com.hedera.node.app.spi.meta.bni.Dispatch;
import com.hedera.node.app.spi.meta.bni.Scope;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProxyWorldUpdaterTest {
    private static final long NUMBER = 123L;
    private static final long NEXT_NUMBER = 124L;
    private static final long NUMBER_OF_DELETED = 125L;
    private static final long HAPI_PAYER_NUMBER = 777L;
    private static final Address LONG_ZERO_ADDRESS = asLongZeroAddress(NUMBER);
    private static final Address NEXT_LONG_ZERO_ADDRESS = asLongZeroAddress(NEXT_NUMBER);
    private static final Address SOME_EVM_ADDRESS = Address.fromHexString("0x1234123412341234123412341234123412341234");
    private static final Address OTHER_EVM_ADDRESS =
            Address.fromHexString("0x1239123912391239123912391239123912391239");

    @Mock
    private Account anImmutableAccount;

    @Mock
    private Account anotherImmutableAccount;

    @Mock
    private EvmAccount mutableAccount;

    @Mock
    private ProxyEvmAccount proxyEvmAccount;

    @Mock
    private Scope scope;

    @Mock
    private Dispatch dispatch;

    @Mock
    private WorldUpdater parent;

    @Mock
    private EvmFrameStateFactory evmFrameStateFactory;

    @Mock
    private EvmFrameState evmFrameState;

    private ProxyWorldUpdater subject;

    @BeforeEach
    void setUp() {
        given(evmFrameStateFactory.createIn(scope)).willReturn(evmFrameState);

        subject = new ProxyWorldUpdater(scope, evmFrameStateFactory, null);
    }

    @Test
    void getsImmutableAccount() {
        given(evmFrameState.getAccount(ALTBN128_ADD)).willReturn(anImmutableAccount);

        assertSame(anImmutableAccount, subject.get(ALTBN128_ADD));
    }

    @Test
    void getsHederaAccountByNumber() {
        final var num = ALTBN128_ADD.toBigInteger().longValueExact();
        final var numericId = AccountID.newBuilder().accountNum(num).build();
        given(evmFrameState.getAddress(num)).willReturn(ALTBN128_ADD);
        given(evmFrameState.getAccount(ALTBN128_ADD)).willReturn(proxyEvmAccount);
        assertSame(proxyEvmAccount, subject.getHederaAccount(numericId));
    }

    @Test
    void getsHederaContractByNumber() {
        final var num = ALTBN128_ADD.toBigInteger().longValueExact();
        final var numericId = ContractID.newBuilder().contractNum(num).build();
        given(evmFrameState.getAddress(num)).willReturn(ALTBN128_ADD);
        given(evmFrameState.getAccount(ALTBN128_ADD)).willReturn(proxyEvmAccount);
        assertSame(proxyEvmAccount, subject.getHederaAccount(numericId));
    }

    @Test
    void returnsNullHederaAccountIfMissing() {
        final var num = ALTBN128_ADD.toBigInteger().longValueExact();
        final var numericId = AccountID.newBuilder().accountNum(num).build();
        doThrow(IllegalArgumentException.class).when(evmFrameState).getAddress(num);
        assertNull(subject.getHederaAccount(numericId));
    }

    @Test
    void returnsNullHederaContractIfMissing() {
        final var num = ALTBN128_ADD.toBigInteger().longValueExact();
        final var numericId = ContractID.newBuilder().contractNum(num).build();
        doThrow(IllegalArgumentException.class).when(evmFrameState).getAddress(num);
        assertNull(subject.getHederaAccount(numericId));
    }

    @Test
    void getsHederaAccountByAlias() {
        final var aliasId = AccountID.newBuilder()
                .alias(tuweniToPbjBytes(
                        asLongZeroAddress(ALTBN128_ADD.toBigInteger().longValueExact())))
                .build();
        given(evmFrameState.getAccount(ALTBN128_ADD)).willReturn(proxyEvmAccount);
        assertSame(proxyEvmAccount, subject.getHederaAccount(aliasId));
    }

    @Test
    void getsHederaContractByAlias() {
        final var aliasId = ContractID.newBuilder()
                .evmAddress(tuweniToPbjBytes(
                        asLongZeroAddress(ALTBN128_ADD.toBigInteger().longValueExact())))
                .build();
        given(evmFrameState.getAccount(ALTBN128_ADD)).willReturn(proxyEvmAccount);
        assertSame(proxyEvmAccount, subject.getHederaAccount(aliasId));
    }

    @Test
    void delegatesHollowCheck() {
        given(evmFrameState.isHollowAccount(ALTBN128_ADD)).willReturn(true);

        assertTrue(subject.isHollowAccount(ALTBN128_ADD));
    }

    @Test
    void delegatesHollowFinalization() {
        subject.finalizeHollowAccount(EIP_1014_ADDRESS);
        verify(evmFrameState).finalizeHollowAccount(EIP_1014_ADDRESS);
    }

    @Test
    void getsMutableAccount() {
        given(evmFrameState.getMutableAccount(ALTBN128_ADD)).willReturn(mutableAccount);

        assertSame(mutableAccount, subject.getAccount(ALTBN128_ADD));
    }

    @Test
    void cannotCreateAccountWithoutPendingCreation() {
        assertThrows(IllegalStateException.class, () -> subject.createAccount(ALTBN128_ADD, 1, Wei.ZERO));
    }

    @Test
    void providesAccessToPendingStorageChanges() {
        final var someChanges = new StorageAccesses(
                123L, List.of(new StorageAccess(UInt256.ONE, UInt256.MIN_VALUE, UInt256.MAX_VALUE)));
        final var expected = List.of(someChanges);

        given(evmFrameState.getStorageChanges()).willReturn(expected);

        assertSame(expected, subject.pendingStorageUpdates());
    }

    @Test
    void cannotCreateUnlessPendingCreationHasExpectedAddress() {
        givenDispatch();
        given(dispatch.peekNextEntityNumber()).willReturn(NEXT_NUMBER);

        subject.setupCreate(ALTBN128_ADD);

        assertThrows(IllegalStateException.class, () -> subject.createAccount(LONG_ZERO_ADDRESS, 1, Wei.ZERO));
    }

    @Test
    void cannotCreateWithNonZeroBalance() {
        assertThrows(IllegalStateException.class, () -> subject.createAccount(NEXT_LONG_ZERO_ADDRESS, 1, Wei.of(123)));
    }

    @Test
    void cannotCreateUnlessPendingCreationHasExpectedNumber() {
        givenDispatch();
        given(dispatch.peekNextEntityNumber()).willReturn(NEXT_NUMBER);
        given(dispatch.useNextEntityNumber()).willReturn(NEXT_NUMBER + 1);

        subject.setupCreate(ALTBN128_ADD);

        assertThrows(IllegalStateException.class, () -> subject.createAccount(NEXT_LONG_ZERO_ADDRESS, 1, Wei.ZERO));
    }

    @Test
    void revertDelegatesToScope() {
        subject.revert();
        verify(scope).revert();
    }

    @Test
    void commitDelegatesToScope() {
        subject.commit();
        verify(scope).commit();
    }

    @Test
    void usesHapiPayerIfRecipientIsZeroAddress() {
        givenDispatch();
        givenMatchingEntityNumbers();
        given(scope.payerAccountNumber()).willReturn(HAPI_PAYER_NUMBER);
        given(evmFrameState.getMutableAccount(NEXT_LONG_ZERO_ADDRESS)).willReturn(mutableAccount);

        final var pendingAddress = subject.setupCreate(ZERO);
        assertEquals(NEXT_LONG_ZERO_ADDRESS, pendingAddress);
        final var newAccount = subject.createAccount(NEXT_LONG_ZERO_ADDRESS, 1, Wei.ZERO);
        assertSame(mutableAccount, newAccount);

        verify(dispatch).createContract(NEXT_NUMBER, HAPI_PAYER_NUMBER, 1, null);
    }

    @Test
    void usesAliasIfCreate2IsSetupRecipient() {
        givenDispatch();
        givenMatchingEntityNumbers();
        given(evmFrameState.getMutableAccount(SOME_EVM_ADDRESS)).willReturn(mutableAccount);

        subject.setupAliasedCreate(ALTBN128_ADD, SOME_EVM_ADDRESS);
        subject.createAccount(SOME_EVM_ADDRESS, 1, Wei.ZERO);

        verify(dispatch)
                .createContract(
                        NEXT_NUMBER, ALTBN128_ADD.toBigInteger().longValueExact(), 1, aliasFrom(SOME_EVM_ADDRESS));
    }

    @Test
    void canResolvePendingCreationHederaId() {
        givenDispatch();
        given(dispatch.peekNextEntityNumber()).willReturn(NEXT_NUMBER);

        subject.setupAliasedCreate(ALTBN128_ADD, SOME_EVM_ADDRESS);

        final var contractId = subject.getHederaContractId(SOME_EVM_ADDRESS);
        assertEquals(ContractID.newBuilder().contractNum(NEXT_NUMBER).build(), contractId);
    }

    @Test
    void throwsIseWithoutCorrespondingAccount() {
        givenDispatch();
        given(dispatch.peekNextEntityNumber()).willReturn(NEXT_NUMBER);

        subject.setupAliasedCreate(ALTBN128_ADD, SOME_EVM_ADDRESS);

        assertThrows(IllegalArgumentException.class, () -> subject.getHederaContractId(OTHER_EVM_ADDRESS));
    }

    @Test
    void getsAvailableContractIdByAddress() {
        given(evmFrameState.getAccount(SOME_EVM_ADDRESS)).willReturn(proxyEvmAccount);
        given(proxyEvmAccount.hederaContractId()).willReturn(CALLED_CONTRACT_ID);
        final var actual = subject.getHederaContractId(SOME_EVM_ADDRESS);
        assertEquals(CALLED_CONTRACT_ID, actual);
    }

    @Test
    void cannotSetupWithMissingParentNumber() {
        givenDispatch();

        assertThrows(IllegalStateException.class, () -> subject.setupCreate(SOME_EVM_ADDRESS));
    }

    @Test
    void dispatchesDeletingLongZeroAddressByNumber() {
        givenDispatch();

        subject.deleteAccount(ALTBN128_ADD);

        verify(dispatch).deleteUnaliasedContract(ALTBN128_ADD.toBigInteger().longValueExact());
    }

    @Test
    void dispatchesDeletingEvmAddressByAddress() {
        givenDispatch();

        subject.deleteAccount(SOME_EVM_ADDRESS);

        verify(dispatch).deleteAliasedContract(aliasFrom(SOME_EVM_ADDRESS));
    }

    @Test
    void hasEmptyParentIfNull() {
        assertTrue(subject.parentUpdater().isEmpty());
    }

    @Test
    void hasGivenParentIfNonNull() {
        subject = new ProxyWorldUpdater(scope, evmFrameStateFactory, parent);
        assertTrue(subject.parentUpdater().isPresent());
        assertSame(parent, subject.parentUpdater().get());
    }

    @Test
    void updaterHasExpectedProperties() {
        given(scope.begin()).willReturn(scope);
        final var updater = subject.updater();
        assertInstanceOf(ProxyWorldUpdater.class, updater);
        assertTrue(updater.parentUpdater().isPresent());
        assertSame(subject, updater.parentUpdater().get());
    }

    @Test
    void delegatesTransfer() {
        given(evmFrameState.tryTransferFromContract(ALTBN128_ADD, SOME_EVM_ADDRESS, 123L, true))
                .willReturn(Optional.of(INVALID_RECEIVER_SIGNATURE));
        final var maybeHaltReason = subject.tryTransferFromContract(ALTBN128_ADD, SOME_EVM_ADDRESS, 123L, true);
        assertTrue(maybeHaltReason.isPresent());
        assertEquals(INVALID_RECEIVER_SIGNATURE, maybeHaltReason.get());
    }

    @Test
    void onlyReturnsNonDeletedAccountsAsTouched() {
        givenDispatch();
        given(dispatch.getModifiedAccountNumbers()).willReturn(List.of(NUMBER, NEXT_NUMBER, NUMBER_OF_DELETED));
        given(evmFrameState.getAddress(NUMBER)).willReturn(asLongZeroAddress(NUMBER));
        given(evmFrameState.getAddress(NEXT_NUMBER)).willReturn(SOME_EVM_ADDRESS);
        given(evmFrameState.getAddress(NUMBER_OF_DELETED)).willReturn(null);
        given(evmFrameState.getAccount(asLongZeroAddress(NUMBER))).willReturn(anImmutableAccount);
        given(evmFrameState.getAccount(SOME_EVM_ADDRESS)).willReturn(anotherImmutableAccount);

        final var touched = subject.getTouchedAccounts();

        assertEquals(List.of(anImmutableAccount, anotherImmutableAccount), touched);
    }

    @Test
    void doesntSupportDeletedAccountAddresses() {
        assertThrows(UnsupportedOperationException.class, subject::getDeletedAccountAddresses);
    }

    private void givenDispatch() {
        given(scope.dispatch()).willReturn(dispatch);
    }

    private void givenMatchingEntityNumbers() {
        given(dispatch.peekNextEntityNumber()).willReturn(NEXT_NUMBER);
        given(dispatch.useNextEntityNumber()).willReturn(NEXT_NUMBER);
    }
}
