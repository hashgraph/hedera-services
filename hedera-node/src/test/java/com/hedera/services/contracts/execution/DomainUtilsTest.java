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

import com.google.protobuf.ByteString;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.commons.codec.binary.Hex;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import org.junit.jupiter.api.Test;

import static com.hedera.services.contracts.execution.DomainUtils.*;

@RunWith(JUnitPlatform.class)
class DomainUtilsTest {
	long gas = 1_234_567L;
	String error = "I don't like any of this!";
	byte[] data = "Not really data!".getBytes();
	byte[] primaryCreatedAddress = null;
	byte[] executionResult = "Not really an exection result!".getBytes();
	ContractID primaryCreated = IdUtils.asContract("0.0.13257");
	Transaction ofMention;
	List<ContractID> some = List.of(IdUtils.asContract("0.0.13257"), IdUtils.asContract("0.0.13258"));

	LogInfo logInfo;
	TransactionReceipt receipt;
	Optional<List<ContractID>> created;

	@BeforeEach
	private void setup() {
		created = Optional.empty();
		receipt = new TransactionReceipt();
		receipt.setGasUsed(gas);
		receipt.setExecutionResult(executionResult);

		ofMention = mock(Transaction.class);
		receipt.setTransaction(ofMention);

		logInfo = new LogInfo(
				EntityIdUtils.asSolidityAddress(0, 0, primaryCreated.getContractNum()),
				List.of(
						DataWord.of(Hex.encodeHexString("First".getBytes())),
						DataWord.of(Hex.encodeHexString("Second".getBytes()))),
				data);
		receipt.setLogInfoList(List.of(logInfo));
	}

	@Test
	public void fakeBlockTimedPrecisely() {
		// setup:
		var now = Instant.now();

		// given:
		var block = fakeBlock(now);

		// expect:
		assertEquals(now.getEpochSecond(), block.getTimestamp());
	}

	@Test
	public void mapsLogInfoAsExpected() {
		// given:
		var bloom = logInfo.getBloom();

		// when:
		var log = asHapiLog(logInfo);

		// then:
		assertEquals(primaryCreated, log.getContractID());
		assertArrayEquals(bloom.getData(), log.getBloom().toByteArray());
		assertArrayEquals(data, log.getData().toByteArray());
		assertEquals(
				logInfo.getTopics().stream().map(DataWord::getData).map(ByteString::copyFrom).collect(toList()),
				log.getTopicList());
	}

	@Test
	public void includesAnyLogInfo() {
		// when:
		var result = asHapiResult(receipt, created);

		// then:
		assertEquals(List.of(asHapiLog(logInfo)), result.getLogInfoList());
	}

	@Test
	public void mapsExecutionResult() {
		// when:
		var result = asHapiResult(receipt, created);

		// then:
		assertArrayEquals(executionResult, result.getContractCallResult().toByteArray());
	}

	@Test
	public void ignoresNullExecutionResult() {
		// given:
		receipt.setExecutionResult(null);

		// when:
		var result = asHapiResult(receipt, created);

		// then:
		assertTrue(result.getContractCallResult().isEmpty());
	}

	@Test
	public void mapsPrimaryCreation() {
		givenContractCreate();

		// when:
		var result = asHapiResult(receipt, created);

		// then:
		assertEquals(primaryCreated, result.getContractID());
	}

	@Test
	public void mapsGasUsed() {
		givenError();

		// when:
		var result = asHapiResult(receipt, created);

		// then:
		assertEquals(gas, result.getGasUsed());
	}

	@Test
	public void mapsErrorMessage() {
		givenError();

		// when:
		var result = asHapiResult(receipt, created);

		// then:
		assertEquals(error, result.getErrorMessage());
	}

	@Test
	public void addsCreatedIfPresent() {
		givenCreations();

		// when:
		var result = asHapiResult(receipt, created);

		// then:
		assertEquals(some, result.getCreatedContractIDsList());
	}

	private void givenContractCreate() {
		primaryCreatedAddress = EntityIdUtils.asSolidityAddress(0, 0, primaryCreated.getContractNum());
		given(ofMention.getContractAddress()).willReturn(primaryCreatedAddress);
	}

	private void givenCreations() {
		created = Optional.of(some);
	}

	private void givenError() {
		receipt.setError(error);
	}
}
