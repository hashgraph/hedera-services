package com.hedera.services.state.logic;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.txns.TransitionRunner;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.security.ops.SystemOpAuthorization.IMPERMISSIBLE;
import static com.hedera.services.security.ops.SystemOpAuthorization.UNNECESSARY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScreenedTransitionTest {
	@Mock
	private TransitionRunner transitionRunner;
	@Mock
	private SystemOpPolicies opPolicies;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private NetworkCtxManager networkCtxManager;
	@Mock
	private TxnAccessor accessor;

	private ScreenedTransition subject;

	@BeforeEach
	void setUp() {
		subject = new ScreenedTransition(transitionRunner, opPolicies, txnCtx, networkCtxManager);
	}

	@Test
	void finishesTransitionWithAuthFailure() {
		given(opPolicies.check(accessor)).willReturn(IMPERMISSIBLE);

		// when:
		subject.finishFor(accessor);

		// then:
		verify(txnCtx).setStatus(IMPERMISSIBLE.asStatus());
		verify(transitionRunner, never()).tryTransition(accessor);
	}

	@Test
	void incorporatesAfterFinishingWithSuccess() {
		given(accessor.getFunction()).willReturn(HederaFunctionality.CryptoTransfer);
		given(opPolicies.check(accessor)).willReturn(UNNECESSARY);
		given(transitionRunner.tryTransition(accessor)).willReturn(true);

		// when:
		subject.finishFor(accessor);

		// then:
		verify(transitionRunner).tryTransition(accessor);
		verify(networkCtxManager).finishIncorporating(HederaFunctionality.CryptoTransfer);
	}

	@Test
	void doesntIncorporateAfterFailedTransition() {
		given(opPolicies.check(accessor)).willReturn(UNNECESSARY);

		// when:
		subject.finishFor(accessor);

		// then:
		verify(networkCtxManager, never()).finishIncorporating(any());
	}
}