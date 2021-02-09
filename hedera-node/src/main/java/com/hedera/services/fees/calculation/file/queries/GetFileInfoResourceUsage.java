package com.hedera.services.fees.calculation.file.queries;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.usage.file.ExtantFileContext;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.FileFeeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GetFileInfoResourceUsage implements QueryResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(GetFileInfoResourceUsage.class);

	private final FileOpsUsage fileOpsUsage;

	public GetFileInfoResourceUsage(FileOpsUsage fileOpsUsage) {
		this.fileOpsUsage = fileOpsUsage;
	}

	@Override
	public boolean applicableTo(Query query) {
		return query.hasFileGetInfo();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageGivenType(query, view, query.getFileGetInfo().getHeader().getResponseType());
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		var op = query.getFileGetInfo();
		var info = view.infoForFile(op.getFileID());
		/* Given the test in {@code GetFileInfoAnswer.checkValidity}, this can only be empty
		* under the extraordinary circumstance that the desired file expired during the query
		* answer flow (which will now fail downstream with an appropriate status code); so
		* just return the default {@code FeeData} here. */
		if (info.isEmpty()) {
			return FeeData.getDefaultInstance();
		}
		var details = info.get();
		var ctx = ExtantFileContext.newBuilder()
				.setCurrentSize(details.getSize())
				.setCurrentWacl(details.getKeys())
				.setCurrentMemo(details.getMemo())
				.setCurrentExpiry(details.getExpirationTime().getSeconds())
				.build();
		return fileOpsUsage.fileInfoUsage(query, ctx);
	}
}
