/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.services.contracts.execution.HederaMessageCallProcessor;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.swirlds.common.utility.CommonUtils;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvmFnResultTest {
    private static final long gasUsed = 1_234;
    private static final long gas = 5_000;
    private static final long amount = 1_000_000;
    private static final byte[] result = "abcdefgh".getBytes();
    private static final byte[] otherResult = "hgfedcba".getBytes();
    private static final byte[] bloom = "ijklmnopqrstuvwxyz".getBytes();
    private static final byte[] evmAddress = Address.BLAKE2B_F_COMPRESSION.toArray();
    private static final byte[] functionParameters = "functionParameters".getBytes();
    private static final String error = "Oops!";
    private static final EntityId contractId = new EntityId(0L, 0L, 3L);
    private static final EntityId senderId = new EntityId(0L, 0L, 42L);
    private static final Address recipient = EntityNum.fromLong(3L).toEvmAddress();
    private static final List<EntityId> createdContractIds =
            List.of(new EntityId(2L, 3L, 4L), new EntityId(3L, 4L, 5L));
    private static final List<ContractID> grpcCreatedContractIds =
            createdContractIds.stream().map(EntityId::toGrpcContractId).toList();
    private final List<EvmLog> logs = List.of(logFrom(0), logFrom(1));
    private final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges =
            new TreeMap<>(
                    Map.of(
                            Address.fromHexString("0x6"),
                            Map.of(Bytes.of(7), Pair.of(Bytes.of(8), null)),
                            Address.fromHexString("0x9"),
                            Map.of(Bytes.of(10), Pair.of(Bytes.of(11), Bytes.of(12)))));

    private EvmFnResult subject;

    @BeforeEach
    void setup() {
        subject =
                new EvmFnResult(
                        contractId,
                        result,
                        error,
                        bloom,
                        gasUsed,
                        logs,
                        createdContractIds,
                        evmAddress,
                        gas,
                        amount,
                        functionParameters,
                        senderId);
    }

    @Test
    void gettersWork() {
        assertEquals(contractId, subject.getContractId());
        assertEquals(result, subject.getResult());
        assertEquals(error, subject.getError());
        assertEquals(bloom, subject.getBloom());
        assertEquals(gasUsed, subject.getGasUsed());
        assertEquals(logs, subject.getLogs());
        assertEquals(createdContractIds, subject.getCreatedContractIds());
        assertEquals(0x2055c5c03ff84eb4L, subject.getClassId());
        assertEquals(EvmFnResult.RELEASE_0290_VERSION, subject.getVersion());
        assertEquals(gas, subject.getGas());
        assertEquals(amount, subject.getAmount());
        assertEquals(functionParameters, subject.getFunctionParameters());
    }

    @Test
    void besuParsingWorksForRevertFailure() {
        final var revertReason = HederaMessageCallProcessor.INVALID_TRANSFER.toString();
        final var expected =
                new EvmFnResult(
                        null,
                        new byte[0],
                        revertReason,
                        new byte[0],
                        gasUsed,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        new byte[0],
                        0L,
                        0L,
                        new byte[0],
                        null);

        final var input =
                TransactionProcessingResult.failed(
                        gasUsed,
                        0,
                        0,
                        Optional.of(HederaMessageCallProcessor.INVALID_TRANSFER),
                        Optional.empty(),
                        Collections.emptyMap(),
                        Collections.emptyList());

        final var actual = EvmFnResult.fromCall(input);

        assertEquals(expected, actual);
    }

    @Test
    void besuParsingWorksForCallSuccess() {
        final var expected =
                new EvmFnResult(
                        contractId,
                        result,
                        null,
                        realBloom,
                        gasUsed,
                        EvmLog.fromBesu(besuLogs),
                        createdContractIds,
                        new byte[0],
                        0L,
                        0L,
                        new byte[0],
                        null);

        final var input =
                TransactionProcessingResult.successful(
                        besuLogs,
                        gasUsed,
                        0,
                        0,
                        Bytes.wrap(result),
                        recipient,
                        Collections.emptyMap(),
                        Collections.emptyList());
        input.setCreatedContracts(grpcCreatedContractIds);

        final var actual = EvmFnResult.fromCall(input);

        assertEquals(expected, actual);
    }

    @Test
    void throwsIaeIfRecipientSomehowMissing() {
        final var result = mock(TransactionProcessingResult.class);
        given(result.getRecipient()).willReturn(Optional.empty());
        given(result.isSuccessful()).willReturn(true);

        assertThrows(IllegalArgumentException.class, () -> EvmFnResult.fromCall(result));
    }

    @Test
    void besuParsingWorksForCreateSuccess() {
        final var expected =
                new EvmFnResult(
                        contractId,
                        result,
                        null,
                        realBloom,
                        gasUsed,
                        EvmLog.fromBesu(besuLogs),
                        createdContractIds,
                        evmAddress,
                        0L,
                        0L,
                        new byte[0],
                        null);

        final var input =
                TransactionProcessingResult.successful(
                        besuLogs,
                        gasUsed,
                        0,
                        0,
                        Bytes.wrap(result),
                        recipient,
                        Collections.emptyMap(),
                        Collections.emptyList());
        input.setCreatedContracts(grpcCreatedContractIds);

        final var actual = EvmFnResult.fromCreate(input, evmAddress);

        assertEquals(expected, actual);
    }

    @Test
    void objectContractWorks() {
        final var one = subject;
        final var two =
                new EvmFnResult(
                        contractId,
                        otherResult,
                        error,
                        bloom,
                        gasUsed,
                        logs,
                        createdContractIds,
                        evmAddress,
                        gas,
                        amount,
                        functionParameters,
                        senderId);
        final var three =
                new EvmFnResult(
                        contractId,
                        result,
                        error,
                        bloom,
                        gasUsed,
                        logs,
                        createdContractIds,
                        evmAddress,
                        gas,
                        amount,
                        functionParameters,
                        senderId);
        final var four =
                new EvmFnResult(
                        contractId,
                        result,
                        error,
                        bloom,
                        gasUsed,
                        logs,
                        createdContractIds,
                        Address.ZERO.toArray(),
                        gas,
                        amount,
                        functionParameters,
                        senderId);
        final var five =
                new EvmFnResult(
                        contractId,
                        result,
                        "AnotherError",
                        bloom,
                        gasUsed,
                        logs,
                        createdContractIds,
                        evmAddress,
                        gas,
                        amount,
                        functionParameters,
                        senderId);
        final var six =
                new EvmFnResult(
                        contractId,
                        result,
                        error,
                        bloom,
                        gasUsed,
                        List.of(logFrom(1)),
                        createdContractIds,
                        evmAddress,
                        gas,
                        amount,
                        functionParameters,
                        senderId);
        final var seven =
                new EvmFnResult(
                        contractId,
                        result,
                        error,
                        bloom,
                        gasUsed,
                        logs,
                        List.of(new EntityId(1L, 1L, 42L)),
                        evmAddress,
                        gas,
                        amount,
                        functionParameters,
                        senderId);
        final var nine =
                new EvmFnResult(
                        contractId,
                        result,
                        error,
                        bloom,
                        gasUsed,
                        logs,
                        createdContractIds,
                        evmAddress,
                        gas,
                        amount,
                        "randomParameters".getBytes(),
                        senderId);
        final var ten =
                new EvmFnResult(
                        contractId,
                        result,
                        error,
                        bloom,
                        gasUsed,
                        logs,
                        createdContractIds,
                        evmAddress,
                        gas,
                        amount,
                        "randomParameters".getBytes(),
                        null);

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertNotEquals(one, two);
        assertNotEquals(one, four);
        assertNotEquals(one, five);
        assertNotEquals(one, six);
        assertNotEquals(one, seven);
        assertNotEquals(one, nine);
        assertNotEquals(one, ten);
        assertEquals(one, three);

        assertNotEquals(one.hashCode(), two.hashCode());
        assertEquals(one.hashCode(), three.hashCode());
    }

    @Test
    void beanWorks() {
        assertEquals(
                new EvmFnResult(
                        subject.getContractId(),
                        subject.getResult(),
                        subject.getError(),
                        subject.getBloom(),
                        subject.getGasUsed(),
                        subject.getLogs(),
                        subject.getCreatedContractIds(),
                        subject.getEvmAddress(),
                        subject.getGas(),
                        subject.getAmount(),
                        subject.getFunctionParameters(),
                        subject.getSenderId()),
                subject);
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "EvmFnResult{"
                        + "gasUsed="
                        + gasUsed
                        + ", "
                        + "bloom="
                        + CommonUtils.hex(bloom)
                        + ", "
                        + "result="
                        + CommonUtils.hex(result)
                        + ", "
                        + "error="
                        + error
                        + ", "
                        + "contractId="
                        + contractId
                        + ", "
                        + "createdContractIds="
                        + createdContractIds
                        + ", "
                        + "logs="
                        + logs
                        + ", evmAddress=0000000000000000000000000000000000000009, "
                        + "gas="
                        + gas
                        + ", "
                        + "amount="
                        + amount
                        + ", "
                        + "functionParameters="
                        + CommonUtils.hex(functionParameters)
                        + ", senderId=EntityId{shard=0, realm=0, num=42}"
                        + "}",
                subject.toString());
    }

    @Test
    void nullEqualsWork() {
        assertEquals(subject, subject);
        assertNotEquals(null, subject);
        assertNotEquals(1, subject);
    }

    @Test
    void grpcFactoryWorksWithEverythingSet() {
        subject =
                new EvmFnResult(
                        contractId,
                        result,
                        error,
                        bloom,
                        gasUsed,
                        logs,
                        createdContractIds,
                        evmAddress,
                        gas,
                        amount,
                        functionParameters,
                        senderId);

        final var grpc =
                ContractFunctionResult.newBuilder()
                        .setGasUsed(gasUsed)
                        .setContractCallResult(ByteString.copyFrom(result))
                        .setBloom(ByteString.copyFrom(bloom))
                        .setErrorMessage(error)
                        .setContractID(contractId.toGrpcContractId())
                        .addAllCreatedContractIDs(
                                createdContractIds.stream()
                                        .map(EntityId::toGrpcContractId)
                                        .collect(toList()))
                        .addAllLogInfo(logs.stream().map(EvmLog::toGrpc).collect(toList()))
                        .setEvmAddress(
                                BytesValue.newBuilder().setValue(ByteString.copyFrom(evmAddress)))
                        .setGas(gas)
                        .setAmount(amount)
                        .setFunctionParameters(ByteString.copyFrom(functionParameters))
                        .setSenderId(senderId.toGrpcAccountId())
                        .build();

        assertEquals(grpc, subject.toGrpc());
    }

    @Test
    void grpcFactoryWorksWithSomeFieldsMissing() {
        subject =
                new EvmFnResult(
                        null,
                        result,
                        null,
                        bloom,
                        gasUsed,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        EvmFnResult.EMPTY,
                        gas,
                        0L,
                        functionParameters,
                        senderId);

        final var grpc =
                ContractFunctionResult.newBuilder()
                        .setGasUsed(gasUsed)
                        .setContractCallResult(ByteString.copyFrom(result))
                        .setBloom(ByteString.copyFrom(bloom))
                        .setGas(gas)
                        .setFunctionParameters(ByteString.copyFrom(functionParameters))
                        .setSenderId(senderId.toGrpcAccountId())
                        .build();

        assertEquals(grpc, subject.toGrpc());
    }

    @Test
    void viewWorks() {
        final var actual = subject.toGrpc();
        final var expected =
                ContractFunctionResult.newBuilder()
                        .setGasUsed(gasUsed)
                        .setContractCallResult(ByteString.copyFrom(result))
                        .setBloom(ByteString.copyFrom(bloom))
                        .setErrorMessage(error)
                        .setContractID(contractId.toGrpcContractId())
                        .addAllCreatedContractIDs(
                                createdContractIds.stream()
                                        .map(EntityId::toGrpcContractId)
                                        .collect(toList()))
                        .addAllLogInfo(logs.stream().map(EvmLog::toGrpc).collect(toList()))
                        .setEvmAddress(
                                BytesValue.newBuilder().setValue(ByteString.copyFrom(evmAddress)))
                        .setGas(gas)
                        .setAmount(amount)
                        .setFunctionParameters(ByteString.copyFrom(functionParameters))
                        .setSenderId(senderId.toGrpcAccountId())
                        .build();

        assertEquals(expected, actual);
    }

    @Test
    void serializableDetWorks() {
        assertEquals(EvmFnResult.RELEASE_0290_VERSION, subject.getVersion());
        assertEquals(EvmFnResult.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void updateFromEvmCallContextWorks() {
        var oneByte = new byte[] {1};
        var senderId = EntityId.fromIdentityCode(42);
        EthTxData ethTxData =
                new EthTxData(
                        oneByte,
                        EthTxData.EthTransactionType.EIP2930,
                        oneByte,
                        1,
                        oneByte,
                        oneByte,
                        oneByte,
                        5678,
                        oneByte,
                        BigInteger.valueOf(34_000_000_000L),
                        oneByte,
                        null,
                        1,
                        oneByte,
                        oneByte,
                        oneByte);

        subject.updateForEvmCall(ethTxData, senderId);

        assertEquals(5678, subject.getGas());
        assertEquals(3, subject.getAmount());
        assertArrayEquals(oneByte, subject.getFunctionParameters());
        assertEquals(senderId, subject.getSenderId());
    }

    private static EvmLog logFrom(final int s) {
        return new EvmLog(contracts[s], blooms[s], List.of(topics[s], topics[s + 1 % 3]), data[s]);
    }

    private static final EntityId[] contracts =
            new EntityId[] {
                new EntityId(1L, 2L, 3L), new EntityId(2L, 3L, 4L), new EntityId(3L, 4L, 5L),
            };

    private static final byte[][] topics =
            new byte[][] {
                "alpha000000000000000000000000000".getBytes(),
                "bravo000000000000000000000000000".getBytes(),
                "charlie0000000000000000000000000".getBytes(),
            };

    private static final byte[][] otherTopics =
            new byte[][] {
                "alpha999999999999999999999999999".getBytes(),
                "bravo999999999999999999999999999".getBytes(),
                "charlie9999999999999999999999999".getBytes(),
            };

    private static final byte[][] blooms =
            new byte[][] {
                "tulip".getBytes(), "lily".getBytes(), "cynthia".getBytes(),
            };

    private static final byte[][] data =
            new byte[][] {
                "one".getBytes(), "two".getBytes(), "three".getBytes(),
            };

    private static final Log aLog = besuLog(123L, data[0], topics);
    private static final Log bLog = besuLog(456L, data[1], otherTopics);
    private static final List<Log> besuLogs = List.of(aLog, bLog);
    private static final byte[] realBloom = bloomForAll(besuLogs);

    private static Log besuLog(final long num, byte[] data, byte[][] topics) {
        final var logger = EntityNum.fromLong(num);
        final var l =
                new Log(
                        logger.toEvmAddress(),
                        Bytes.wrap(data),
                        Arrays.stream(topics)
                                .map(bytes -> LogTopic.of(Bytes.wrap(bytes)))
                                .toList());
        return l;
    }

    static byte[] bloomForAll(final List<Log> logs) {
        return LogsBloomFilter.builder().insertLogs(logs).build().toArray();
    }
}
