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
package com.hedera.services.bdd.spec.transactions.contract;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getPrivateKeyFromSpec;
import static com.hedera.services.bdd.suites.HapiApiSuite.CHAIN_ID;
import static com.hedera.services.bdd.suites.HapiApiSuite.ETH_HASH_KEY;
import static com.hedera.services.bdd.suites.HapiApiSuite.ETH_SENDER_ADDRESS;
import static com.hedera.services.bdd.suites.HapiApiSuite.MAX_CALL_DATA_SIZE;
import static com.hedera.services.bdd.suites.HapiApiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiApiSuite.RELAYER;
import static com.hedera.services.bdd.suites.HapiApiSuite.SECP_256K1_SOURCE_KEY;
import static com.hedera.services.bdd.suites.HapiApiSuite.WEIBARS_TO_TINYBARS;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ethereum.EthTxSigs;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;

public class HapiEthereumContractCreate extends HapiBaseContractCreate<HapiEthereumContractCreate> {
    private EthTxData.EthTransactionType type;
    private long nonce;
    private BigInteger gasPrice = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(50L));
    private final BigInteger maxFeePerGas = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(50L));
    private long maxPriorityGas = 20_000L;
    private Optional<FileID> ethFileID = Optional.empty();
    private boolean invalidateEthData = false;
    private Optional<Long> maxGasAllowance = Optional.of(ONE_HUNDRED_HBARS);
    private String privateKeyRef = SECP_256K1_SOURCE_KEY;

    public HapiEthereumContractCreate exposingNumTo(LongConsumer obs) {
        newNumObserver = Optional.of(obs);
        return this;
    }

    public HapiEthereumContractCreate proxy(String proxy) {
        this.proxy = Optional.of(proxy);
        return this;
    }

    public HapiEthereumContractCreate advertisingCreation() {
        advertiseCreation = true;
        return this;
    }

    public HapiEthereumContractCreate(String contract) {
        super(contract);
        this.payer = Optional.of(RELAYER);
        super.omitAdminKey = true;
    }

    public HapiEthereumContractCreate(String contract, String abi, Object... args) {
        super(contract, abi, args);
        this.payer = Optional.of(RELAYER);
        super.omitAdminKey = true;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.EthereumTransaction;
    }

    @Override
    protected HapiEthereumContractCreate self() {
        return this;
    }

    @Override
    protected Key lookupKey(HapiApiSpec spec, String name) {
        return spec.registry().getKey(name);
    }

    public HapiEthereumContractCreate bytecode(String fileName) {
        bytecodeFile = Optional.of(fileName);
        return this;
    }

    public HapiEthereumContractCreate invalidateEthereumData() {
        invalidateEthData = true;
        return this;
    }

    public HapiEthereumContractCreate bytecode(Supplier<String> supplier) {
        bytecodeFileFn = Optional.of(supplier);
        return this;
    }

    public HapiEthereumContractCreate autoRenewSecs(long period) {
        autoRenewPeriodSecs = Optional.of(period);
        return this;
    }

    public HapiEthereumContractCreate balance(long initial) {
        balance =
                Optional.of(
                        WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(initial)).longValueExact());
        return this;
    }

    public HapiEthereumContractCreate gas(long amount) {
        gas = OptionalLong.of(amount);
        return this;
    }

    public HapiEthereumContractCreate entityMemo(String s) {
        memo = Optional.of(s);
        return this;
    }

    public HapiEthereumContractCreate maxGasAllowance(long maxGasAllowance) {
        this.maxGasAllowance = Optional.of(maxGasAllowance);
        return this;
    }

    public HapiEthereumContractCreate signingWith(String signingWith) {
        this.privateKeyRef = signingWith;
        return this;
    }

    public HapiEthereumContractCreate type(EthTxData.EthTransactionType type) {
        this.type = type;
        return this;
    }

    public HapiEthereumContractCreate nonce(long nonce) {
        this.nonce = nonce;
        return this;
    }

    public HapiEthereumContractCreate gasPrice(long gasPrice) {
        this.gasPrice = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(gasPrice));
        return this;
    }

    public HapiEthereumContractCreate maxPriorityGas(long maxPriorityGas) {
        this.maxPriorityGas = maxPriorityGas;
        return this;
    }

    public HapiEthereumContractCreate gasLimit(long gasLimit) {
        this.gas = OptionalLong.of(gasLimit);
        return this;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
        bytecodeFileFn.ifPresent(
                stringSupplier -> bytecodeFile = Optional.of(stringSupplier.get()));
        if (bytecodeFile.isEmpty()) {
            setBytecodeToDefaultContract(spec);
        }

        final var filePath = Utils.getResourcePath(bytecodeFile.get(), ".bin");
        final var fileContents = Utils.extractByteCode(filePath);

        final byte[] callData =
                Bytes.fromHexString(new String(fileContents.toByteArray())).toArray();
        final var gasPriceBytes = gasLongToBytes(gasPrice.longValueExact());

        final var maxFeePerGasBytes = gasLongToBytes(maxFeePerGas.longValueExact());
        final var maxPriorityGasBytes = gasLongToBytes(maxPriorityGas);

        final var ethTxData =
                new EthTxData(
                        null,
                        type,
                        Integers.toBytes(CHAIN_ID),
                        nonce,
                        gasPriceBytes,
                        maxPriorityGasBytes,
                        maxFeePerGasBytes,
                        gas.orElse(0L),
                        new byte[] {},
                        BigInteger.valueOf(balance.orElse(0L)),
                        callData,
                        new byte[] {},
                        0,
                        null,
                        null,
                        null);

        final byte[] privateKeyByteArray = getPrivateKeyFromSpec(spec, privateKeyRef);
        var signedEthTxData = EthTxSigs.signMessage(ethTxData, privateKeyByteArray);
        spec.registry()
                .saveBytes(ETH_HASH_KEY, ByteString.copyFrom((signedEthTxData.getEthereumHash())));

        final var extractedSignatures = EthTxSigs.extractSignatures(signedEthTxData);
        final var senderAddress = ByteString.copyFrom(extractedSignatures.address());
        spec.registry().saveBytes(ETH_SENDER_ADDRESS, senderAddress);

        System.out.println("Size = " + callData.length + " vs " + MAX_CALL_DATA_SIZE);
        if (fileContents.toByteArray().length > MAX_CALL_DATA_SIZE) {
            ethFileID = Optional.of(TxnUtils.asFileId(bytecodeFile.get(), spec));
            signedEthTxData = signedEthTxData.replaceCallData(new byte[] {});
        }

        final var ethData = signedEthTxData;
        final EthereumTransactionBody opBody =
                spec.txns()
                        .<EthereumTransactionBody, EthereumTransactionBody.Builder>body(
                                EthereumTransactionBody.class,
                                builder -> {
                                    if (invalidateEthData) {
                                        builder.setEthereumData(ByteString.EMPTY);
                                    } else {
                                        builder.setEthereumData(
                                                ByteString.copyFrom(ethData.encodeTx()));
                                    }
                                    ethFileID.ifPresent(builder::setCallData);
                                    maxGasAllowance.ifPresent(builder::setMaxGasAllowance);
                                });
        return b -> b.setEthereumTransaction(opBody);
    }

    @Override
    protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerSigs) throws Throwable {
        return spec.fees()
                .forActivityBasedOp(
                        HederaFunctionality.EthereumTransaction,
                        scFees::getEthereumTransactionFeeMatrices,
                        txn,
                        numPayerSigs);
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
        return spec.clients().getScSvcStub(targetNodeFor(spec), useTls)::createContract;
    }
}
