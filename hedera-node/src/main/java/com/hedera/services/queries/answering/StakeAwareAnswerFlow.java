package com.hedera.services.queries.answering;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.queries.AnswerFlow;
import com.hedera.services.queries.AnswerService;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;

public class StakeAwareAnswerFlow implements AnswerFlow {
	private final NodeInfo nodeInfo;
	private final StakedAnswerFlow stakedAnswerFlow;
	private final ZeroStakeAnswerFlow zeroStakeAnswerFlow;

	public StakeAwareAnswerFlow(
			final NodeInfo nodeInfo,
			final StakedAnswerFlow stakedAnswerFlow,
			final ZeroStakeAnswerFlow zeroStakeAnswerFlow
	) {
		this.nodeInfo = nodeInfo;
		this.stakedAnswerFlow = stakedAnswerFlow;
		this.zeroStakeAnswerFlow = zeroStakeAnswerFlow;
	}

	@Override
	public Response satisfyUsing(final AnswerService service, final Query query) {
		return nodeInfo.isSelfZeroStake()
				? zeroStakeAnswerFlow.satisfyUsing(service, query)
				: stakedAnswerFlow.satisfyUsing(service, query);
	}
}
