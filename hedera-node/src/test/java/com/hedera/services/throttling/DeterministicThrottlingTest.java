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
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.services.throttles.BucketThrottle;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.SerdeUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
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

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
	private AliasManager aliasManager;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private DeterministicThrottling subject;

	@BeforeEach
	void setUp() {
		subject = new DeterministicThrottling(() -> n, aliasManager, dynamicProperties);
	}

	@Test
	void worksAsExpectedForKnownQueries() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		// when:
		subject.rebuildFor(defs);
		// and:
		var noAns = subject.shouldThrottleQuery(CryptoGetAccountBalance, consensusNow);
		subject.shouldThrottleQuery(GetVersionInfo, consensusNow.plusNanos(1));
		var yesAns = subject.shouldThrottleQuery(GetVersionInfo, consensusNow.plusNanos(2));
		var throttlesNow = subject.activeThrottlesFor(CryptoGetAccountBalance);
		// and:
		var dNow = throttlesNow.get(0);

		// then:
		assertFalse(noAns);
		assertTrue(yesAns);
		assertEquals(10999999990000L, dNow.used());
	}

	@Test
	void usesScheduleCreateThrottleForSubmitMessage() throws IOException {
		final var scheduledSubmit = SchedulableTransactionBody.newBuilder()
				.setConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody.getDefaultInstance())
				.build();
		var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
		subject.rebuildFor(defs);

		final var accessor = scheduling(scheduledSubmit);
		final var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
		boolean subsequentAns = false;
		for (int i = 1; i <= 150; i++) {
			subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
		}

		final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
		final var aNow = throttlesNow.get(0);

		assertFalse(firstAns);
		assertTrue(subsequentAns);
		assertEquals(149999992500000L, aNow.used());
	}

	@Test
	void usesScheduleCreateThrottleForCryptoTransferNoAutoCreations() throws IOException {
		given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
		final var scheduledXferNoAliases = SchedulableTransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
				.build();
		var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
		subject.rebuildFor(defs);

		final var accessor = scheduling(scheduledXferNoAliases);
		final var ans = subject.shouldThrottleTxn(accessor, consensusNow);

		final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
		final var aNow = throttlesNow.get(0);

		assertFalse(ans);
		assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());
	}

	@Test
	void doesntUseCryptoCreateThrottleForCryptoTransferWithAutoCreationIfAutoCreationDisabled() throws IOException {
		final var alias = aPrimitiveKey.toByteString();
		final var scheduledXferWithAutoCreation = SchedulableTransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()
						.setTransfers(TransferList.newBuilder()
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAmount(-1_000_000_000)
										.setAccountID(IdUtils.asAccount("0.0.3333")))
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAmount(+1_000_000_000)
										.setAccountID(AccountID.newBuilder().setAlias(alias)))))
				.build();
		var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
		subject.rebuildFor(defs);

		final var accessor = scheduling(scheduledXferWithAutoCreation);
		final var ans = subject.shouldThrottleTxn(accessor, consensusNow);

		final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
		final var aNow = throttlesNow.get(0);

		assertFalse(ans);
		assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());
	}

	@Test
	void doesntUseCryptoCreateThrottleForCryptoTransferWithNoAliases() throws IOException {
		given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
		final var scheduledXferWithAutoCreation = SchedulableTransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()
						.setTransfers(TransferList.newBuilder()
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAmount(-1_000_000_000)
										.setAccountID(IdUtils.asAccount("0.0.3333")))
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAmount(+1_000_000_000)
										.setAccountID(IdUtils.asAccount("0.0.4444")))))
				.build();
		var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
		subject.rebuildFor(defs);

		final var accessor = scheduling(scheduledXferWithAutoCreation);
		final var ans = subject.shouldThrottleTxn(accessor, consensusNow);

		final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
		final var aNow = throttlesNow.get(0);

		assertFalse(ans);
		assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());
	}

	@Test
	void usesCryptoCreateThrottleForCryptoTransferWithAutoCreation() throws IOException {
		final var alias = aPrimitiveKey.toByteString();
		given(aliasManager.lookupIdBy(alias)).willReturn(MISSING_NUM);
		given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
		final var scheduledXferWithAutoCreation = SchedulableTransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()
						.setTransfers(TransferList.newBuilder()
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAmount(-1_000_000_000)
										.setAccountID(IdUtils.asAccount("0.0.3333")))
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAmount(+1_000_000_000)
										.setAccountID(AccountID.newBuilder().setAlias(alias)))))
				.build();
		var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
		subject.rebuildFor(defs);

		final var accessor = scheduling(scheduledXferWithAutoCreation);
		final var ans = subject.shouldThrottleTxn(accessor, consensusNow);

		final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
		final var aNow = throttlesNow.get(0);

		assertFalse(ans);
		assertEquals(50 * BucketThrottle.capacityUnitsPerTxn(), aNow.used());
	}

	@Test
	void usesScheduleCreateThrottleForAliasedCryptoTransferWithNoAutoCreation() throws IOException {
		final var alias = aPrimitiveKey.toByteString();
		given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
		given(aliasManager.lookupIdBy(alias)).willReturn(EntityNum.fromLong(1_234L));
		final var scheduledXferWithAutoCreation = SchedulableTransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()
						.setTransfers(TransferList.newBuilder()
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAmount(+1_000_000_000)
										.setAccountID(IdUtils.asAccount("0.0.3333")))
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAmount(-1_000_000_000)
										.setAccountID(AccountID.newBuilder().setAlias(alias)))))
				.build();
		var defs = SerdeUtils.pojoDefs("bootstrap/schedule-create-throttles.json");
		subject.rebuildFor(defs);

		final var accessor = scheduling(scheduledXferWithAutoCreation);
		final var ans = subject.shouldThrottleTxn(accessor, consensusNow);

		final var throttlesNow = subject.activeThrottlesFor(ScheduleCreate);
		final var aNow = throttlesNow.get(0);

		assertFalse(ans);
		assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());
	}

	@Test
	void worksAsExpectedForUnknownQueries() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		// when:
		subject.rebuildFor(defs);

		// then:
		assertTrue(subject.shouldThrottleQuery(ContractCallLocal, consensusNow));
	}

	@Test
	void managerBehavesAsExpectedForFungibleMint() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		givenMintWith(0);

		// when:
		subject.rebuildFor(defs);
		// and:
		var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
		boolean subsequentAns = false;
		for (int i = 1; i <= 3000; i++) {
			subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
		}
		var throttlesNow = subject.activeThrottlesFor(TokenMint);
		var aNow = throttlesNow.get(0);

		// then:
		assertFalse(firstAns);
		assertTrue(subsequentAns);
		assertEquals(29999955000000000L, aNow.used());
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
		var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
		boolean subsequentAns = false;
		for (int i = 1; i <= 400; i++) {
			subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
		}
		var throttlesNow = subject.activeThrottlesFor(TokenMint);
		// and:
		var aNow = throttlesNow.get(0);

		// then:
		assertFalse(firstAns);
		assertTrue(subsequentAns);
		assertEquals(29999994000000000L, aNow.used());
	}

	@Test
	void managerBehavesAsExpectedForMultiBucketOp() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		givenFunction(ContractCall);

		// when:
		subject.rebuildFor(defs);
		// and:
		var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
		boolean subsequentAns = false;
		for (int i = 1; i <= 12; i++) {
			subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
		}
		var throttlesNow = subject.activeThrottlesFor(ContractCall);
		// and:
		var aNow = throttlesNow.get(0);
		var bNow = throttlesNow.get(1);

		// then:
		assertFalse(firstAns);
		assertTrue(subsequentAns);
		assertEquals(24999999820000000L, aNow.used());
		assertEquals(9999999940000L, bNow.used());
	}

	@Test
	void computesNumAutoCreationsIfNotAlreadyKnown() throws IOException {
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		givenFunction(CryptoTransfer);
		given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
		given(accessor.getNumAutoCreations()).willReturn(0);
		subject.rebuildFor(defs);

		var ans = subject.shouldThrottleTxn(accessor, consensusNow);

		verify(accessor).countAutoCreationsWith(aliasManager);
		assertFalse(ans);
	}

	@Test
	void reusesNumAutoCreationsIfNotCounted() throws IOException {
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		givenFunction(CryptoTransfer);
		given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
		given(accessor.areAutoCreationsCounted()).willReturn(true);
		given(accessor.getNumAutoCreations()).willReturn(0);
		subject.rebuildFor(defs);

		var firstAns = subject.shouldThrottleTxn(accessor, consensusNow);
		boolean subsequentAns = false;
		for (int i = 1; i <= 10000; i++) {
			subsequentAns = subject.shouldThrottleTxn(accessor, consensusNow.plusNanos(i));
		}

		verify(accessor, never()).countAutoCreationsWith(aliasManager);
		assertFalse(firstAns);
		assertTrue(subsequentAns);
	}

	@Test
	void cryptoTransfersWithNoAutoAccountCreationsAreThrottledAsExpected() throws IOException {
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		givenFunction(CryptoTransfer);
		subject.rebuildFor(defs);

		var ans = subject.shouldThrottleTxn(accessor, consensusNow);

		assertFalse(ans);
	}

	@Test
	void managerAllowsCryptoTransfersWithAutoAccountCreationsAsExpected() throws IOException {
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		givenFunction(CryptoTransfer);
		given(accessor.getNumAutoCreations()).willReturn(1);
		given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
		subject.rebuildFor(defs);

		var ans = subject.shouldThrottleTxn(accessor, consensusNow);

		assertFalse(ans);
	}

	@Test
	void managerRejectsCryptoTransfersWithAutoAccountCreationsAsExpected() throws IOException {
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");

		givenFunction(CryptoTransfer);
		given(accessor.getNumAutoCreations()).willReturn(10);
		given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
		subject.rebuildFor(defs);

		var ans = subject.shouldThrottleTxn(accessor, consensusNow);

		assertTrue(ans);
	}

	@Test
	void managerRejectsCryptoTransfersWithMissingCryptoCreateThrottle() throws IOException {
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles-sans-creation.json");

		givenFunction(CryptoTransfer);
		given(accessor.getNumAutoCreations()).willReturn(1);
		given(dynamicProperties.isAutoCreationEnabled()).willReturn(true);
		subject.rebuildFor(defs);

		var ans = subject.shouldThrottleTxn(accessor, consensusNow);

		assertTrue(ans);
	}

	@Test
	void logsErrorOnBadBucketButDoesntFail() throws IOException {
		final var ridiculousSplitFactor = 1_000_000;
		subject = new DeterministicThrottling(() -> ridiculousSplitFactor, aliasManager, dynamicProperties);

		var defs = SerdeUtils.pojoDefs("bootstrap/insufficient-capacity-throttles.json");

		// expect:
		assertDoesNotThrow(() -> subject.rebuildFor(defs));
		// and:
		assertEquals(1, subject.activeThrottlesFor(CryptoGetAccountBalance).size());
		// and:
		assertThat(logCaptor.errorLogs(),
				contains("When constructing bucket 'A' from state: NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION :: " +
						"Bucket A contains an unsatisfiable milliOpsPerSec with 1000000 nodes!"));
	}

	@Test
	void logsAsExpected() throws IOException {
		// setup:
		var defs = SerdeUtils.pojoDefs("bootstrap/throttles.json");
		final var desired = "Resolved throttles (after splitting capacity 2 ways) - \n  ContractCall: min{6.00 tps (A)" +
				", 5.00 tps (B)}\n  CryptoCreate: min{5000.00 tps (A), 1.00 tps (C)}\n  CryptoGetAccountBalance: " +
				"min{5.00 tps (D)}\n  CryptoTransfer: min{5000.00 tps (A)}\n  GetVersionInfo: min{0.50 tps (D)}\n  " +
				"TokenAssociateToAccount: min{50.00 tps (C)}\n  TokenCreate: min{50.00 tps (C)}\n  TokenMint: " +
				"min{1500.00 tps (A)}\n  TransactionGetReceipt: min{5.00 tps (D)}";

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
				DeterministicThrottle.withMtpsAndBurstPeriod(5000, 4));

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
	void shouldRejectWithInsufficientCapacity() {
		subject.setFunctionReqs(reqsManager());

		givenFunction(CryptoTransfer);

		assertTrue(subject.shouldThrottleTxn(accessor, consensusNow));
	}

	@Test
	void requiresExplicitTimestamp() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.shouldThrottleTxn(accessor));
		assertThrows(UnsupportedOperationException.class, () -> subject.shouldThrottleQuery(FileGetInfo));
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

	private SignedTxnAccessor scheduling(final SchedulableTransactionBody inner) {
		final var schedule = ScheduleCreateTransactionBody.newBuilder()
				.setScheduledTransactionBody(inner);
		final var body = TransactionBody.newBuilder()
				.setScheduleCreate(schedule)
				.build();
		final var signedTxn = SignedTransaction.newBuilder()
				.setBodyBytes(body.toByteString())
				.build();
		final var txn = Transaction.newBuilder()
				.setSignedTransactionBytes(signedTxn.toByteString())
				.build();
		return SignedTxnAccessor.uncheckedFrom(txn);
	}

	private static final Key aPrimitiveKey = Key.newBuilder()
			.setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
			.build();
}
