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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_OPERATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.service.contract.impl.exec.failure.ResourceExhaustedException;
import com.hedera.node.app.service.contract.impl.exec.gas.GasCharges;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult;
import com.hedera.node.app.service.contract.impl.state.StorageAccess;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompiledContract.PrecompileContractResult;

public class TestHelpers {

    public static final Bytes ETH_WITH_CALL_DATA = Bytes.fromHex(
            "f864012f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc18180827653820277a0f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2fa00c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792");
    public static final Bytes ETH_WITH_TO_ADDRESS = Bytes.fromHex(
            "02f8ad82012a80a000000000000000000000000000000000000000000000000000000000000003e8a0000000000000000000000000000000000000000000000000000000746a528800831e848094fee687d5088faff48013a6767505c027e2742536880de0b6b3a764000080c080a0f5ddf2394311e634e2147bf38583a017af45f4326bdf5746cac3a1110f973e4fa025bad52d9a9f8b32eb983c9fb8959655258bd75e2826b2c6a48d4c26ec30d112");
    public static final EthTxData ETH_DATA_WITH_TO_ADDRESS =
            requireNonNull(EthTxData.populateEthTxData(ETH_WITH_TO_ADDRESS.toByteArray()));
    public static final EthTxData ETH_DATA_WITH_CALL_DATA =
            requireNonNull(EthTxData.populateEthTxData(ETH_WITH_CALL_DATA.toByteArray()));
    public static final EthTxData ETH_DATA_WITHOUT_TO_ADDRESS = ETH_DATA_WITH_TO_ADDRESS.replaceTo(new byte[0]);
    public static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();
    public static final LedgerConfig DEFAULT_LEDGER_CONFIG = DEFAULT_CONFIG.getConfigData(LedgerConfig.class);
    public static final StakingConfig DEFAULT_STAKING_CONFIG = DEFAULT_CONFIG.getConfigData(StakingConfig.class);
    public static final ContractsConfig DEFAULT_CONTRACTS_CONFIG = DEFAULT_CONFIG.getConfigData(ContractsConfig.class);
    public static final Configuration AUTO_ASSOCIATING_CONFIG = HederaTestConfigBuilder.create()
            .withValue("contracts.allowAutoAssociations", true)
            .getOrCreateConfig();

