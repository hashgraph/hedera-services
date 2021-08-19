package com.hedera.services.state.logic;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.txns.TransitionRunner;
import com.hedera.services.utils.TxnAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.security.ops.SystemOpAuthorization.IMPERMISSIBLE;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HandleTransactionTest {
	@Mock
	private Rationalization rationalization;
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

	private HandleTransaction subject;

	@BeforeEach
	void setUp() {
		subject = new HandleTransaction(
				rationalization,
				transitionRunner,
				opPolicies,
				networkCtxManager,
				txnCtx,
				chargingPolicyAgent,
				activationHelper);
	}

	@Test
	void finishesTransitionWithAuthFailure() {
		given(opPolicies.check(accessor)).willReturn(IMPERMISSIBLE);

		// when:
		subject.finishTransition(accessor);

		// then:
		verify(txnCtx).setStatus(IMPERMISSIBLE.asStatus());
	}
}