package com.hedera.services.queries.validation;

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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class QueryFeeCheckTest {
	AccountID aMissing = asAccount("1.2.3");
	AccountID aRich = asAccount("0.0.2");
	AccountID aNode = asAccount("0.0.3");
	AccountID anotherNode = asAccount("0.0.4");
	AccountID aBroke = asAccount("0.0.13257");
	AccountID aQueryPayer = asAccount("0.0.13258");
	AccountID aTestPayer = asAccount("0.0.13259");

	TransactionID txnId = TransactionID.newBuilder().setAccountID(aRich).build();
	long feeRequired = 1234L;

	long aLittle = 2L, aLot = Long.MAX_VALUE - 1L, aFew = 100L;
	MerkleAccount broke, rich, testPayer, queryPayer;
	MerkleEntityId missingKey = MerkleEntityId.fromAccountId(aMissing);
	MerkleEntityId richKey = MerkleEntityId.fromAccountId(aRich);
	MerkleEntityId brokeKey = MerkleEntityId.fromAccountId(aBroke);
	MerkleEntityId nodeKey = MerkleEntityId.fromAccountId(aNode);
	MerkleEntityId anotherNodeKey = MerkleEntityId.fromAccountId(anotherNode);

	MerkleEntityId queryPayerKey = MerkleEntityId.fromAccountId(aQueryPayer);
	MerkleEntityId testPayerKey = MerkleEntityId.fromAccountId(aTestPayer);

	FCMap<MerkleEntityId, MerkleAccount> accounts;

	QueryFeeCheck subject;

	@BeforeEach
	private void setup() {
		broke = mock(MerkleAccount.class);
		given(broke.getBalance()).willReturn(aLittle);
		rich = mock(MerkleAccount.class);
		given(rich.getBalance()).willReturn(aLot);


		broke = mock(MerkleAccount.class);
		given(broke.getBalance()).willReturn(aLittle);
		rich = mock(MerkleAccount.class);
		given(rich.getBalance()).willReturn(aLot);

		testPayer = mock(MerkleAccount.class);
		given(testPayer.getBalance()).willReturn(aFew);
		queryPayer = mock(MerkleAccount.class);
		given(queryPayer.getBalance()).willReturn(aLot);

		accounts = mock(FCMap.class);
		given(accounts.get(argThat(missingKey::equals))).willReturn(null);
		given(accounts.get(argThat(richKey::equals))).willReturn(rich);
		given(accounts.get(argThat(brokeKey::equals))).willReturn(broke);
		given(accounts.get(argThat(testPayerKey::equals))).willReturn(testPayer);
		given(accounts.get(argThat(queryPayerKey::equals))).willReturn(queryPayer);

		given(accounts.containsKey(argThat(missingKey::equals))).willReturn(false);
		given(accounts.containsKey(argThat(richKey::equals))).willReturn(true);
		given(accounts.containsKey(argThat(brokeKey::equals))).willReturn(true);
		given(accounts.containsKey(argThat(nodeKey::equals))).willReturn(true);
		given(accounts.containsKey(argThat(anotherNodeKey::equals))).willReturn(true);
		given(accounts.containsKey(argThat(testPayerKey::equals))).willReturn(true);
		given(accounts.containsKey(argThat(testPayerKey::equals))).willReturn(true);

		subject = new QueryFeeCheck(() -> accounts);
	}

	@Test
	public void rejectsEmptyTransfers() {
		// expect:
		assertEquals(INVALID_ACCOUNT_AMOUNTS, subject.transfersPlausibility(null));
		assertEquals(INVALID_ACCOUNT_AMOUNTS, subject.transfersPlausibility(Collections.emptyList()));
	}

	@Test
	public void acceptsSufficientFee() {
		// expect:
		assertEquals(
				OK,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle),
								adjustmentWith(aNode, aLittle)),
						aLittle - 1, aNode));
	}

	@Test
	public void rejectsWrongRecipient() {
		// expect:
		assertEquals(
				INVALID_RECEIVING_NODE_ACCOUNT,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle),
								adjustmentWith(aBroke, aLittle)),
						aLittle - 1, aNode));
	}

	@Test
	public void rejectsWhenNodeIsMissing() {
		// expect:
		assertEquals(
				INVALID_RECEIVING_NODE_ACCOUNT,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle * 2),
								adjustmentWith(aBroke, aLittle),
								adjustmentWith(aBroke, aLittle)),
						aLittle - 1, aNode));
	}

	@Test
	public void allowsMultipleRecipients() {
		// expect:
		assertEquals(
				OK,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle * 2),
								adjustmentWith(aBroke, aLittle),
								adjustmentWith(aNode, aLittle)),
						aLittle - 1, aNode));
	}

	@Test
	public void rejectsInsufficientNodePayment() {
		// expect:
		assertEquals(
				INSUFFICIENT_TX_FEE,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle * 2),
								adjustmentWith(aBroke, aLittle + aLittle / 2),
								adjustmentWith(aNode, aLittle / 2)),
						aLittle , aNode));
	}

	@Test
	public void rejectsInsufficientFee() {
		// expect:
		assertEquals(
				INSUFFICIENT_TX_FEE,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, -aLittle),
								adjustmentWith(aBroke, aLittle)),
						aLittle + 1, aNode));
	}

	@Test
	public void filtersOnBasicImplausibility() {
		// expect:
		assertEquals(
				INVALID_ACCOUNT_AMOUNTS,
				subject.nodePaymentValidity(
						transfersWith(
								adjustmentWith(aRich, aLot),
								adjustmentWith(aBroke, aLittle)), 0L, aNode));
	}

	@Test
	public void rejectsOverflowingTransfer() {
		// expect:
		assertEquals(
				INVALID_ACCOUNT_AMOUNTS,
				subject.transfersPlausibility(
						transfersWith(
								adjustmentWith(aRich, aLot),
								adjustmentWith(aBroke, aLittle))));
	}

	@Test
	public void rejectsNonNetTransfer() {
		// expect:
		assertEquals(
				INVALID_ACCOUNT_AMOUNTS,
				subject.transfersPlausibility(
						transfersWith(
								adjustmentWith(aRich, aLittle),
								adjustmentWith(aBroke, aLittle))));
	}

	@Test
	public void catchesBadEntry() {
		// expect:
		assertEquals(
				ACCOUNT_ID_DOES_NOT_EXIST,
				subject.transfersPlausibility(
						transfersWith(
								adjustmentWith(aRich, -aLittle),
								adjustmentWith(aMissing, 0),
								adjustmentWith(aBroke, aLittle))));
	}

	@Test
	public void rejectsMinValue() {
		// given:
		var adjustment = adjustmentWith(aRich, Long.MIN_VALUE);

		// when:
		var status = subject.adjustmentPlausibility(adjustment);

		// then:
		assertEquals(INVALID_ACCOUNT_AMOUNTS, status);
	}

	@Test
	public void nonexistentSenderHasNoBalance() {
		// given:
		var adjustment = adjustmentWith(aMissing, -aLittle);

		// when:
		var status = subject.adjustmentPlausibility(adjustment);

		// then:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, status);
	}

	@Test
	public void brokePayerRejected() {
		// given:
		var adjustment = adjustmentWith(aBroke, -aLot);

		// when:
		var status = subject.adjustmentPlausibility(adjustment);

		// then:
		assertEquals(INSUFFICIENT_PAYER_BALANCE, status);
	}

	@Test
	public void missingReceiverRejected() {
		// given:
		var adjustment = adjustmentWith(aMissing, aLot);

		// when:
		var status = subject.adjustmentPlausibility(adjustment);

		// then:
		assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, status);
	}

	@Test
	public void validateQueryPaymentSucceeds() {
		// setup:
		long amount = 8;
		// given :
		TransactionBody body = getPaymentTxnBody(amount, null);

		// then:
		assertEquals(body.getTransactionID().getAccountID(), aRich);
		assertTrue(checkPayerInTransferList(body, aRich));
		assertEquals(subject.validateQueryPaymentTransfers(body), OK);
	}

	@Test
	public void paymentFailsWithQueryPayerBalance() {
		// setup:
		long amount = 5000L;
		// given :
		TransferList transList = TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aBroke).setAmount(-1 * amount))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount))
				.build();
		TransactionBody body = TransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().setTransfers(transList))
				.setTransactionID(txnId)
				.setNodeAccountID(aNode)
				.setTransactionFee(feeRequired).build();

		// then:
		assertEquals(body.getTransactionID().getAccountID(), aRich);
		assertFalse(checkPayerInTransferList(body, aRich));
		assertEquals(INSUFFICIENT_PAYER_BALANCE, subject.validateQueryPaymentTransfers(body));
	}

	@Test
	public void paymentFailsWithBrokenPayer() {
		// setup:
		long amount = 5000L;
		// given :
		TransferList transList = TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aBroke).setAmount(-1 * amount))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount))
				.build();
		TransactionBody body = TransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().setTransfers(transList))
				.setTransactionID(TransactionID.newBuilder().setAccountID(aBroke).build())
				.setNodeAccountID(aNode)
				.setTransactionFee(feeRequired).build();

		// then:
		assertEquals(body.getTransactionID().getAccountID(), aBroke);
		assertTrue(checkPayerInTransferList(body, aBroke));
		assertEquals(INSUFFICIENT_PAYER_BALANCE, subject.validateQueryPaymentTransfers(body));
	}

	@Test
	public void queryPaymentMultiPayerMultiNodeSucceeds() {
		// setup:
		long amount = 200L;

		// given :
		TransferList transList = TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aRich).setAmount(-1 * amount/4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aTestPayer).setAmount(-1 * amount/4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aQueryPayer).setAmount(-1 * amount/2))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount/2))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(anotherNode).setAmount(amount/2))
				.build();
		TransactionBody body = getPaymentTxnBody(amount, transList);

		// then:
		assertEquals(3, body.getCryptoTransfer().getTransfers()
				.getAccountAmountsList().stream()
				.filter(aa -> aa.getAmount() < 0)
				.collect(Collectors.toList()).size());
		assertEquals(OK, subject.validateQueryPaymentTransfers(body));
	}

	@Test
	public void queryPaymentMultiTransferFails() {
		// setup:
		long amount = 200L;

		// given :
		TransferList transList = TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aRich).setAmount(-1 * amount/4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aBroke).setAmount(-1 * amount/4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aQueryPayer).setAmount(-1 * amount/4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aTestPayer).setAmount(-1 * amount/4))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount))
				.build();
		TransactionBody body = getPaymentTxnBody(amount, transList);

		// then:
		assertEquals(4, body.getCryptoTransfer().getTransfers()
				.getAccountAmountsList().stream()
				.filter(aa -> aa.getAmount() < 0)
				.collect(Collectors.toList()).size());
		assertEquals(INSUFFICIENT_PAYER_BALANCE, subject.validateQueryPaymentTransfers(body));
	}

	private AccountAmount adjustmentWith(AccountID id, long amount) {
		return AccountAmount.newBuilder()
				.setAccountID(id)
				.setAmount(amount)
				.build();
	}

	private List<AccountAmount> transfersWith(
			AccountAmount a,
			AccountAmount b
	) {
		return List.of(a, b);
	}

	private List<AccountAmount> transfersWith(
			AccountAmount a,
			AccountAmount b,
			AccountAmount c
	) {
		return List.of(a, b, c);
	}

	private TransactionBody getPaymentTxnBody(long amount, TransferList transferList) {
		TransferList transList = TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aRich).setAmount(-1 * amount))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(aNode).setAmount(amount))
				.build();
		if (transferList != null) {
			transList = transferList;
		}
		TransactionBody body = TransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().setTransfers(transList))
				.setTransactionID(txnId)
				.setNodeAccountID(aNode)
				.setTransactionFee(feeRequired)
				.build();
		return body;
	}

	private boolean checkPayerInTransferList(TransactionBody body, AccountID payer){
		AccountAmount  payerTransfer = body.getCryptoTransfer().
				getTransfers().
				getAccountAmountsList().
				stream().filter(aa -> aa.getAccountID() == payer).findAny().orElse(null);
		return payerTransfer != null ? true : false;

	}
}
