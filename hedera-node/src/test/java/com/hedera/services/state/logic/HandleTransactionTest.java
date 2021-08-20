package com.hedera.services.state.logic;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.txns.TransitionRunner;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.services.security.ops.SystemOpAuthorization.IMPERMISSIBLE;
import static com.hedera.services.security.ops.SystemOpAuthorization.UNNECESSARY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HandleTransactionTest {
	private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);

	@Mock
	private SystemOpPolicies opPolicies;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private NetworkCtxManager networkCtxManager;
	@Mock
	private TxnChargingPolicyAgent chargingPolicyAgent;
	@Mock
	private InHandleActivationHelper activationHelper;
	@Mock
	private TxnAccessor accessor;
	@Mock
	private TransitionRunner transitionRunner;
	@Mock
	private SignatureScreen signatureScreen;
	@Mock
	private KeyActivationScreen keyActivationScreen;

	private HandleTransaction subject;

	@BeforeEach
	void setUp() {
		subject = new HandleTransaction(
				transitionRunner,
				opPolicies,
				networkCtxManager,
				txnCtx,
				signatureScreen,
				chargingPolicyAgent,
				keyActivationScreen);
	}

	@Test
	void happyPathScopedProcessFlows() {
		// setup:
		InOrder inOrder = Mockito.inOrder(networkCtxManager);

		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(signatureScreen.applyTo(accessor)).willReturn(OK);
		given(chargingPolicyAgent.applyPolicyFor(accessor)).willReturn(true);
		given(keyActivationScreen.reqKeysAreActiveGiven(OK)).willReturn(true);
		givenHappyTransition();

		// when:
		subject.runTopLevelProcess();

		// then:
		inOrder.verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
		inOrder.verify(signatureScreen).applyTo(accessor);
		inOrder.verify(chargingPolicyAgent).applyPolicyFor(accessor);
		inOrder.verify(keyActivationScreen).reqKeysAreActiveGiven(OK);
		verifyTransitionIsComplete();
	}

	@Test
	void abortsWhenChargingPolicyAgentFails() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(signatureScreen.applyTo(accessor)).willReturn(OK);

		// when:
		subject.runTopLevelProcess();

		// then:
		verifyTransitionIsIncomplete();
	}

	@Test
	void abortsWhenKeyActivationScreenFails() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(signatureScreen.applyTo(accessor)).willReturn(OK);
		given(chargingPolicyAgent.applyPolicyFor(accessor)).willReturn(true);

		// when:
		subject.runTopLevelProcess();

		// then:
		verifyTransitionIsIncomplete();
	}

	@Test
	void finishesTransitionWithAuthFailure() {
		given(opPolicies.check(accessor)).willReturn(IMPERMISSIBLE);

		// when:
		subject.finishTransition(accessor);

		// then:
		verify(txnCtx).setStatus(IMPERMISSIBLE.asStatus());
		verify(transitionRunner, never()).tryTransition(accessor);
	}

	@Test
	void incorporatesAfterFinishingWithSuccess() {
		givenHappyTransition();

		// when:
		subject.finishTransition(accessor);

		// then:
		verifyTransitionIsComplete();
	}

	private void givenHappyTransition() {
		given(accessor.getFunction()).willReturn(HederaFunctionality.CryptoTransfer);
		given(opPolicies.check(accessor)).willReturn(UNNECESSARY);
		given(transitionRunner.tryTransition(accessor)).willReturn(true);
	}

	private void verifyTransitionIsComplete() {
		verify(transitionRunner).tryTransition(accessor);
		verify(networkCtxManager).finishIncorporating(HederaFunctionality.CryptoTransfer);
	}

	private void verifyTransitionIsIncomplete() {
		verify(transitionRunner, never()).tryTransition(accessor);
	}
}