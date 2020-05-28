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
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.legacy.evm.SolidityExecutor;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.ServicesRepositoryRoot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static com.hedera.services.contracts.execution.SolidityLifecycle.OVERSIZE_RESULT_ERROR_MSG_TPL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class SolidityLifecycleTest {
	int maxStorageKb = 4321;
	long maxResultSize = 4321;
	Transaction txn;
	List<ContractID> some = List.of(IdUtils.asContract("0.0.13257"), IdUtils.asContract("0.0.13258"));
	TransactionReceipt receipt;
	Optional<List<ContractID>> allCreations = Optional.empty();

	ContractFunctionResult expected;

	PropertySource properties;
	SolidityExecutor executor;
	ServicesRepositoryRoot root;

	SolidityLifecycle subject;

	@BeforeEach
	private void setup() {
		txn = mock(Transaction.class);
		receipt = new TransactionReceipt();
		receipt.setGasUsed(maxStorageKb);
		receipt.setTransaction(txn);
		receipt.setExecutionResult("Something".getBytes());

		executor = mock(SolidityExecutor.class);
		given(executor.getReceipt()).willReturn(receipt);
		given(executor.getCreatedContracts()).willReturn(allCreations);

		properties = mock(PropertySource.class);
		given(properties.getIntProperty("contracts.maxStorageKb")).willReturn(maxStorageKb);
		root = mock(ServicesRepositoryRoot.class);
		given(root.flushStorageCacheIfTotalSizeLessThan(maxStorageKb)).willReturn(true);

		subject = new SolidityLifecycle(properties);
	}

	@Test
	public void usesSuggestedErrorIfPresent() {
		receipt.setError("Oops!");
		given(executor.getErrorCode()).willReturn(CONTRACT_REVERT_EXECUTED);
		givenNoCreation();

		// when:
		var result = subject.runPure(maxResultSize, executor);

		// then:
		Assertions.assertEquals(CONTRACT_REVERT_EXECUTED, result.getValue());
	}

	@Test
	public void usesDefaultErrorIfNoneAvailable() {
		receipt.setError("Oops!");
		givenNoCreation();

		// when:
		var result = subject.runPure(maxResultSize, executor);

		// then:
		Assertions.assertEquals(expected, result.getKey());
		Assertions.assertEquals(CONTRACT_EXECUTION_EXCEPTION, result.getValue());
	}

	@Test
	public void errorsOutIfResultSizeExceeded() {
		givenNoCreation();
		// and:
		expected = expected.toBuilder()
				.clearContractCallResult()
				.setErrorMessage(
						String.format(OVERSIZE_RESULT_ERROR_MSG_TPL, expected.getContractCallResult().size(), 1))
				.build();

		// when:
		var result = subject.runPure(1, executor);

		// then:
		Assertions.assertEquals(expected, result.getKey());
		Assertions.assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, result.getValue());
	}

	@Test
	public void pureHappyPathRuns() {
		// setup:
		InOrder inOrder = inOrder(root, executor);

		givenNoCreation();

		// when:
		var result = subject.runPure(maxResultSize, executor);

		// then:
		inOrder.verify(executor).init();
		inOrder.verify(executor).execute();
		inOrder.verify(executor).go();
		inOrder.verify(executor).finalizeExecution();
		// and:
		Assertions.assertEquals(expected, result.getKey());
		Assertions.assertEquals(ResponseCodeEnum.OK, result.getValue());
	}

	@Test
	public void happyPathRuns() {
		// setup:
		InOrder inOrder = inOrder(root, executor);

		givenNoCreation();

		// when:
		var result = subject.run(executor, root);

		// then:
		inOrder.verify(executor).init();
		inOrder.verify(executor).execute();
		inOrder.verify(executor).go();
		inOrder.verify(executor).finalizeExecution();
		// and:
		inOrder.verify(root).flushStorageCacheIfTotalSizeLessThan(maxStorageKb);
		inOrder.verify(root).flush();
		// and:
		Assertions.assertEquals(expected, result.getKey());
		Assertions.assertEquals(ResponseCodeEnum.SUCCESS, result.getValue());
	}

	@Test
	public void errorsOutIfCannotPersist() {
		givenNoCreation();
		given(root.flushStorageCacheIfTotalSizeLessThan(maxStorageKb)).willReturn(false);

		// when:
		var result = subject.run(executor, root);

		// then:
		verify(root).emptyStorageCache();
		// and:
		Assertions.assertEquals(expected, result.getKey());
		Assertions.assertEquals(MAX_CONTRACT_STORAGE_EXCEEDED, result.getValue());
	}

	@Test
	public void usesSuggestedError() {
		receipt.setError("Oops!");
		given(executor.getErrorCode()).willReturn(CONTRACT_REVERT_EXECUTED);
		givenNoCreation();

		// when:
		var result = subject.run(executor, root);

		// then:
		Assertions.assertEquals(CONTRACT_REVERT_EXECUTED, result.getValue());
	}

	@Test
	public void emptiesCacheOnFailure() {
		receipt.setError("Oops!");
		givenNoCreation();

		// when:
		var result = subject.run(executor, root);

		// then:
		verify(root, never()).flushStorageCacheIfTotalSizeLessThan(anyInt());
		verify(root).emptyStorageCache();
		// and:
		Assertions.assertEquals(expected, result.getKey());
		Assertions.assertEquals(CONTRACT_EXECUTION_EXCEPTION, result.getValue());
	}

	private void givenNoCreation() {
		expected = DomainUtils.asHapiResult(receipt, allCreations);
	}

	private void givenSomeCreations() {
		allCreations = Optional.of(some);
		expected = DomainUtils.asHapiResult(receipt, allCreations);
	}
}
