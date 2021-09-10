package com.hedera.services.fees.calculation;

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
import com.hedera.services.fees.calculation.utils.AccessorBasedUsages;
import com.hedera.services.fees.calculation.utils.OpUsageCtxHelper;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.consensus.ConsensusOpsUsage;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.CryptoTransferMeta;
import com.hedera.services.usage.file.FileAppendMeta;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hedera.services.usage.token.meta.TokenCreateMeta;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.services.state.submerkle.FcCustomFee.fractionalFee;
import static com.hedera.services.utils.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccessorBasedUsagesTest {
	private final String memo = "Even the most cursory inspection would yield that...";
	private final long now = 1_234_567L;
	private final SigUsage sigUsage = new SigUsage(1, 2, 3);
	private final ExpandHandleSpanMapAccessor spanMapAccessor = new ExpandHandleSpanMapAccessor();

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private TxnAccessor txnAccessor;
	@Mock
	private OpUsageCtxHelper opUsageCtxHelper;
	@Mock
	private FileOpsUsage fileOpsUsage;
	@Mock
	private TokenOpsUsage tokenOpsUsage;
	@Mock
	private CryptoOpsUsage cryptoOpsUsage;
	@Mock
	private ConsensusOpsUsage consensusOpsUsage;
	@Mock
	private GlobalDynamicProperties dynamicProperties;

	private AccessorBasedUsages subject;

	@BeforeEach
	void setUp() {
		subject = new AccessorBasedUsages(
				fileOpsUsage, tokenOpsUsage, cryptoOpsUsage, opUsageCtxHelper, consensusOpsUsage, dynamicProperties);
	}

	@Test
	void throwsIfNotSupported() {
		// setup:
		final var accumulator = new UsageAccumulator();

		given(txnAccessor.getFunction()).willReturn(ContractCreate);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.assess(sigUsage, txnAccessor, accumulator));
	}

	@Test
	void worksAsExpectedForFileAppend() {
		// setup:
		final var baseMeta = new BaseTransactionMeta(100, 2);
		final var opMeta = new FileAppendMeta(1_234, 1_234_567L);
		final var accumulator = new UsageAccumulator();

		given(txnAccessor.getFunction()).willReturn(FileAppend);
		given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
		given(txnAccessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
		given(opUsageCtxHelper.metaForFileAppend(TransactionBody.getDefaultInstance())).willReturn(opMeta);

		// expect:
		subject.assess(sigUsage, txnAccessor, accumulator);

		// then:
		verify(fileOpsUsage).fileAppendUsage(sigUsage, opMeta, baseMeta, accumulator);
	}

	@Test
	void worksAsExpectedForCryptoTransfer() {
		// setup:
		int multiplier = 30;
		final var baseMeta = new BaseTransactionMeta(100, 2);
		final var xferMeta = new CryptoTransferMeta(1, 3, 7, 4);
		final var usageAccumulator = new UsageAccumulator();

		given(dynamicProperties.feesTokenTransferUsageMultiplier()).willReturn(multiplier);
		given(txnAccessor.getFunction()).willReturn(CryptoTransfer);
		given(txnAccessor.availXferUsageMeta()).willReturn(xferMeta);
		given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);

		// when:
		subject.assess(sigUsage, txnAccessor, usageAccumulator);

		// then:
		verify(cryptoOpsUsage).cryptoTransferUsage(sigUsage, xferMeta, baseMeta, usageAccumulator);
		// and:
		assertEquals(multiplier, xferMeta.getTokenMultiplier());
	}

	@Test
	void worksAsExpectedForSubmitMessage() {
		// setup:
		final var baseMeta = new BaseTransactionMeta(100, 0);
		final var submitMeta = new SubmitMessageMeta(1_234);
		final var usageAccumulator = new UsageAccumulator();

		given(txnAccessor.getFunction()).willReturn(ConsensusSubmitMessage);
		given(txnAccessor.availSubmitUsageMeta()).willReturn(submitMeta);
		given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);

		// when:
		subject.assess(sigUsage, txnAccessor, usageAccumulator);

		// then:
		verify(consensusOpsUsage).submitMessageUsage(sigUsage, submitMeta, baseMeta, usageAccumulator);
	}

	@Test
	void worksAsExpectedForFeeScheduleUpdate() {
		// setup:
		final var realAccessor = uncheckedFrom(signedFeeScheduleUpdateTxn());

		final var op = feeScheduleUpdateTxn().getTokenFeeScheduleUpdate();
		final var opMeta = new FeeScheduleUpdateMeta(now, 234);
		final var baseMeta = new BaseTransactionMeta(memo.length(), 0);
		final var feeScheduleCtx = new ExtantFeeScheduleContext(now, 123);

		given(opUsageCtxHelper.ctxForFeeScheduleUpdate(op)).willReturn(feeScheduleCtx);
		// and:
		spanMapAccessor.setFeeScheduleUpdateMeta(realAccessor, opMeta);

		// when:
		final var accum = new UsageAccumulator();
		// and:
		subject.assess(sigUsage, realAccessor, accum);

		// then:
		verify(tokenOpsUsage).feeScheduleUpdateUsage(sigUsage, baseMeta, opMeta, feeScheduleCtx, accum);
	}


	@Test
	void worksAsExpectedForTokenCreate() {
		// setup:
		final var baseMeta = new BaseTransactionMeta(100, 2);
		final var opMeta = new TokenCreateMeta.Builder()
				.baseSize(1_234)
				.customFeeScheleSize(0)
				.lifeTime(1_234_567L)
				.fungibleNumTransfers(0)
				.nftsTranfers(0)
				.nftsTranfers(1000)
				.nftsTranfers(1)
				.build();
		final var accumulator = new UsageAccumulator();


		given(txnAccessor.getFunction()).willReturn(TokenCreate);
		given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
		given(txnAccessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
		given(txnAccessor.getSpanMapAccessor().getTokenCreateMeta(any())).willReturn(opMeta);

		// expect:
		subject.assess(sigUsage, txnAccessor, accumulator);

		// then:
		verify(tokenOpsUsage).tokenCreateUsage(sigUsage, baseMeta, opMeta,  accumulator);
	}

	@Test
	void worksAsExpectedForCryptoCreate() {
		final var baseMeta = new BaseTransactionMeta(100, 0);
		final var opMeta = new CryptoCreateMeta.Builder()
				.baseSize(1_234)
				.lifeTime(1_234_567L)
				.maxAutomaticAssociations(3)
				.build();
		final var accumulator = new UsageAccumulator();

		given(txnAccessor.getFunction()).willReturn(CryptoCreate);
		given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
		given(txnAccessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
		given(txnAccessor.getSpanMapAccessor().getCryptoCreateMeta(any())).willReturn(opMeta);

		subject.assess(sigUsage, txnAccessor, accumulator);

		verify(cryptoOpsUsage).cryptoCreateUsage(sigUsage, baseMeta, opMeta, accumulator);
	}


	@Test
	void supportsIfInSet() {
		// expect:
		assertTrue(subject.supports(CryptoTransfer));
		assertTrue(subject.supports(ConsensusSubmitMessage));
		assertTrue(subject.supports(CryptoCreate));
		assertFalse(subject.supports(CryptoUpdate));
	}

	private Transaction signedFeeScheduleUpdateTxn() {
		return Transaction.newBuilder()
				.setSignedTransactionBytes(SignedTransaction.newBuilder()
						.setBodyBytes(feeScheduleUpdateTxn().toByteString())
						.build().toByteString())
				.build();
	}

	private TransactionBody feeScheduleUpdateTxn() {
		return TransactionBody.newBuilder()
				.setMemo(memo)
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.newBuilder()
						.addAllCustomFees(fees()))
				.build();
	}

	private List<CustomFee> fees() {
		final var collector = new EntityId(1, 2 ,3);
		final var aDenom = new EntityId(2, 3 ,4);
		final var bDenom = new EntityId(3, 4 ,5);

		return List.of(
				fixedFee(1, null, collector),
				fixedFee(2, aDenom, collector),
				fixedFee(2, bDenom, collector),
				fractionalFee(
						1, 2, 1, 2, false, collector),
				fractionalFee(
						1, 3, 1, 2, false, collector),
				fractionalFee(
						1, 4, 1, 2, false, collector)
		).stream().map(FcCustomFee::asGrpc).collect(toList());
	}
}
