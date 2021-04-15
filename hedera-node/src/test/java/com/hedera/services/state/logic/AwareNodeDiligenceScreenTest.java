package com.hedera.services.state.logic;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.inject.Inject;
import java.time.Instant;

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hedera.services.txns.diligence.DuplicateClassification.NODE_DUPLICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class AwareNodeDiligenceScreenTest {
	long submittingMember = 2L;
	String pretendMemo = "ignored";
	Instant consensusTime = Instant.ofEpochSecond(1_234_567L);
	AccountID aNodeAccount = IdUtils.asAccount("0.0.3");
	AccountID bNodeAccount = IdUtils.asAccount("0.0.4");
	TxnAccessor accessor;
	Duration validDuration = Duration.newBuilder().setSeconds(1_234_567L).build();

	@Mock
	TransactionContext txnCtx;
	@Mock
	OptionValidator validator;
	@Mock
	BackingStore<AccountID, MerkleAccount> backingAccounts;

	@Inject
	private LogCaptor logCaptor;

	@LoggingSubject
	private AwareNodeDiligenceScreen subject;

	@BeforeEach
	void setUp() {
		subject = new AwareNodeDiligenceScreen(validator, txnCtx, backingAccounts);
	}

	@Test
	void flagsMissingNodeAccount() throws InvalidProtocolBufferException {
		givenHandleCtx(aNodeAccount, aNodeAccount);
		given(txnCtx.submittingSwirldsMember()).willReturn(submittingMember);
		given(backingAccounts.contains(aNodeAccount)).willReturn(false);

		// then:
		assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
		// and:
		verify(txnCtx).setStatus(INVALID_NODE_ACCOUNT);
		// and:
		assertThat(
				logCaptor.warnLogs(),
				contains(Matchers.startsWith("Node 0.0.3 (member #2) submitted a txn w/ missing node account 0.0.3")));
	}

	@Test
	void flagsNodeSubmittingTxnWithDiffNodeAccountId() throws InvalidProtocolBufferException {
		givenHandleCtx(bNodeAccount, aNodeAccount);
		given(txnCtx.submittingSwirldsMember()).willReturn(submittingMember);
		given(backingAccounts.contains(aNodeAccount)).willReturn(true);

		// then:
		assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
		// and:
		verify(txnCtx).setStatus(INVALID_NODE_ACCOUNT);
		// and:
		assertThat(
				logCaptor.warnLogs(),
				contains(Matchers.startsWith("Node 0.0.4 (member #2) submitted a txn meant for node account 0.0.3")));
	}

	@Test
	void flagsInvalidPayerSig() throws InvalidProtocolBufferException {
		givenHandleCtx(aNodeAccount, aNodeAccount);
		given(backingAccounts.contains(aNodeAccount)).willReturn(true);
		given(txnCtx.isPayerSigKnownActive()).willReturn(false);

		// then:
		assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
		// and:
		verify(txnCtx).setStatus(INVALID_PAYER_SIGNATURE);
	}

	@Test
	void flagsNodeDuplicate() throws InvalidProtocolBufferException {
		givenHandleCtx(aNodeAccount, aNodeAccount);
		given(backingAccounts.contains(aNodeAccount)).willReturn(true);
		given(txnCtx.isPayerSigKnownActive()).willReturn(true);

		// then:
		assertTrue(subject.nodeIgnoredDueDiligence(NODE_DUPLICATE));
		// and:
		verify(txnCtx).setStatus(DUPLICATE_TRANSACTION);
	}

	@Test
	void flagsInvalidDuration() throws InvalidProtocolBufferException {
		givenHandleCtx(aNodeAccount, aNodeAccount);
		given(backingAccounts.contains(aNodeAccount)).willReturn(true);
		given(txnCtx.isPayerSigKnownActive()).willReturn(true);
		given(validator.isValidTxnDuration(validDuration.getSeconds())).willReturn(false);

		// then:
		assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
		// and:
		verify(txnCtx).setStatus(INVALID_TRANSACTION_DURATION);
	}

	@Test
	void flagsInvalidChronology() throws InvalidProtocolBufferException {
		givenHandleCtx(aNodeAccount, aNodeAccount);
		given(backingAccounts.contains(aNodeAccount)).willReturn(true);
		given(txnCtx.isPayerSigKnownActive()).willReturn(true);
		given(validator.isValidTxnDuration(validDuration.getSeconds())).willReturn(true);
		given(validator.chronologyStatus(accessor, consensusTime)).willReturn(TRANSACTION_EXPIRED);
		given(txnCtx.consensusTime()).willReturn(consensusTime);

		// then:
		assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
		// and:
		verify(txnCtx).setStatus(TRANSACTION_EXPIRED);
	}

	@Test
	void flagsInvalidMemo() throws InvalidProtocolBufferException {
		givenHandleCtx(aNodeAccount, aNodeAccount);
		given(backingAccounts.contains(aNodeAccount)).willReturn(true);
		given(txnCtx.isPayerSigKnownActive()).willReturn(true);
		given(validator.isValidTxnDuration(validDuration.getSeconds())).willReturn(true);
		given(validator.chronologyStatus(accessor, consensusTime)).willReturn(OK);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		given(validator.memoCheck(pretendMemo)).willReturn(INVALID_ZERO_BYTE_IN_STRING);

		// then:
		assertTrue(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
		// and:
		verify(txnCtx).setStatus(INVALID_ZERO_BYTE_IN_STRING);
	}

	@Test
	void doesntFlagWithAllOk() throws InvalidProtocolBufferException {
		givenHandleCtx(aNodeAccount, aNodeAccount);
		given(backingAccounts.contains(aNodeAccount)).willReturn(true);
		given(txnCtx.isPayerSigKnownActive()).willReturn(true);
		given(validator.isValidTxnDuration(validDuration.getSeconds())).willReturn(true);
		given(validator.chronologyStatus(accessor, consensusTime)).willReturn(OK);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		given(validator.memoCheck(pretendMemo)).willReturn(OK);

		// then:
		assertFalse(subject.nodeIgnoredDueDiligence(BELIEVED_UNIQUE));
		// and:
		verify(txnCtx, never()).setStatus(any());
	}

	private void givenHandleCtx(
			AccountID submittingNodeAccount,
			AccountID designatedNodeAccount
	) throws InvalidProtocolBufferException {
		given(txnCtx.submittingNodeAccount()).willReturn(submittingNodeAccount);
		accessor = accessorWith(designatedNodeAccount);
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private TxnAccessor accessorWith(AccountID designatedNodeAccount) throws InvalidProtocolBufferException {
		var bodyBytes = TransactionBody.newBuilder()
				.setMemo(pretendMemo)
				.setTransactionValidDuration(validDuration)
				.setNodeAccountID(designatedNodeAccount)
				.build()
				.toByteString();
		var signedTxn = Transaction.newBuilder()
				.setSignedTransactionBytes(SignedTransaction.newBuilder()
						.setBodyBytes(bodyBytes)
						.build()
						.toByteString())
				.build();
		return new SignedTxnAccessor(signedTxn);
	}
}
