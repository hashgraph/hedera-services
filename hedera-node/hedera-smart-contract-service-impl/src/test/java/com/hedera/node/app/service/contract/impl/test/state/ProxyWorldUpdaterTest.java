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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.aliasFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static org.hyperledger.besu.datatypes.Address.ALTBN128_ADD;
import static org.hyperledger.besu.datatypes.Address.ZERO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.state.EvmFrameState;
import com.hedera.node.app.service.contract.impl.state.EvmFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.spi.meta.bni.Dispatch;
import com.hedera.node.app.spi.meta.bni.Scope;
import java.util.List;
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

    @Mock
    private Account anImmutableAccount;

    @Mock
    private Account anotherImmutableAccount;

    @Mock
    private EvmAccount mutableAccount;

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
    void getsMutableAccount() {
        given(evmFrameState.getMutableAccount(ALTBN128_ADD)).willReturn(mutableAccount);

        assertSame(mutableAccount, subject.getAccount(ALTBN128_ADD));
    }

    @Test
    void cannotCreateAccountWithoutPendingCreation() {
        assertThrows(IllegalStateException.class, () -> subject.createAccount(ALTBN128_ADD, 1, Wei.ZERO));
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
    void usesAliasIfCreate2IsSetupRecipientIsZeroAddress() {
        givenDispatch();
        givenMatchingEntityNumbers();
        given(evmFrameState.getMutableAccount(SOME_EVM_ADDRESS)).willReturn(mutableAccount);

        subject.setupCreate2(ALTBN128_ADD, SOME_EVM_ADDRESS);
        subject.createAccount(SOME_EVM_ADDRESS, 1, Wei.ZERO);

        verify(dispatch)
                .createContract(
                        NEXT_NUMBER, ALTBN128_ADD.toBigInteger().longValueExact(), 1, aliasFrom(SOME_EVM_ADDRESS));
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
        final var updater = subject.updater();
        assertInstanceOf(ProxyWorldUpdater.class, updater);
        assertTrue(updater.parentUpdater().isPresent());
        assertSame(subject, updater.parentUpdater().get());
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
