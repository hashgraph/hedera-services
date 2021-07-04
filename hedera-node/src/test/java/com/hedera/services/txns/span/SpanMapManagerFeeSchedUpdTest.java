package com.hedera.services.txns.span;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.services.state.submerkle.FcCustomFee.fractionalFee;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SpanMapManagerFeeSchedUpdTest {
	final long now = 1_234_567L;
	final TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();
	final ExpandHandleSpanMapAccessor spanMapAccessor = new ExpandHandleSpanMapAccessor();

	final SpanMapManager subject = new SpanMapManager(null, null, null);

	@Test
	void setsFeeScheduleUpdateMeta() {
		// setup:
		final var txn = signedFeeScheduleUpdateTxn();
		final var expectedGrpcReprBytes =
				feeScheduleUpdateTxn().getTokenFeeScheduleUpdate().getSerializedSize()
						- FeeBuilder.BASIC_ENTITY_ID_SIZE;
		final var expectedReprBytes = tokenOpsUsage.bytesNeededToRepr(fees());

		// given:
		final var accessor = SignedTxnAccessor.uncheckedFrom(txn);

		// when:
		subject.expandSpan(accessor);
		// and:
		final var expandedMeta = spanMapAccessor.getFeeScheduleUpdateMeta(accessor);

		// then:
		assertEquals(now, expandedMeta.effConsensusTime());
		assertEquals(expectedReprBytes, expandedMeta.numBytesInNewFeeScheduleRepr());
		assertEquals(expectedGrpcReprBytes, expandedMeta.numBytesInGrpcFeeScheduleRepr());
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
				fractionalFee(1, 2, 1, 2, collector),
				fractionalFee(1, 3, 1, 2, collector),
				fractionalFee(1, 4, 1, 2, collector)
		).stream().map(FcCustomFee::asGrpc).collect(toList());
	}
}