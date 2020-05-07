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
import com.hederahashgraph.service.proto.java.NetworkServiceGrpc;
import io.grpc.stub.StreamObserver;

public class NetworkController extends NetworkServiceGrpc.NetworkServiceImplBase {
	private final MetaAnswers metaAnswers;
	private final QueryResponseHelper queryHelper;

	public static String GET_VERSION_INFO_METRIC = "getVersionInfo";

	public NetworkController(MetaAnswers metaAnswers, QueryResponseHelper queryHelper) {
		this.metaAnswers = metaAnswers;
		this.queryHelper = queryHelper;
	}

	@Override
	public void getVersionInfo(Query query, StreamObserver<Response> observer) {
		queryHelper.respondToNetwork(query, observer, metaAnswers.getVersionInfo(), GET_VERSION_INFO_METRIC);
	}
}
