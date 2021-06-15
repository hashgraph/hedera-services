package com.hedera.services.txns.span;

import com.hedera.services.utils.TxnAccessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ExpandHandleSpanMapAccessorTest {
	private Map<String, Object>	span = new HashMap<>();

	@Mock
	private TxnAccessor accessor;

	private ExpandHandleSpanMapAccessor subject;

	@BeforeEach
	void setUp() {
		subject = new ExpandHandleSpanMapAccessor();

		given(accessor.getSpanMap()).willReturn(span);
	}

	@Test
	void testsForImpliedXfersAsExpected() {
		// expect:
		Assertions.assertDoesNotThrow(() -> subject.getImpliedTransfers(accessor));
	}
}