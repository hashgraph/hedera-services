package com.hedera.services.throttling;

import com.hedera.services.utils.TxnAccessor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TimedFunctionalityThrottlingTest {
	@Test
	void defaultImplsAsExpected() {
		// setup:
		final var accessor = mock(TxnAccessor.class);

		// given:
		final var subject = mock(TimedFunctionalityThrottling.class);
		// and:
		Mockito.doCallRealMethod().when(subject).shouldThrottleQuery(FileGetInfo);
		Mockito.doCallRealMethod().when(subject).shouldThrottleTxn(accessor);

		// when:
		subject.shouldThrottleQuery(FileGetInfo);
		subject.shouldThrottleTxn(accessor);

		// then:
		verify(subject).shouldThrottle(eq(FileGetInfo), any());
		verify(subject).shouldThrottleTxn(eq(accessor), any());
	}
}