    public static final Configuration DEV_CHAIN_ID_CONFIG =
            HederaTestConfigBuilder.create().withValue("contracts.chainId", 298).getOrCreateConfig();
    public static final LedgerConfig AUTO_ASSOCIATING_LEDGER_CONFIG =
            AUTO_ASSOCIATING_CONFIG.getConfigData(LedgerConfig.class);
    public static final ContractsConfig AUTO_ASSOCIATING_CONTRACTS_CONFIG =
            AUTO_ASSOCIATING_CONFIG.getConfigData(ContractsConfig.class);
    public static final ContractsConfig DEV_CHAIN_ID_CONTRACTS_CONFIG =
            DEV_CHAIN_ID_CONFIG.getConfigData(ContractsConfig.class);
    public static final int HEDERA_MAX_REFUND_PERCENTAGE = 20;
    public static final Instant ETERNAL_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    public static final Key AN_ED25519_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0101010101010101010101010101010101010101010101010101010101010101"))
            .build();
    public static final long REQUIRED_GAS = 123L;
    public static final long NONCE = 678;
    public static final long VALUE = 999_999;
    public static final Wei WEI_VALUE = Wei.of(VALUE);
    public static final long INTRINSIC_GAS = 12_345;
    public static final long GAS_LIMIT = 1_000_000;
    public static final long REMAINING_GAS = GAS_LIMIT / 2;
    public static final long DEFAULT_COINBASE = 98;
    public static final String SOME_MEMO = "Something to think about";
    public static final Duration SOME_DURATION =
            Duration.newBuilder().seconds(1234567).build();
    public static final long SOME_BLOCK_NO = 321321;
    public static final long USER_OFFERED_GAS_PRICE = 666;
    public static final long NETWORK_GAS_PRICE = 777;
    public static final Wei WEI_NETWORK_GAS_PRICE = Wei.of(NETWORK_GAS_PRICE);
    public static final long BESU_MAX_REFUND_QUOTIENT = 2;
    public static final long MAX_GAS_ALLOWANCE = 666_666_666;
    public static final int STACK_DEPTH = 1;
    public static final Bytes INITCODE = Bytes.wrap("60a06040526000600b55".getBytes());
    public static final Bytes CALL_DATA = Bytes.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
    public static final Bytes CONSTRUCTOR_PARAMS = Bytes.wrap(new byte[] {2, 3, 2, 3, 2, 3, 2, 3, 2, 3});
    public static final Bytecode BYTECODE = new Bytecode(CALL_DATA);
    public static final Bytes LOG_DATA = Bytes.wrap(new byte[] {6, 6, 6});
    public static final Bytes OUTPUT_DATA = Bytes.wrap(new byte[] {9, 8, 7, 6, 5, 4, 3, 2, 1});
    public static final Bytes TOPIC = Bytes.wrap(new byte[] {11, 21, 31, 41, 51, 61, 71, 81, 91});
    public static final Bytes OTHER_TOPIC = Bytes.wrap(new byte[] {99, 29, 39, 49, 59, 69, 79, 89, 99});
    public static final Bytes MAINNET_CHAIN_ID = Bytes.fromHex("0127");
    public static final AccountID SENDER_ID =
            AccountID.newBuilder().accountNum(1234).build();
    public static final AccountID RELAYER_ID =
            AccountID.newBuilder().accountNum(2345).build();
    public static final ContractID CALLED_CONTRACT_ID =
            ContractID.newBuilder().contractNum(666).build();
    public static final ContractID CHILD_CONTRACT_ID =
            ContractID.newBuilder().contractNum(777).build();
    public static final AccountID CALLED_EOA_ID =
            AccountID.newBuilder().accountNum(666).build();
    public static final ContractID INVALID_CONTRACT_ADDRESS =
            ContractID.newBuilder().evmAddress(Bytes.wrap("abcdefg")).build();
    public static final ContractID VALID_CONTRACT_ADDRESS = ContractID.newBuilder()
            .evmAddress(Bytes.fromHex("1234123412341234123412341234123412341234"))
            .build();
    public static final Address SYSTEM_ADDRESS =
            Address.fromHexString(BigInteger.valueOf(750).toString(16));
    public static final Address HTS_SYSTEM_CONTRACT_ADDRESS = Address.fromHexString("0x167");
    public static final Address PRNG_SYSTEM_CONTRACT_ADDRESS = Address.fromHexString("0x169");
    public static final Address NON_SYSTEM_LONG_ZERO_ADDRESS = Address.fromHexString("0x1234576890");
    public static final FileID INITCODE_FILE_ID =
            FileID.newBuilder().fileNum(6789L).build();
    public static final FileID ETH_CALLDATA_FILE_ID =
            FileID.newBuilder().fileNum(7890L).build();
    public static final TokenID FUNGIBLE_TOKEN_ID =
            TokenID.newBuilder().tokenNum(9876L).build();
    public static final Token FUNGIBLE_TOKEN =
            Token.newBuilder().tokenType(TokenType.FUNGIBLE_COMMON).build();
    public static final AccountID NON_SYSTEM_ACCOUNT_ID = AccountID.newBuilder()
            .accountNum(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS))
            .build();

    public static final AccountID A_NEW_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(191919L).build();
    public static final AccountID B_NEW_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(919191L).build();
    public static final org.apache.tuweni.bytes.Bytes SOME_REVERT_REASON =
            org.apache.tuweni.bytes.Bytes.wrap("I prefer not to".getBytes());
    public static final ContractID NON_SYSTEM_CONTRACT_ID = ContractID.newBuilder()
            .contractNum(numberOfLongZero(NON_SYSTEM_LONG_ZERO_ADDRESS))
            .build();
    public static final Address EIP_1014_ADDRESS = Address.fromHexString("0x89abcdef89abcdef89abcdef89abcdef89abcdef");
    public static final Bytes CANONICAL_ALIAS = tuweniToPbjBytes(EIP_1014_ADDRESS);
    public static final ContractID CALLED_CONTRACT_EVM_ADDRESS =
            ContractID.newBuilder().evmAddress(CANONICAL_ALIAS).build();
    public static final List<ContractNonceInfo> NONCES =
            List.of(new ContractNonceInfo(CALLED_CONTRACT_ID, NONCE), new ContractNonceInfo(CHILD_CONTRACT_ID, 1L));
    public static final EntityNumber CALLED_CONTRACT_ENTITY_NUMBER = new EntityNumber(666);
    public static final Code CONTRACT_CODE = CodeFactory.createCode(pbjToTuweniBytes(CALL_DATA), 0, false);
    public static final Log BESU_LOG = new Log(
            NON_SYSTEM_LONG_ZERO_ADDRESS,
            pbjToTuweniBytes(TestHelpers.CALL_DATA),
            List.of(LogTopic.of(pbjToTuweniBytes(TestHelpers.TOPIC))));
    public static final Log SECOND_BESU_LOG = new Log(
            HTS_SYSTEM_CONTRACT_ADDRESS,
            pbjToTuweniBytes(TestHelpers.CALL_DATA),
            List.of(LogTopic.of(pbjToTuweniBytes(TestHelpers.OTHER_TOPIC))));
    public static final List<Log> BESU_LOGS = List.of(BESU_LOG, SECOND_BESU_LOG);

    public static final GasCharges CHARGING_RESULT = new GasCharges(INTRINSIC_GAS, MAX_GAS_ALLOWANCE / 2);
    public static final GasCharges NO_ALLOWANCE_CHARGING_RESULT = new GasCharges(INTRINSIC_GAS, 0);

    public static final String PSEUDORANDOM_SEED_GENERATOR_SELECTOR = "0xd83bf9a1";
    public static final org.apache.tuweni.bytes.Bytes PSEUDO_RANDOM_SYSTEM_CONTRACT_ADDRESS =
            org.apache.tuweni.bytes.Bytes.fromHexString(PSEUDORANDOM_SEED_GENERATOR_SELECTOR);
    public static final org.apache.tuweni.bytes.Bytes EXPECTED_RANDOM_NUMBER =
            org.apache.tuweni.bytes.Bytes.fromHexString(
                    "0x1234567890123456789012345678901234567890123456789012345678901234");
    public static final PrecompileContractResult PRECOMPILE_CONTRACT_SUCCESS_RESULT =
            PrecompiledContract.PrecompileContractResult.success(EXPECTED_RANDOM_NUMBER);
    public static final PrecompileContractResult PRECOMPILE_CONTRACT_FAILED_RESULT =
            PrecompiledContract.PrecompileContractResult.halt(
                    org.apache.tuweni.bytes.Bytes.EMPTY, Optional.of(INVALID_OPERATION));

    public static final HederaEvmTransaction HEVM_CREATION = new HederaEvmTransaction(
            SENDER_ID,
            null,
            CALLED_CONTRACT_ID,
            NONCE,
            CALL_DATA,
            MAINNET_CHAIN_ID,
            VALUE,
            GAS_LIMIT,
            0L,
            0L,
            ContractCreateTransactionBody.DEFAULT);
    public static final HederaEvmTransactionResult SUCCESS_RESULT = HederaEvmTransactionResult.successFrom(
            GAS_LIMIT / 2,
            Wei.of(NETWORK_GAS_PRICE),
            SENDER_ID,
            CALLED_CONTRACT_ID,
            CALLED_CONTRACT_EVM_ADDRESS,
            pbjToTuweniBytes(CALL_DATA),
            List.of(BESU_LOG),
            null);

    public static final HederaEvmTransactionResult HALT_RESULT = new HederaEvmTransactionResult(
            GAS_LIMIT / 2,
            NETWORK_GAS_PRICE,
            SENDER_ID,
            null,
            null,
            Bytes.EMPTY,
            "I prefer not to",
            null,
            Collections.emptyList(),
            null);

    public static final StorageAccesses ONE_STORAGE_ACCESSES =
            new StorageAccesses(123L, List.of(StorageAccess.newRead(UInt256.MIN_VALUE, UInt256.MAX_VALUE)));
    public static final StorageAccesses TWO_STORAGE_ACCESSES = new StorageAccesses(
            456L,
            List.of(
                    StorageAccess.newRead(UInt256.MAX_VALUE, UInt256.MIN_VALUE),
                    StorageAccess.newWrite(UInt256.ONE, UInt256.MIN_VALUE, UInt256.MAX_VALUE)));
    public static final List<StorageAccesses> SOME_STORAGE_ACCESSES =
            List.of(ONE_STORAGE_ACCESSES, TWO_STORAGE_ACCESSES);

    public static final ContractAction CALL_ACTION = ContractAction.newBuilder()
            .callType(ContractActionType.CALL)
            .input(CALL_DATA)
            .output(OUTPUT_DATA)
            .gas(REMAINING_GAS)
            .callingContract(CALLED_CONTRACT_ID)
            .recipientContract(CALLED_CONTRACT_ID)
            .callOperationType(CallOperationType.OP_CALL)
            .build();

    public static final ContractAction MISSING_ADDRESS_CALL_ACTION = ContractAction.newBuilder()
            .callType(ContractActionType.CALL)
            .error(Bytes.wrap("INVALID_SOLIDITY_ADDRESS".getBytes()))
            .gas(REMAINING_GAS)
            .build();
    public static final ContractAction LAZY_CREATE_ACTION = ContractAction.newBuilder()
            .callType(ContractActionType.CALL)
            .targetedAddress(tuweniToPbjBytes(EIP_1014_ADDRESS))
            .gas(REMAINING_GAS)
            .build();

    public static final ContractAction CREATE_ACTION = ContractAction.newBuilder()
            .callType(ContractActionType.CREATE)
            .recipientContract(CALLED_CONTRACT_ID)
            .gas(REMAINING_GAS)
            .build();
    private static final ContractCreateTransactionBody MOCK_CREATE_BODY = ContractCreateTransactionBody.newBuilder()
            .memo("Something to think about")
            .build();
    public static final TransactionBody MOCK_CREATION = TransactionBody.newBuilder()
            .contractCreateInstance(MOCK_CREATE_BODY)
            .build();

    private static final ContractCallTransactionBody MOCK_CALL_BODY = ContractCallTransactionBody.newBuilder()
            .contractID(CALLED_CONTRACT_ID)
            .build();
    private static final EthereumTransactionBody MOCK_ETH_BODY =
            EthereumTransactionBody.newBuilder().ethereumData(Bytes.EMPTY).build();
    public static final TransactionBody MOCK_ETH =
            TransactionBody.newBuilder().ethereumTransaction(MOCK_ETH_BODY).build();

    public static final VerificationStrategy MOCK_VERIFICATION_STRATEGY =
            new ActiveContractVerificationStrategy(1, Bytes.EMPTY, true);

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
                maxGasAllowance,
                null);
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
                maxGasAllowance,
                ContractCreateTransactionBody.DEFAULT);
    }

    public static HederaEvmContext wellKnownContextWith(@NonNull final HederaEvmBlocks blocks) {
        return new HederaEvmContext(NETWORK_GAS_PRICE, false, blocks);
    }

    public static HederaEvmContext wellKnownContextWith(
            @NonNull final HederaEvmBlocks blocks, final boolean staticCall) {
        return new HederaEvmContext(NETWORK_GAS_PRICE, staticCall, blocks);
    }

    public static void assertFailsWith(@NonNull final ResponseCodeEnum status, @NonNull final Runnable something) {
        final var ex = assertThrows(HandleException.class, something::run);
        assertEquals(status, ex.getStatus());
    }

    public static void assertExhaustsResourceLimit(
            @NonNull final Runnable something, @NonNull final ResponseCodeEnum status) {
        final var ex = assertThrows(ResourceExhaustedException.class, something::run);
        assertEquals(status, ex.getStatus());
    }
}
