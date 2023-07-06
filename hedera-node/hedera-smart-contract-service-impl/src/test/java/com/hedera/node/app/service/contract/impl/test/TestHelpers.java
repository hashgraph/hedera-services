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

package com.hedera.node.app.service.contract.impl.test;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.gas.GasCharges;
import com.hedera.node.app.service.contract.impl.hevm.*;
import com.hedera.node.app.service.contract.impl.state.StorageAccess;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.operation.Operation;

public class TestHelpers {
    public static int HEDERA_MAX_REFUND_PERCENTAGE = 20;
    public static long REQUIRED_GAS = 123L;
    public static long NONCE = 678;
    public static long VALUE = 999_999;
    public static long INTRINSIC_GAS = 12_345;
    public static long GAS_LIMIT = 1_000_000;
    public static long DEFAULT_COINBASE = 98;
    public static long SOME_BLOCK_NO = 321321;
    public static long USER_OFFERED_GAS_PRICE = 666;
    public static long NETWORK_GAS_PRICE = 777;
    public static Wei WEI_NETWORK_GAS_PRICE = Wei.of(NETWORK_GAS_PRICE);
    public static long BESU_MAX_REFUND_QUOTIENT = 2;
    public static long MAX_GAS_ALLOWANCE = 666_666_666;
    public static Bytes CALL_DATA = Bytes.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
    public static Bytes OUTPUT_DATA = Bytes.wrap(new byte[] {9, 8, 7, 6, 5, 4, 3, 2, 1});
    public static Bytes TOPIC = Bytes.wrap(new byte[] {11, 21, 31, 41, 51, 61, 71, 81, 91});
    public static Bytes MAINNET_CHAIN_ID = Bytes.fromHex("0127");
    public static AccountID SENDER_ID = AccountID.newBuilder().accountNum(1234).build();
    public static AccountID RELAYER_ID = AccountID.newBuilder().accountNum(2345).build();
    public static ContractID CALLED_CONTRACT_ID =
            ContractID.newBuilder().contractNum(666).build();
    public static ContractID INVALID_CONTRACT_ADDRESS =
            ContractID.newBuilder().evmAddress(Bytes.wrap("abcdefg")).build();
    public static Address SYSTEM_ADDRESS =
            Address.fromHexString(BigInteger.valueOf(750).toString(16));
    public static Address HTS_PRECOMPILE_ADDRESS = Address.fromHexString("0x167");
    public static Address NON_SYSTEM_LONG_ZERO_ADDRESS = Address.fromHexString("0x1234576890");
    public static org.apache.tuweni.bytes.Bytes SOME_REVERT_REASON =
            org.apache.tuweni.bytes.Bytes.wrap("I prefer not to".getBytes());
    public static ContractID NON_SYSTEM_CONTRACT_ID = ContractID.newBuilder()
            .contractNum(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS))
            .build();
    public static Address EIP_1014_ADDRESS = Address.fromHexString("0x89abcdef89abcdef89abcdef89abcdef89abcdef");
    public static ContractID CALLED_CONTRACT_EVM_ADDRESS = ContractID.newBuilder()
            .evmAddress(tuweniToPbjBytes(EIP_1014_ADDRESS))
            .build();
    public static Code CONTRACT_CODE = CodeFactory.createCode(pbjToTuweniBytes(CALL_DATA), 0, false);
    public static Log BESU_LOG = new Log(
            NON_SYSTEM_LONG_ZERO_ADDRESS,
            pbjToTuweniBytes(TestHelpers.CALL_DATA),
            List.of(LogTopic.of(pbjToTuweniBytes(TestHelpers.TOPIC))));

    public static GasCharges CHARGING_RESULT = new GasCharges(INTRINSIC_GAS, MAX_GAS_ALLOWANCE / 2);
    public static GasCharges NO_ALLOWANCE_CHARGING_RESULT = new GasCharges(INTRINSIC_GAS, 0);
    public static HederaEvmTransactionResult SUCCESS_RESULT = HederaEvmTransactionResult.successFrom(
            GAS_LIMIT / 2,
            Wei.of(NETWORK_GAS_PRICE),
            CALLED_CONTRACT_ID,
            CALLED_CONTRACT_EVM_ADDRESS,
            pbjToTuweniBytes(CALL_DATA),
            List.of(BESU_LOG),
            null);

    public static StorageAccesses ONE_STORAGE_ACCESSES =
            new StorageAccesses(123L, List.of(StorageAccess.newRead(UInt256.MIN_VALUE, UInt256.MAX_VALUE)));
    public static StorageAccesses TWO_STORAGE_ACCESSES = new StorageAccesses(
            456L,
            List.of(
                    StorageAccess.newRead(UInt256.MAX_VALUE, UInt256.MIN_VALUE),
                    StorageAccess.newWrite(UInt256.ONE, UInt256.MIN_VALUE, UInt256.MAX_VALUE)));
    public static List<StorageAccesses> SOME_STORAGE_ACCESSES = List.of(ONE_STORAGE_ACCESSES, TWO_STORAGE_ACCESSES);

    public static void assertSameResult(
            final Operation.OperationResult expected, final Operation.OperationResult actual) {
        assertEquals(expected.getHaltReason(), actual.getHaltReason());
        assertEquals(expected.getGasCost(), actual.getGasCost());
    }

    public static boolean isSameResult(
            final Operation.OperationResult expected, final Operation.OperationResult actual) {
        return Objects.equals(expected.getHaltReason(), actual.getHaltReason())
                && expected.getGasCost() == actual.getGasCost();
    }

    public static HederaEvmTransaction wellKnownHapiCall() {
        return wellKnownHapiCall(null, VALUE);
    }

    public static HederaEvmTransaction wellKnownRelayedHapiCall(final long value) {
        return wellKnownHapiCall(RELAYER_ID, value);
    }

    public static HederaEvmTransaction wellKnownRelayedHapiCallWithGasLimit(final long gasLimit) {
        return wellKnownHapiCall(RELAYER_ID, VALUE, gasLimit);
    }

    public static HederaEvmTransaction wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(
            final long gasPrice, final long maxGasAllowance) {
        return wellKnownHapiCall(RELAYER_ID, VALUE, GAS_LIMIT, gasPrice, maxGasAllowance);
    }

    public static HederaEvmTransaction wellKnownHapiCall(@Nullable final AccountID relayer, final long value) {
        return wellKnownHapiCall(relayer, value, GAS_LIMIT);
    }

    public static HederaEvmTransaction wellKnownHapiCall(
            @Nullable final AccountID relayer, final long value, final long gasLimit) {
        return wellKnownHapiCall(relayer, value, gasLimit, USER_OFFERED_GAS_PRICE, MAX_GAS_ALLOWANCE);
    }

    public static HederaEvmTransaction wellKnownHapiCall(
            @Nullable final AccountID relayer,
            final long value,
            final long gasLimit,
            final long userGasPrice,
            final long maxGasAllowance) {
        return new HederaEvmTransaction(
                SENDER_ID,
                relayer,
                CALLED_CONTRACT_ID,
                NONCE,
                CALL_DATA,
                MAINNET_CHAIN_ID,
                value,
                gasLimit,
                userGasPrice,
                maxGasAllowance);
    }

    public static HederaEvmTransaction wellKnownHapiCreate() {
        return wellKnownHapiCreate(null, VALUE, GAS_LIMIT, NETWORK_GAS_PRICE, 0);
    }

    public static HederaEvmTransaction wellKnownRelayedHapiCreate() {
        return wellKnownHapiCreate(RELAYER_ID, VALUE, GAS_LIMIT, USER_OFFERED_GAS_PRICE, MAX_GAS_ALLOWANCE);
    }

    private static HederaEvmTransaction wellKnownHapiCreate(
            @Nullable final AccountID relayer,
            final long value,
            final long gasLimit,
            final long userGasPrice,
            final long maxGasAllowance) {
        return new HederaEvmTransaction(
                SENDER_ID,
                relayer,
                null,
                NONCE,
                CALL_DATA,
                MAINNET_CHAIN_ID,
                value,
                gasLimit,
                userGasPrice,
                maxGasAllowance);
    }

    public static HederaEvmContext wellKnownContextWith(
            @NonNull final HederaEvmCode code, @NonNull final HederaEvmBlocks blocks) {
        return new HederaEvmContext(NETWORK_GAS_PRICE, false, code, blocks);
    }

    public static HederaEvmContext wellKnownContextWith(
            @NonNull final HederaEvmCode code, @NonNull final HederaEvmBlocks blocks, final boolean staticCall) {
        return new HederaEvmContext(NETWORK_GAS_PRICE, staticCall, code, blocks);
    }

    public static void assertFailsWith(@NonNull final ResponseCodeEnum status, @NonNull final Runnable something) {
        final var ex = assertThrows(HandleException.class, something::run);
        assertEquals(status, ex.getStatus());
    }
}
