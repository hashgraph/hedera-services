package com.hedera.services.records;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.contracts.operation.HederaExceptionalHaltReason;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Topic;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.ResponseCodeUtil;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.annotation.Nullable;
import java.util.Optional;

import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.INVALID_SIGNATURE;
import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionRecordServiceTest {
	private static final Long GAS_USED = 1234L;
	private static final Long SBH_REFUND = 234L;
	private static final Long NON_THRESHOLD_FEE = GAS_USED - SBH_REFUND;

	@Mock
	private TransactionContext txnCtx;
	@Mock
	private TransactionProcessingResult processingResult;
	@Mock
	private EvmFnResult functionResult;
	@Mock
	private EthTxData evmFnCallContext;

	private TransactionRecordService subject;

	@BeforeEach
	void setUp() {
		subject = new TransactionRecordService(txnCtx);
	}

	@Test
	void externalisesEvmCreateTransactionWithSuccess() {
		final var captor = ArgumentCaptor.forClass(EvmFnResult.class);
		final var recipient = Optional.of(EntityNum.fromLong(1234).toEvmAddress());
		final var mockAddr = Address.ALTBN128_ADD.toArrayUnsafe();
		given(processingResult.isSuccessful()).willReturn(true);
		given(processingResult.getGasPrice()).willReturn(1L);
		given(processingResult.getSbhRefund()).willReturn(SBH_REFUND);
		given(processingResult.getGasUsed()).willReturn(GAS_USED);
		given(processingResult.getRecipient()).willReturn(recipient);
		given(processingResult.getOutput()).willReturn(Bytes.fromHexStringLenient("0xabcd"));
		// when:
		subject.externalizeSuccessfulEvmCreate(processingResult, mockAddr);
		// then:
		verify(txnCtx).setStatus(SUCCESS);
		verify(txnCtx).setCreateResult(captor.capture());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
		assertArrayEquals(mockAddr, captor.getValue().getEvmAddress());
	}

	@Test
	void externalisesEvmCallTransactionWithSuccess() {
		// given:
		givenProcessingResult(true, null);
		final var recipient = Optional.of(EntityNum.fromLong(1234).toEvmAddress());
		given(processingResult.getRecipient()).willReturn(recipient);
		given(processingResult.getOutput()).willReturn(Bytes.fromHexStringLenient("0xabcd"));
		// when:
		subject.externaliseEvmCallTransaction(processingResult);
		// then:
		verify(txnCtx).setStatus(SUCCESS);
		verify(txnCtx).setCallResult(any());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
	}

	@Test
	void externalisesEvmCreateTransactionWithContractRevert() {
		// given:
		givenProcessingResult(false, null);
		// when:
		subject.externalizeUnsuccessfulEvmCreate(processingResult);
		// then:
		verify(txnCtx).setStatus(CONTRACT_EXECUTION_EXCEPTION);
		verify(txnCtx).setCreateResult(any());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
	}

	@Test
	void externalisesEvmCreateTransactionWithSelfDestruct() {
		// given:
		givenProcessingResult(false, SELF_DESTRUCT_TO_SELF);
		// when:
		subject.externalizeUnsuccessfulEvmCreate(processingResult);
		// then:
		verify(txnCtx).setStatus(OBTAINER_SAME_CONTRACT_ID);
		verify(txnCtx).setCreateResult(any());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
	}

	@Test
	void externalisesEvmCreateTransactionWithInvalidSolidityAddress() {
		// given:
		givenProcessingResult(false, INVALID_SOLIDITY_ADDRESS);
		// when:
		subject.externalizeUnsuccessfulEvmCreate(processingResult);
		// then:
		verify(txnCtx).setStatus(ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS);
		verify(txnCtx).setCreateResult(any());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
	}

	@Test
	void externalisesEvmCreateTransactionWithInvalidSignature() {
		// given:
		givenProcessingResult(false, INVALID_SIGNATURE);
		// when:
		subject.externalizeUnsuccessfulEvmCreate(processingResult);
		// then:
		verify(txnCtx).setStatus(ResponseCodeEnum.INVALID_SIGNATURE);
		verify(txnCtx).setCreateResult(any());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
	}
	
	@Test 
	void updateFromEvmCallContextRelaysToDelegate() {
		EntityId senderId = EntityId.fromIdentityCode(42);
		// when:
		subject.updateForEvmCall(evmFnCallContext, senderId);
		// then:
		verify(txnCtx).updateForEvmCall(evmFnCallContext, senderId);
	}

	@Test
	void updatesReceiptForNewTopic() {
		final var topic = new Topic(Id.DEFAULT);
		topic.setNew(true);
		subject.includeChangesToTopic(topic);
		verify(txnCtx).setCreated(Id.DEFAULT.asGrpcTopic());
	}

	@Test
	void getStatusTest() {
		var processingResult = mock(TransactionProcessingResult.class);
		given(processingResult.isSuccessful()).willReturn(true);
		assertEquals(ResponseCodeEnum.SUCCESS, ResponseCodeUtil.getStatus(processingResult, ResponseCodeEnum.SUCCESS));
		given(processingResult.isSuccessful()).willReturn(false);
		given(processingResult.getHaltReason())
				.willReturn(Optional.of(HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF));
		assertEquals(ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID,
				ResponseCodeUtil.getStatus(processingResult, ResponseCodeEnum.SUCCESS));
		given(processingResult.getHaltReason())
				.willReturn(Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
		assertEquals(ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS,
				ResponseCodeUtil.getStatus(processingResult, ResponseCodeEnum.SUCCESS));
		given(processingResult.getHaltReason())
				.willReturn(Optional.of(HederaExceptionalHaltReason.INVALID_SIGNATURE));
		assertEquals(ResponseCodeEnum.INVALID_SIGNATURE,
				ResponseCodeUtil.getStatus(processingResult, ResponseCodeEnum.SUCCESS));
		given(processingResult.getHaltReason())
				.willReturn(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
		assertEquals(ResponseCodeEnum.INSUFFICIENT_GAS,
				ResponseCodeUtil.getStatus(processingResult, ResponseCodeEnum.SUCCESS));
	}

	private void givenProcessingResult(final boolean isSuccessful, @Nullable final ExceptionalHaltReason haltReason) {
		given(processingResult.isSuccessful()).willReturn(isSuccessful);
		given(processingResult.getGasPrice()).willReturn(1L);
		given(processingResult.getSbhRefund()).willReturn(SBH_REFUND);
		given(processingResult.getGasUsed()).willReturn(GAS_USED);
		if (haltReason != null) {
			given(processingResult.getHaltReason()).willReturn(Optional.of(haltReason));
		}
	}
}