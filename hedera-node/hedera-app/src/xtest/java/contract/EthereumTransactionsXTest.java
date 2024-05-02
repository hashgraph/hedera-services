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

import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static contract.XTestConstants.ONE_HBAR;
import static contract.XTestConstants.SENDER_ID;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Integers;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

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

    private long nonce = 0L;
    public static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);
    private final BigInteger gasPrice = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(10L));
    private final BigInteger maxFeePerGas = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(50L));
    private final long maxPriorityGas = 20_000L;
    private final TupleType LONG_TUPLE = TupleType.parse("(int64)");
    static final long NEXT_ENTITY_NUM = 1234L;
    private static final FileID FUSE_INITCODE_ID =
            FileID.newBuilder().fileNum(1002L).build();
    private static final Function LIGHT = new Function("light()");

    @Override
    protected void doScenarioOperations() {
        // create Fuse contract
        handleAndCommitSingleTransaction(
                CONTRACT_SERVICE.handlers().ethereumTransactionHandler(), synthDeployTxn(FUSE_INITCODE_ID), OK);

        nonce++;
        // call contract
        handleAndCommitSingleTransaction(CONTRACT_SERVICE.handlers().ethereumTransactionHandler(), synthCallTxn(), OK);
    }

    @Override
    protected Configuration configuration() {
        // TODO: Maybe override contracts.maxRefundPercentOfGasLimit to 100% for easy gas calculations
        return HederaTestConfigBuilder.create()
                .withValue("contracts.chainId", "298")
                // .withValue("contracts.maxRefundPercentOfGasLimit", "100")
                .withValue("contracts.throttle.throttleByGas", "false")
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
                FUSE_INITCODE_ID,
                File.newBuilder().contents(resourceAsBytes("initcode/Fuse.bin")).build());
        return files;
    }

    @Override
    protected Map<EntityNumber, Bytecode> initialBytecodes() {
        final var bytecodes = super.initialBytecodes();
        bytecodes.put(new EntityNumber(234567890), new Bytecode(Bytes.wrap("NONSENSICAL")));
        bytecodes.put(new EntityNumber(123456789), new Bytecode(Bytes.wrap("PLACEHOLDER")));
        return bytecodes;
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
                        .ethereumData(Bytes.wrap(buildData(
                                        EthTxData.EthTransactionType.EIP1559,
                                        org.apache.tuweni.bytes.Bytes.fromHexString(
                                                        new String(resourceAsBytes("initcode/Fuse.bin")
                                                                .toByteArray()))
                                                .toArray(),
                                        new byte[] {})
                                .encodeTx()))
                        .callData(fileID)
                        .build())
                .build();
    }

    private TransactionBody synthCallTxn() {
        var accounts = component.hederaState().getReadableStates("TokenService").get("ACCOUNTS");
        ((MapReadableKVState) accounts).reset();
        var account =
                (Account) accounts.get(AccountID.newBuilder().accountNum(1234).build());
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(SENDER_ACCOUNT.accountId()))
                .ethereumTransaction(EthereumTransactionBody.newBuilder()
                        .maxGasAllowance(100 * ONE_HBAR)
                        .ethereumData(Bytes.wrap(buildData(
                                        EthTxData.EthTransactionType.EIP1559,
                                        Bytes.wrap(LIGHT.encodeCallWithArgs().array())
                                                .toByteArray(),
                                        account.alias().toByteArray())
                                .encodeTx()))
                        .build())
                .build();
    }

    private EthTxData buildData(
            @NonNull EthTxData.EthTransactionType type, @NonNull byte[] callData, @NonNull byte[] to) {

        final var gasPriceBytes = gasLongToBytes(gasPrice.longValueExact());

        final var maxFeePerGasBytes = gasLongToBytes(maxFeePerGas.longValueExact());
        final var maxPriorityGasBytes = gasLongToBytes(maxPriorityGas);

        final var ethTxData = new EthTxData(
                null,
                type,
                Integers.toBytes(298),
                nonce,
                gasPriceBytes,
                maxPriorityGasBytes,
                maxFeePerGasBytes,
                1_000_000L,
                to,
                BigInteger.ZERO, // value
                callData,
                new byte[] {}, // accessList
                0,
                null,
                null,
                null);

        var signedEthTxData = EthTxSigs.signMessage(
                ethTxData, A_SECP256K1_KEY.ecdsaSecp256k1().toByteArray());

        // is create transaction
        if (to.length == 0) {
            signedEthTxData = signedEthTxData.replaceCallData(new byte[] {});
        }

        return signedEthTxData;
    }

    protected byte[] gasLongToBytes(final Long gas) {
        return org.apache.tuweni.bytes.Bytes.wrap(
                        LONG_TUPLE.encode(Tuple.of(gas)).array())
                .toArray();
    }
}
