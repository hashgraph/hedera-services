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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.contracts.operation.HederaExceptionalHaltReason;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.Topic;
import com.hedera.services.utils.ResponseCodeUtil;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.INVALID_SIGNATURE;
import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionRecordServiceTest {

	private static final Long GAS_USED = 1234L;
	private static final Long SBH_REFUND = 234L;
	private static final Long NON_THRESHOLD_FEE = GAS_USED - SBH_REFUND;

	@Mock private TransactionContext txnCtx;
	@Mock private TransactionProcessingResult processingResult;
	@Mock private ContractFunctionResult functionResult;

	private TransactionRecordService subject;

	@BeforeEach
	void setUp() {
		subject = new TransactionRecordService(txnCtx);
	}

	@Test
	void externalisesEvmCreateTransactionWithSuccess() {
		// given:
		givenProcessingResult(true, null);
		// when:
		subject.externaliseEvmCreateTransaction(processingResult);
		// then:
		verify(txnCtx).setStatus(SUCCESS);
		verify(txnCtx).setCreateResult(processingResult.toGrpc());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
	}

	@Test
	void externalisesEvmCallTransactionWithSuccess() {
		// given:
		givenProcessingResult(true, null);
		// when:
		subject.externaliseEvmCallTransaction(processingResult);
		// then:
		verify(txnCtx).setStatus(SUCCESS);
		verify(txnCtx).setCallResult(processingResult.toGrpc());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
	}

	@Test
	void externalisesEvmCreateTransactionWithContractRevert() {
		// given:
		givenProcessingResult(false, null);
		// when:
		subject.externaliseEvmCreateTransaction(processingResult);
		// then:
		verify(txnCtx).setStatus(CONTRACT_EXECUTION_EXCEPTION);
		verify(txnCtx).setCreateResult(processingResult.toGrpc());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
	}

	@Test
	void externalisesEvmCreateTransactionWithSelfDestruct() {
		// given:
		givenProcessingResult(false, SELF_DESTRUCT_TO_SELF);
		// when:
		subject.externaliseEvmCreateTransaction(processingResult);
		// then:
		verify(txnCtx).setStatus(OBTAINER_SAME_CONTRACT_ID);
		verify(txnCtx).setCreateResult(processingResult.toGrpc());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
	}

	@Test
	void externalisesEvmCreateTransactionWithInvalidSolidityAddress() {
		// given:
		givenProcessingResult(false, INVALID_SOLIDITY_ADDRESS);
		// when:
		subject.externaliseEvmCreateTransaction(processingResult);
		// then:
		verify(txnCtx).setStatus(ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS);
		verify(txnCtx).setCreateResult(processingResult.toGrpc());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
	}

	@Test
	void externalisesEvmCreateTransactionWithInvalidSignature() {
		// given:
		givenProcessingResult(false, INVALID_SIGNATURE);
		// when:
		subject.externaliseEvmCreateTransaction(processingResult);
		// then:
		verify(txnCtx).setStatus(ResponseCodeEnum.INVALID_SIGNATURE);
		verify(txnCtx).setCreateResult(processingResult.toGrpc());
		verify(txnCtx).addNonThresholdFeeChargedToPayer(NON_THRESHOLD_FEE);
	}

	@Test
	void updatesReceiptWithNewTotalSupply() {
		// setup:
		final var token = new Token(new Id(0, 0, 2));
		token.setTotalSupply(123L);

		// when:
		subject.includeChangesToToken(token);

		// then:
		verify(txnCtx).setNewTotalSupply(123L);
	}

	@Test
	void doesntUpdatesReceiptWithoutNewTotalSupply() {
		// setup:
		final var token = new Token(new Id(0, 0, 2));
		token.initTotalSupply(123L);

		// when:
		subject.includeChangesToToken(token);

		// then:
		verify(txnCtx, never()).setNewTotalSupply(123L);
	}

	@Test
	void updatesWithChangedRelationshipBalance() {
		// setup:
		var token = new Token(new Id(0, 0, 2));
		token.setType(TokenType.FUNGIBLE_COMMON);
		final var tokenRel = new TokenRelationship(
				token,
				new Account(new Id(0, 0, 3)));
		tokenRel.initBalance(0L);
		tokenRel.setBalance(246L);
		tokenRel.setBalance(123L);

		// when:
		subject.includeChangesToTokenRels(List.of(tokenRel));

		// then:
		verify(txnCtx).setTokenTransferLists(List.of(TokenTransferList.newBuilder()
				.setToken(TokenID.newBuilder().setTokenNum(2L))
				.addTransfers(AccountAmount.newBuilder()
						.setAmount(123L)
						.setAccountID(AccountID.newBuilder().setAccountNum(3L))
				).build()));
	}

	@Test
	void doesntUpdatesWithIfNoChangedRelationshipBalance() {
		// setup:
		final var tokenRel = new TokenRelationship(
				new Token(new Id(0, 0, 2)),
				new Account(new Id(0, 0, 3)));
		tokenRel.initBalance(123L);

		// when:
		subject.includeChangesToTokenRels(List.of(tokenRel));

		// then:
		verify(txnCtx, never()).setTokenTransferLists(any());
	}

	@Test
	void updatesReceiptOnUniqueMint() {
		// setup:
		final var supply = 1000;
		final var token = new Token(Id.DEFAULT);
		final var treasury = new Account(Id.DEFAULT);
		final var rel = token.newEnabledRelationship(treasury);
		final var ownershipTracker = mock(OwnershipTracker.class);
		token.setTreasury(treasury);
		token.setSupplyKey(TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKeyUnchecked());
		token.setType(TokenType.NON_FUNGIBLE_UNIQUE);
		token.setTotalSupply(supply);

		token.mint(ownershipTracker, rel, List.of(ByteString.copyFromUtf8("memo")), RichInstant.MISSING_INSTANT);
		subject.includeChangesToToken(token);
		verify(txnCtx).setCreated(List.of(1L));

		var change = mock(OwnershipTracker.Change.class);
		var changedTokenIdMock = mock(Id.class);
		given(changedTokenIdMock.asGrpcToken()).willReturn(IdUtils.asToken("1.2.3"));
		given(change.getNewOwner()).willReturn(Id.DEFAULT);
		given(change.getPreviousOwner()).willReturn(Id.DEFAULT);
		given(change.getSerialNumber()).willReturn(1L);
		given(ownershipTracker.isEmpty()).willReturn(false);
		given(ownershipTracker.getChanges()).willReturn(Map.of(changedTokenIdMock, List.of(change)));

		subject.includeOwnershipChanges(ownershipTracker);
		verify(ownershipTracker).getChanges();
		verify(txnCtx).setTokenTransferLists(anyList());


		for (var id : ownershipTracker.getChanges().keySet()) {
			var changeElement = ownershipTracker.getChanges().get(id);
			verify(id).asGrpcToken();
			for (var ch : changeElement) {
				verify(ch).getNewOwner();
				verify(ch).getPreviousOwner();
			}
		}
	}

	@Test
	void updatesReceiptForNewToken() {
		final var treasury = new Account(Id.DEFAULT);
		final var token = Token.fromGrpcOpAndMeta(
				Id.DEFAULT,
				TokenCreateTransactionBody.newBuilder().build(),
				treasury,
				null,
				10L);

		subject.includeChangesToToken(token);
		verify(txnCtx).setCreated(Id.DEFAULT.asGrpcToken());
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
		assertEquals( ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID, ResponseCodeUtil.getStatus(processingResult, ResponseCodeEnum.SUCCESS));
		given(processingResult.getHaltReason())
				.willReturn(Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
		assertEquals(ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS, ResponseCodeUtil.getStatus(processingResult, ResponseCodeEnum.SUCCESS));
		given(processingResult.getHaltReason())
				.willReturn(Optional.of(HederaExceptionalHaltReason.INVALID_SIGNATURE));
		assertEquals(ResponseCodeEnum.INVALID_SIGNATURE, ResponseCodeUtil.getStatus(processingResult, ResponseCodeEnum.SUCCESS));
		given(processingResult.getHaltReason())
				.willReturn(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
		assertEquals( ResponseCodeEnum.INSUFFICIENT_GAS, ResponseCodeUtil.getStatus(processingResult, ResponseCodeEnum.SUCCESS));
	}

	private void givenProcessingResult(final boolean isSuccessful, @Nullable final ExceptionalHaltReason haltReason) {
		given(processingResult.isSuccessful()).willReturn(isSuccessful);
		given(processingResult.toGrpc()).willReturn(functionResult);
		given(processingResult.getGasPrice()).willReturn(1L);
		given(processingResult.getSbhRefund()).willReturn(SBH_REFUND);
		given(processingResult.getGasUsed()).willReturn(GAS_USED);
		if (haltReason != null) {
			given(processingResult.getHaltReason()).willReturn(Optional.of(haltReason));
		}
	}
}
