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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
public class ContractCallTransitionLogicTest {
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();
	private long gas = 1_234L;
	private long sent = 1_234L;

	private Instant consensusTime;
	private OptionValidator validator;
	private SequenceNumber seqNo;
	private ContractCallTransitionLogic.LegacyCaller delegate;
	private TransactionBody contractCallTxn;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	FCMap<MerkleEntityId, MerkleAccount> contracts;
	ContractCallTransitionLogic subject;

	@BeforeEach
	private void setup() {
		consensusTime = Instant.now();

		seqNo = mock(SequenceNumber.class);
		delegate = mock(ContractCallTransitionLogic.LegacyCaller.class);
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		accessor = mock(PlatformTxnAccessor.class);
		validator = mock(OptionValidator.class);
		withRubberstampingValidator();

		subject = new ContractCallTransitionLogic(delegate, validator, txnCtx, () -> seqNo, () -> contracts);
	}

	@Test
	public void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(contractCallTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void capturesBadCall() {
		// setup:
		TransactionRecord callRec = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(INVALID_CONTRACT_ID)
						.build())
				.setContractCallResult(ContractFunctionResult.newBuilder().setGasUsed(555))
				.build();

		givenValidTxnCtx();
		// and:
		given(delegate.perform(contractCallTxn, consensusTime, seqNo)).willReturn(callRec);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_CONTRACT_ID);
		verify(txnCtx).setCallResult(callRec.getContractCallResult());
	}

	@Test
	public void followsHappyPathWithOverrides() {
		// setup:
		TransactionRecord callRec = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(SUCCESS)
						.build())
				.setContractCallResult(ContractFunctionResult.newBuilder().setGasUsed(555))
				.build();

		givenValidTxnCtx();
		// and:
		given(delegate.perform(contractCallTxn, consensusTime, seqNo)).willReturn(callRec);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(SUCCESS);
		verify(txnCtx).setCallResult(callRec.getContractCallResult());
	}

	@Test
	public void acceptsOkSyntax() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(contractCallTxn));
	}

	@Test
	public void rejectsNegativeSend() {
		// setup:
		sent = -1;

		givenValidTxnCtx();

		// expect:
		assertEquals(CONTRACT_NEGATIVE_VALUE, subject.syntaxCheck().apply(contractCallTxn));
	}

	@Test
	public void rejectsNegativeGas() {
		// setup:
		gas = -1;

		givenValidTxnCtx();

		// expect:
		assertEquals(CONTRACT_NEGATIVE_GAS, subject.syntaxCheck().apply(contractCallTxn));
	}

	@Test
	public void rejectsInvalidCid() {
		givenValidTxnCtx();
		// and:
		given(validator.queryableContractStatus(target, contracts)).willReturn(CONTRACT_DELETED);

		// expect:
		assertEquals(CONTRACT_DELETED, subject.syntaxCheck().apply(contractCallTxn));
	}

	@Test
	public void translatesUnknownException() {
		givenValidTxnCtx();

		given(delegate.perform(any(), any(), any())).willThrow(IllegalStateException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	private void givenValidTxnCtx() {
		var op = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setContractCall(
						ContractCallTransactionBody.newBuilder()
								.setGas(gas)
								.setAmount(sent)
								.setContractID(target));
		contractCallTxn = op.build();
		given(accessor.getTxn()).willReturn(contractCallTxn);
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
		given(validator.queryableContractStatus(target, contracts)).willReturn(OK);
	}
}
