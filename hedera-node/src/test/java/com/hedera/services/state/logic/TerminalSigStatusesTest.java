package com.hedera.services.state.logic;

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

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.stream.Stream;

import static com.hedera.services.state.logic.TerminalSigStatuses.TERMINAL_SIG_STATUSES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;

class TerminalSigStatusesTest {
	private static final EnumSet<ResponseCodeEnum> SIG_RATIONALIZATION_ERRORS = EnumSet.of(
			INVALID_FILE_ID,
			INVALID_TOKEN_ID,
			INVALID_ACCOUNT_ID,
			INVALID_SCHEDULE_ID,
			INVALID_SIGNATURE,
			KEY_PREFIX_MISMATCH,
			MODIFYING_IMMUTABLE_CONTRACT,
			INVALID_CONTRACT_ID,
			ACCOUNT_ID_DOES_NOT_EXIST,
			UNRESOLVABLE_REQUIRED_SIGNERS,
			SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);

	@Test
	void recommendsTerminationForStandardStatuses() {
		// expect:
		Stream.of(ResponseCodeEnum.class.getEnumConstants()).forEach(status -> {
			if (SIG_RATIONALIZATION_ERRORS.contains(status)) {
				Assertions.assertTrue(
						TERMINAL_SIG_STATUSES.test(status),
						status + " is expected to abort handleTransaction");
			} else {
				Assertions.assertFalse(
						TERMINAL_SIG_STATUSES.test(status),
						status + " is NOT expected to abort handleTransaction");
			}
		});
	}
}
