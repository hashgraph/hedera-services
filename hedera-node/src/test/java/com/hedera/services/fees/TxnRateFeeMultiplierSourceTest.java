package com.hedera.services.fees;

import com.hedera.services.throttling.DeterministicThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.mockito.BDDMockito.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(MockitoExtension.class)
class TxnRateFeeMultiplierSourceTest {
	@Mock
	FunctionalityThrottling throttling;

	TxnRateFeeMultiplierSource subject;

	@BeforeEach
	void setUp() {
		subject = new TxnRateFeeMultiplierSource(throttling);
	}

	@ParameterizedTest
	@CsvSource({
			"1, 10, 1, 10, 1",
			"9, 10, 1, 10, 10",
			"1, 10, 9, 10, 10",
			"89, 100, 1, 10, 1",
			"1, 10, 89, 100, 1",
			"2, 2, 1, 10, 100",
			"0, 2, 950, 1000, 25",
	})
	public void usesExpectedMultiplier(
			long firstUsed,
			long firstCapacity,
			long secondUsed,
			long secondCapacity,
			long expectedMultiplier
	) {
		given(throttling.throttleStatesFor(CryptoTransfer))
				.willReturn(snapshotsWith(firstUsed, firstCapacity, secondUsed, secondCapacity));

		// when:
		subject.updateMultiplier();
		// and:
		long actualMultiplier = subject.currentMultiplier();

		// then:
		Assertions.assertEquals(expectedMultiplier, actualMultiplier);
	}

	private List<DeterministicThrottle.StateSnapshot> snapshotsWith(
			long firstUsed, long firstCapacity,
			long secondUsed, long secondCapacity
	) {
		return List.of(
				new DeterministicThrottle.StateSnapshot(firstUsed, firstCapacity),
				new DeterministicThrottle.StateSnapshot(secondUsed, secondCapacity)
		);
	}
}