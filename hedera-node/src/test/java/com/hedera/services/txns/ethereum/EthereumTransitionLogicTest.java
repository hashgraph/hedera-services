package com.hedera.services.txns.ethereum;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.SynthCreationCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.txns.contract.ContractCallTransitionLogic;
import com.hedera.services.txns.contract.ContractCreateTransitionLogic;
import com.hedera.services.txns.span.EthTxExpansion;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.txns.span.SpanMapManager;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.ledger.properties.AccountProperty.ETHEREUM_NONCE;
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_CHAIN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_NONCE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EthereumTransitionLogicTest {
	@Mock
	private EthTxSigs ethTxSigs;
	@Mock
	private EthTxData ethTxData;
	@Mock
	private TxnAccessor accessor;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private SpanMapManager spanMapManager;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private SynthCreationCustomizer creationCustomizer;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private Function<TransactionBody, ResponseCodeEnum> semanticCheck;
	@Mock
	private TransactionRecordService recordService;
	@Mock
	private ExpandHandleSpanMapAccessor spanMapAccessor;
	@Mock
	private ContractCallTransitionLogic contractCallTransitionLogic;
	@Mock
	private ContractCreateTransitionLogic contractCreateTransitionLogic;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

	private EthereumTransitionLogic subject;

	@BeforeEach
	void setUp() {
		subject = new EthereumTransitionLogic(
				txnCtx, syntheticTxnFactory, creationCustomizer,
				spanMapAccessor, contractCallTransitionLogic, contractCreateTransitionLogic,
				recordService, dynamicProperties, aliasManager, accountsLedger, spanMapManager);
	}

	@Test
	void delegatesPrefetch() {
		subject.preFetch(accessor);

		verify(spanMapManager).expandEthereumSpan(accessor);
	}

	@Test
	void recoversFromUnhandledException() {
		willThrow(IllegalStateException.class).given(spanMapManager).expandEthereumSpan(accessor);

		assertDoesNotThrow(() -> subject.preFetch(accessor));
	}

	@Test
	void recognizesApplicability() {
		assertTrue(subject.applicability().test(ethTxn));
		assertFalse(subject.applicability().test(nonEthTxn));
	}

	@Test
	void doesntSupportDirectSemanticCheck() {
		assertThrows(UnsupportedOperationException.class, subject::semanticCheck);
	}

	@Test
	void transitionFailsFastIfExpansionWasntOk() {
		givenContextualAccessor();
		given(spanMapAccessor.getEthTxExpansion(accessor)).willReturn(notOkExpansion);

		assertFailsWith(() -> subject.doStateTransition(), INVALID_ETHEREUM_TRANSACTION);
	}

	@Test
	void transitionFailsFastIfCallerDoesntExist() {
		givenOkContextualAccessor();
		given(aliasManager.lookupIdBy(ByteString.copyFrom(callerAddress))).willReturn(MISSING_NUM);

		assertFailsWith(() -> subject.doStateTransition(), INVALID_ACCOUNT_ID);
	}

	@Test
	void transitionFailsFastIfNonceDoesntMatch() {
		givenOkExtantContextualAccessor();
		given(ethTxData.nonce()).willReturn(requiredNonce + 1);

		assertFailsWith(() -> subject.doStateTransition(), WRONG_NONCE);
	}

	@Test
	void transitionDelegatesToContractCallForSynthCall() {
		givenValidlyCalled(callTxn);

		subject.doStateTransition();

		verify(contractCallTransitionLogic).doStateTransitionOperation(callTxn, callerNum.toId(), true);
		verify(recordService).updateForEvmCall(ethTxData, callerNum.toEntityId());
	}

	@Test
	void transitionDelegatesToCustomContractCreateForSynthCreate() {
		givenValidlyCalled(createTxn);
		given(creationCustomizer.customize(createTxn, callerId)).willReturn(createTxn);

		subject.doStateTransition();

		verify(contractCreateTransitionLogic).doStateTransitionOperation(createTxn, callerNum.toId(), true, true);
		verify(recordService).updateForEvmCall(ethTxData, callerNum.toEntityId());
	}

	@Test
	void invalidIfNoEthTxData() {
		assertEquals(INVALID_ETHEREUM_TRANSACTION, subject.validateSemantics(accessor));
	}

	@Test
	void invalidIfChainIdDoesntMatch() {
		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);
		given(dynamicProperties.chainIdBytes()).willReturn(chainIdBytes);

		assertEquals(WRONG_CHAIN_ID, subject.validateSemantics(accessor));
	}

	@Test
	void invalidIfSynthTxnCantBeConstructed() {
		givenReasonableSemantics();

		assertEquals(INVALID_ETHEREUM_TRANSACTION, subject.validateSemantics(accessor));
	}

	@Test
	void invalidIfSynthTxnSomehowUnrecognizable() {
		givenReasonableSemantics();
		given(syntheticTxnFactory.synthContractOpFromEth(ethTxData))
				.willReturn(Optional.of(TransactionBody.newBuilder()));

		assertEquals(FAIL_INVALID, subject.validateSemantics(accessor));
	}

	@Test
	void hasCallValidityIfReasonableCall() {
		givenReasonableSemantics();
		given(spanMapAccessor.getEthTxBodyMeta(accessor)).willReturn(callTxn);
		given(semanticCheck.apply(callTxn)).willReturn(INVALID_ACCOUNT_ID);
		given(contractCallTransitionLogic.semanticCheck()).willReturn(semanticCheck);

		assertEquals(INVALID_ACCOUNT_ID, subject.validateSemantics(accessor));
	}

	@Test
	void hasCreateValidityIfReasonableCreate() {
		givenReasonableSemantics();
		given(spanMapAccessor.getEthTxBodyMeta(accessor)).willReturn(createTxn);
		given(semanticCheck.apply(createTxn)).willReturn(INVALID_AUTORENEW_ACCOUNT);
		given(contractCreateTransitionLogic.semanticCheck()).willReturn(semanticCheck);

		assertEquals(INVALID_AUTORENEW_ACCOUNT, subject.validateSemantics(accessor));
	}

	private void givenReasonableSemantics() {
		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);
		given(dynamicProperties.chainIdBytes()).willReturn(chainIdBytes);
		given(ethTxData.matchesChainId(chainIdBytes)).willReturn(true);
	}

	private void givenValidlyCalled(final TransactionBody txn) {
		givenOkExtantContextualAccessor();
		given(ethTxData.nonce()).willReturn(requiredNonce);
		given(spanMapAccessor.getEthTxBodyMeta(accessor)).willReturn(txn);
	}

	private void givenOkExtantContextualAccessor() {
		givenOkContextualAccessor();
		given(aliasManager.lookupIdBy(ByteString.copyFrom(callerAddress))).willReturn(callerNum);
		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);
		given(accountsLedger.get(callerId, ETHEREUM_NONCE)).willReturn(requiredNonce);
	}

	private void givenOkContextualAccessor() {
		givenContextualAccessor();
		given(spanMapAccessor.getEthTxExpansion(accessor)).willReturn(okExpansion);
		given(spanMapAccessor.getEthTxSigsMeta(accessor)).willReturn(ethTxSigs);
		given(ethTxSigs.address()).willReturn(callerAddress);
	}

	private void givenContextualAccessor() {
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private static final byte[] chainIdBytes = "0123".getBytes();
	private static final long requiredNonce = 666L;
	private static final EthTxExpansion okExpansion = new EthTxExpansion(null, OK);
	private static final EthTxExpansion notOkExpansion = new EthTxExpansion(null, INVALID_ETHEREUM_TRANSACTION);
	private static final EntityNum callerNum = EntityNum.fromLong(666);
	private static final AccountID callerId = callerNum.toGrpcAccountId();
	private static final byte[] callerAddress = callerNum.toRawEvmAddress();
	private static final TransactionBody ethTxn = TransactionBody.newBuilder()
			.setEthereumTransaction(EthereumTransactionBody.newBuilder())
			.build();
	private static final TransactionBody callTxn = TransactionBody.newBuilder()
			.setContractCall(ContractCallTransactionBody.getDefaultInstance())
			.build();
	private static final TransactionBody createTxn = TransactionBody.newBuilder()
			.setContractCreateInstance(ContractCreateTransactionBody.getDefaultInstance())
			.build();
	private static final TransactionBody nonEthTxn = TransactionBody.getDefaultInstance();
}
