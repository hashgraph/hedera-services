/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.node.app.service.mono.utils;

import com.hedera.node.app.service.mono.contracts.execution.TransactionProcessingResult;
import com.hedera.node.app.service.mono.exceptions.ResourceLimitException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseCodeUtilTest {
	@Test
	void translatesResourceLimitReversions() {
		for (final var status : ResponseCodeUtil.RESOURCE_EXHAUSTION_REVERSIONS.values()) {
			final var result = failureWithRevertReasonFrom(status);
			final var code = ResponseCodeUtil.getStatusOrDefault(result, OK);
			assertEquals(status, code);
		}
	}

	private TransactionProcessingResult failureWithRevertReasonFrom(final ResponseCodeEnum status) {
		final var ex = new ResourceLimitException(status);
		return TransactionProcessingResult.failed(
				1L, 2L, 3L, Optional.of(ex.messageBytes()), Optional.empty(), Map.of(), List.of());
	}
}
