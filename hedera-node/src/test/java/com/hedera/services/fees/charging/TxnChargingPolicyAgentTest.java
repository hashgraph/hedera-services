package com.hedera.services.fees.charging;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.state.logic.AwareNodeDiligenceScreen;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hedera.services.txns.diligence.DuplicateClassification.DUPLICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TxnChargingPolicyAgentTest {
	private final long submittingNode = 1L;
	private final JKey payerKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
	private final FeeObject mockFees = new FeeObject(1L, 2L, 3L);
	private final TxnAccessor accessor = SignedTxnAccessor.uncheckedFrom(Transaction.newBuilder()
			.setBodyBytes(TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder()
					.setTransactionValidStart(Timestamp.newBuilder()
							.setSeconds(1_234_567L)
							.build())
					.setAccountID(IdUtils.asAccount("0.0.1234")))
					.build()
					.toByteString())
			.build());

	@Mock
	private StateView currentView;
	@Mock
	private FeeCalculator fees;
	@Mock
	private TxnIdRecentHistory recentHistory;
	@Mock
	private FeeChargingPolicy chargingPolicy;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private AwareNodeDiligenceScreen nodeDiligenceScreen;
	@Mock
	private Map<TransactionID, TxnIdRecentHistory> txnHistories;

	private TxnChargingPolicyAgent subject;

	@BeforeEach
	void setUp() {
		subject = new TxnChargingPolicyAgent(
				fees, chargingPolicy, txnCtx, () -> currentView, nodeDiligenceScreen, txnHistories);
	}

	@Test
	void appliesForLackOfNodeDueDiligence() {
		givenBaseCtx();
		given(nodeDiligenceScreen.nodeIgnoredDueDiligence(BELIEVED_UNIQUE)).willReturn(true);

		// when:
		final var shouldContinue = subject.applyPolicyFor(accessor);

		// then:
		assertFalse(shouldContinue);
		verify(chargingPolicy).applyForIgnoredDueDiligence(mockFees);
	}

	@Test
	void appliesForPayerDuplicate() {
		givenBaseCtx();
		given(txnCtx.submittingSwirldsMember()).willReturn(submittingNode);
		given(txnHistories.get(accessor.getTxnId())).willReturn(recentHistory);
		given(recentHistory.currentDuplicityFor(submittingNode)).willReturn(DUPLICATE);

		// when:
		final var shouldContinue = subject.applyPolicyFor(accessor);

		// then:
		assertFalse(shouldContinue);
		verify(txnCtx).setStatus(DUPLICATE_TRANSACTION);
		verify(chargingPolicy).applyForDuplicate(mockFees);
	}

	@Test
	void appliesForNonOkOutcome() {
		givenBaseCtx();
		given(chargingPolicy.apply(mockFees)).willReturn(INSUFFICIENT_PAYER_BALANCE);

		// when:
		final var shouldContinue = subject.applyPolicyFor(accessor);

		// then:
		assertFalse(shouldContinue);
		verify(txnCtx).setStatus(INSUFFICIENT_PAYER_BALANCE);
		verify(chargingPolicy).apply(mockFees);
	}

	@Test
	void appliesForOkOutcome() {
		givenBaseCtx();
		given(chargingPolicy.apply(mockFees)).willReturn(OK);

		// when:
		final var shouldContinue = subject.applyPolicyFor(accessor);

		// then:
		assertTrue(shouldContinue);
		verify(txnCtx, never()).setStatus(any());
		verify(chargingPolicy).apply(mockFees);
	}

	private void givenBaseCtx() {
		given(txnCtx.activePayerKey()).willReturn(payerKey);
		given(fees.computeFee(accessor, payerKey, currentView)).willReturn(mockFees);
	}
}