package com.hedera.services.yahcli.suites;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileContents;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.SysFileSerde;
import com.hedera.services.yahcli.commands.validation.ValidationCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.StandardSerdes.SYS_FILE_SERDES;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.IMMUTABLE_SCHEDULE;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.MUTABLE_SCHEDULE;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.PAYER;
import static com.hedera.services.yahcli.commands.validation.ValidationCommand.checkBoxed;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

public class SchedulesValidationSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SchedulesValidationSuite.class);

	private final Map<String, String> specConfig;

	public SchedulesValidationSuite(Map<String, String> specConfig) {
		this.specConfig = specConfig;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				validateScheduling(),
		});
	}

	private HapiApiSpec validateScheduling() {
		return HapiApiSpec.customHapiSpec("ValidateScheduling").withProperties(specConfig)
				.given(
						getScheduleInfo(MUTABLE_SCHEDULE)
								.payingWith(PAYER)
								.isNotExecuted()
								.hasEntityMemo("When love with one another so / Inter-animates two souls")
								.hasAdminKey(MUTABLE_SCHEDULE),
						logIt(checkBoxed(MUTABLE_SCHEDULE + " exists as expected")),
						getScheduleInfo(IMMUTABLE_SCHEDULE)
								.payingWith(PAYER)
								.isNotExecuted()
								.hasEntityMemo("When love with one another so / Inter-animates two souls")
								.hasAdminKey(MUTABLE_SCHEDULE),
						logIt(checkBoxed(IMMUTABLE_SCHEDULE + " exists as expected"))
				).when(

				).then(
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
