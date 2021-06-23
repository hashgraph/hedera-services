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

import com.google.protobuf.ByteString;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.charging.NarratedCharging;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.serdes.DomainSerdesTest;
import com.hedera.services.state.submerkle.AssessedCustomFee;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.fcmap.FCMap;
import javafx.util.Pair;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.state.expiry.NoopExpiringCreations.NOOP_EXPIRING_CREATIONS;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.CommonUtils.hex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpiringCreationsTest {
	private final int cacheTtl = 180;
	private final long now = 1_234_567L;
	private final long submittingMember = 1L;
	private final long expectedExpiry = now + cacheTtl;

	private final AccountID effPayer = IdUtils.asAccount("0.0.75231");
	private final ExpirableTxnRecord record = DomainSerdesTest.recordOne();
	private ExpirableTxnRecord expectedRecord;

	@Mock
	private RecordCache recordCache;
	@Mock
	private ExpiryManager expiries;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	@Mock
	private ServicesContext ctx;
	@Mock
	private NarratedCharging narratedCharging;
	@Mock
	private HederaLedger ledger;
	@Mock
	private TxnAccessor accessor;

	private final AccountID payer = asAccount("0.0.2");
	private final AccountID created = asAccount("1.0.2");
	private final AccountID another = asAccount("1.0.300");
	private final TransferList transfers = withAdjustments(payer, -2L, created, 1L, another, 1L);
	private final TokenID tokenCreated = asToken("3.0.2");
	private final TokenTransferList tokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenCreated)
			.addAllTransfers(withAdjustments(payer, -2L, created, 1L, another, 1L).getAccountAmountsList())
			.build();

	private static final String memo = "TEST_MEMO";
	private static final String hashString = "TEST";
	private static final long scheduleNum = 100L;
	private static final String account = "0.0.10001";
	private final Instant timestamp = Instant.now();
	private final byte[] hash = hashString.getBytes(StandardCharsets.UTF_8);

	private ExpiringCreations subject;

	private final TxnReceipt receipt = TxnReceipt.newBuilder().setStatus(SUCCESS.name()).build();

	private final EntityId customFeeToken = new EntityId(0, 0, 123);
	private final EntityId customFeeCollector = new EntityId(0, 0, 124);
	private final List<AssessedCustomFee> customFeesCharged = List.of(new AssessedCustomFee(customFeeCollector, customFeeToken, 123L));


	@BeforeEach
	void setup() {
		subject = new ExpiringCreations(expiries, dynamicProperties, () -> accounts);

		expectedRecord = record;
		expectedRecord.setExpiry(expectedExpiry);
		expectedRecord.setSubmittingMember(submittingMember);

		subject.setRecordCache(recordCache);
	}

	void setUpForExpiringRecordBuilder() {
		given(accessor.getTxnId()).willReturn(TransactionID.newBuilder().setAccountID(asAccount(account)).build());
		given(accessor.getMemo()).willReturn(memo);
		given(accessor.isTriggeredTxn()).willReturn(true);
		given(accessor.getScheduleRef()).willReturn(ScheduleID.newBuilder().setScheduleNum(scheduleNum).build());
	}

	@Test
	void ifNotCreatingStatePayerRecordsDirectlyTracksWithCache() {
		given(dynamicProperties.shouldKeepRecordsInState()).willReturn(false);
		given(dynamicProperties.cacheRecordsTtl()).willReturn(cacheTtl);

		// when:
		var actual = subject.saveExpiringRecord(effPayer, record, now, submittingMember);

		// then:
		verify(recordCache).trackForExpiry(expectedRecord);
		// and:
		verify(expiries, never()).trackRecordInState(effPayer, expectedExpiry);
		// and:
		assertEquals(expectedRecord, actual);
	}

	@Test
	void addsToPayerRecordsAndTracks() {
		// setup:
		final var key = MerkleEntityId.fromAccountId(effPayer);
		final var payerAccount = new MerkleAccount();
		given(accounts.getForModify(key)).willReturn(payerAccount);
		given(dynamicProperties.shouldKeepRecordsInState()).willReturn(true);
		given(dynamicProperties.cacheRecordsTtl()).willReturn(cacheTtl);

		// when:
		var actual = subject.saveExpiringRecord(effPayer, record, now, submittingMember);

		// then:
		assertEquals(expectedRecord, actual);
		// and:
		verify(expiries).trackRecordInState(effPayer, expectedExpiry);
		assertEquals(expectedRecord, payerAccount.records().peek());
	}

	@Test
	void noopFormDoesNothing() {
		// expect:
		Assertions.assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.saveExpiringRecord(
						null, null, 0L, submittingMember));
		Assertions.assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.buildExpiringRecord(
						0L, null, null, null, null, null, null, null));
		Assertions.assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.buildFailedExpiringRecord(null, null));
	}

	@Test
	void validateBuildExpiringRecord() {
		//given:
		setUpForExpiringRecordBuilder();
		given(ctx.narratedCharging()).willReturn(narratedCharging);
		given(narratedCharging.totalFeesChargedToPayer()).willReturn(10L);

		given(ctx.ledger()).willReturn(ledger);
		given(ctx.ledger().netTransfersInTxn()).willReturn(transfers);
		given(ctx.ledger().netTokenTransfersInTxn()).willReturn(List.of(tokenTransfers));

		//when:
		ExpirableTxnRecord.Builder builder =
				subject.buildExpiringRecord(100L, hash, accessor, timestamp, receipt, null, ctx, customFeesCharged);
		ExpirableTxnRecord actualRecord = builder.build();

		//then:
		assertEquals(memo, actualRecord.getMemo());
		assertEquals(SUCCESS, ResponseCodeEnum.valueOf(actualRecord.getReceipt().getStatus()));
		assertEquals(scheduleNum, actualRecord.getScheduleRef().num());
		assertEquals(timestamp.getEpochSecond(), actualRecord.getConsensusTimestamp().getSeconds());
		assertEquals(timestamp.getNano(), actualRecord.getConsensusTimestamp().getNanos());
		assertEquals(asAccount(account).getAccountNum(), actualRecord.getTxnId().getPayerAccount().num());
		assertEquals(hex(ByteString.copyFrom(hashString.getBytes(StandardCharsets.UTF_8)).toByteArray()),
				hex(actualRecord.getTxnHash()));
		assertEquals(110L, actualRecord.getFee());
		//and:
		List<CurrencyAdjustments> tokenTransferListExpected = getTokenAdjustments(List.of(tokenTransfers)).getValue();
		List<EntityId> tokensExpected = getTokenAdjustments(List.of(tokenTransfers)).getKey();

		//verify:

		assertEquals(tokenTransferListExpected.size(), actualRecord.getTokenAdjustments().size());
		assertEquals(tokensExpected.size(), actualRecord.getTokens().size());
		for (int i = 0; i < tokensExpected.size(); i++) {
			assertEquals(tokensExpected.get(i), actualRecord.getTokens().get(i));
		}
		for (int i = 0; i < tokenTransferListExpected.size(); i++) {
			assertEquals(tokenTransferListExpected.get(i), actualRecord.getTokenAdjustments().get(i));
		}

		assertEquals(1, actualRecord.getCustomFeesCharged().size());
		assertEquals(customFeesCharged.get(0), actualRecord.getCustomFeesCharged().get(0));
	}

	@Test
	void canOverrideTokenTransfers() {
		//given:
		setUpForExpiringRecordBuilder();
		given(ctx.narratedCharging()).willReturn(narratedCharging);
		given(narratedCharging.totalFeesChargedToPayer()).willReturn(123L);
		given(ctx.ledger()).willReturn(ledger);
		given(ledger.netTransfersInTxn()).willReturn(transfers);
		final var someTokenXfers = List.of(TokenTransferList.newBuilder()
				.setToken(IdUtils.asToken("1.2.3"))
				.addAllTransfers(
						withAdjustments(payer, -100,
								asAccount("0.0.3"), 10,
								asAccount("0.0.98"), 90).getAccountAmountsList())
				.build());

		//when:
		final var builder =
				subject.buildExpiringRecord(100L, hash, accessor, timestamp, receipt, someTokenXfers, ctx, null);
		final var actualRecord = builder.build();

		//then:
		verify(ledger, never()).netTokenTransfersInTxn();
		assertEquals(someTokenXfers, actualRecord.asGrpc().getTokenTransferListsList());
	}

	@Test
	void validateBuildFailedExpiringRecord() {
		//given:
		setUpForExpiringRecordBuilder();
		given(accessor.getHash()).willReturn(hash);
		//when:
		ExpirableTxnRecord.Builder builder =
				subject.buildFailedExpiringRecord(accessor, timestamp);
		ExpirableTxnRecord actualRecord = builder.build();

		//then:
		assertEquals(memo, actualRecord.getMemo());
		assertEquals(FAIL_INVALID, ResponseCodeEnum.valueOf(actualRecord.getReceipt().getStatus()));
		assertEquals(scheduleNum, actualRecord.getScheduleRef().num());
		assertEquals(timestamp.getEpochSecond(), actualRecord.getConsensusTimestamp().getSeconds());
		assertEquals(timestamp.getNano(), actualRecord.getConsensusTimestamp().getNanos());
		assertEquals(asAccount(account).getAccountNum(), actualRecord.getTxnId().getPayerAccount().num());
		assertEquals(Hex.encodeHexString(ByteString.copyFrom(hashString.getBytes(StandardCharsets.UTF_8)).toByteArray()),
				Hex.encodeHexString(actualRecord.getTxnHash()));
		assertEquals(0L, actualRecord.getFee());
		//and:
		assertNull(actualRecord.getTokenAdjustments());
		assertNull(actualRecord.getTokens());
	}

	private Pair<List<EntityId>, List<CurrencyAdjustments>> getTokenAdjustments(
			List<TokenTransferList> tokenTransferList) {
		List<EntityId> tokens = new ArrayList<>();
		List<CurrencyAdjustments> tokenAdjustments = new ArrayList<>();
		if (tokenTransferList.size() > 0) {
			for (TokenTransferList tokenTransfers : tokenTransferList) {
				tokens.add(EntityId.fromGrpcTokenId(tokenTransfers.getToken()));
				tokenAdjustments.add(CurrencyAdjustments.fromGrpc(tokenTransfers.getTransfersList()));
			}
		}
		return new Pair<>(tokens, tokenAdjustments);
	}
}
