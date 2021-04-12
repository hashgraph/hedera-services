package com.hedera.services.yahcli.output;

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

import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.Utils;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionReceipt;

import static com.hedera.services.bdd.spec.queries.QueryUtils.reflectForPrecheck;
import static com.hedera.services.yahcli.config.ConfigUtils.asId;

public enum CommonMessages {
	COMMON_MESSAGES;

	public void printGlobalInfo(ConfigManager config) {
		var msg = String.format("Targeting %s, paying with %s", config.getTargetName(), asId(config.getDefaultPayer()));
		System.out.println(msg);
	}

	public void appendBeginning(FileID target) {
		var msg = "Appending to the uploaded " + Utils.nameOf(target) + "...";
		System.out.print(msg);
		System.out.flush();
	}

	public void appendEnding(ResponseCodeEnum resolvedStatus) {
		System.out.println(resolvedStatus.toString());
	}

	public void uploadBeginning(FileID target) {
		var msg = "Uploading the " + Utils.nameOf(target) + "...";
		System.out.print(msg);
		System.out.flush();
	}

	public void uploadEnding(ResponseCodeEnum resolvedStatus) {
		System.out.println(resolvedStatus.toString());
	}

	public void downloadBeginning(FileID target) {
		var msg = "Downloading the " + Utils.nameOf(target) + "...";
		System.out.print(msg);
		System.out.flush();
	}

	public void downloadEnding(Response response) {
		try {
			var precheck = reflectForPrecheck(response);
			System.out.println(precheck.toString());
		} catch (Throwable throwable) {
			throwable.printStackTrace();
		}
	}

	public String fq(Integer num) {
		return "0.0." + num;
	}
}
