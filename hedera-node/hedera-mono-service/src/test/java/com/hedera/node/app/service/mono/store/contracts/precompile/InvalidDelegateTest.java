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

package com.hedera.node.app.service.mono.store.contracts.precompile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvalidDelegateTest {
    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame parentMessageFrame;

    @Mock
    private TxnAwareEvmSigsVerifier sigsVerifier;

    @Mock
    private RecordsHistorian recordsHistorian;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private ExpiringCreations creator;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private StateView stateView;

    @Mock
    private HederaStackedWorldStateUpdater worldUpdater;

    @Mock
    private WorldLedgers wrappedLedgers;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private InfrastructureFactory infrastructureFactory;

    @Mock
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;

    @Mock
    private TokenInfoWrapper<TokenID> tokenInfoWrapper;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private Account acc;

    @Mock
    private Deque<MessageFrame> stack;

    @Mock
    private Iterator<MessageFrame> dequeIterator;

    @Mock
    private PrecompilePricingUtils precompilePricingUtils;

    @Mock
    private MessageFrame frame;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    private HTSPrecompiledContract subject;

    @BeforeEach
    void setUp() {
        subject = new HTSPrecompiledContract(
                dynamicProperties,
                gasCalculator,
                recordsHistorian,
                sigsVerifier,
                encoder,
                evmEncoder,
                syntheticTxnFactory,
                creator,
                () -> feeCalculator,
                stateView,
                precompilePricingUtils,
                infrastructureFactory,
                evmHTSPrecompiledContract);
    }

    @Test
    void disallowsDelegateCallsIfNotTokenOrAllowListed() {
        givenFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_MUL);
        givenRecipientAccount(false, Address.ALTBN128_ADD);
        assertSame(HTSPrecompiledContract.INVALID_DELEGATE, subject.computePrecompile(Bytes.of(1, 2, 3), frame));
        verify(frame).setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    }

    @Test
    void immediatelyAllowsNonDelegateCalls() {
        // With a normal call, the contract address is the recipient address
        givenFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_ADD);
        assertFalse(subject.unqualifiedDelegateDetected(frame));
    }

    @Test
    void delegateCallNonTokenAndNotPrivilegedImmediatelyRejected() {
        // With a normal call, the contract address is the recipient address;
        // so if we give different addresses, we know it's a delegate call
        givenFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_MUL);
        givenRecipientAccount(false, Address.ALTBN128_ADD);
        givenPrivileged(Collections.emptySet());

        assertTrue(subject.unqualifiedDelegateDetected(frame));
    }

    @Test
    void delegateCallFromTokenStillRequiresNonDelegateParentFrame() {
        // With a normal call, the contract address is the recipient address;
        // so if we give different addresses, we know it's a delegate call
        givenFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_MUL);
        givenRecipientAccount(true, Address.ALTBN128_ADD);
        givenParentFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_MUL);

        assertTrue(subject.unqualifiedDelegateDetected(frame));
    }

    @Test
    void delegateCallFromPrivilegedStillRequiresNonDelegateParentFrame() {
        // With a normal call, the contract address is the recipient address;
        // so if we give different addresses, we know it's a delegate call
        givenFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_MUL);
        givenRecipientAccount(false, Address.ALTBN128_ADD);
        givenPrivileged(Set.of(Address.ALTBN128_ADD));
        givenParentFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_MUL);

        assertTrue(subject.unqualifiedDelegateDetected(frame));
    }

    @Test
    void delegateCallFromPrivilegedWithSomehowMissingReceipientStillRequiresNonDelegateParentFrame() {
        // With a normal call, the contract address is the recipient address;
        // so if we give different addresses, we know it's a delegate call
        givenFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_MUL);
        givenMissingRecipientAccount();
        givenPrivileged(Set.of(Address.ALTBN128_ADD));
        givenParentFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_MUL);

        assertTrue(subject.unqualifiedDelegateDetected(frame));
    }

    @Test
    void delegateCallFromPrivilegedOkWithNonDelegateParentFrame() {
        // With a normal call, the contract address is the recipient address;
        // so if we give different addresses, we know it's a delegate call
        givenFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_MUL);
        givenRecipientAccount(false, Address.ALTBN128_ADD);
        givenPrivileged(Set.of(Address.ALTBN128_ADD));
        givenParentFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_ADD);

        assertFalse(subject.unqualifiedDelegateDetected(frame));
    }

    @Test
    void delegateCallFromPrivilegedNotOkWithImpossibleMissingParentFrame() {
        // With a normal call, the contract address is the recipient address;
        // so if we give different addresses, we know it's a delegate call
        givenFrameWith(Address.ALTBN128_ADD, Address.ALTBN128_MUL);
        givenRecipientAccount(false, Address.ALTBN128_ADD);
        givenPrivileged(Set.of(Address.ALTBN128_ADD));
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.iterator()).willReturn(dequeIterator);

        assertFalse(subject.unqualifiedDelegateDetected(frame));
    }

    private void givenFrameWith(final Address recipient, final Address contract) {
        given(frame.getContractAddress()).willReturn(contract);
        given(frame.getRecipientAddress()).willReturn(recipient);
    }

    private void givenParentFrameWith(final Address recipient, final Address contract) {
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(stack.iterator()).willReturn(dequeIterator);
        given(dequeIterator.hasNext()).willReturn(true);
        given(dequeIterator.next()).willReturn(parentMessageFrame);
        given(parentMessageFrame.getContractAddress()).willReturn(contract);
        given(parentMessageFrame.getRecipientAddress()).willReturn(recipient);
    }

    private void givenPrivileged(final Set<Address> addresses) {
        given(dynamicProperties.permittedDelegateCallers()).willReturn(addresses);
    }

    private void givenRecipientAccount(final boolean isToken, final Address address) {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.get(address)).willReturn(acc);
        if (isToken) {
            given(acc.getNonce()).willReturn(-1L);
        } else {
            given(acc.getNonce()).willReturn(1234L);
        }
    }

    private void givenMissingRecipientAccount() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
    }
}
