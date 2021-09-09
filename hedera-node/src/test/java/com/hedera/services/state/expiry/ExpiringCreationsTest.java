package com.hedera.services.state.expiry;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.charging.NarratedCharging;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.serdes.DomainSerdesTest;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static com.hedera.services.state.expiry.NoopExpiringCreations.NOOP_EXPIRING_CREATIONS;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpiringCreationsTest {
	private static final int cacheTtl = 180;
	private static final long now = 1_234_567L;
	private static final long submittingMember = 1L;
	private static final long expectedExpiry = now + cacheTtl;

	private static final AccountID effPayer = IdUtils.asAccount("0.0.75231");
	private static final ExpirableTxnRecord record = DomainSerdesTest.recordOne();
	private static ExpirableTxnRecord expectedRecord;

	@Mock
	private ExpiryManager expiries;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	@Mock
	private NarratedCharging narratedCharging;
	@Mock
	private HederaLedger ledger;
	@Mock
	private TxnAccessor accessor;

	private static final AccountID payer = asAccount("0.0.2");
	private static final AccountID created = asAccount("1.0.2");
	private static final AccountID another = asAccount("1.0.300");
	private static final TransferList transfers = withAdjustments(payer, -2L, created, 1L, another, 1L);
	private static final TokenID tokenCreated = asToken("3.0.2");
	private static final List<AccountAmount> adjustments =
			withAdjustments(payer, -2L, created, 1L, another, 1L).getAccountAmountsList();
	private static final TokenTransferList tokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenCreated)
			.addAllTransfers(adjustments)
			.build();

	private static final String memo = "TEST_MEMO";
	private static final String hashString = "TEST";
	private static final long scheduleNum = 100L;
	private static final String account = "0.0.10001";
	private static final Instant timestamp = Instant.now();
	private static final byte[] hash = hashString.getBytes(StandardCharsets.UTF_8);
	private static final TransactionID grpcTxnId = TransactionID.newBuilder().setAccountID(asAccount(account)).build();
	private static final ScheduleID scheduleRef = ScheduleID.newBuilder().setScheduleNum(scheduleNum).build();

	private ExpiringCreations subject;

	private static final TxnReceipt receipt = receiptWith(SUCCESS);

	private static final EntityId customFeeToken = new EntityId(0, 0, 123);
	private static final EntityId customFeeCollector = new EntityId(0, 0, 124);
	private static final List<FcAssessedCustomFee> customFeesCharged = List.of(
			new FcAssessedCustomFee(customFeeCollector, customFeeToken, 123L, new long[] { 123L }));
	private static final List<FcTokenAssociation> newTokenAssociations = List.of(
			new FcTokenAssociation(customFeeToken.num(), customFeeCollector.num()));


	@BeforeEach
	void setup() {
		subject = new ExpiringCreations(expiries, narratedCharging, dynamicProperties, () -> accounts);
		subject.setLedger(ledger);

		expectedRecord = record;
		expectedRecord.setExpiry(expectedExpiry);
		expectedRecord.setSubmittingMember(submittingMember);

		verify(narratedCharging).setLedger(ledger);
	}

	void setUpForExpiringRecordBuilder() {
		given(accessor.getTxnId()).willReturn(grpcTxnId);
		given(accessor.getMemo()).willReturn(memo);
		given(accessor.isTriggeredTxn()).willReturn(true);
		given(accessor.getScheduleRef()).willReturn(scheduleRef);
	}

	@Test
	void addsToPayerRecordsAndTracks() {
		final var key = MerkleEntityId.fromAccountId(effPayer);
		final var payerAccount = new MerkleAccount();
		given(accounts.getForModify(key)).willReturn(payerAccount);
		given(dynamicProperties.cacheRecordsTtl()).willReturn(cacheTtl);

		final var actual = subject.saveExpiringRecord(effPayer, record, now, submittingMember);

		assertEquals(expectedRecord, actual);
		verify(expiries).trackRecordInState(effPayer, expectedExpiry);
		assertEquals(expectedRecord, payerAccount.records().peek());
	}

	@Test
	void noopFormDoesNothing() {
		Assertions.assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.saveExpiringRecord(
						null, null, 0L, submittingMember));
		Assertions.assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.buildExpiringRecord(
						0L, null, null, null, null, null, null, null, null));
		Assertions.assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.buildFailedExpiringRecord(null, null));
	}

	@Test
	void validateBuildExpiringRecord() {
		setUpForExpiringRecordBuilder();
		given(narratedCharging.totalFeesChargedToPayer()).willReturn(10L);
		given(ledger.netTransfersInTxn()).willReturn(transfers);
		given(ledger.netTokenTransfersInTxn()).willReturn(List.of(tokenTransfers));
		given(ledger.getNewTokenAssociations()).willReturn(newTokenAssociations);

		final var builder = subject.buildExpiringRecord(
				100L, hash, accessor, timestamp, receipt, null, null, customFeesCharged, null);
		final var actualRecord = builder.build();

		validateCommonFields(actualRecord, receipt);
		assertEquals(110L, actualRecord.getFee());
		validateTokensAndTokenAdjustments(actualRecord);
		validateCustomFeesChargedAndNewTokenAssociations(actualRecord);
	}

	@Test
	void validateBuildExpiringRecordWithNewTokenAssociationsFromCtx() {
		setUpForExpiringRecordBuilder();
		given(narratedCharging.totalFeesChargedToPayer()).willReturn(10L);
		given(ledger.netTransfersInTxn()).willReturn(transfers);
		given(ledger.netTokenTransfersInTxn()).willReturn(List.of(tokenTransfers));

		final var builder = subject.buildExpiringRecord(
				100L, hash, accessor, timestamp, receipt, null, null, customFeesCharged, newTokenAssociations);
		final var actualRecord = builder.build();

		validateTokensAndTokenAdjustments(actualRecord);
		validateCustomFeesChargedAndNewTokenAssociations(actualRecord);
	}

	@Test
	void canOverrideTokenTransfers() {
		setUpForExpiringRecordBuilder();
		given(narratedCharging.totalFeesChargedToPayer()).willReturn(123L);
		given(ledger.netTransfersInTxn()).willReturn(transfers);
		final var someTokenXfers = List.of(TokenTransferList.newBuilder()
				.setToken(IdUtils.asToken("1.2.3"))
				.addAllTransfers(
						withAdjustments(payer, -100,
								asAccount("0.0.3"), 10,
								asAccount("0.0.98"), 90).getAccountAmountsList())
				.build());

		final var builder = subject.buildExpiringRecord(
				100L, hash, accessor, timestamp, receipt, null, someTokenXfers, null, null);
		final var actualRecord = builder.build();

		verify(ledger, never()).netTokenTransfersInTxn();
		assertEquals(someTokenXfers, actualRecord.asGrpc().getTokenTransferListsList());
	}

	// TODO can override hbar transfers

	@Test
	void validateBuildFailedExpiringRecord() {
		setUpForExpiringRecordBuilder();
		given(accessor.getHash()).willReturn(hash);

		final var builder = subject.buildFailedExpiringRecord(accessor, timestamp);
		final var actualRecord = builder.build();

		validateCommonFields(actualRecord, receiptWith(FAIL_INVALID));
	}

	private void validateCommonFields(final ExpirableTxnRecord actualRecord, final TxnReceipt receipt) {
		assertEquals(grpcTxnId, actualRecord.getTxnId().toGrpc());
		assertEquals(receipt, actualRecord.getReceipt());
		assertEquals(memo, actualRecord.getMemo());
		assertArrayEquals(hash, actualRecord.getTxnHash());
		assertEquals(timestamp, actualRecord.getConsensusTimestamp().toJava());
		assertEquals(scheduleRef, actualRecord.getScheduleRef().toGrpcScheduleId());
	}

	private void validateTokensAndTokenAdjustments(final ExpirableTxnRecord actualRecord) {
		final var tokensExpected = List.of(EntityId.fromGrpcTokenId(tokenCreated));
		final var tokenAdjustmentsExpected = List.of(CurrencyAdjustments.fromGrpc(adjustments));

		assertEquals(tokensExpected, actualRecord.getTokens());
		assertEquals(tokenAdjustmentsExpected, actualRecord.getTokenAdjustments());
	}

	private void validateCustomFeesChargedAndNewTokenAssociations(final ExpirableTxnRecord actualRecord) {
		assertEquals(customFeesCharged, actualRecord.getCustomFeesCharged());
		assertEquals(newTokenAssociations, actualRecord.getNewTokenAssociations());
	}

	private static final TxnReceipt receiptWith(final ResponseCodeEnum code) {
		return TxnReceipt.newBuilder().setStatus(code.name()).build();
	}
}
