package com.hedera.services.utils.accessors;

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

import com.google.protobuf.BoolValue;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAdjustAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAdjustAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoAllowanceAccessorTest {
	@Mock
	AliasManager aliasManager;

	private final TokenID token1 = asToken("0.0.100");
	private final TokenID token2 = asToken("0.0.200");
	private final AccountID owner = asAccount("0.0.123");
	private final AccountID spender = asAccount("0.0.234");
	private final AccountID aliasedOwner = asAccountWithAlias("yevarra manaki custody!!");
	private final AccountID aliasedSpender = asAccountWithAlias("ee oollo maname khiladi!!");
	private final EntityNum spenderNum = EntityNum.fromAccountId(spender);
	private final EntityNum ownerNum = EntityNum.fromAccountId(owner);
	private final long amount = 100L;
	private final long adjust = -15L;


	private CryptoAllowanceAccessor subject;
	private SwirldTransaction cryptoApproveAllowanceTxn;
	private SwirldTransaction cryptoAdjustAllowanceTxn;

	@Test
	void fetchesApproveDataAsExpected() throws InvalidProtocolBufferException {
		setUpApprovalWith(owner, spender);
		when(aliasManager.unaliased(owner)).thenReturn(ownerNum);
		when(aliasManager.unaliased(spender)).thenReturn(spenderNum);

		subject = new CryptoAllowanceAccessor(cryptoApproveAllowanceTxn, aliasManager);

		validateApprove();
	}

	@Test
	void fetchesAdjustDataAsExpected() throws InvalidProtocolBufferException {
		setUpAdjustWith(owner, spender);
		when(aliasManager.unaliased(owner)).thenReturn(ownerNum);
		when(aliasManager.unaliased(spender)).thenReturn(spenderNum);

		subject = new CryptoAllowanceAccessor(cryptoAdjustAllowanceTxn, aliasManager);

		validateAdjust();
	}

	@Test
	void fetchesApproveAliasedDataAsExpected() throws InvalidProtocolBufferException {
		setUpApprovalWith(aliasedOwner, aliasedSpender);
		when(aliasManager.unaliased(aliasedOwner)).thenReturn(ownerNum);
		when(aliasManager.unaliased(aliasedSpender)).thenReturn(spenderNum);

		subject = new CryptoAllowanceAccessor(cryptoApproveAllowanceTxn, aliasManager);

		validateApprove();
	}

	@Test
	void fetchesAdjustAliasedDataAsExpected() throws InvalidProtocolBufferException {
		setUpAdjustWith(aliasedOwner, aliasedSpender);
		when(aliasManager.unaliased(aliasedOwner)).thenReturn(ownerNum);
		when(aliasManager.unaliased(aliasedSpender)).thenReturn(spenderNum);

		subject = new CryptoAllowanceAccessor(cryptoAdjustAllowanceTxn, aliasManager);

		validateAdjust();
	}

	@Test
	void fetchesApproveMissingAliasAsExpected() throws InvalidProtocolBufferException {
		setUpApprovalWith(aliasedOwner, aliasedSpender);
		when(aliasManager.unaliased(aliasedSpender)).thenReturn(MISSING_NUM);

		subject = new CryptoAllowanceAccessor(cryptoApproveAllowanceTxn, aliasManager);


		assertEquals(MISSING_NUM.longValue(), subject.getCryptoAllowances().get(0).getSpender().getAccountNum());
		assertEquals(MISSING_NUM.longValue(), subject.getNftAllowances().get(0).getSpender().getAccountNum());
		assertEquals(MISSING_NUM.longValue(), subject.getNftAllowances().get(1).getSpender().getAccountNum());
		assertEquals(MISSING_NUM.longValue(), subject.getTokenAllowances().get(0).getSpender().getAccountNum());

	}

	@Test
	void fetchesAdjustMissingAliasAsExpected() throws InvalidProtocolBufferException {
		setUpAdjustWith(aliasedOwner, aliasedSpender);
		when(aliasManager.unaliased(aliasedSpender)).thenReturn(MISSING_NUM);

		subject = new CryptoAllowanceAccessor(cryptoAdjustAllowanceTxn, aliasManager);


		assertEquals(MISSING_NUM.longValue(), subject.getCryptoAllowances().get(0).getSpender().getAccountNum());
		assertEquals(MISSING_NUM.longValue(), subject.getNftAllowances().get(0).getSpender().getAccountNum());
		assertEquals(MISSING_NUM.longValue(), subject.getNftAllowances().get(1).getSpender().getAccountNum());
		assertEquals(MISSING_NUM.longValue(), subject.getTokenAllowances().get(0).getSpender().getAccountNum());

	}

	private void validateApprove() {
		assertEquals(CryptoApproveAllowance, subject.getFunction());
		assertEquals(owner, subject.getOwner());
		assertEquals(spender, subject.getCryptoAllowances().get(0).getSpender());
		assertEquals(amount, subject.getCryptoAllowances().get(0).getAmount());
		assertEquals(token1, subject.getTokenAllowances().get(0).getTokenId());
		assertEquals(spender, subject.getTokenAllowances().get(0).getSpender());
		assertEquals(amount, subject.getTokenAllowances().get(0).getAmount());
		assertEquals(token1, subject.getNftAllowances().get(0).getTokenId());
		assertEquals(spender, subject.getNftAllowances().get(0).getSpender());
		assertTrue(subject.getNftAllowances().get(0).getApprovedForAll().getValue());
		assertEquals(token2, subject.getNftAllowances().get(1).getTokenId());
		assertEquals(spender, subject.getNftAllowances().get(1).getSpender());
		assertFalse(subject.getNftAllowances().get(1).getApprovedForAll().getValue());
		assertEquals(List.of(1L, 2L), subject.getNftAllowances().get(1).getSerialNumbersList());
	}

	private void validateAdjust() {
		assertEquals(CryptoAdjustAllowance, subject.getFunction());
		assertEquals(owner, subject.getOwner());
		assertEquals(spender, subject.getCryptoAllowances().get(0).getSpender());
		assertEquals(adjust, subject.getCryptoAllowances().get(0).getAmount());
		assertEquals(token1, subject.getTokenAllowances().get(0).getTokenId());
		assertEquals(spender, subject.getTokenAllowances().get(0).getSpender());
		assertEquals(adjust, subject.getTokenAllowances().get(0).getAmount());
		assertEquals(token1, subject.getNftAllowances().get(0).getTokenId());
		assertEquals(spender, subject.getNftAllowances().get(0).getSpender());
		assertFalse(subject.getNftAllowances().get(0).getApprovedForAll().getValue());
		assertEquals(token2, subject.getNftAllowances().get(1).getTokenId());
		assertEquals(spender, subject.getNftAllowances().get(1).getSpender());
		assertFalse(subject.getNftAllowances().get(1).getApprovedForAll().getValue());
		assertEquals(List.of(-2L), subject.getNftAllowances().get(1).getSerialNumbersList());
	}

	private void setUpApprovalWith(final AccountID owner, final AccountID spender) {
		final var cryptoAllowance = CryptoAllowance.newBuilder().setSpender(spender).setAmount(
				amount).build();
		final var tokenAllowance = TokenAllowance.newBuilder().setSpender(spender).setAmount(
				amount).setTokenId(token1).build();
		final var nftAllowance1 = NftAllowance.newBuilder()
				.setSpender(spender)
				.setTokenId(token1).setApprovedForAll(BoolValue.of(true)).build();
		final var nftAllowance2 = NftAllowance.newBuilder()
				.setSpender(spender)
				.setTokenId(token2).setApprovedForAll(BoolValue.of(false))
				.addAllSerialNumbers(List.of(1L, 2L)).build();

		final var approveTxnBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(owner).build())
				.setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
						.addCryptoAllowances(cryptoAllowance)
						.addTokenAllowances(tokenAllowance)
						.addNftAllowances(nftAllowance1)
						.addNftAllowances(nftAllowance2)
						.build())
				.build();
		cryptoApproveAllowanceTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(approveTxnBody.toByteString())
				.build().toByteArray());
	}

	void setUpAdjustWith(final AccountID owner, final AccountID spender) {
		final var cryptoAllowance = CryptoAllowance.newBuilder().setSpender(spender).setAmount(
				adjust).build();
		final var tokenAllowance = TokenAllowance.newBuilder().setSpender(spender).setAmount(
				adjust).setTokenId(token1).build();
		final var nftAllowance1 = NftAllowance.newBuilder()
				.setSpender(spender)
				.setTokenId(token1).setApprovedForAll(BoolValue.of(false)).build();
		final var nftAllowance2 = NftAllowance.newBuilder()
				.setSpender(spender)
				.setTokenId(token2).setApprovedForAll(BoolValue.of(false))
				.addAllSerialNumbers(List.of(-2L)).build();

		final var adjustTxnBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(owner).build())
				.setCryptoAdjustAllowance(CryptoAdjustAllowanceTransactionBody.newBuilder()
						.addCryptoAllowances(cryptoAllowance)
						.addTokenAllowances(tokenAllowance)
						.addNftAllowances(nftAllowance1)
						.addNftAllowances(nftAllowance2)
						.build())
				.build();
		cryptoAdjustAllowanceTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(adjustTxnBody.toByteString())
				.build().toByteArray());
	}
}
