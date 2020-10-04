package com.hedera.services.usage.token;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hedera.services.usage.TxnUsage;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Optional;

import static com.hedera.services.test.KeyUtils.A_KEY_LIST;
import static com.hedera.services.test.KeyUtils.C_COMPLEX_KEY;
import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.token.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
public class TokenUpdateUsageTest {
	long newKeyBytes;
	Key kycKey = KeyUtils.A_COMPLEX_KEY, oldKycKey = KeyUtils.A_KEY_LIST;
	Key adminKey = KeyUtils.A_THRESHOLD_KEY, oldAdminKey = KeyUtils.A_KEY_LIST;
	Key freezeKey = KeyUtils.A_KEY_LIST, oldFreezeKey = KeyUtils.A_KEY_LIST;
	Key supplyKey = KeyUtils.B_COMPLEX_KEY, oldSupplyKey = KeyUtils.A_KEY_LIST;
	Key wipeKey = C_COMPLEX_KEY, oldWipeKey = KeyUtils.A_KEY_LIST;
	long oldExpiry = 2_345_670L;
	long expiry = 2_345_678L;
	long oldAutoRenewPeriod = 1_234_567L;
	long now = oldExpiry - oldAutoRenewPeriod;
	long autoRenewPeriod = expiry - now;
	long delta = expiry - oldExpiry;
	String oldSymbol = "ABC";
	String symbol = "ABCDEFGH";
	String oldName = "WhyWhy";
	String name = "WhyWhyWhy";
	int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
	AccountID treasury = IdUtils.asAccount("1.2.3");
	AccountID autoRenewAccount = IdUtils.asAccount("3.2.1");
	TokenID id = IdUtils.asToken("0.0.75231");

	TokenUpdateTransactionBody op;
	TransactionBody txn;

	EstimatorFactory factory;
	TxnUsageEstimator base;
	TokenUpdateUsage subject;

	@BeforeEach
	public void setUp() throws Exception {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);

		TxnUsage.estimatorFactory = factory;
	}

	@Test
	public void createsExpectedDeltaForNewLargerKeys() {
		// setup:
		var curRb = curSize(A_KEY_LIST);
		var newRb = newRb();
		var expectedBytes = newRb + 3 * BASIC_ENTITY_ID_SIZE + 8;

		givenOp();
		// and:
		givenImpliedSubjectWithSmallerKeys();

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedBytes);
		verify(base).addRbs((newRb - curRb) * (expiry - now));
		verify(base).addRbs(
				TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 2) *
						USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	public void createsExpectedDeltaForNewSmallerKeys() {
		// setup:
		var newRb = newRb();
		var expectedBytes = newRb + 3 * BASIC_ENTITY_ID_SIZE + 8;

		givenOp();
		// and:
		givenImpliedSubjectWithLargerKeys();

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedBytes);
		verify(base, atMostOnce()).addRbs(anyLong());
	}

	@Test
	public void ignoresNewAutoRenewBytesIfAlreadyUsingAutoRenew() {
		// setup:
		var curRb = curSize(A_KEY_LIST) + BASIC_ENTITY_ID_SIZE;
		var newRb = newRb();
		var expectedBytes = newRb + 3 * BASIC_ENTITY_ID_SIZE + 8;

		givenOp();
		// and:
		givenImpliedSubjectWithSmallerKeys();
		subject.givenCurrentlyUsingAutoRenewAccount();

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedBytes);
		verify(base).addRbs((newRb - curRb) * (expiry - now));
		verify(base).addRbs(
				TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 2) *
						USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	public void understandsRemovingAutoRenew() {
		// setup:
		var curRb = curSize(A_KEY_LIST) + BASIC_ENTITY_ID_SIZE;
		var newRb = newRb() - BASIC_ENTITY_ID_SIZE;
		var expectedBytes = newRb + 3 * BASIC_ENTITY_ID_SIZE + 8;

		givenOp();
		op = op.toBuilder().setAutoRenewAccount(AccountID.getDefaultInstance()).build();
		setTxn();
		// and:
		givenImpliedSubjectWithSmallerKeys();
		subject.givenCurrentlyUsingAutoRenewAccount();

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedBytes);
		verify(base).addRbs((newRb - curRb) * (expiry - now));
		verify(base).addRbs(
				TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 2) *
						USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	private void givenImpliedSubjectWithLargerKeys() {
		givenImpliedSubjectWithKey(C_COMPLEX_KEY);
	}

	private void givenImpliedSubjectWithSmallerKeys() {
		givenImpliedSubjectWithKey(KeyUtils.A_KEY_LIST);
	}

	private void givenImpliedSubjectWithKey(Key oldKey) {
		newKeyBytes = FeeBuilder.getAccountKeyStorageSize(oldKey) * 5;
		subject = TokenUpdateUsage.newEstimate(txn, sigUsage)
				.givenCurrentExpiry(oldExpiry)
				.givenCurrentName(oldName)
				.givenCurrentSymbol(oldSymbol)
				.givenCurrentAdminKey(Optional.of(oldKey))
				.givenCurrentKycKey(Optional.of(oldKey))
				.givenCurrentSupplyKey(Optional.of(oldKey))
				.givenCurrentWipeKey(Optional.of(oldKey))
				.givenCurrentFreezeKey(Optional.of(oldKey));
	}

	private long curSize(Key oldKey) {
		return oldSymbol.length() + oldName.length()
				+ 5 * FeeBuilder.getAccountKeyStorageSize(oldKey);
	}

	private long newRb() {
		return symbol.length() + name.length()
		+ FeeBuilder.getAccountKeyStorageSize(adminKey)
				+ FeeBuilder.getAccountKeyStorageSize(kycKey)
				+ FeeBuilder.getAccountKeyStorageSize(wipeKey)
				+ FeeBuilder.getAccountKeyStorageSize(supplyKey)
				+ FeeBuilder.getAccountKeyStorageSize(freezeKey) + BASIC_ENTITY_ID_SIZE;
	}

	private void givenOp() {
		op = TokenUpdateTransactionBody.newBuilder()
				.setToken(id)
				.setExpiry(expiry)
				.setTreasury(treasury)
				.setAutoRenewAccount(autoRenewAccount)
				.setSymbol(symbol)
				.setName(name)
				.setKycKey(kycKey)
				.setAdminKey(adminKey)
				.setFreezeKey(freezeKey)
				.setSupplyKey(supplyKey)
				.setWipeKey(wipeKey)
				.build();
		setTxn();
	}

	private void setTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenUpdate(op)
				.build();
	}
}
