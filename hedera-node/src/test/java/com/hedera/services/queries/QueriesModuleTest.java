package com.hedera.services.queries;

import com.hedera.services.context.ServicesNodeType;
import com.hedera.services.queries.answering.ZeroStakeAnswerFlow;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

class QueriesModuleTest {
	@Test
	void usesZeroStakeWhenAppropriate() {
		// expect:
		assertThat(QueriesModule.provideAnswerFlow(
			null,
			null,
			null,
			ServicesNodeType.ZERO_STAKE_NODE,
			null,
			null,
			null,
			null,
			null,
			null,
			null
		), instanceOf(ZeroStakeAnswerFlow.class));
	}
}