package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

/*-
 * ‌
 * Hedera Services Test Clients
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

import java.util.HashMap;
import java.util.Map;

public class StandardSerdes {
	public static final Map<Long, SysFileSerde<String>> SYS_FILE_SERDES = new HashMap<>() {{
		put(101L, new AddrBkJsonToGrpcBytes());
		put(102L, new NodesJsonToGrpcBytes());
		put(111L, new FeesJsonToGrpcBytes());
		put(112L, new XRatesJsonToGrpcBytes());
		put(121L, new JutilPropsToSvcCfgBytes("application.properties"));
		put(122L, new JutilPropsToSvcCfgBytes("api-permission.properties"));
		put(123L, new ThrottlesJsonToGrpcBytes());
	}};
}
