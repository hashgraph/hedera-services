package com.hedera.services.contracts.execution;

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

import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.legacy.evm.SolidityExecutor;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.db.ServicesRepositoryRoot;
import static com.hedera.services.contracts.execution.DomainUtils.asHapiResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

public class SolidityLifecycle {
	private final PropertySource properties;

	public static final String OVERSIZE_RESULT_ERROR_MSG_TPL =
			"Result size (%d bytes) exceeded maximum allowed size (%d bytes)";

	public SolidityLifecycle(PropertySource properties) {
		this.properties = properties;
	}

	public Map.Entry<ContractFunctionResult, ResponseCodeEnum> run(
			SolidityExecutor executor,
			ServicesRepositoryRoot root
	) {
		cycle(executor);

		var status = SUCCESS;
		var result = asHapiResult(executor.getReceipt(), executor.getCreatedContracts());
		System.out.println("Result: " + result);

		var succeededSoFar = StringUtils.isEmpty(result.getErrorMessage());
		if (succeededSoFar) {
			if (!root.flushStorageCacheIfTotalSizeLessThan(properties.getIntProperty("contracts.maxStorageKb"))) {
				succeededSoFar = false;
				status = MAX_CONTRACT_STORAGE_EXCEEDED;
			}
		}
		if (!succeededSoFar) {
			status = (status != SUCCESS)
					? status
					: Optional.ofNullable(executor.getErrorCode()).orElse(CONTRACT_EXECUTION_EXCEPTION);
			root.emptyStorageCache();
		}

		root.flush();

		return new AbstractMap.SimpleImmutableEntry<>(result, status);
	}

	public Map.Entry<ContractFunctionResult, ResponseCodeEnum> runPure(long maxResultSize, SolidityExecutor executor) {
		cycle(executor);

		var status = OK;
		var result = asHapiResult(executor.getReceipt(), Optional.empty());

		var failed = StringUtils.isNotEmpty(result.getErrorMessage());
		if (failed) {
			status = Optional.ofNullable(executor.getErrorCode()).orElse(CONTRACT_EXECUTION_EXCEPTION);
		} else {
			if (maxResultSize > 0) {
				long resultSize = result.getContractCallResult().size();
				if (resultSize > maxResultSize) {
					status = RESULT_SIZE_LIMIT_EXCEEDED;
					result = result.toBuilder()
							.clearContractCallResult()
							.setErrorMessage(String.format(OVERSIZE_RESULT_ERROR_MSG_TPL, resultSize, maxResultSize))
							.build();
				}
			}
		}

		return new AbstractMap.SimpleImmutableEntry<>(result, status);
	}

	private void cycle(SolidityExecutor executor) {
		executor.init();
		executor.execute();
		executor.go();
		executor.finalizeExecution();
	}
}
