package com.hedera.services.contracts.execution;

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

import com.google.protobuf.ByteString;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.swirlds.common.CommonUtils;
import org.ethereum.core.AccountState;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.ServicesRepositoryImpl;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.program.ProgramResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.contracts.execution.DomainUtils.asHapiLog;
import static com.hedera.services.contracts.execution.DomainUtils.asHapiResult;
import static com.hedera.services.contracts.execution.DomainUtils.fakeBlock;
import static com.hedera.services.contracts.execution.DomainUtils.newScopedAccountInitializer;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.booleanThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.longThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class DomainUtilsTest {
	private static final long gas = 1_234_567L;
	private static final String error = "I don't like any of this!";
	private static final byte[] hr = "Not really a result!".getBytes();
	private static final byte[] data = "Not really data!".getBytes();
	private byte[] primaryCreatedAddress = null;
	private static final byte[] executionResult = "Not really an exection result!".getBytes();
	private static final byte[] senderAddr = "01234567890123456789".getBytes();
	private static final ContractID primaryCreated = IdUtils.asContract("0.0.13257");
	private Transaction ofMention;
	private ProgramResult result;
	private static final List<ContractID> some = List.of(
			IdUtils.asContract("0.0.13257"),
			IdUtils.asContract("0.0.13258"));

	private static final LogInfo logInfo = new LogInfo(
			EntityIdUtils.asSolidityAddress(0, 0, primaryCreated.getContractNum()),
			List.of(
					DataWord.of(CommonUtils.hex("First".getBytes())),
					DataWord.of(CommonUtils.hex("Second".getBytes()))),
			data);
	private TransactionReceipt receipt;
	private Optional<List<ContractID>> created;

	@BeforeEach
	private void setup() {
		created = Optional.empty();
		receipt = new TransactionReceipt();
		receipt.setGasUsed(gas);
		receipt.setExecutionResult(executionResult);

		result = mock(ProgramResult.class);
		given(result.getHReturn()).willReturn(hr);

		ofMention = mock(Transaction.class);
		receipt.setTransaction(ofMention);

		receipt.setLogInfoList(List.of(logInfo));
	}

	@Test
	void initsAccountAsExpected() {
		final long startTime = Instant.now().getEpochSecond();
		final long contractDurationSecs = 1_234_567L;
		final var sponsorAccount = mock(AccountState.class);
		given(sponsorAccount.getShardId()).willReturn(1L);
		given(sponsorAccount.getRealmId()).willReturn(2L);
		final var repository = mock(ServicesRepositoryImpl.class);
		given(repository.getAccount(argThat((byte[] addr) -> Arrays.equals(senderAddr, addr))))
				.willReturn(sponsorAccount);
		final var address = EntityIdUtils.asSolidityAddress(0, 0, 13257);

		final var scopedInitializer =
				newScopedAccountInitializer(startTime, contractDurationSecs, senderAddr, repository);
		scopedInitializer.accept(address);

		verify(repository).setSmartContract(
				argThat((byte[] addr) -> Arrays.equals(address, addr)),
				booleanThat(Boolean.TRUE::equals));
		verify(repository).setShardId(
				argThat((byte[] addr) -> Arrays.equals(address, addr)),
				longThat(l -> l == 1L));
		verify(repository).setRealmId(
				argThat((byte[] addr) -> Arrays.equals(address, addr)),
				longThat(l -> l == 2L));
		verify(repository).setAccountNum(
				argThat((byte[] addr) -> Arrays.equals(address, addr)),
				longThat(l -> l == 13257L));
		verify(repository).setCreateTimeMs(
				argThat((byte[] addr) -> Arrays.equals(address, addr)),
				longThat(l -> l == (startTime * 1_000L)));
		verify(repository).setExpirationTime(
				argThat((byte[] addr) -> Arrays.equals(address, addr)),
				longThat(l -> l == (startTime + contractDurationSecs)));
	}

	@Test
	void constructsExpectedReceipt() {
		final var gasUsedBytes = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(gas));
		final var vmLogs = List.of(logInfo);

		final var receipt = DomainUtils.asReceipt(gas, error, ofMention, vmLogs, result);

		assertArrayEquals(gasUsedBytes, receipt.getGasUsed());
		assertEquals(gas, receipt.getCumulativeGasLong());
		assertEquals(error, receipt.getError());
		assertEquals(receipt.getTransaction(), ofMention);
		assertEquals(vmLogs, receipt.getLogInfoList());
		assertArrayEquals(hr, receipt.getExecutionResult());
	}

	@Test
	void fakeBlockTimedPrecisely() {
		final var now = Instant.now();

		final var block = fakeBlock(now);

		assertEquals(now.getEpochSecond(), block.getTimestamp());
	}

	@Test
	void mapsLogInfoAsExpected() {
		final var bloom = logInfo.getBloom();

		final var log = asHapiLog(logInfo);

		assertEquals(primaryCreated, log.getContractID());
		assertArrayEquals(bloom.getData(), log.getBloom().toByteArray());
		assertArrayEquals(data, log.getData().toByteArray());
		assertEquals(
				logInfo.getTopics().stream().map(DataWord::getData).map(ByteString::copyFrom).collect(toList()),
				log.getTopicList());
	}

	@Test
	void includesAnyLogInfo() {
		final var result = asHapiResult(receipt, created);

		assertEquals(List.of(asHapiLog(logInfo)), result.getLogInfoList());
	}

	@Test
	void mapsExecutionResult() {
		final var result = asHapiResult(receipt, created);

		assertArrayEquals(executionResult, result.getContractCallResult().toByteArray());
	}

	@Test
	void ignoresNullExecutionResult() {
		receipt.setExecutionResult(null);

		final var result = asHapiResult(receipt, created);

		assertTrue(result.getContractCallResult().isEmpty());
	}

	@Test
	void mapsPrimaryCreation() {
		givenContractCreate();

		final var result = asHapiResult(receipt, created);

		assertEquals(primaryCreated, result.getContractID());
	}

	@Test
	void mapsGasUsed() {
		givenError();

		final var result = asHapiResult(receipt, created);

		assertEquals(gas, result.getGasUsed());
	}

	@Test
	void mapsErrorMessage() {
		givenError();

		final var result = asHapiResult(receipt, created);

		assertEquals(error, result.getErrorMessage());
	}

	@Test
	void addsCreatedIfPresent() {
		givenCreations();

		final var result = asHapiResult(receipt, created);

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
