package com.hedera.services.txns.span;

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
import com.hedera.services.grpc.marshalling.CustomFeeMeta;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hedera.services.usage.crypto.CryptoTransferMeta;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpanMapManagerTest {
	private final int maxHbarAdjusts = 1;
	private final int maxTokenAdjusts = 2;
	private final int maxOwnershipChanges = 3;
	private final boolean areNftsEnabled = false;
	private final int maxFeeNesting = 4;
	private final int maxBalanceChanges = 5;
	private final ImpliedTransfersMeta.ValidationProps validationProps = new ImpliedTransfersMeta.ValidationProps(
			maxHbarAdjusts, maxTokenAdjusts, maxOwnershipChanges, maxFeeNesting, maxBalanceChanges, areNftsEnabled);
	private final ImpliedTransfersMeta.ValidationProps otherValidationProps = new ImpliedTransfersMeta.ValidationProps(
			maxHbarAdjusts, maxTokenAdjusts, maxOwnershipChanges + 1, maxFeeNesting, maxBalanceChanges, areNftsEnabled);
	private final TransactionBody pretendXferTxn = TransactionBody.getDefaultInstance();
	private final ImpliedTransfers someImpliedXfers = ImpliedTransfers.invalid(
			validationProps, ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);
	private final ImpliedTransfers someOtherImpliedXfers = ImpliedTransfers.invalid(
			otherValidationProps, ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);

	private final Id treasury = new Id(0 , 0, 2);
	private final Id customFeeToken = new Id(0, 0, 123);
	private final Id customFeeCollector = new Id(0, 0, 124);
	final List<CustomFeeMeta> entityCustomFees = List.of(
			new CustomFeeMeta(customFeeToken, treasury, new ArrayList<>()));

	final List<CustomFeeMeta> newCustomFeeChanges = List.of(
			new CustomFeeMeta(
					customFeeToken, treasury, List.of(FcCustomFee.fixedFee(
							10L, customFeeToken.asEntityId(), customFeeCollector.asEntityId()))));
	private final List<FcAssessedCustomFee> assessedCustomFees = List.of(
			new FcAssessedCustomFee(customFeeCollector.asEntityId(), customFeeToken.asEntityId(), 123L),
			new FcAssessedCustomFee(customFeeCollector.asEntityId(), 123L)
	);

	private final ImpliedTransfers validImpliedTransfers = ImpliedTransfers.valid(
			validationProps, new ArrayList<>(), entityCustomFees, assessedCustomFees);
	private final ImpliedTransfers feeChangedImpliedTransfers = ImpliedTransfers.valid(
			otherValidationProps, new ArrayList<>(), newCustomFeeChanges, assessedCustomFees);

	private final ExpandHandleSpanMapAccessor spanMapAccessor = new ExpandHandleSpanMapAccessor();

	private CryptoTransferMeta xferMeta = new CryptoTransferMeta(1, 1, 1, 0);

	private Map<String, Object> span = new HashMap<>();

	@Mock
	private TxnAccessor accessor;
	@Mock
	private ImpliedTransfersMarshal impliedTransfersMarshal;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private ImpliedTransfers mockImpliedTransfers;
	@Mock
	private CustomFeeSchedules customFeeSchedules;
	@Mock
	private HederaLedger ledger;
	private SpanMapManager subject;

	@BeforeEach
	void setUp() {
		subject = new SpanMapManager(impliedTransfersMarshal, dynamicProperties, customFeeSchedules, ledger);
	}

	@Test
	void expandsImpliedTransfersForCryptoTransfer() {
		given(accessor.getTxn()).willReturn(pretendXferTxn);
		given(accessor.getSpanMap()).willReturn(span);
		given(accessor.getFunction()).willReturn(CryptoTransfer);
		given(accessor.availXferUsageMeta()).willReturn(xferMeta);
		given(impliedTransfersMarshal.unmarshalFromGrpc(pretendXferTxn.getCryptoTransfer(), ledger))
				.willReturn(someImpliedXfers);

		// when:
		subject.expandSpan(accessor);

		// then:
		assertSame(someImpliedXfers, spanMapAccessor.getImpliedTransfers(accessor));
	}

	@Test
	void expandsImpliedTransfersWithDetails() {
		given(accessor.getTxn()).willReturn(pretendXferTxn);
		given(accessor.getSpanMap()).willReturn(span);
		given(accessor.getFunction()).willReturn(CryptoTransfer);
		given(accessor.availXferUsageMeta()).willReturn(xferMeta);
		given(impliedTransfersMarshal.unmarshalFromGrpc(pretendXferTxn.getCryptoTransfer(), ledger))
				.willReturn(mockImpliedTransfers);
		given(mockImpliedTransfers.getAssessedCustomFees()).willReturn(assessedCustomFees);

		// when:
		subject.expandSpan(accessor);

		// then:
		assertEquals(1, xferMeta.getCustomFeeTokenTransfers());
		assertEquals(1, xferMeta.getNumTokensInvolved());
		assertEquals(1, xferMeta.getCustomFeeHbarTransfers());
	}

	@Test
	void doesntRecomputeImpliedTransfersIfMetaMatches() {
		given(accessor.getSpanMap()).willReturn(span);
		given(accessor.getFunction()).willReturn(CryptoTransfer);
		given(dynamicProperties.maxTransferListSize()).willReturn(maxHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxTokenAdjusts);
		given(dynamicProperties.maxNftTransfersLen()).willReturn(maxOwnershipChanges);
		given(dynamicProperties.maxXferBalanceChanges()).willReturn(maxBalanceChanges);
		given(dynamicProperties.maxCustomFeeDepth()).willReturn(maxFeeNesting);
		spanMapAccessor.setImpliedTransfers(accessor, someImpliedXfers);

		// when:
		subject.rationalizeSpan(accessor);

		// then:
		verify(impliedTransfersMarshal, never()).unmarshalFromGrpc(any(), any());
		assertSame(someImpliedXfers, spanMapAccessor.getImpliedTransfers(accessor));
	}

	@Test
	void recomputesImpliedTransfersIfMetaNotMatches() {
		given(accessor.getTxn()).willReturn(pretendXferTxn);
		given(accessor.getSpanMap()).willReturn(span);
		given(accessor.getFunction()).willReturn(CryptoTransfer);
		given(accessor.availXferUsageMeta()).willReturn(xferMeta);
		given(dynamicProperties.maxTransferListSize()).willReturn(maxHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxTokenAdjusts + 1);
		spanMapAccessor.setImpliedTransfers(accessor, someImpliedXfers);
		given(impliedTransfersMarshal.unmarshalFromGrpc(pretendXferTxn.getCryptoTransfer(), ledger))
				.willReturn(someOtherImpliedXfers);

		// when:
		subject.rationalizeSpan(accessor);

		// then:
		verify(impliedTransfersMarshal).unmarshalFromGrpc(pretendXferTxn.getCryptoTransfer(), ledger);
		assertSame(someOtherImpliedXfers, spanMapAccessor.getImpliedTransfers(accessor));
	}

	@Test
	void recomputesImpliedTransfersIfCustomFeeChanges() {
		given(accessor.getTxn()).willReturn(pretendXferTxn);
		given(accessor.getSpanMap()).willReturn(span);
		given(accessor.getFunction()).willReturn(CryptoTransfer);
		given(accessor.availXferUsageMeta()).willReturn(xferMeta);
		given(dynamicProperties.maxTransferListSize()).willReturn(maxHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxTokenAdjusts + 1);
		spanMapAccessor.setImpliedTransfers(accessor, validImpliedTransfers);
		given(impliedTransfersMarshal.unmarshalFromGrpc(pretendXferTxn.getCryptoTransfer(), ledger))
				.willReturn(feeChangedImpliedTransfers);

		// when:
		subject.rationalizeSpan(accessor);

		// then:
		verify(impliedTransfersMarshal).unmarshalFromGrpc(pretendXferTxn.getCryptoTransfer(), ledger);
		assertSame(feeChangedImpliedTransfers, spanMapAccessor.getImpliedTransfers(accessor));
	}
}
