package com.hedera.services.contracts.execution;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.logic.BlockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.services.contracts.execution.BlockMetaSource.UNAVAILABLE_BLOCK_HASH;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InHandleBlockMetaSourceTest {
	@Mock
	private BlockManager blockManager;
	@Mock
	private TransactionContext txnCtx;

	private InHandleBlockMetaSource subject;

	@BeforeEach
	void setUp() {
		subject = new InHandleBlockMetaSource(blockManager, txnCtx);
	}

	@Test
	void delegatesComputeToManagerAtCurrentTime() {
		final var values = new HederaBlockValues(gasLimit, someBlockNo, then);

		given(txnCtx.consensusTime()).willReturn(then);
		given(blockManager.computeBlockValues(then, gasLimit)).willReturn(values);

		final var actual = subject.computeBlockValues(gasLimit);

		assertSame(values, actual);
	}

	@Test
	void delegatesBlockHashToManager() {
		given(blockManager.getBlockHash(someBlockNo)).willReturn(UNAVAILABLE_BLOCK_HASH);

		assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHash(someBlockNo));
	}

	private static final long gasLimit = 888L;
	private static final long someBlockNo = 123L;
	private static final Instant then = Instant.ofEpochSecond(1_234_567, 890);
}