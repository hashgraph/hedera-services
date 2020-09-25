package com.hedera.services.fees.calculation;

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

import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.List;

import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;
import static com.hederahashgraph.fee.FeeBuilder.RECIEPT_STORAGE_TIME_SEC;
import static com.hederahashgraph.fee.FeeBuilder.getDefaultRBHNetworkSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hedera.services.fees.calculation.UsageEstimatorUtils.*;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class UsageEstimatorUtilsTest {
	String memo = "abcdefgh";
	TransferList transfers = TxnUtils.withAdjustments(
			IdUtils.asAccount("0.0.2"), -2,
			IdUtils.asAccount("0.0.3"), 1,
			IdUtils.asAccount("0.0.4"), 1);

	@Test
	void usesLegacyKeySbs() {
		// given:
		var keys = List.of(
				TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey(),
				TxnHandlingScenario.MISC_FILE_WACL_KT.asKey());

		// expect:
		assertEquals(keys.stream().mapToInt(FeeBuilder::getAccountKeyStorageSize).sum(), keyBytes(keys));
	}

	@Test
	public void graspsTransferListSize() {
		// expect:
		assertEquals(3 * FeeBuilder.BASIC_ACCOUNT_AMT_SIZE, transferListBytes(transfers));
	}

	@Test
	public void understandsLifetimeOnlyExtends() {
		// given:
		long oldSize = 50000, oldSecs = 100, newSize = 100_000, newSecs = 1;

		// then:
		assertEquals(5_000_000, changeInSbsUsage(oldSize, oldSecs, newSize, newSecs));
	}

	@Test
	public void understandsNoRefunds() {
		// given:
		long oldSize = 50000, oldSecs = 100, newSize = 100, newSecs = 200;

		// then:
		assertEquals(0, changeInSbsUsage(oldSize, oldSecs, newSize, newSecs));
	}

	@Test
	public void understandsSimple() {
		// given:
		long oldSize = 50, oldSecs = 100, newSize = 100, newSecs = 200;

		// then:
		assertEquals(15_000, changeInSbsUsage(oldSize, oldSecs, newSize, newSecs));
	}

	@Test
	public void understandsStartTime() {
		// given:
		long now = Instant.now().getEpochSecond();
		long then = 4688462211L;
		var txnId = TransactionID.newBuilder()
				.setTransactionValidStart(Timestamp.newBuilder().setSeconds(now));
		var txn = TransactionBody.newBuilder().setTransactionID(txnId).build();

		// when:
		long lifetime = relativeLifetime(txn, then);

		// then:
		assertEquals(then - now, lifetime);
	}

	@Test
	public void getsBaseRecordBytesForNonTransfer() {
		// given:
		TransactionBody txn = TransactionBody.newBuilder()
				.setMemo(memo)
				.build();
		// and:
		int expected = FeeBuilder.BASIC_TX_RECORD_SIZE + memo.length();

		// when:
		int actual = baseRecordBytes(txn);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void getsBaseRecordBytesForTransfer() {
		// given:
		TransactionBody txn = TransactionBody.newBuilder()
				.setMemo(memo)
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().setTransfers(transfers))
				.build();
		// and:
		int expected = FeeBuilder.BASIC_TX_RECORD_SIZE + memo.length() + FeeBuilder.BASIC_ACCOUNT_AMT_SIZE * 3;

		// when:
		int actual = baseRecordBytes(txn);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void setsBaseUsage() {
		// setup:
		int expectedVpt = 3;
		SigValueObj sigUsage = mock(SigValueObj.class);
		TransactionBody txn = TransactionBody.newBuilder()
				.setMemo(memo)
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder().setTransfers(transfers))
				.build();
		FeeComponents.Builder components = mock(FeeComponents.Builder.class);

		given(sigUsage.getSignatureSize()).willReturn(111);
		given(sigUsage.getTotalSigCount()).willReturn(expectedVpt);

		// when:
		withBaseTxnUsage(components, sigUsage, txn);

		// then:
		verify(components).setBpt(FeeBuilder.BASIC_TX_BODY_SIZE + memo.length() + 111);
		verify(components).setVpt(expectedVpt);
		verify(components).setBpr(FeeBuilder.INT_SIZE);
		verify(components).setRbh(
				nonDegenerateDiv(baseRecordBytes(txn) * RECIEPT_STORAGE_TIME_SEC, HRS_DIVISOR));
	}

	@Test
	public void avoidsDegeneracy() {
		// expect:
		assertEquals(0, nonDegenerateDiv(0, 60));
		assertEquals(1, nonDegenerateDiv(1, 60));
		assertEquals(5, nonDegenerateDiv(301, 60));
	}

	@Test
	public void emptyMemoIsZeroBytes() {
		// expect:
		assertEquals(0, memoBytesUtf8(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void memoIsExpectedSize() {
		var memo = "abcdefgh";

		// expect:
		assertEquals(
				8,
				memoBytesUtf8(TransactionBody.newBuilder().setMemo(memo).build()));
	}

	@Test
	public void allZerosAreSet() {
		// given:
		FeeComponents explicit = FeeComponents.newBuilder()
				.setBpt(0)
				.setVpt(0)
				.setRbh(0)
				.setSbh(0)
				.setGas(0)
				.setTv(0)
				.setBpr(0)
				.setSbpr(0)
				.build();
		// and:
		var zeros = zeroedComponents().build();

		// expect:
		assertEquals(explicit, zeros);
	}

	@Test
	public void doesDefaultPartitioning() {
		// given:
		FeeComponents explicit = FeeComponents.newBuilder()
				.setBpt(11)
				.setVpt(22)
				.setRbh(33)
				.setSbh(44)
				.setGas(55)
				.setTv(66)
				.setBpr(77)
				.setSbpr(88)
				.build();
		// and:
		FeeComponents fake = FeeComponents.newBuilder()
				.setBpt(11)
				.setVpt(22)
				.setRbh(33 * HRS_DIVISOR)
				.setSbh(44 * HRS_DIVISOR)
				.setGas(55)
				.setTv(66)
				.setBpr(77)
				.setSbpr(88)
				.build();
		// and:
		FeeData legacy = FileFeeBuilder.getFeeDataMatrices(fake, 5, getDefaultRBHNetworkSize());

		// when:
		FeeData partitioned = defaultPartitioning(explicit, 5);

		// then:
		assertEquals(legacy, partitioned);
	}
}
