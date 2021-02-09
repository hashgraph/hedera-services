package com.hedera.services.usage;

/*-
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.ResponseType;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;

public class QueryUsage {
	private long sb = 0;
	private long tb = BASIC_QUERY_HEADER;
	private long rb = BASIC_QUERY_RES_HEADER;

	/* Once state proofs are supported, this will be needed to compute {@code rb}. */
	private final ResponseType responseType;

	public QueryUsage(ResponseType responseType) {
		this.responseType = responseType;
	}

	public FeeData get() {
		var usage = FeeComponents.newBuilder()
				.setBpt(tb)
				.setBpr(rb)
				.setSbpr(sb)
				.build();
		return ESTIMATOR_UTILS.withDefaultQueryPartitioning(usage);
	}

	public void updateRb(long amount) {
		rb += amount;
	}

	public void updateTb(long amount) {
		tb += amount;
	}

	public void updateSb(long amount) {
		sb += amount;
	}
}
