package com.hedera.services.txns.contract;

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
import com.hedera.services.contracts.execution.CreateEvmTxProcessor;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.CommonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIALIZATION_FAILED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContractCreateTransitionLogicTest {
	private int gas = 33_333;
	private long customAutoRenewPeriod = 100_001L;
	private Long balance = 1_234L;
	private final AccountID proxy = AccountID.newBuilder().setAccountNum(4_321L).build();
	private final FileID bytecodeSrc = IdUtils.asFile("0.0.75231");
	private final byte[] bytecode =
			("6080604052603e8060116000396000f3fe6080604052600080fdfea265627a7a723158209dcac4560f0f51610e07" +
					"ac469a3401491cfed6040caf961950f8964fe5ca3fe264736f6c634300050b0032").getBytes();

	@Mock
	private HederaFs hfs;
	@Mock
	private OptionValidator validator;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private AccountStore accountStore;
	@Mock
	private HederaWorldState worldState;
	@Mock
	private TransactionRecordService recordServices;
	@Mock
	private CreateEvmTxProcessor evmTxProcessor;
	@Mock
	private HederaLedger hederaLedger;
	@Mock
	private ContractCreateTransactionBody transactionBody;
	@Mock
	private GlobalDynamicProperties properties;
	@Mock
	private SigImpactHistorian sigImpactHistorian;

	private ContractCreateTransitionLogic subject;

	private final Instant consensusTime = Instant.now();
	private final Account senderAccount = new Account(new Id(0, 0, 1002));
	private final Account contractAccount = new Account(new Id(0, 0, 1006));
	private TransactionBody contractCreateTxn;

	@BeforeEach
	private void setup() {
		subject = new ContractCreateTransitionLogic(
				hfs,
				txnCtx, accountStore, validator,
				worldState, recordServices, evmTxProcessor, 
                                hederaLedger, properties, sigImpactHistorian);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(contractCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void acceptsOkSyntax() {
		givenValidTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(validator.memoCheck(any())).willReturn(OK);
		given(properties.maxGas()).willReturn(gas+1);

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(contractCreateTxn));
	}

	@Test
	void providingGasOverLimitReturnsCorrectPrecheck() {
		givenValidTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(properties.maxGas()).willReturn(gas - 1);
		// expect:
		assertEquals(MAX_GAS_LIMIT_EXCEEDED,
				subject.semanticCheck().apply(contractCreateTxn));
	}

	@Test
	void rejectsInvalidAutoRenew() {
		givenValidTxnCtx(false);

		// expect:
		assertEquals(INVALID_RENEWAL_PERIOD, subject.semanticCheck().apply(contractCreateTxn));
	}

	@Test
	void rejectsNegativeAutoRenew() {
		// setup:
		customAutoRenewPeriod = -1L;

		givenValidTxnCtx();

		// expect:
		assertEquals(INVALID_RENEWAL_PERIOD, subject.semanticCheck().apply(contractCreateTxn));
	}

	@Test
	void rejectsOutOfRangeAutoRenew() {
		givenValidTxnCtx();
		// and:
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		// expect:
		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.semanticCheck().apply(contractCreateTxn));
	}

	@Test
	void rejectsNegativeGas() {
		// setup:
		gas = -1;

		givenValidTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);

		// expect:
		assertEquals(CONTRACT_NEGATIVE_GAS, subject.semanticCheck().apply(contractCreateTxn));
	}

	@Test
	void rejectsNegativeBalance() {
		// setup:
		balance = -1L;

		givenValidTxnCtx();
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		// expect:
		assertEquals(CONTRACT_NEGATIVE_VALUE, subject.semanticCheck().apply(contractCreateTxn));
	}

	@Test
	void usesContractKeyWhenAdminKeyNotSetInOp() {
		var op = ContractCreateTransactionBody.newBuilder()
				.setFileID(bytecodeSrc)
				.setInitialBalance(balance)
				.setGas(gas)
				.setConstructorParameters(copyFromUtf8("test"))
				.setProxyAccountID(proxy)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(customAutoRenewPeriod).build());

		var txn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractCreateInstance(op);
		contractCreateTxn = txn.build();

		var contractByteCodeString = new String(bytecode);
		var constructorParamsHexString = CommonUtils.hex(op.getConstructorParameters().toByteArray());
		contractByteCodeString += constructorParamsHexString;
		var expiry = RequestBuilder.getExpirationTime(consensusTime,
				Duration.newBuilder().setSeconds(customAutoRenewPeriod).build()).getSeconds();
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(hfs.exists(bytecodeSrc)).willReturn(true);
		given(hfs.cat(bytecodeSrc)).willReturn(bytecode);
		given(accessor.getTxn()).willReturn(contractCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		final var result = TransactionProcessingResult
				.successful(
						null,
						1234L,
						0L,
						124L,
						Bytes.EMPTY,
						contractAccount.getId().asEvmAddress());
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		given(worldState.newContractAddress(senderAccount.getId().asEvmAddress()))
				.willReturn(contractAccount.getId().asEvmAddress());
		given(evmTxProcessor.execute(
				senderAccount,
				contractAccount.getId().asEvmAddress(),
				gas,
				balance,
				Bytes.fromHexString(contractByteCodeString),
				txnCtx.consensusTime(),
				expiry))
				.willReturn(result);

		// when:
		subject.doStateTransition();

		// then:
		verify(evmTxProcessor).execute(senderAccount,
				contractAccount.getId().asEvmAddress(),
				gas,
				balance,
				Bytes.fromHexString(contractByteCodeString),
				txnCtx.consensusTime(),
				expiry);
		final ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		verify(hederaLedger).customizePotentiallyDeleted(eq(contractAccount.getId().asGrpcAccount()), captor.capture());
		final var standin = new MerkleAccount();
		captor.getValue().customizing(standin);
		final var accountKey = standin.getAccountKey();
		assertThat(accountKey, instanceOf(JContractIDKey.class));
		assertEquals(
				contractAccount.getId().num(),
				((JContractIDKey) accountKey).getContractID().getContractNum());
	}

	@Test
	void usesAdminKeyWhenSetInOp() {
		final var adminKey = Key.newBuilder()
				.setEd25519(copyFromUtf8("01234567890123456789012345678901"))
				.build();
		final var rcAdminKey = new JEd25519Key(adminKey.getEd25519().toByteArray());
		given(validator.attemptToDecodeOrThrow(adminKey, SERIALIZATION_FAILED))
				.willReturn(rcAdminKey);

		final var op = ContractCreateTransactionBody.newBuilder()
				.setAdminKey(adminKey)
				.setFileID(bytecodeSrc)
				.setInitialBalance(balance)
				.setGas(gas)
				.setConstructorParameters(copyFromUtf8("test"))
				.setProxyAccountID(proxy)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(customAutoRenewPeriod).build());

		final var txn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractCreateInstance(op);
		contractCreateTxn = txn.build();

		var contractByteCodeString = new String(bytecode);
		final var constructorParamsHexString = CommonUtils.hex(op.getConstructorParameters().toByteArray());
		contractByteCodeString += constructorParamsHexString;
		final var expiry = RequestBuilder.getExpirationTime(consensusTime,
				Duration.newBuilder().setSeconds(customAutoRenewPeriod).build()).getSeconds();
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(hfs.exists(bytecodeSrc)).willReturn(true);
		given(hfs.cat(bytecodeSrc)).willReturn(bytecode);
		given(accessor.getTxn()).willReturn(contractCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		final var result = TransactionProcessingResult
				.successful(
						null,
						1234L,
						0L,
						124L,
						Bytes.EMPTY,
						contractAccount.getId().asEvmAddress());
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		given(worldState.newContractAddress(senderAccount.getId().asEvmAddress()))
				.willReturn(contractAccount.getId().asEvmAddress());
		given(evmTxProcessor.execute(
				senderAccount,
				contractAccount.getId().asEvmAddress(),
				gas,
				balance,
				Bytes.fromHexString(contractByteCodeString),
				txnCtx.consensusTime(),
				expiry))
				.willReturn(result);

		// when:
		subject.doStateTransition();

		// then:
		verify(evmTxProcessor).execute(senderAccount,
				contractAccount.getId().asEvmAddress(),
				gas,
				balance,
				Bytes.fromHexString(contractByteCodeString),
				txnCtx.consensusTime(),
				expiry);
		final ArgumentCaptor<HederaAccountCustomizer> captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
		verify(hederaLedger).customizePotentiallyDeleted(eq(contractAccount.getId().asGrpcAccount()), captor.capture());
		final var standin = new MerkleAccount();
		captor.getValue().customizing(standin);
		final var accountKey = standin.getAccountKey();
		assertThat(accountKey, instanceOf(JEd25519Key.class));
		assertArrayEquals(
				adminKey.getEd25519().toByteArray(),
				accountKey.getEd25519());
	}

	@Test
	void capturesUnsuccessfulCreate() {
		// setup:
		givenValidTxnCtx();
		List<ContractID> expectedCreatedContracts = List.of(contractAccount.getId().asGrpcContract());

		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(worldState.newContractAddress(senderAccount.getId().asEvmAddress())).willReturn(
				contractAccount.getId().asEvmAddress());
		given(worldState.persistProvisionalContractCreations()).willReturn(expectedCreatedContracts);
		given(hfs.exists(bytecodeSrc)).willReturn(true);
		given(hfs.cat(bytecodeSrc)).willReturn(bytecode);
		given(accessor.getTxn()).willReturn(contractCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		var expiry = RequestBuilder.getExpirationTime(consensusTime,
				Duration.newBuilder().setSeconds(customAutoRenewPeriod).build()).getSeconds();
		var result = TransactionProcessingResult.failed(1234L, 0L,
				124L, Optional.empty(), Optional.empty());
		given(evmTxProcessor.execute(
				senderAccount,
				contractAccount.getId().asEvmAddress(),
				gas,
				balance,
				Bytes.fromHexString(new String(bytecode)),
				txnCtx.consensusTime(),
				expiry))
				.willReturn(result);

		// when:
		subject.doStateTransition();

		// then:
		verify(worldState).reclaimContractId();
		verify(worldState).persistProvisionalContractCreations();
		verify(txnCtx, never()).setCreated(contractAccount.getId().asGrpcContract());
		verify(recordServices).externaliseEvmCreateTransaction(result);
	}

	@Test
	void followsHappyPathWithOverrides() {
		// setup:
		givenValidTxnCtx();
		final var secondaryCreations = List.of(IdUtils.asContract("0.0.849321"));
		// and:
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(hfs.exists(bytecodeSrc)).willReturn(true);
		given(hfs.cat(bytecodeSrc)).willReturn(bytecode);
		given(accessor.getTxn()).willReturn(contractCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given (worldState.persistProvisionalContractCreations()).willReturn(secondaryCreations);
		final var result = TransactionProcessingResult
				.successful(
						null,
						1234L,
						0L,
						124L,
						Bytes.EMPTY,
						contractAccount.getId().asEvmAddress());
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		var expiry = RequestBuilder.getExpirationTime(consensusTime,
				Duration.newBuilder().setSeconds(customAutoRenewPeriod).build()).getSeconds();
		given(worldState.newContractAddress(senderAccount.getId().asEvmAddress()))
				.willReturn(contractAccount.getId().asEvmAddress());
		given(evmTxProcessor.execute(
				senderAccount,
				contractAccount.getId().asEvmAddress(),
				gas,
				balance,
				Bytes.fromHexString(new String(bytecode)),
				txnCtx.consensusTime(),
				expiry))
				.willReturn(result);

		// when:
		subject.doStateTransition();

		// then:
		verify(sigImpactHistorian).markEntityChanged(contractAccount.getId().num());
		verify(sigImpactHistorian).markEntityChanged(secondaryCreations.get(0).getContractNum());
		verify(worldState).newContractAddress(senderAccount.getId().asEvmAddress());
		verify(worldState).persistProvisionalContractCreations();
		verify(recordServices).externaliseEvmCreateTransaction(result);
		verify(worldState, never()).reclaimContractId();
		verify(txnCtx).setCreated(contractAccount.getId().asGrpcContract());
	}

	@Test
	void rejectsInvalidMemoInSyntaxCheck() {
		givenValidTxnCtx();
		// and:
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(validator.memoCheck(any())).willReturn(MEMO_TOO_LONG);
		given(properties.maxGas()).willReturn(gas+1);

		// expect:
		assertEquals(MEMO_TOO_LONG, subject.semanticCheck().apply(contractCreateTxn));
	}

	@Test
	void rejectsMissingBytecodeFile() {
		givenValidTxnCtx();
		given(accessor.getTxn()).willReturn(contractCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(hfs.exists(bytecodeSrc)).willReturn(false);
		// when:
		Exception exception = assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());

		// then:
		assertEquals("INVALID_FILE_ID", exception.getMessage());
	}

	@Test
	void rejectsEmptyBytecodeFile() {
		givenValidTxnCtx();
		given(accessor.getTxn()).willReturn(contractCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
		given(hfs.exists(bytecodeSrc)).willReturn(true);
		given(hfs.cat(bytecodeSrc)).willReturn(new byte[0]);

		// when:
		Exception exception = assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());

		// then:
		assertEquals("CONTRACT_FILE_EMPTY", exception.getMessage());
	}

	@Test
	void rejectSerializationFailed() {
		Key key = Key.getDefaultInstance();
		var op = ContractCreateTransactionBody.newBuilder()
				.setFileID(bytecodeSrc)
				.setInitialBalance(balance)
				.setGas(gas)
				.setAdminKey(key)
				.setProxyAccountID(proxy);

		var txn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractCreateInstance(op);
		contractCreateTxn = txn.build();
		given(accessor.getTxn()).willReturn(contractCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(validator.attemptToDecodeOrThrow(key, SERIALIZATION_FAILED)).willThrow(
				new InvalidTransactionException(SERIALIZATION_FAILED));
		// when:
		Exception exception = assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());

		// then:
		assertEquals("SERIALIZATION_FAILED", exception.getMessage());
	}

	@Test
	void throwsErrorOnInvalidBytecode() {
		given(hfs.exists(any())).willReturn(true);
		given(hfs.cat(any())).willReturn(new byte[] { 1, 2, 3, '\n' });
		given(transactionBody.getConstructorParameters()).willReturn(ByteString.EMPTY);
		// when:
		Exception exception = assertThrows(InvalidTransactionException.class,
				() -> subject.prepareCodeWithConstructorArguments(transactionBody));
		// then:
		assertEquals("ERROR_DECODING_BYTESTRING", exception.getMessage());
	}

	private void givenValidTxnCtx() {
		givenValidTxnCtx(true);
	}

	private void givenValidTxnCtx(boolean rememberAutoRenew) {
		var op = ContractCreateTransactionBody.newBuilder()
				.setFileID(bytecodeSrc)
				.setInitialBalance(balance)
				.setGas(gas)
				.setProxyAccountID(proxy);
		if (rememberAutoRenew) {
			op.setAutoRenewPeriod(Duration.newBuilder().setSeconds(customAutoRenewPeriod));
		}
		var txn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractCreateInstance(op);
		contractCreateTxn = txn.build();
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(senderAccount.getId().asGrpcAccount())
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
