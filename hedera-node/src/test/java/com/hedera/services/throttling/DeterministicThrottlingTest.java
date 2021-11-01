package com.hedera.services.throttling;

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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.services.throttles.BucketThrottle;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.SerdeUtils;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
class DeterministicThrottlingTest {
	private final int n = 2;
	private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 123);
	private final ThrottleReqOpsScaleFactor nftScaleFactor = ThrottleReqOpsScaleFactor.from("5:2");

	@Mock
	private TxnAccessor accessor;
	@Mock
	private ThrottleReqsManager manager;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private GasLimitDeterministicThrottle gasLimitDeterministicThrottle;
	@Mock
	private TransactionBody transactionBody;
	@Mock
	private ContractCallTransactionBody callTransactionBody;
	@Mock
	private ContractCreateTransactionBody createTransactionBody;
	@Mock
	private Query query;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private DeterministicThrottling subject;

	@BeforeEach
	void setUp() {
		subject = new DeterministicThrottling(() -> n, dynamicProperties);
	}

	@Test
	void worksAsExpectedForKnownQueries() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		// when:
		subject.rebuildFor(defs);
		// and:
		var ans = subject.shouldThrottleQuery(CryptoGetAccountBalance, consensusNow, query);
		var throttlesNow = subject.activeThrottlesFor(CryptoGetAccountBalance);
		// and:
		var dNow = throttlesNow.get(0);

		// then:
		assertFalse(ans);
		assertEquals(BucketThrottle.capacityUnitsPerTxn(), dNow.used());
	}

	@Test
	void worksAsExpectedForUnknownQueries() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		// when:
		subject.rebuildFor(defs);

		// then:
		assertTrue(subject.shouldThrottleQuery(ContractCallLocal, consensusNow, query));
	}

	@Test
	void shouldThrottleByGasAndTotalAllowedGasPerSecNotSetOrZero() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");
		given(dynamicProperties.shouldThrottleByGas()).willReturn(true);

		// when:
		subject.rebuildFor(defs);

		// then:
		assertEquals(0L, gasLimitDeterministicThrottle.getCapacity());
		assertThat(logCaptor.errorLogs(),
				contains("ThrottleByGas global dynamic property is set to true but totalAllowedGasPerSecFrontend is not set in throttles.json or is set to 0.",
						"ThrottleByGas global dynamic property is set to true but totalAllowedGasPerSecConsensus is not set in throttles.json or is set to 0."));
	}

	@Test
	void managerBehavesAsExpectedForFungibleMint() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		givenMintWith(0);

		// when:
		subject.rebuildFor(defs);
		// and:
		var ans = subject.shouldThrottleTxn(accessor, consensusNow);
		var throttlesNow = subject.activeThrottlesFor(TokenMint);
		// and:
		var aNow = throttlesNow.get(0);

		// then:
		assertFalse(ans);
		assertEquals(10 * BucketThrottle.capacityUnitsPerTxn(), aNow.used());
	}

	@Test
	void managerBehavesAsExpectedForNftMint() throws IOException {
		// setup:
		final var numNfts = 3;
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		givenMintWith(numNfts);
		given(dynamicProperties.nftMintScaleFactor()).willReturn(nftScaleFactor);

		// when:
		subject.rebuildFor(defs);
		// and:
		var ans = subject.shouldThrottleTxn(accessor, consensusNow);
		var throttlesNow = subject.activeThrottlesFor(TokenMint);
		// and:
		var aNow = throttlesNow.get(0);

		// then:
		assertFalse(ans);
		assertEquals(75 * BucketThrottle.capacityUnitsPerTxn(), aNow.used());
	}

	@Test
	void managerBehavesAsExpectedForMultiBucketOp() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		givenFunction(ContractCall);

		// when:
		subject.rebuildFor(defs);
		// and:
		var ans = subject.shouldThrottleTxn(accessor, consensusNow);
		var throttlesNow = subject.activeThrottlesFor(ContractCall);
		// and:
		var aNow = throttlesNow.get(0);
		var bNow = throttlesNow.get(1);

		// then:
		assertFalse(ans);
		assertEquals(2500 * BucketThrottle.capacityUnitsPerTxn(), aNow.used());
		assertEquals(BucketThrottle.capacityUnitsPerTxn(), bNow.used());
	}

	@Test
	void logsErrorOnBadBucketButDoesntFail() throws IOException {
		// given:
		var defs = SerdeUtils.pojoDefs("bootstrap/insufficient-capacity-throttles.json");

		// expect:
		assertDoesNotThrow(() -> subject.rebuildFor(defs));
		// and:
		assertEquals(1, subject.activeThrottlesFor(CryptoGetAccountBalance).size());
		// and:
		assertThat(logCaptor.errorLogs(),
				contains("When constructing bucket 'A' from state: NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION :: " +
						"Bucket A contains an unsatisfiable milliOpsPerSec with 2 nodes!"));
	}

	@Test
	void logsAsExpected() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");
		var desired = "Resolved throttles (after splitting capacity 2 ways) - \n" +
				"  ContractCall: min{6.00 tps (A), 5.00 tps (B)}\n" +
				"  CryptoCreate: min{5000.00 tps (A), 1.00 tps (C)}\n" +
				"  CryptoGetAccountBalance: min{500000.00 tps (D)}\n" +
				"  CryptoTransfer: min{5000.00 tps (A)}\n" +
				"  TokenAssociateToAccount: min{50.00 tps (C)}\n" +
				"  TokenCreate: min{50.00 tps (C)}\n" +
				"  TokenMint: min{1500.00 tps (A)}\n" +
				"  TransactionGetReceipt: min{500000.00 tps (D)}\n" +
				"  ThrottleByGasLimit: 0 throttleByGas false";

		// when:
		subject.rebuildFor(defs);

		// then:
		assertThat(logCaptor.infoLogs(), contains(desired));
	}

	@Test
	void constructsExpectedBucketsFromTestResource() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");
		// and:
		var expected = List.of(
				DeterministicThrottle.withMtpsAndBurstPeriod(15_000_000, 2),
				DeterministicThrottle.withMtpsAndBurstPeriod(5_000, 2),
				DeterministicThrottle.withMtpsAndBurstPeriod(50_000, 3),
				DeterministicThrottle.withMtpsAndBurstPeriod(500_000_000, 4));

		// when:
		subject.rebuildFor(defs);
		// and:
		var rebuilt = subject.allActiveThrottles();

		// then:
		assertEquals(expected, rebuilt);
	}

	@Test
	void alwaysRejectsIfNoThrottle() {
		givenFunction(ContractCall);

		// expect:
		assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
	}

	@Test
	void alwaysRejectsIfNoThrottleForCreate() {
		givenFunction(ContractCreate);

		// expect:
		assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
	}

	@Test
	void alwaysRejectsIfNoThrottleForConsensus() {
		givenFunction(ContractCall);

		// expect:
		assertTrue(subject.shouldThrottleConsensusTxn(accessor, consensusNow));
	}

	@Test
	void alwaysRejectsIfNoThrottleForCreateForConsensus() {
		givenFunction(ContractCreate);

		// expect:
		assertTrue(subject.shouldThrottleConsensusTxn(accessor, consensusNow));
	}

	@Test
	void returnsNoActiveThrottlesForUnconfiguredOp() {
		Assertions.assertSame(Collections.emptyList(), subject.activeThrottlesFor(ContractCall));
	}

	@Test
	void shouldAllowWithEnoughCapacity() {
		// setup:
		subject.setFunctionReqs(reqsManager());

		givenFunction(CryptoTransfer);
		given(manager.allReqsMetAt(consensusNow)).willReturn(true);

		// then:
		assertFalse(subject.shouldThrottleTxn(accessor, consensusNow));
	}

	@Test
	void requiresExplicitTimestamp() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.shouldThrottleTxn(accessor));
		assertThrows(UnsupportedOperationException.class, () -> subject.shouldThrottleConsensusTxn(accessor));
		assertThrows(UnsupportedOperationException.class, () -> subject.shouldThrottleQuery(FileGetInfo, query));
	}

	@Test
	void frontEndContractCreateTXCallsFrontendGasThrottle() throws IOException {
		Instant now = Instant.now();
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");
		defs.setTotalAllowedGasPerSecFrontend(1_000L);

		//setup:
		givenFunction(ContractCreate);
		given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
		given(accessor.getTxn()).willReturn(transactionBody);
		given(transactionBody.getContractCreateInstance()).willReturn(createTransactionBody);
		given(createTransactionBody.getGas()).willReturn(100_000L);

		subject.rebuildFor(defs);

		//when:
		assertTrue(subject.shouldThrottleTxn(accessor, now));
	}

	@Test
	void frontEndContractCallTXCallsFrontendGasThrottle() {
		Instant now = Instant.now();

		//setup:
		given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
		givenFunction(ContractCall);

		//when:
		assertTrue(subject.shouldThrottleTxn(accessor, now));
	}

	@Test
	void backEndContractCallTXCallsBackendGasThrottle() throws IOException {
		Instant now = Instant.now();
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");
		defs.setTotalAllowedGasPerSecConsensus(1_000L);

		//setup:
		givenFunction(ContractCreate);
		given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
		given(accessor.getTxn()).willReturn(transactionBody);
		given(transactionBody.getContractCreateInstance()).willReturn(createTransactionBody);
		given(createTransactionBody.getGas()).willReturn(100_000L);

		//when:
		subject.rebuildFor(defs);

		//expect:
		assertTrue(subject.shouldThrottleConsensusTxn(accessor, now));
	}

	@Test
	void backEndContractCreateTXCallsBackendGasThrottle() {
		Instant now = Instant.now();

		//setup:
		given(dynamicProperties.shouldThrottleByGas()).willReturn(true);
		givenFunction(ContractCall);

		//when:
		subject.shouldThrottleConsensusTxn(accessor, now);
	}

	@Test
	void backEndContractCallTxCalls() throws IOException {
		Instant now = Instant.now();
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");
		defs.setTotalAllowedGasPerSecConsensus(1L);
		var miscUtilsHandle = mockStatic(MiscUtils.class);
		miscUtilsHandle.when(() -> MiscUtils.isGasThrottled(ContractCall)).thenReturn(false);
		given(dynamicProperties.shouldThrottleByGas()).willReturn(true);

		subject.rebuildFor(defs);

		assertTrue(subject.shouldThrottleConsensusTxn(accessor, now));
		miscUtilsHandle.close();
	}

	@Test
	void verifyLeakUnusedGas() throws IOException {
		Instant now = Instant.now();
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");
		defs.setTotalAllowedGasPerSecConsensus(10L);
		var contractCallLocal = mock(ContractCallLocalQuery.class);
		given(dynamicProperties.shouldThrottleByGas()).willReturn(true);

		subject.rebuildFor(defs);

		subject.leakUnusedGasPreviouslyReserved(100L);

		assertTrue(subject.shouldThrottleQuery(ContractCall, now, query));
	}

	private void givenFunction(HederaFunctionality functionality) {
		given(accessor.getFunction()).willReturn(functionality);
	}

	private void givenMintWith(int numNfts) {
		// setup:
		final List<ByteString> meta = new ArrayList<>();
		final var op = TokenMintTransactionBody.newBuilder();
		if (numNfts == 0) {
			op.setAmount(1_234_567L);
		} else {
			while (numNfts-- > 0) {
				op.addMetadata(ByteString.copyFromUtf8("metadata" + numNfts));
			}
		}
		final var txn = TransactionBody.newBuilder()
				.setTokenMint(op)
				.build();

		given(accessor.getFunction()).willReturn(TokenMint);
		given(accessor.getTxn()).willReturn(txn);
	}

	private EnumMap<HederaFunctionality, ThrottleReqsManager> reqsManager() {
		EnumMap<HederaFunctionality, ThrottleReqsManager> opsManagers = new EnumMap<>(HederaFunctionality.class);
		opsManagers.put(CryptoTransfer, manager);
		return opsManagers;
	}
}
