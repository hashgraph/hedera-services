package com.hedera.services.txns.ethereum;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hedera.services.contracts.execution.CallEvmTxProcessor;
import com.hedera.services.contracts.execution.CreateEvmTxProcessor;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.contract.ContractCallTransitionLogic;
import com.hedera.services.txns.contract.ContractCreateTransitionLogic;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.CommonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.hedera.services.txns.ethereum.TestingConstants.CHAINID_TESTNET;
import static com.hedera.services.txns.ethereum.TestingConstants.TINYBARS_2_IN_WEIBARS;
import static com.hedera.services.txns.ethereum.TestingConstants.TINYBARS_57_IN_WEIBARS;
import static com.hedera.services.txns.ethereum.TestingConstants.TRUFFLE0_ADDRESS;
import static com.hedera.services.txns.ethereum.TestingConstants.TRUFFLE0_PRIVATE_ECDSA_KEY;
import static com.hedera.services.txns.ethereum.TestingConstants.WEIBARS_IN_TINYBAR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EthereumTransactionTransitionLogicTest {
	private final Instant consensusTime = Instant.now();
	private final Account relayerAccount = new Account(new Id(0, 0, 1001));
	private final Account senderAccount = new Account(new Id(0, 0, 1002));
	private final Account contractAccount = new Account(new Id(0, 0, 1006));
	@Mock
	OptionValidator optionValidator;
	@Mock
	GlobalDynamicProperties globalDynamicProperties;
	ContractCallTransitionLogic contractCallTransitionLogic;
	ContractCreateTransitionLogic contractCreateTransitionLogic;
	EthereumTransitionLogic subject;
	private ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();
	private int gas = 1_234;
	private long sent = 1_234L;
	private byte[] chainId= CHAINID_TESTNET;
	private byte[] callData = new byte[0];
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private AccountStore accountStore;
	@Mock
	private HederaWorldState worldState;
	@Mock
	private TransactionRecordService recordService;
	@Mock
	private CallEvmTxProcessor evmTxProcessor;
	@Mock
	private CreateEvmTxProcessor createEvmTxProcessor;
	@Mock
	private GlobalDynamicProperties properties;
	@Mock
	private CodeCache codeCache;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private ExpandHandleSpanMapAccessor spanMapAccessor;
	@Mock
	TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	@Mock
	private HederaFs hfs;
	private TransactionBody ethTxTxn;
	private EthTxData ethTxData;

	@BeforeEach
	private void setup() {
		contractCallTransitionLogic = new ContractCallTransitionLogic(
				txnCtx, accountStore, worldState, recordService,
				evmTxProcessor, properties, codeCache, sigImpactHistorian, aliasManager);
		contractCreateTransitionLogic = new ContractCreateTransitionLogic(hfs, txnCtx, accountStore, optionValidator,
				worldState, recordService, createEvmTxProcessor, globalDynamicProperties, sigImpactHistorian);
		given(globalDynamicProperties.getChainId()).willReturn(0x128);
		subject = new EthereumTransitionLogic(txnCtx, spanMapAccessor, contractCallTransitionLogic,
				contractCreateTransitionLogic, recordService,
				hfs, globalDynamicProperties, aliasManager, accountsLedger);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(ethTxTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void verifyExternaliseContractResultCall() {
		// setup:
		givenValidTxnCtx();
		// and:
		given(accessor.getTxn()).willReturn(ethTxTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(accountStore.loadContract(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())))
				.willReturn(contractAccount);
		// and:
		var results = TransactionProcessingResult.successful(
				null, 1234L, 0L, 124L, Bytes.EMPTY,
				contractAccount.getId().asEvmAddress(), Map.of());
		given(evmTxProcessor.execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent, Bytes.EMPTY,
				txnCtx.consensusTime()))
				.willReturn(results);
		given(worldState.getCreatedContractIds()).willReturn(List.of());

		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);
		given(aliasManager.lookupIdBy(ByteString.copyFrom(TRUFFLE0_ADDRESS))).willReturn(
				senderAccount.getId().asEntityNum());
		// when:
		subject.doStateTransition();

		// then:
		verify(recordService).externaliseEvmCallTransaction(any());
		verify(recordService).updateFromEvmCallContext(any());
		verify(worldState).getCreatedContractIds();
		verify(txnCtx).setTargetedContract(target);
	}

	@Test
	void verifyExternaliseContractResultCreate() {
		// setup:
		target = null;
		givenValidTxnCtx();
		// and:
		given(accessor.getTxn()).willReturn(ethTxTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(worldState.newContractAddress(senderAccount.getId().asEvmAddress())).willReturn(
				contractAccount.getId().asEvmAddress());

		// and:
		var results = TransactionProcessingResult.successful(
				null, 1234L, 0L, 124L, Bytes.EMPTY,
				contractAccount.getId().asEvmAddress(), Map.of());
		given(createEvmTxProcessor.execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent,
				Bytes.EMPTY,
				txnCtx.consensusTime(), consensusTime.getEpochSecond() + Duration.ofDays(90).toSeconds()))
				.willReturn(results);
		given(worldState.getCreatedContractIds()).willReturn(List.of(contractAccount.getId().asGrpcContract()));

		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);
		given(aliasManager.lookupIdBy(ByteString.copyFrom(TRUFFLE0_ADDRESS))).willReturn(
				senderAccount.getId().asEntityNum());
		// when:
		subject.doStateTransition();

		// then:
		verify(recordService).externalizeSuccessfulEvmCreate(any(), any());
		verify(recordService).updateFromEvmCallContext(any());
		verify(worldState).getCreatedContractIds();
		verify(txnCtx).setTargetedContract(contractAccount.getId().asGrpcContract());
	}

	@Test
	void verifyProcessorCallingWithCorrectCallData() {
		// setup:
		callData = Hex.decode("fffefdfc");
		gas = 867_5309;
		sent = 100_000_000L;
		givenValidTxnCtx();

		// and:
		given(accessor.getTxn()).willReturn(ethTxTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(accountStore.loadContract(new Id(target.getShardNum(), target.getRealmNum(), target.getContractNum())))
				.willReturn(contractAccount);
		// and:
		var results = TransactionProcessingResult.successful(
				null, 1234L, 0L, 124L, Bytes.EMPTY,
				contractAccount.getId().asEvmAddress(), Map.of());
		given(evmTxProcessor.execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent,
				Bytes.fromHexString(CommonUtils.hex(callData)), txnCtx.consensusTime()))
				.willReturn(results);
		given(worldState.getCreatedContractIds()).willReturn(List.of(target));

		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);
		given(aliasManager.lookupIdBy(ByteString.copyFrom(TRUFFLE0_ADDRESS))).willReturn(
				senderAccount.getId().asEntityNum());
		// when:
		subject.doStateTransition();

		// then:
		verify(evmTxProcessor).execute(senderAccount, contractAccount.getId().asEvmAddress(), gas, sent,
				Bytes.fromHexString(CommonUtils.hex(callData)), txnCtx.consensusTime());
		verify(sigImpactHistorian).markEntityChanged(target.getContractNum());
	}

	@Test
	void successfulPreFetch() {
		givenValidTxnCtx();
		given(accessor.getTxn()).willReturn(ethTxTxn);
		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);

		subject.preFetch(accessor);

		verify(codeCache).getIfPresent(EntityNum.fromContractId(target).toEvmAddress());
		verify(spanMapAccessor).setEthTxSigsMeta(any(), any());
		verify(spanMapAccessor).setEthTxBodyMeta(any(), any());
	}

	@Test
	void codeCacheThrowingExceptionDuringGetDoesntPropagate() {
		givenValidTxnCtx();
		given(accessor.getTxn()).willReturn(ethTxTxn);
		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);

		given(codeCache.getIfPresent(any(Address.class))).willThrow(new RuntimeException("oh no"));


		// when:
		assertDoesNotThrow(() -> subject.preFetch(accessor));
	}

	@Test
	void acceptsOkSyntax() {
		givenValidTxnCtx();
		given(properties.maxGas()).willReturn(gas + 1);
		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);

		// expect:
		assertEquals(OK, subject.validateSemantics(accessor));
	}

	@Test
	void providingGasOverLimitReturnsCorrectPrecheck() {
		givenValidTxnCtx();
		given(properties.maxGas()).willReturn(gas - 1);
		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);

		// expect:
		assertEquals(MAX_GAS_LIMIT_EXCEEDED, subject.validateSemantics(accessor));
	}

	@Test
	void wrongChainId() {
		chainId = new byte[] { 1, 42 };
		givenValidTxnCtx();
		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);

		// expect:
		assertEquals(FAIL_INVALID, subject.validateSemantics(accessor));
	}

	@Test
	void expandedSignaturesValid() {
		given(properties.maxGas()).willReturn(gas + 1);
		givenValidTxnCtx();
		given(accessor.getExpandedSigStatus()).willReturn(OK);
		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);
		given(aliasManager.lookupIdBy(ByteString.copyFrom(TRUFFLE0_ADDRESS))).willReturn(
				senderAccount.getId().asEntityNum());

		// expect:
		assertEquals(OK, subject.validateSemantics(accessor));
	}

	@Test
	void expandedSignaturesInvalid() {
		givenValidTxnCtx();
		given(accessor.getExpandedSigStatus()).willReturn(OK);
		given(spanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);

		// expect:
		assertEquals(INVALID_ACCOUNT_ID, subject.validateSemantics(accessor));
	}

	private void givenValidTxnCtx() {
		var unsignedTx = new EthTxData(
				null,
				EthTxData.EthTransactionType.EIP1559,
				chainId,
				1,
				null,
				TINYBARS_2_IN_WEIBARS,
				TINYBARS_57_IN_WEIBARS,
				gas,
				target == null ? new byte[0] : EntityIdUtils.asEvmAddress(target),
				BigInteger.valueOf(sent).multiply(WEIBARS_IN_TINYBAR),
				callData,
				null,
				0,
				null,
				null,
				null
		);
		ethTxData = EthTxSigs.signMessage(unsignedTx, TRUFFLE0_PRIVATE_ECDSA_KEY);

		var op = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setEthereumTransaction(EthereumTransactionBody.newBuilder().setEthereumData(
						ByteString.copyFrom(ethTxData.encodeTx())).build());
		ethTxTxn = op.build();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(relayerAccount.getId().asGrpcAccount())
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
