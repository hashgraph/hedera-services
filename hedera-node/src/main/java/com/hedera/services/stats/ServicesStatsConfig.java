package com.hedera.services.stats;

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

import com.hederahashgraph.api.proto.java.HederaFunctionality;

import java.util.EnumSet;
import java.util.Set;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetByKey;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UNRECOGNIZED;

public class ServicesStatsConfig {
	public static Set<HederaFunctionality> IGNORED_FUNCTIONS = EnumSet.of(
			NONE,
			UNRECOGNIZED,
			GetByKey
	);

	static final String COUNTER_HANDLED_NAME_TPL = "%sHdl";
	static final String COUNTER_RECEIVED_NAME_TPL = "%sRcv";
	static final String COUNTER_ANSWERED_NAME_TPL = "%sSub";
	static final String COUNTER_SUBMITTED_NAME_TPL = "%sSub";
	static final String SPEEDOMETER_HANDLED_NAME_TPL = "%sHdl/sec";
	static final String SPEEDOMETER_RECEIVED_NAME_TPL = "%sRcv/sec";
	static final String SPEEDOMETER_ANSWERED_NAME_TPL = "%sSub/sec";
	static final String SPEEDOMETER_SUBMITTED_NAME_TPL = "%sSub/sec";

	static final String COUNTER_HANDLED_DESC_TPL = "number of %s handled";
	static final String COUNTER_RECEIVED_DESC_TPL = "number of %s received";
	static final String COUNTER_ANSWERED_DESC_TPL = "number of %s answered";
	static final String COUNTER_SUBMITTED_DESC_TPL = "number of %s submitted";
	static final String SPEEDOMETER_HANDLED_DESC_TPL = "number of %s handled per second";
	static final String SPEEDOMETER_RECEIVED_DESC_TPL = "number of %s received per second";
	static final String SPEEDOMETER_ANSWERED_DESC_TPL = "number of %s answered per second";
	static final String SPEEDOMETER_SUBMITTED_DESC_TPL = "number of %s submitted per second";

	public static final String SYSTEM_DELETE_METRIC = "systemDelete";
	public static final String SYSTEM_UNDELETE_METRIC = "systemUndelete";
}
