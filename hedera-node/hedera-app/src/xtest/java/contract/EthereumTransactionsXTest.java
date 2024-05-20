/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package contract;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.streams.TransactionSidecarRecord.SidecarRecordsOneOfType.ACTIONS;
import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static contract.XTestConstants.ONE_HBAR;
import static contract.XTestConstants.SENDER_ID;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Integers;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.test.fixtures.state.MapReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Verifying behavior of the different ethereum transaction types and their gas consumption
 */
public class EthereumTransactionsXTest extends AbstractContractXTest {

    public static final Key A_SECP256K1_KEY = Key.newBuilder()
            .ecdsaSecp256k1(Bytes.fromHex("024fcacef3cde69224130af221ca1366ac0bffc28c3e52e212a8db8abbdd9c81fd"))
            .build();
    public static final Bytes SENDER_ALIAS = Bytes.fromHex("4093a526e47d08b91dc09e51e65c8cafbaa6dade");
    public static final Account SENDER_ACCOUNT = Account.newBuilder()
            .accountId(SENDER_ID)
            .alias(SENDER_ALIAS)
            .key(A_SECP256K1_KEY)
            .tinybarBalance(1000 * ONE_HBAR)
            .build();
    public static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);
    private final BigInteger gasPrice = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(10L));
    private final BigInteger maxFeePerGas = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(50L));
    private final long maxPriorityGas = 20_000L;
    private final long gasLimit = 1_000_000L;
    private final TupleType LONG_TUPLE = TupleType.parse("(int64)");
    static final long NEXT_ENTITY_NUM = 1234L;
    private static final FileID PAY_RESEIVABLE_ID =
            FileID.newBuilder().fileNum(1002L).build();
    private static final Function DEPOSIT_FUNCTION = new Function("deposit(uint256)");
    private static final Function WITHDRAW_FUNCTION = new Function("withdraw()");
    private static final Function GET_BALANCE_FUNCTION = new Function("getBalance()", "(uint256)");

    @Override
    protected void doScenarioOperations() {

        var ethereumTxnHandler = CONTRACT_SERVICE.handlers().ethereumTransactionHandler();
        var depositArgs = Bytes.wrap(
                        DEPOSIT_FUNCTION.encodeCallWithArgs(BigInteger.ONE).array())
                .toByteArray();
        // deploy contract
        handleAndCommitEthereumTransaction(ethereumTxnHandler, synthDeployTxn(PAY_RESEIVABLE_ID), OK);

        // get contract alias
        var accounts = component.hederaState().getReadableStates("TokenService").get("ACCOUNTS");
        ((MapReadableKVState) accounts).reset();
        var account =
                (Account) accounts.get(AccountID.newBuilder().accountNum(1234).build());

        // assert gas used for LEGACY call
        var legacyEthData = buildEthereumDataData(
                EthTxData.EthTransactionType.LEGACY_ETHEREUM,
                depositArgs,
                account.alias().toByteArray(),
                gasLimit,
                gasPrice.longValueExact(),
                maxFeePerGas.longValueExact(),
                maxPriorityGas);
        var legacyRecord = handleAndCommitEthereumTransaction(ethereumTxnHandler, synthEthCallTxn(legacyEthData), OK);
        assertContractCallGasUsed(legacyRecord);

        // assert gas used for EIP1559 call
        var depositEthereumData = buildEthereumDataData(
                EthTxData.EthTransactionType.EIP1559,
                depositArgs,
                account.alias().toByteArray(),
                gasLimit,
                gasPrice.longValueExact(),
                maxFeePerGas.longValueExact(),
                maxPriorityGas);

        var depositTxn = synthEthCallTxn(depositEthereumData);
        var depositRecord = handleAndCommitEthereumTransaction(ethereumTxnHandler, depositTxn, OK);
        assertContractCallGasUsed(depositRecord);

        // assert gas used for EIP1559 call without priority gas
        var noPriorityData = buildEthereumDataData(
                EthTxData.EthTransactionType.EIP1559,
                Bytes.wrap(DEPOSIT_FUNCTION.encodeCallWithArgs(BigInteger.ONE).array())
                        .toByteArray(),
                account.alias().toByteArray(),
                gasLimit,
                0,
                0,
                0);

        var noPriorityTnx = synthEthCallTxn(noPriorityData);
        var noPriorityRecord = handleAndCommitEthereumTransaction(ethereumTxnHandler, noPriorityTnx, OK);
        assertContractCallGasUsed(noPriorityRecord);

        // assert negative gas values
        var negativeGasData = buildEthereumDataData(
                EthTxData.EthTransactionType.EIP1559,
                depositArgs,
                account.alias().toByteArray(),
                -gasLimit,
                gasPrice.longValueExact(),
                maxFeePerGas.longValueExact(),
                maxPriorityGas);

        var negativeGasTxn = synthEthCallTxn(negativeGasData);
        handleAndCommitEthereumTransaction(ethereumTxnHandler, negativeGasTxn, INSUFFICIENT_GAS);

        // FUTURE - test consumed gas with EIP2930 accessList
    }

    @Override
    protected Configuration configuration() {
        return HederaTestConfigBuilder.create()
                .withValue("contracts.chainId", "298")
                .withValue("contracts.maxRefundPercentOfGasLimit", "100")
                .getOrCreateConfig();
    }

    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                PAY_RESEIVABLE_ID,
                File.newBuilder()
                        .contents(resourceAsBytes("initcode/PayReceivable.bin"))
                        .build());
        return files;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = withSenderAlias(new HashMap<>());
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ALIAS).build(), SENDER_ACCOUNT.accountId());
        return aliases;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                AssortedOpsXTestConstants.COINBASE_ID,
                Account.newBuilder()
                        .accountId(AssortedOpsXTestConstants.COINBASE_ID)
                        .build());
        accounts.put(SENDER_ACCOUNT.accountId(), SENDER_ACCOUNT);
        return accounts;
    }

    private TransactionBody synthDeployTxn(@Nullable FileID fileID) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(SENDER_ACCOUNT.accountId()))
                .ethereumTransaction(EthereumTransactionBody.newBuilder()
                        .maxGasAllowance(100 * ONE_HBAR)
                        .ethereumData(buildEthereumDataData(
                                EthTxData.EthTransactionType.EIP1559,
                                org.apache.tuweni.bytes.Bytes.fromHexString(
                                                new String(resourceAsBytes("initcode/PayReceivable.bin")
                                                        .toByteArray()))
                                        .toArray(),
                                new byte[] {},
                                gasLimit,
                                gasPrice.longValueExact(),
                                maxFeePerGas.longValueExact(),
                                maxPriorityGas))
                        .callData(fileID)
                        .build())
                .build();
    }

    private TransactionBody synthEthCallTxn(@NonNull Bytes ethereumData) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(SENDER_ACCOUNT.accountId()))
                .ethereumTransaction(EthereumTransactionBody.newBuilder()
                        .maxGasAllowance(ONE_HBAR)
                        .ethereumData(ethereumData)
                        .build())
                .build();
    }

    private Bytes buildEthereumDataData(
            @NonNull EthTxData.EthTransactionType type,
            @NonNull byte[] callData,
            @NonNull byte[] to,
            long gasLimit,
            long gasPrice,
            long maxFeePerGas,
            long maxPriorityGas) {

        final var gasPriceBytes = gasLongToBytes(gasPrice);
        final var maxFeePerGasBytes = gasLongToBytes(maxFeePerGas);
        final var maxPriorityGasBytes = gasLongToBytes(maxPriorityGas);

        final var ethTxData = new EthTxData(
                null,
                type,
                Integers.toBytes(298),
                ethNonce,
                gasPriceBytes,
                maxPriorityGasBytes,
                maxFeePerGasBytes,
                gasLimit,
                to,
                WEIBARS_TO_TINYBARS.multiply(BigInteger.ONE), // value
                callData,
                new byte[] {}, // accessList
                0,
                null,
                null,
                null);

        var signedEthTxData = EthTxSigs.signMessage(
                ethTxData, A_SECP256K1_KEY.ecdsaSecp256k1().toByteArray());

        if (to.length == 0) {
            // create transaction will use file id to obtain the call data
            signedEthTxData = signedEthTxData.replaceCallData(new byte[] {});
        }

        return Bytes.wrap(signedEthTxData.encodeTx());
    }

    protected byte[] gasLongToBytes(final Long gas) {
        return org.apache.tuweni.bytes.Bytes.wrap(
                        LONG_TUPLE.encode(Tuple.of(gas)).array())
                .toArray();
    }

    protected void assertContractCallGasUsed(@NonNull SingleTransactionRecord record) {
        var contractCallResult = record.transactionRecord().contractCallResult();
        requireNonNull(contractCallResult, "Contract call result can not be null");

        var gasFromResult = contractCallResult.gasUsed();

        // calculate gas consumed by toplevel sidecar action
        var actions = new ArrayList<ContractAction>();
        var sidecars = record.transactionSidecarRecords();
        // filter only actions sidecars
        var actionsSidecars = sidecars.stream()
                .filter((sidecar) -> sidecar.sidecarRecords().kind().equals(ACTIONS))
                .toList();

        // filter only call actions
        actionsSidecars.forEach(
                (sidecar) -> ((ContractActions) sidecar.sidecarRecords().value())
                        .contractActions().stream()
                                .filter(action -> action.callType().equals(ContractActionType.CALL))
                                .forEach(actions::add));

        var intrinsic = component()
                .gasCalculator()
                .transactionIntrinsicGasCost(
                        ConversionUtils.pbjToTuweniBytes(contractCallResult.functionParameters()), false);

        var gasFromActions =
                actions.stream().findFirst().orElse(ContractAction.DEFAULT).gasUsed() + intrinsic;
        assertEquals(gasFromResult, gasFromActions);
    }

    BiConsumer<ContractActions, ArrayList<ContractAction>> getContractCallActions = (sidecar, result) -> {
        sidecar.contractActions().forEach(action -> {
            if (action.callType().equals(ContractActionType.CALL)) {
                result.add(action);
            }
        });
    };
}
