package com.hedera.services.context;

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

import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.charging.NarratedCharging;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.expiry.ExpiringEntity;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.SolidityFnResult;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.fcmap.FCMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hedera.services.context.AwareTransactionContext.EMPTY_KEY;
import static com.hedera.services.state.submerkle.EntityId.fromGrpcScheduleId;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountString;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.IdUtils.asSchedule;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(LogCaptureExtension.class)
class AwareTransactionContextTest {
	private final long offeredFee = 123_000_000L;
	private final TransactionID scheduledTxnId = TransactionID.newBuilder()
			.setAccountID(IdUtils.asAccount("0.0.2"))
			.build();
	private long memberId = 3;
	private long anotherMemberId = 4;
	private Instant now = Instant.now();
	private ExchangeRate rateNow = ExchangeRate.newBuilder().setHbarEquiv(1).setCentEquiv(100).setExpirationTime(
			TimestampSeconds.newBuilder()).build();
	private ExchangeRateSet ratesNow =
			ExchangeRateSet.newBuilder().setCurrentRate(rateNow).setNextRate(rateNow).build();
	private AccountID payer = asAccount("0.0.2");
	private AccountID anotherNodeAccount = asAccount("0.0.4");
	private AccountID created = asAccount("1.0.2");
	private AccountID another = asAccount("1.0.300");
	private TransferList transfers = withAdjustments(payer, -2L, created, 1L, another, 1L);
	private TokenID tokenCreated = asToken("3.0.2");
	private ScheduleID scheduleCreated = asSchedule("0.0.10");
	private TokenTransferList tokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenCreated)
			.addAllTransfers(withAdjustments(payer, -2L, created, 1L, another, 1L).getAccountAmountsList())
			.build();
	private FileID fileCreated = asFile("2.0.1");
	private ContractID contractCreated = asContract("0.1.2");
	private TopicID topicCreated = asTopic("5.4.3");
	private long txnValidStart = now.getEpochSecond() - 1_234L;
	private HederaLedger ledger;
	private AccountID nodeAccount = asAccount("0.0.3");
	private Address address;
	private Address anotherAddress;
	private AddressBook book;
	private HbarCentExchange exchange;
	private NodeInfo nodeInfo;
	private ServicesContext ctx;
	private NarratedCharging narratedCharging;
	private PlatformTxnAccessor accessor;
	private Transaction signedTxn;
	private TransactionBody txn;
	private ExpirableTxnRecord record;
	private ExpiringEntity expiringEntity;
	private String memo = "Hi!";
	private byte[] hash = "fake hash".getBytes();
	private TransactionID txnId = TransactionID.newBuilder()
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(txnValidStart))
			.setAccountID(payer)
			.build();
	private ContractFunctionResult result = ContractFunctionResult.newBuilder().setContractID(contractCreated).build();
	private JKey payerKey;

	@Inject
	private LogCaptor logCaptor;

	@LoggingSubject
	private AwareTransactionContext subject;

	private ExpiringCreations creator;

	@BeforeEach
	private void setup() {
		address = mock(Address.class);
		given(address.getMemo()).willReturn(asAccountString(nodeAccount));
		anotherAddress = mock(Address.class);
		given(anotherAddress.getMemo()).willReturn(asAccountString(anotherNodeAccount));
		book = mock(AddressBook.class);
		given(book.getAddress(memberId)).willReturn(address);
		given(book.getAddress(anotherMemberId)).willReturn(anotherAddress);

		ledger = mock(HederaLedger.class);
		given(ledger.netTransfersInTxn()).willReturn(transfers);
		given(ledger.netTokenTransfersInTxn()).willReturn(List.of(tokenTransfers));

		exchange = mock(HbarCentExchange.class);
		given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));

		narratedCharging = mock(NarratedCharging.class);

		payerKey = mock(JKey.class);
		MerkleAccount payerAccount = mock(MerkleAccount.class);
		given(payerAccount.getKey()).willReturn(payerKey);
		FCMap<MerkleEntityId, MerkleAccount> accounts = mock(FCMap.class);
		given(accounts.get(MerkleEntityId.fromAccountId(payer))).willReturn(payerAccount);

		ctx = mock(ServicesContext.class);
		creator = mock(ExpiringCreations.class);
		given(ctx.exchange()).willReturn(exchange);
		given(ctx.ledger()).willReturn(ledger);
		given(ctx.accounts()).willReturn(accounts);
		given(ctx.narratedCharging()).willReturn(narratedCharging);
		given(ctx.accounts()).willReturn(accounts);
		given(ctx.addressBook()).willReturn(book);
		given(ctx.creator()).willReturn(creator);

		nodeInfo = mock(NodeInfo.class);
		given(ctx.nodeInfo()).willReturn(nodeInfo);
		given(nodeInfo.accountOf(memberId)).willReturn(nodeAccount);

		txn = mock(TransactionBody.class);
		given(txn.getMemo()).willReturn(memo);
		signedTxn = mock(Transaction.class);
		given(signedTxn.toByteArray()).willReturn(memo.getBytes());
		accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getOfferedFee()).willReturn(offeredFee);
		given(accessor.getTxnId()).willReturn(txnId);
		given(accessor.getTxn()).willReturn(txn);
		given(accessor.getSignedTxnWrapper()).willReturn(signedTxn);
		given(accessor.getPayer()).willReturn(payer);
		given(accessor.getHash()).willReturn(hash);

		expiringEntity = mock(ExpiringEntity.class);

		subject = new AwareTransactionContext(ctx);
		subject.resetFor(accessor, now, memberId);

		verify(narratedCharging).resetForTxn(accessor, memberId);
	}

	@Test
	void throwsIseIfNoPayerActive() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.activePayer());
	}

	@Test
	void returnsPayerIfSigActive() {
		// given:
		subject.payerSigIsKnownActive();

		// expect:
		assertEquals(payer, subject.activePayer());
	}

	@Test
	void returnsEmptyKeyIfNoPayerActive() {
		// expect:
		assertEquals(EMPTY_KEY, subject.activePayerKey());
	}

	@Test
	void getsPayerKeyIfSigActive() {
		// given:
		subject.payerSigIsKnownActive();

		// then:
		assertEquals(payerKey, subject.activePayerKey());
	}

	@Test
	void getsExpectedNodeAccount() {
		// expect:
		assertEquals(nodeAccount, subject.submittingNodeAccount());
	}

	@Test
	void failsHardForMissingMemberAccount() {
		given(nodeInfo.accountOf(memberId)).willThrow(IllegalArgumentException.class);

		// then:
		var ise = assertThrows(IllegalStateException.class, () -> subject.submittingNodeAccount());
		// and:
		assertThat(logCaptor.warnLogs(), contains(Matchers.startsWith("No available Hedera account for member 3!")));
		assertEquals("Member 3 must have a Hedera account!", ise.getMessage());
	}

	@Test
	void resetsRecordSoFar() {
		// given:
		subject.recordSoFar = mock(ExpirableTxnRecord.Builder.class);

		// when:
		subject.resetFor(accessor, now, anotherMemberId);

		// then:
		verify(subject.recordSoFar).clear();
	}

	@Test
	void resetsEverythingElse() {
		given(nodeInfo.accountOf(anotherMemberId)).willReturn(anotherNodeAccount);
		// and:
		subject.addNonThresholdFeeChargedToPayer(1_234L);
		subject.setCallResult(result);
		subject.setStatus(SUCCESS);
		subject.setCreated(contractCreated);
		subject.payerSigIsKnownActive();
		subject.hasComputedRecordSoFar = true;
		subject.setAssessedCustomFees(Collections.emptyList());
		// and:
		assertEquals(memberId, subject.submittingSwirldsMember());
		assertEquals(nodeAccount, subject.submittingNodeAccount());

		// when:
		subject.resetFor(accessor, now, anotherMemberId);
		assertNull(subject.getAssessedCustomFees());
		assertFalse(subject.hasComputedRecordSoFar);
		// and:
		setUpBuildingExpirableTxnRecord();
		record = subject.recordSoFar();

		// then:
		assertEquals(ResponseCodeEnum.UNKNOWN, ResponseCodeEnum.valueOf(record.getReceipt().getStatus()));
		assertFalse(record.getReceipt().toGrpc().hasContractID());
		assertEquals(0, record.asGrpc().getTransactionFee());
		assertFalse(record.asGrpc().hasContractCallResult());
		assertFalse(subject.isPayerSigKnownActive());
		assertTrue(subject.hasComputedRecordSoFar);
		assertEquals(anotherNodeAccount, subject.submittingNodeAccount());
		assertEquals(anotherMemberId, subject.submittingSwirldsMember());
		// and:
		verify(narratedCharging).resetForTxn(accessor, memberId);
	}

	@Test
	void effectivePayerIsSubmittingNodeIfNotVerified() {
		// expect:
		assertEquals(nodeAccount, subject.effectivePayer());
	}

	@Test
	void effectivePayerIsActiveIfVerified() {
		// given:
		subject.payerSigIsKnownActive();

		// expect:
		assertEquals(payer, subject.effectivePayer());
	}

	@Test
	void usesChargingToSetTransactionFee() {
		long std = 1_234L;
		long other = 4_321L;
		given(narratedCharging.totalFeesChargedToPayer()).willReturn(std);

		// when:
		subject.addNonThresholdFeeChargedToPayer(other);

		setUpBuildingExpirableTxnRecord();
		record = subject.recordSoFar();

		// then:
		assertEquals(std + other, record.asGrpc().getTransactionFee());
	}

	@Test
	void usesTokenTransfersToSetApropos() {
		// when:
		setUpBuildingExpirableTxnRecord();
		record = subject.recordSoFar();

		// then:
		assertEquals(tokenTransfers, record.asGrpc().getTokenTransferLists(0));
	}

	@Test
	void configuresCallResult() {
		// when:
		subject.setCallResult(result);
		setUpBuildingExpirableTxnRecord();
		record = subject.recordSoFar();

		// expect:
		assertEquals(SolidityFnResult.fromGrpc(result), record.getContractCallResult());
	}

	@Test
	void configuresCreateResult() {
		// when:
		setUpBuildingExpirableTxnRecord();
		subject.setCreateResult(result);
		record = subject.recordSoFar();

		// expect:
		assertEquals(SolidityFnResult.fromGrpc(result), record.getContractCreateResult());
	}

	@Test
	void hasTransferList() {
		setUpBuildingExpirableTxnRecord();
		// expect:
		assertEquals(transfers, subject.recordSoFar().asGrpc().getTransferList());
	}

	@Test
	void hasExpectedCopyFields() {
		setUpBuildingExpirableTxnRecord();
		// when:
		ExpirableTxnRecord record = subject.recordSoFar();

		// expect:
		assertEquals(memo, record.getMemo());
		assertArrayEquals(hash, record.asGrpc().getTransactionHash().toByteArray());
		assertEquals(txnId, record.asGrpc().getTransactionID());
		assertEquals(RichInstant.fromJava(now), record.getConsensusTimestamp());
	}

	@Test
	void hasExpectedPrimitives() {
		// expect:
		assertEquals(accessor, subject.accessor());
		assertEquals(now, subject.consensusTime());
		assertEquals(ResponseCodeEnum.UNKNOWN, subject.status());
	}

	@Test
	void hasExpectedStatus() {
		// when:
		subject.setStatus(ResponseCodeEnum.INVALID_PAYER_SIGNATURE);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_PAYER_SIGNATURE, subject.status());
	}

	@Test
	void hasExpectedRecordStatus() {
		// when:
		subject.setStatus(ResponseCodeEnum.INVALID_PAYER_SIGNATURE);
		setUpBuildingExpirableTxnRecord();
		record = subject.recordSoFar();

		// then:
		assertEquals(ResponseCodeEnum.INVALID_PAYER_SIGNATURE,
				ResponseCodeEnum.valueOf(record.getReceipt().getStatus()));
	}

	@Test
	void getsExpectedReceiptForAccountCreation() {
		// when:
		subject.setCreated(created);
		setUpBuildingExpirableTxnRecord();
		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().toGrpc().getExchangeRate());
		assertEquals(created, record.getReceipt().toGrpc().getAccountID());
	}

	@Test
	void getsExpectedReceiptForTokenCreation() {
		// when:
		subject.setCreated(tokenCreated);
		setUpBuildingExpirableTxnRecord();

		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().toGrpc().getExchangeRate());
		assertEquals(tokenCreated, record.getReceipt().toGrpc().getTokenID());
	}

	@Test
	void getsExpectedReceiptForTokenMintBurnWipe() {
		// when:
		final var newTotalSupply = 1000L;
		subject.setNewTotalSupply(newTotalSupply);
		setUpBuildingExpirableTxnRecord();

		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().toGrpc().getExchangeRate());
		assertEquals(newTotalSupply, record.getReceipt().getNewTotalSupply());
	}


	@Test
	void getsExpectedReceiptForFileCreation() {
		// when:
		subject.setCreated(fileCreated);
		setUpBuildingExpirableTxnRecord();

		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, TxnReceipt.convert(record.getReceipt()).getExchangeRate());
		assertEquals(fileCreated, record.getReceipt().toGrpc().getFileID());
	}

	@Test
	void getsExpectedReceiptForContractCreation() {
		// when:
		subject.setCreated(contractCreated);
		setUpBuildingExpirableTxnRecord();
		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().toGrpc().getExchangeRate());
		assertEquals(contractCreated, record.getReceipt().toGrpc().getContractID());
	}

	@Test
	void getsExpectedReceiptForTopicCreation() {
		// when:
		subject.setCreated(topicCreated);
		setUpBuildingExpirableTxnRecord();
		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().toGrpc().getExchangeRate());
		assertEquals(topicCreated, record.getReceipt().toGrpc().getTopicID());
	}

	@Test
	void getsExpectedReceiptForSubmitMessage() {
		var sequenceNumber = 1000L;
		var runningHash = new byte[11];

		// when:
		subject.setTopicRunningHash(runningHash, sequenceNumber);
		setUpBuildingExpirableTxnRecord();
		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().toGrpc().getExchangeRate());
		assertArrayEquals(runningHash, record.getReceipt().toGrpc().getTopicRunningHash().toByteArray());
		assertEquals(sequenceNumber, record.getReceipt().getTopicSequenceNumber());
		assertEquals(MerkleTopic.RUNNING_HASH_VERSION, record.getReceipt().toGrpc().getTopicRunningHashVersion());
	}

	@Test
	void getsExpectedReceiptForSuccessfulScheduleOps() {
		// when:
		subject.setCreated(scheduleCreated);
		subject.setScheduledTxnId(scheduledTxnId);
		setUpBuildingExpirableTxnRecord();
		// and:
		record = subject.recordSoFar();

		// then:
		assertEquals(scheduleCreated, record.getReceipt().toGrpc().getScheduleID());
		assertEquals(scheduledTxnId, record.getReceipt().toGrpc().getScheduledTransactionID());
	}

	@Test
	void startsWithoutKnownValidPayerSig() {
		// expect:
		assertFalse(subject.isPayerSigKnownActive());
	}

	@Test
	void setsSigToKnownValid() {
		// given:
		subject.payerSigIsKnownActive();

		// expect:
		assertTrue(subject.isPayerSigKnownActive());
	}

	@Test
	void triggersTxn() {
		// when:
		subject.trigger(accessor);
		// then:
		assertEquals(subject.triggeredTxn(), accessor);
	}

	@Test
	void getsExpectedRecordForTriggeredTxn() {
		// given:
		given(accessor.getScheduleRef()).willReturn(scheduleCreated);
		given(accessor.isTriggeredTxn()).willReturn(true);
		setUpBuildingExpirableTxnRecord();

		// when:
		record = subject.recordSoFar();

		// then:
		assertEquals(fromGrpcScheduleId(scheduleCreated), record.getScheduleRef());
	}

	@Test
	void addsExpiringEntities() {
		// given:
		var expected = Collections.singletonList(expiringEntity);
		// when:
		subject.addExpiringEntities(expected);

		// then:
		assertEquals(subject.expiringEntities(), expected);
	}

	@Test
	void throwsIfAccessorIsAlreadyTriggered() {
		// given:
		given(accessor.getScheduleRef()).willReturn(scheduleCreated);
		given(accessor.isTriggeredTxn()).willReturn(true);

		// when:
		assertThrows(IllegalStateException.class, () -> subject.trigger(accessor));
	}

	private ExpirableTxnRecord.Builder buildRecord(
                  long otherNonThresholdFees, 
                  byte[] hash, 
                  TxnAccessor accessor, 
                  Instant consensusTime, 
                  TxnReceipt receipt
        ) {
		long amount = ctx.narratedCharging().totalFeesChargedToPayer() + otherNonThresholdFees;
		TransferList transfersList = ctx.ledger().netTransfersInTxn();
		List<TokenTransferList> tokenTransferList = ctx.ledger().netTokenTransfersInTxn();

		var builder = ExpirableTxnRecord.newBuilder()
				.setReceipt(receipt)
				.setTxnHash(hash)
				.setTxnId(TxnId.fromGrpc(accessor.getTxnId()))
				.setConsensusTime(RichInstant.fromJava(consensusTime))
				.setMemo(accessor.getTxn().getMemo())
				.setFee(amount)
				.setTransferList(!transfersList.getAccountAmountsList().isEmpty() ? CurrencyAdjustments.fromGrpc(
						transfersList) : null)
				.setScheduleRef(accessor.isTriggeredTxn() ? fromGrpcScheduleId(accessor.getScheduleRef()) : null);

		List<EntityId> tokens = new ArrayList<>();
		List<CurrencyAdjustments> tokenAdjustments = new ArrayList<>();
		if (tokenTransferList.size() > 0) {
			for (TokenTransferList tokenTransfers : tokenTransferList) {
				tokens.add(EntityId.fromGrpcTokenId(tokenTransfers.getToken()));
				tokenAdjustments.add(CurrencyAdjustments.fromGrpc(tokenTransfers.getTransfersList()));
			}
		}

		builder.setTokens(tokens)
				.setTokenAdjustments(tokenAdjustments);
		return builder;
	}

	private ExpirableTxnRecord.Builder setUpBuildingExpirableTxnRecord() {
		var expirableRecordBuilder = buildRecord(subject.getNonThresholdFeeChargedToPayer(),
				accessor.getHash(),
				accessor, now, subject.receiptSoFar().build());
		when(creator.buildExpiringRecord(anyLong(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(expirableRecordBuilder);
		return expirableRecordBuilder;
	}
}
