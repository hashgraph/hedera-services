package com.hedera.services.queries.meta;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AbstractAnswer;
import com.hedera.services.stats.ExecutionTimeTracker;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.inject.Singleton;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.NetworkGetExecutionTime;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public class GetExecTimeAnswer extends AbstractAnswer {
	private final ExecutionTimeTracker executionTimeTracker;

	public GetExecTimeAnswer(ExecutionTimeTracker executionTimeTracker) {
		super(
				NetworkGetExecutionTime,
				query -> query.getNetworkGetExecutionTime().getHeader().getPayment(),
				query -> query.getNetworkGetExecutionTime().getHeader().getResponseType(),
				response -> response.getNetworkGetExecutionTime().getHeader().getNodeTransactionPrecheckCode(),
				(query, view) -> OK);

		this.executionTimeTracker = executionTimeTracker;
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
//		var op = query.getNetworkGetVersionInfo();
//		var response = NetworkGetVersionInfoResponse.newBuilder();
//
//		ResponseType type = op.getHeader().getResponseType();
//		if (validity != OK) {
//			response.setHeader(header(validity, type, cost));
//		} else {
//			if (type == COST_ANSWER) {
//				response.setHeader(costAnswerHeader(OK, cost));
//			} else {
//				response.setHeader(answerOnlyHeader(OK));
//				var answer = semanticVersions.getDeployed().get();
//				response.setHapiProtoVersion(answer.protoSemVer());
//				response.setHederaServicesVersion(answer.hederaSemVer());
//			}
//		}
//
//		return Response.newBuilder()
//				.setNetworkGetVersionInfo(response)
//				.build();

		throw new AssertionError("Not implemented");
	}
}
