package com.hedera.services.queries.answering;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.queries.AnswerService;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StakeAwareAnswerFlowTest {
	private static final Query mockQuery = Query.getDefaultInstance();
	private static final Response mockResponse = Response.getDefaultInstance();

	@Mock
	private NodeInfo nodeInfo;
	@Mock
	private AnswerService service;
	@Mock
	private StakedAnswerFlow stakedAnswerFlow;
	@Mock
	private ZeroStakeAnswerFlow zeroStakeAnswerFlow;

	private StakeAwareAnswerFlow subject;

	@BeforeEach
	void setUp() {
		subject = new StakeAwareAnswerFlow(nodeInfo, stakedAnswerFlow, zeroStakeAnswerFlow);
	}

	@Test
	void delegatesToZeroStakeAsExpected() {
		given(nodeInfo.isSelfZeroStake()).willReturn(true);
		given(zeroStakeAnswerFlow.satisfyUsing(service, mockQuery)).willReturn(mockResponse);

		assertSame(mockResponse, subject.satisfyUsing(service, mockQuery));
	}

	@Test
	void delegatesToStakedAsExpected() {
		given(stakedAnswerFlow.satisfyUsing(service, mockQuery)).willReturn(mockResponse);

		assertSame(mockResponse, subject.satisfyUsing(service, mockQuery));
	}
}