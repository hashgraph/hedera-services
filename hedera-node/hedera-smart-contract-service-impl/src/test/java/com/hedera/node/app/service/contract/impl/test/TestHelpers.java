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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.Objects;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.operation.Operation;

public class TestHelpers {
    public static long REQUIRED_GAS = 123L;
    public static long NONCE = 678;
    public static long VALUE = 999_999;
    public static long INTRINSIC_GAS = 12_345;
    public static long GAS_LIMIT = 1_000_000;
    public static long GAS_PRICE = 666;
    public static long NETWORK_GAS_PRICE = 777;
    public static long MAX_GAS_ALLOWANCE = 666_666_666;
    public static Bytes CALL_DATA = Bytes.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
    public static Bytes MAINNET_CHAIN_ID = Bytes.fromHex("0127");
    public static AccountID SENDER_ID = AccountID.newBuilder().accountNum(1234).build();
    public static AccountID RELAYER_ID = AccountID.newBuilder().accountNum(2345).build();
    public static ContractID CALLED_CONTRACT_ID =
            ContractID.newBuilder().contractNum(666).build();
    public static Address SYSTEM_ADDRESS =
            Address.fromHexString(BigInteger.valueOf(750).toString(16));
    public static Address HTS_PRECOMPILE_ADDRESS = Address.fromHexString("0x167");
    public static Address NON_SYSTEM_LONG_ZERO_ADDRESS = Address.fromHexString("0x1234576890");
    public static Address EIP_1014_ADDRESS = Address.fromHexString("0x89abcdef89abcdef89abcdef89abcdef89abcdef");
    public static ContractID CALLED_CONTRACT_EVM_ADDRESS =
            ContractID.newBuilder().evmAddress(tuweniToPbjBytes(EIP_1014_ADDRESS)).build();

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

    public static HederaEvmTransaction wellKnownHapiCall(
            @Nullable final AccountID relayer,
            final long value) {
        return wellKnownHapiCall(relayer, value, GAS_LIMIT);
    }

    public static HederaEvmTransaction wellKnownHapiCall(
            @Nullable final AccountID relayer,
            final long value,
            final long gasLimit) {
        return new HederaEvmTransaction(
                SENDER_ID,
                relayer,
                CALLED_CONTRACT_ID,
                NONCE,
                CALL_DATA,
                MAINNET_CHAIN_ID,
                value,
                gasLimit,
                GAS_PRICE,
                MAX_GAS_ALLOWANCE);
    }

    public static HederaEvmTransaction wellKnownLazyCreationWithGasLimit(final long gasLimit) {
        return new HederaEvmTransaction(
                SENDER_ID,
                RELAYER_ID,
                CALLED_CONTRACT_EVM_ADDRESS,
                NONCE,
                CALL_DATA,
                MAINNET_CHAIN_ID,
                VALUE,
                gasLimit,
                GAS_PRICE,
                MAX_GAS_ALLOWANCE);
    }

    public static HederaEvmContext wellKnownContextWith(@NonNull final HederaEvmBlocks blocks) {
        return new HederaEvmContext(NETWORK_GAS_PRICE, false, blocks);
    }

    public static HederaEvmContext wellKnownContextWith(
            @NonNull final HederaEvmBlocks blocks, final boolean staticCall) {
        return new HederaEvmContext(NETWORK_GAS_PRICE, staticCall, blocks);
    }
}
