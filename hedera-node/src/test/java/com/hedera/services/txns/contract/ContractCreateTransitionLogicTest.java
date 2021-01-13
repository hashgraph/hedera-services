package com.hedera.services.txns.contract;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.HederaFs;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
public class ContractCreateTransitionLogicTest {
	private long gas = 33_333L;
	private long customAutoRenewPeriod = 100_001L;
	private Long balance = 1_234L;
	final private AccountID proxy = AccountID.newBuilder().setAccountNum(4_321L).build();
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private ContractID created = ContractID.newBuilder().setContractNum(9_999L).build();
	final private FileID bytecodeSrc = IdUtils.asFile("0.0.75231");
	final private byte[] bytecode = "NotReallyEvmBytecode".getBytes();

	private Instant consensusTime;
	private HederaFs hfs;
	private SequenceNumber seqNo;
	private OptionValidator validator;
	private ContractCreateTransitionLogic.LegacyCreator delegate;
	private TransactionBody contractCreateTxn;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	private ContractCreateTransitionLogic subject;

	@BeforeEach
	private void setup() {
		consensusTime = Instant.now();

		delegate = mock(ContractCreateTransitionLogic.LegacyCreator.class);
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		hfs = mock(HederaFs.class);
		accessor = mock(PlatformTxnAccessor.class);
		validator = mock(OptionValidator.class);
		withRubberstampingValidator();
		seqNo = mock(SequenceNumber.class);

		subject = new ContractCreateTransitionLogic(hfs, delegate, () -> seqNo, validator, txnCtx);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(contractCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void acceptsOkSyntax() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(contractCreateTxn));
	}

	@Test
	public void rejectsInvalidAutoRenew() {
		givenValidTxnCtx(false);

		// expect:
		assertEquals(INVALID_RENEWAL_PERIOD, subject.syntaxCheck().apply(contractCreateTxn));
	}

	@Test
	public void rejectsNegativeBalance() {
		// setup:
		balance = -1L;

		givenValidTxnCtx();

		// expect:
		assertEquals(CONTRACT_NEGATIVE_VALUE, subject.syntaxCheck().apply(contractCreateTxn));
	}

	@Test
	public void rejectsNegativeGas() {
		// setup:
		gas = -1L;

		givenValidTxnCtx();

		// expect:
		assertEquals(CONTRACT_NEGATIVE_GAS, subject.syntaxCheck().apply(contractCreateTxn));
	}

	@Test
	public void rejectsNegativeAutoRenew() {
		// setup:
		customAutoRenewPeriod = -1L;

		givenValidTxnCtx();

		// expect:
		assertEquals(INVALID_RENEWAL_PERIOD, subject.syntaxCheck().apply(contractCreateTxn));
	}

	@Test
	public void rejectsOutOfRangeAutoRenew() {
		givenValidTxnCtx();
		// and:
		given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

		// expect:
		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, subject.syntaxCheck().apply(contractCreateTxn));
	}

	@Test
	public void capturesUnsuccessfulCreate() {
		// setup:
		TransactionRecord creation = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(CONTRACT_EXECUTION_EXCEPTION)
						.build())
				.setContractCreateResult(ContractFunctionResult.newBuilder().setGasUsed(555))
				.build();

		givenValidTxnCtx();
		// and:
		given(hfs.exists(bytecodeSrc)).willReturn(true);
		given(hfs.cat(bytecodeSrc)).willReturn(bytecode);
		given(delegate.perform(contractCreateTxn, consensusTime, bytecode, seqNo)).willReturn(creation);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setCreateResult(creation.getContractCreateResult());
		verify(txnCtx).setStatus(CONTRACT_EXECUTION_EXCEPTION);
		verify(txnCtx, never()).setCreated(any(ContractID.class));
	}

	@Test
	public void followsHappyPathWithOverrides() {
		// setup:
		TransactionRecord creation = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(SUCCESS)
						.setContractID(created)
						.build())
				.setContractCreateResult(ContractFunctionResult.newBuilder().setGasUsed(555))
				.build();

		givenValidTxnCtx();
		// and:
		given(hfs.exists(bytecodeSrc)).willReturn(true);
		given(hfs.cat(bytecodeSrc)).willReturn(bytecode);
		given(delegate.perform(contractCreateTxn, consensusTime, bytecode, seqNo)).willReturn(creation);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setCreateResult(creation.getContractCreateResult());
		verify(txnCtx).setCreated(created);
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void rejectsInvalidMemoInSyntaxCheck() {
		givenValidTxnCtx();
		// and:
		given(validator.isValidEntityMemo(any())).willReturn(false);

		// expect:
		assertEquals(MEMO_TOO_LONG, subject.syntaxCheck().apply(contractCreateTxn));
	}

	@Test
	public void rejectsMissingBytecodeFile() {
		givenValidTxnCtx();
		given(hfs.exists(bytecodeSrc)).willReturn(false);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_FILE_ID);
	}

	@Test
	public void rejectsEmptyBytecodeFile() {
		givenValidTxnCtx();
		given(hfs.exists(bytecodeSrc)).willReturn(true);
		given(hfs.cat(bytecodeSrc)).willReturn(new byte[0]);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(CONTRACT_FILE_EMPTY);
	}

	@Test
	public void translatesUnknownException() {
		givenValidTxnCtx();
		given(hfs.exists(bytecodeSrc)).willReturn(true);
		given(hfs.cat(bytecodeSrc)).willReturn(bytecode);
		given(delegate.perform(any(), any(), any(), any())).willThrow(IllegalStateException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
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
		given(accessor.getTxn()).willReturn(contractCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private void withRubberstampingValidator() {
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(validator.isValidEntityMemo(any())).willReturn(true);
	}
}
