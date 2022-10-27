/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.evm.contracts.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmTransactionProcessingResultTest {

    private static final long GAS_USAGE = 1234L;
    private static final long GAS_REFUND = 345L;
    private static final long GAS_PRICE = 1234L;
    private final Address logger =
            Address.fromHexString("fe3b557e8fb62b89f4916b721be55ceb828dbd73");

    private final Bytes logTopicBytes =
            Bytes.fromHexString(
                    "0xce8688f853ffa65c042b72302433c25d7a230c322caba0901587534b6551091d");
    private final Bytes output = Bytes.fromHexString("0x05");

    private final LogTopic logTopic = LogTopic.create(logTopicBytes);

    private final Log log =
            new Log(
                    logger,
                    Bytes.fromHexString("0x0102"),
                    List.of(logTopic, logTopic, logTopic, logTopic));

    private final Address recipient =
            Address.fromHexString("0x000000000000000000000000000000000000077e");

    @Test
    void assertCorrectDataOnSuccessfulTransaction() {
        var log =
                new Log(
                        logger,
                        Bytes.fromHexString("0x0102"),
                        List.of(logTopic, logTopic, logTopic, logTopic));
        final var logList = List.of(log);

        var result =
                HederaEvmTransactionProcessingResult.successful(
                        logList, GAS_USAGE, GAS_REFUND, 1234L, output, recipient);

        assertEquals(GAS_USAGE, result.getGasUsed());
        assertEquals(GAS_REFUND, result.getSbhRefund());
        assertEquals(Optional.empty(), result.getHaltReason());
        assertEquals(output, result.getOutput());
    }

    @Test
    void assertCorrectDataOnFailedTransaction() {

        var exception =
                new ExceptionalHaltReason() {
                    @Override
                    public String name() {
                        return "TestExceptionalHaltReason";
                    }

                    @Override
                    public String getDescription() {
                        return "Exception Halt Reason Test Description";
                    }
                };
        var revertReason = Optional.of(Bytes.fromHexString("0x43"));

        final var expect =
                ContractFunctionResult.newBuilder()
                        .setGasUsed(GAS_USAGE)
                        .setBloom(ByteString.copyFrom(new byte[256]));
        expect.setContractCallResult(ByteString.copyFrom(Bytes.EMPTY.toArray()));
        expect.setErrorMessageBytes(ByteString.copyFrom(revertReason.get().toArray()));

        var result =
                HederaEvmTransactionProcessingResult.failed(
                        GAS_USAGE, GAS_REFUND, GAS_PRICE, revertReason, Optional.of(exception));

        assertEquals(expect.getGasUsed(), result.getGasUsed());
        assertEquals(GAS_PRICE, result.getGasPrice());
        assertEquals(GAS_REFUND, result.getSbhRefund());
        assertEquals(Optional.of(exception), result.getHaltReason());
        assertEquals(
                revertReason.get().toString(),
                Bytes.wrap(result.getRevertReason().get()).toHexString());
    }

    @Test
    void assertGasPrice() {
        var result =
                HederaEvmTransactionProcessingResult.successful(
                        List.of(log), GAS_USAGE, GAS_REFUND, GAS_PRICE, Bytes.EMPTY, recipient);

        assertEquals(GAS_PRICE, result.getGasPrice());
    }

    @Test
    void assertSbhRefund() {
        var result =
                HederaEvmTransactionProcessingResult.successful(
                        List.of(log), GAS_USAGE, GAS_REFUND, GAS_PRICE, Bytes.EMPTY, recipient);

        assertEquals(GAS_REFUND, result.getSbhRefund());
    }

    @Test
    void assertGasUsage() {
        var result =
                HederaEvmTransactionProcessingResult.successful(
                        List.of(log), GAS_USAGE, GAS_REFUND, GAS_PRICE, Bytes.EMPTY, recipient);

        assertEquals(GAS_USAGE, result.getGasUsed());
    }

    @Test
    void assertSuccessfulStatus() {
        var result =
                HederaEvmTransactionProcessingResult.successful(
                        List.of(log), GAS_USAGE, GAS_REFUND, GAS_PRICE, Bytes.EMPTY, recipient);

        assertTrue(result.isSuccessful());
    }
}
