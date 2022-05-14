package com.hedera.services.contracts.execution;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.services.contracts.execution.BlockMetaSource.UNAVAILABLE_BLOCK_HASH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StaticBlockMetaSourceTest {
	@Mock
	private StateView stateView;
	@Mock
	private MerkleNetworkContext networkCtx;

	private StaticBlockMetaSource subject;

	@BeforeEach
	void setUp() {
		given(stateView.networkCtx()).willReturn(networkCtx);

		subject = StaticBlockMetaSource.from(stateView);
	}

	@Test
	void blockValuesAreForCurrentBlockAfterSync() {
		given(networkCtx.getAlignmentBlockNo()).willReturn(someBlockNo);
		given(networkCtx.firstConsTimeOfCurrentBlock()).willReturn(then);

		final var ans = subject.computeBlockValues(gasLimit);

		assertEquals(gasLimit, ans.getGasLimit());
		assertEquals(someBlockNo, ans.getNumber());
		assertEquals(then.getEpochSecond(), ans.getTimestamp());
	}

	@Test
	void blockValuesAreMixUntilSync() {
		given(networkCtx.getAlignmentBlockNo()).willReturn(Long.MIN_VALUE);
		given(networkCtx.firstConsTimeOfCurrentBlock()).willReturn(then);

		final var ans = subject.computeBlockValues(gasLimit);

		assertEquals(gasLimit, ans.getGasLimit());
		assertEquals(then.getEpochSecond(), ans.getNumber());
		assertEquals(then.getEpochSecond(), ans.getTimestamp());
	}

	@Test
	void usesNowIfFirstConsTimeIsStillSomehowNull() {
		given(networkCtx.getAlignmentBlockNo()).willReturn(Long.MIN_VALUE);

		final var ans = subject.computeBlockValues(gasLimit);

		assertEquals(gasLimit, ans.getGasLimit());
		assertNotEquals(0, ans.getNumber());
		assertNotEquals(0, ans.getTimestamp());
	}

	@Test
	void alwaysDelegatesBlockHashLookup() {
		given(networkCtx.getBlockHashByNumber(someBlockNo)).willReturn(UNAVAILABLE_BLOCK_HASH);

		assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHash(someBlockNo));
	}

	private static final long gasLimit = 888L;
	private static final long someBlockNo = 123L;
	private static final Instant then = Instant.ofEpochSecond(1_234_567, 890);
}