package com.hedera.services.grpc.controllers;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.meta.MetaAnswers;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.mockito.BDDMockito.*;
import static com.hedera.services.grpc.controllers.NetworkController.*;

@RunWith(JUnitPlatform.class)
class NetworkControllerTest {
	Query query = Query.getDefaultInstance();
	MetaAnswers answers;
	QueryResponseHelper queryResponseHelper;
	StreamObserver<Response> queryObserver;

	NetworkController subject;

	@BeforeEach
	private void setup() {
		answers = mock(MetaAnswers.class);
		queryObserver = mock(StreamObserver.class);

		queryResponseHelper = mock(QueryResponseHelper.class);

		subject = new NetworkController(answers, queryResponseHelper);
	}

	@Test
	public void forwardsVersionInfoAsExpected() {
		// when:
		subject.getVersionInfo(query, queryObserver);

		// expect:
		verify(answers).getVersionInfo();
		verify(queryResponseHelper).respondToNetwork(query, queryObserver, null, GET_VERSION_INFO_METRIC);
	}
}
