/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.utils;

import static com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations.ZERO_ENTROPY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.BESU_LOG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALL_DATA;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_LONG_ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SOME_STORAGE_ACCESSES;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.TOPIC;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asNumberedContractId;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjLogFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjLogsFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractLoginfo;
import com.hedera.hapi.streams.ContractStateChange;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.hapi.streams.StorageChange;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversionUtilsTest {
    @Mock
    private HederaNativeOperations nativeOperations;

    @Test
    void numberedIdsRequireLongZeroAddress() {
        assertThrows(IllegalArgumentException.class, () -> asNumberedContractId(EIP_1014_ADDRESS));
    }

    @Test
    void wrapsExpectedHashPrefix() {
        assertEquals(Bytes32.leftPad(Bytes.EMPTY, (byte) 0), ConversionUtils.ethHashFrom(ZERO_ENTROPY));
    }

    @Test
    void convertsNumberToLongZeroAddress() {
        final var number = 0x1234L;
        final var expected = Address.fromHexString("0x1234");
        final var actual = ConversionUtils.asLongZeroAddress(number);
        assertEquals(expected, actual);
    }

    @Test
    void justReturnsNumberFromSmallLongZeroAddress() {
        final var smallNumber = 0x1234L;
        final var address = Address.fromHexString("0x1234");
        final var actual = ConversionUtils.maybeMissingNumberOf(address, nativeOperations);
        assertEquals(smallNumber, actual);
    }

    @Test
    void justReturnsNumberFromLargeLongZeroAddress() {
        final var largeNumber = 0x7fffffffffffffffL;
        final var address = Address.fromHexString("0x7fffffffffffffff");
        final var actual = ConversionUtils.maybeMissingNumberOf(address, nativeOperations);
        assertEquals(largeNumber, actual);
    }

    @Test
    void returnsZeroIfMissingAlias() {
        final var address = Address.fromHexString("0x010000000000000000");
        given(nativeOperations.resolveAlias(any())).willReturn(HederaNativeOperations.MISSING_ENTITY_NUMBER);
        final var actual = ConversionUtils.maybeMissingNumberOf(address, nativeOperations);
        assertEquals(-1L, actual);
    }

    @Test
    void returnsGivenIfPresentAlias() {
        given(nativeOperations.resolveAlias(any())).willReturn(0x1234L);
        final var address = Address.fromHexString("0x010000000000000000");
        final var actual = ConversionUtils.maybeMissingNumberOf(address, nativeOperations);
        assertEquals(0x1234L, actual);
    }

    @Test
    void convertsFromBesuLogAsExpected() {
        final var expectedBloom = Bytes.wrap(bloomFor(BESU_LOG));
        final var expected = ContractLoginfo.newBuilder()
                .contractID(ContractID.newBuilder().contractNum(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS)))
                .bloom(tuweniToPbjBytes(expectedBloom))
                .data(CALL_DATA)
                .topic(List.of(TOPIC))
                .build();

        final var actual = pbjLogFrom(BESU_LOG);

        assertEquals(expected, actual);
    }

    @Test
    void convertsFromBesuLogsAsExpected() {
        final var expectedBloom = Bytes.wrap(bloomFor(BESU_LOG));
        final var expected = ContractLoginfo.newBuilder()
                .contractID(ContractID.newBuilder().contractNum(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS)))
                .bloom(tuweniToPbjBytes(expectedBloom))
                .data(CALL_DATA)
                .topic(List.of(TOPIC))
                .build();

        final var actual = pbjLogsFrom(List.of(BESU_LOG));

        assertEquals(List.of(expected), actual);
    }

    @Test
    void convertsFromStorageAccessesAsExpected() {
        final var expectedPbj = ContractStateChanges.newBuilder()
                .contractStateChanges(
                        ContractStateChange.newBuilder()
                                .contractId(ContractID.newBuilder().contractNum(123L))
                                .storageChanges(new StorageChange(
                                        tuweniToPbjBytes(UInt256.MIN_VALUE), tuweniToPbjBytes(UInt256.MAX_VALUE), null))
                                .build(),
                        ContractStateChange.newBuilder()
                                .contractId(ContractID.newBuilder().contractNum(456L))
                                .storageChanges(
                                        new StorageChange(
                                                tuweniToPbjBytes(UInt256.MAX_VALUE),
                                                tuweniToPbjBytes(UInt256.MIN_VALUE),
                                                null),
                                        new StorageChange(
                                                tuweniToPbjBytes(UInt256.ONE),
                                                tuweniToPbjBytes(UInt256.MIN_VALUE),
                                                tuweniToPbjBytes(UInt256.MAX_VALUE)))
                                .build())
                .build();
        final var actualPbj = ConversionUtils.asPbjStateChanges(SOME_STORAGE_ACCESSES);
        assertEquals(expectedPbj, actualPbj);
    }

    private byte[] bloomFor(@NonNull final Log log) {
        return LogsBloomFilter.builder().insertLog(log).build().toArray();
    }
}
