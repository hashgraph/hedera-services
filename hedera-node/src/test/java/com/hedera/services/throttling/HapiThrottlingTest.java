package com.hedera.services.throttling;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HapiThrottlingTest {
	@Mock
	TimedFunctionalityThrottling delegate;

	HapiThrottling subject;

	@BeforeEach
	void setUp() {
		subject = new HapiThrottling(delegate);
	}

	@Test
	void delegatesWithSomeInstant() {
		given(delegate.shouldThrottle(any(), any())).willReturn(true);

		// when:
		var ans = subject.shouldThrottle(CryptoTransfer);

		// then:
		assertTrue(ans);
		verify(delegate).shouldThrottle(eq(CryptoTransfer), any());
	}

	@Test
	void unsupportedMethodsThrow() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.activeThrottlesFor(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.allActiveThrottles());
	}

	@Test
	void delegatesRebuild() {
		// setup:
		ThrottleDefinitions defs = new ThrottleDefinitions();

		// when:
		subject.rebuildFor(defs);

		// then:
		verify(delegate).rebuildFor(defs);
	}
}