package com.hedera.services.throttling;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static org.mockito.BDDMockito.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TxnAwareHandleThrottlingTest {
	Instant consensusTime = Instant.ofEpochSecond(1_234_567L, 123);

	@Mock
	TimedFunctionalityThrottling delegate;
	@Mock
	TransactionContext txnCtx;

	TxnAwareHandleThrottling subject;

	@BeforeEach
	void setUp() {
		subject = new TxnAwareHandleThrottling(txnCtx, delegate);
	}

	@Test
	void delegatesThrottlingDecisionsWithConsensusTime() {
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		given(delegate.shouldThrottle(HederaFunctionality.CryptoTransfer, consensusTime)).willReturn(true);

		// expect:
		assertTrue(subject.shouldThrottle(HederaFunctionality.CryptoTransfer));
		// and:
		verify(delegate).shouldThrottle(HederaFunctionality.CryptoTransfer, consensusTime);
	}

	@Test
	void otherMethodsPassThrough() {
		// setup:
		ThrottleDefinitions defs = new ThrottleDefinitions();
		List<DeterministicThrottle> whatever = List.of(DeterministicThrottle.withTps(1));

		given(delegate.allActiveThrottles()).willReturn(whatever);
		given(delegate.activeThrottlesFor(HederaFunctionality.CryptoTransfer)).willReturn(whatever);

		// when:
		var all = subject.allActiveThrottles();
		var onlyXfer = subject.activeThrottlesFor(HederaFunctionality.CryptoTransfer);
		subject.rebuildFor(defs);

		// then:
		verify(delegate).rebuildFor(defs);
		assertSame(whatever, all);
		assertSame(whatever, onlyXfer);
	}
}