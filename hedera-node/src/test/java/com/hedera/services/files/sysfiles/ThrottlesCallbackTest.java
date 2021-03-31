package com.hedera.services.files.sysfiles;

import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.test.utils.SerdeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.BDDMockito.*;

import java.io.IOException;

@ExtendWith(MockitoExtension.class)
class ThrottlesCallbackTest {
	@Mock
	FeeMultiplierSource multiplierSource;
	@Mock
	FunctionalityThrottling hapiThrottling;
	@Mock
	FunctionalityThrottling handleThrottling;

	ThrottlesCallback subject;

	@BeforeEach
	void setUp() {
		subject = new ThrottlesCallback(multiplierSource, hapiThrottling, handleThrottling);
	}

	@Test
	void throttlesCbAsExpected() throws IOException {
		var throttles = SerdeUtils.protoDefs("bootstrap/throttles.json");

		// when:
		subject.throttlesCb().accept(throttles);

		// then:
		verify(hapiThrottling).rebuildFor(argThat(pojo -> pojo.toProto().equals(throttles)));
		verify(handleThrottling).rebuildFor(argThat(pojo -> pojo.toProto().equals(throttles)));
		verify(multiplierSource).resetExpectations();
	}
}