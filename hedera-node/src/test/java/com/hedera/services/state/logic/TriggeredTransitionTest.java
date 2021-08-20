package com.hedera.services.state.logic;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.charging.FeeChargingPolicy;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.fee.FeeObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TriggeredTransitionTest {
	private final JKey activePayerKey = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
	private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
	private final FeeObject fee = new FeeObject(1, 2, 3);

	@Mock
	private TxnAccessor accessor;
	@Mock
	private StateView currentView;
	@Mock
	private FeeCalculator fees;
	@Mock
	private FeeChargingPolicy chargingPolicy;
	@Mock
	private NetworkCtxManager networkCtxManager;
	@Mock
	private ScreenedTransition screenedTransition;
	@Mock
	private TransactionContext txnCtx;

	private TriggeredTransition subject;

	@BeforeEach
	void setUp() {
		subject = new TriggeredTransition(
				currentView, fees, chargingPolicy, txnCtx, networkCtxManager, screenedTransition);
	}

	@Test
	void happyPathFlows() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(txnCtx.activePayerKey()).willReturn(activePayerKey);
		given(fees.computeFee(accessor, activePayerKey, currentView)).willReturn(fee);
		given(chargingPolicy.applyForTriggered(fee)).willReturn(OK);

		// when:
		subject.run();

		// then:
		verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
		verify(networkCtxManager).prepareForIncorporating(accessor);
		verify(screenedTransition).finishFor(accessor);
	}

	@Test
	void abortsOnChargingFailure() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(txnCtx.activePayerKey()).willReturn(activePayerKey);
		given(fees.computeFee(accessor, activePayerKey, currentView)).willReturn(fee);
		given(chargingPolicy.applyForTriggered(fee)).willReturn(INSUFFICIENT_TX_FEE);

		// when:
		subject.run();

		// then:
		verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
		verify(networkCtxManager).prepareForIncorporating(accessor);
		verify(txnCtx).setStatus(INSUFFICIENT_TX_FEE);
		verify(screenedTransition, never()).finishFor(accessor);
	}
}