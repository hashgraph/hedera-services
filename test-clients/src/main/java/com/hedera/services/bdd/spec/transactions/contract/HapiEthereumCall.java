package com.hedera.services.bdd.spec.transactions.contract;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.Integers;
import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCall;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ethereum.EthTxSigs;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.common.utility.CommonUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.CallTransaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.extractTxnId;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getPrivateKeyFromSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.suites.HapiApiSuite.DEFAULT_CONTRACT_SENDER;
import static com.hedera.services.bdd.suites.HapiApiSuite.FIVE_HBARS;
import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiApiSuite.MAX_CALL_DATA_SIZE;
import static com.hedera.services.bdd.suites.HapiApiSuite.RELAYER;
import static com.hedera.services.bdd.suites.HapiApiSuite.WEIBARS_TO_TINYBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class HapiEthereumCall extends HapiBaseCall<HapiEthereumCall> {
    public static final String ETH_HASH_KEY = "EthHash";
    private static final String callDataFileName = "CallData";
    private List<String> otherSigs = Collections.emptyList();
    private Optional<String> details = Optional.empty();
    private Optional<Function<HapiApiSpec, Object[]>> paramsFn = Optional.empty();
    private Optional<ObjLongConsumer<ResponseCodeEnum>> gasObserver = Optional.empty();
    private Optional<Supplier<String>> explicitHexedParams = Optional.empty();

    public static  final long DEFAULT_GAS_PRICE_TINYBARS = 50L;
    private EthTxData.EthTransactionType type = EthTxData.EthTransactionType.EIP1559;
    private byte[] chainId = Integers.toBytes(298);
    private long nonce = 0L;
    private boolean useSpecNonce = true;
    private BigInteger gasPrice = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(DEFAULT_GAS_PRICE_TINYBARS));
    private BigInteger maxFeePerGas = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(DEFAULT_GAS_PRICE_TINYBARS));
    private long maxPriorityGas = 1_000L;
    private Optional<Long> maxGasAllowance = Optional.of(FIVE_HBARS);
    private Optional<BigInteger> valueSent = Optional.of(BigInteger.ZERO);
    private Consumer<Object[]> resultObserver = null;
    private Optional<FileID> ethFileID = Optional.empty();
    private boolean createCallDataFile;
    private boolean isTokenFlow;
    private String account = null;

    public HapiEthereumCall withExplicitParams(final Supplier<String> supplier) {
        explicitHexedParams = Optional.of(supplier);
        return this;
    }

    public static HapiEthereumCall fromDetails(String actionable) {
        HapiEthereumCall call = new HapiEthereumCall();
        call.details = Optional.of(actionable);
        return call;
    }

    private HapiEthereumCall() {
    }

    public HapiEthereumCall(String contract) {
        this.abi = FALLBACK_ABI;
        this.params = new Object[0];
        this.contract = contract;
        this.payer = Optional.of(RELAYER);
    }

    public HapiEthereumCall(String account, long amount) {
        this.account = account;
        this.valueSent = Optional.of(WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(amount)));
        this.abi = FALLBACK_ABI;
        this.params = new Object[0];
        this.payer = Optional.of(RELAYER);
    }

    public HapiEthereumCall(final HapiContractCall contractCall) {
        this.abi = contractCall.getAbi();
        this.params = contractCall.getParams();
        this.contract = contractCall.getContract();
        this.txnName = contractCall.getTxnName();
        this.gas = contractCall.getGas();
        this.expectedStatus = Optional.of(contractCall.getExpectedStatus());
        this.payer = contractCall.getPayer();
        this.expectedPrecheck = Optional.of(contractCall.getExpectedPrecheck());
        this.fiddler = contractCall.getFiddler();
        this.memo = contractCall.getMemo();
        this.fee = contractCall.getFee();
        this.submitDelay = contractCall.getSubmitDelay();
        this.validDurationSecs = contractCall.getValidDurationSeconds();
        this.customTxnId = contractCall.getCustomTxnId();
        this.node = contractCall.getNode();
        this.usdFee = contractCall.getUsdFee();
        this.retryLimits = contractCall.getRetryLimits();
        this.resultObserver = contractCall.getResultObserver();
        this.explicitHexedParams = contractCall.getExplicitHexedParams();
        this.privateKeyRef = contractCall.getPrivateKeyRef();
        this.deferStatusResolution = contractCall.getDeferStatusResolution();
        if (contractCall.getValueSent().isPresent()) {
            this.valueSent = Optional.of(WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(contractCall.getValueSent().get())));
        }
        if (!contractCall.otherSigs.isEmpty()) {
            this.alsoSigningWithFullPrefix(contractCall.otherSigs.toArray(new String[0]));
        }
        shouldRegisterTxn = true;
    }

    public HapiEthereumCall notTryingAsHexedliteral() {
        tryAsHexedAddressIfLenMatches = false;
        return this;
    }

    public HapiEthereumCall(String abi, String contract, Object... params) {
        this.abi = abi;
        this.params = params;
        this.contract = contract;
    }

    public HapiEthereumCall(boolean isTokenFlow, String abi, String contract, Object... params) {
        this.abi = abi;
        this.params = params;
        this.contract = contract;
        this.isTokenFlow = isTokenFlow;
    }

    public HapiEthereumCall(String abi, String contract, Function<HapiApiSpec, Object[]> fn) {
        this(abi, contract);
        paramsFn = Optional.of(fn);
    }

    public HapiEthereumCall exposingResultTo(final Consumer<Object[]> observer) {
        resultObserver = observer;
        return this;
    }

    public HapiEthereumCall exposingGasTo(ObjLongConsumer<ResponseCodeEnum> gasObserver) {
        this.gasObserver = Optional.of(gasObserver);
        return this;
    }

    public HapiEthereumCall alsoSigningWithFullPrefix(String... keys) {
        otherSigs = List.of(keys);
        return sigMapPrefixes(uniqueWithFullPrefixesFor(keys));
    }

    public HapiEthereumCall sending(long amount) {
        valueSent = Optional.of(WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(amount)));
        return this;
    }

    public HapiEthereumCall maxGasAllowance(long maxGasAllowance) {
        this.maxGasAllowance = Optional.of(maxGasAllowance);
        return this;
    }

    public HapiEthereumCall signingWith(String signingWith) {
        this.privateKeyRef = signingWith;
        return this;
    }

    public HapiEthereumCall type(EthTxData.EthTransactionType type) {
        this.type = type;
        return this;
    }

    public HapiEthereumCall nonce(long nonce) {
        this.nonce = nonce;
        useSpecNonce = false;
        return this;
    }

    public HapiEthereumCall gasPrice(long gasPrice) {
        this.gasPrice = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(gasPrice));
        return this;
    }

    public HapiEthereumCall maxFeePerGas(long maxFeePerGas) {
        this.maxFeePerGas = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(maxFeePerGas));
        return this;
    }

    public HapiEthereumCall maxPriorityGas(long maxPriorityGas) {
        this.maxPriorityGas = maxPriorityGas;
        return this;
    }

    public HapiEthereumCall gasLimit(long gasLimit) {
        this.gas = Optional.of(gasLimit);
        return this;
    }

    public HapiEthereumCall createCallDataFile() {
        this.createCallDataFile = true;
        return this;
    }

    @Override
    protected HapiEthereumCall self() {
        return this;
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
        return spec.clients().getScSvcStub(targetNodeFor(spec), useTls)::callEthereum;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.EthereumTransaction;
    }

    @Override
    protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return spec.fees().forActivityBasedOp(HederaFunctionality.EthereumTransaction,
                scFees::getEthereumTransactionFeeMatrices, txn, numPayerKeys);
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
        if (details.isPresent()) {
            ActionableContractCall actionable = spec.registry().getActionableCall(details.get());
            contract = actionable.getContract();
            abi = actionable.getDetails().getAbi();
            params = actionable.getDetails().getExampleArgs();
        } else if (paramsFn.isPresent()) {
            params = paramsFn.get().apply(spec);
        }

        byte[] callData;
        if (explicitHexedParams.isPresent()) {
            callData = explicitHexedParams.map(Supplier::get).map(CommonUtils::unhex).get();
        } else {
            final var paramsList = Arrays.asList(params);
            final var tupleExist = paramsList.stream().anyMatch(p -> p instanceof Tuple || p instanceof Tuple[]);
            if (tupleExist) {
                callData = encodeParametersWithTuple(params);
            } else {
                callData = (!abi.equals(FALLBACK_ABI))
                        ? CallTransaction.Function.fromJsonInterface(abi).encode(params) : new byte[] { };
            }
        }

        final byte[] to;
        if (account != null) {
            to = Utils.asAddress(spec.registry().getAccountID(account));
        } else if (isTokenFlow) {
            to = Utils.asAddress(spec.registry().getTokenID(contract));
        } else {
            if (!tryAsHexedAddressIfLenMatches) {
                to = Utils.asAddress(spec.registry().getContractId(contract));
            } else {
                to = Utils.asAddress(TxnUtils.asContractId(contract, spec));
            }
        }

        final var gasPriceBytes = gasLongToBytes(gasPrice.longValueExact());;
        final var maxFeePerGasBytes = gasLongToBytes(maxFeePerGas.longValueExact());
        final var maxPriorityGasBytes = gasLongToBytes(maxPriorityGas);

        if (useSpecNonce) {
            nonce = spec.getNonce(privateKeyRef);
        }
        final var ethTxData = new EthTxData(null, type, chainId, nonce, gasPriceBytes,
                maxPriorityGasBytes, maxFeePerGasBytes, gas.orElse(100_000L),
                to, valueSent.orElse(BigInteger.ZERO), callData, new byte[]{}, 0, null, null, null);

        byte[] privateKeyByteArray = getPrivateKeyFromSpec(spec, privateKeyRef);
        var signedEthTxData = EthTxSigs.signMessage(ethTxData, privateKeyByteArray);
        spec.registry().saveBytes(ETH_HASH_KEY, ByteString.copyFrom((signedEthTxData.getEthereumHash())));

        System.out.println("Size = " + callData.length + " vs " + MAX_CALL_DATA_SIZE);
        if (createCallDataFile || callData.length > MAX_CALL_DATA_SIZE) {
            final var callDataBytesString = ByteString.copyFrom(Hex.encode(callData));
            final var createFile = new HapiFileCreate(callDataFileName);
            final var updateLargeFile = updateLargeFile(payer.orElse(DEFAULT_CONTRACT_SENDER), callDataFileName, callDataBytesString);
            createFile.execFor(spec);
            updateLargeFile.execFor(spec);
            ethFileID = Optional.of(TxnUtils.asFileId(callDataFileName, spec));
            signedEthTxData = signedEthTxData.replaceCallData(new byte[] { });
        }
        final var finalEthTxData = signedEthTxData;

        final EthereumTransactionBody ethOpBody = spec
                .txns()
                .<EthereumTransactionBody, EthereumTransactionBody.Builder>body(
                        EthereumTransactionBody.class, builder -> {
                            builder.setEthereumData(ByteString.copyFrom(finalEthTxData.encodeTx()));
                            maxGasAllowance.ifPresent(builder::setMaxGasAllowance);
                            ethFileID.ifPresent(builder::setCallData);
                        }
                );
        return b -> b.setEthereumTransaction(ethOpBody);
    }

    @Override
    protected void updateStateOf(final HapiApiSpec spec) throws Throwable {
        if (actualPrecheck == OK) {
            spec.incrementNonce(privateKeyRef);
        }
        if (gasObserver.isPresent()) {
            doGasLookup(gas -> gasObserver.get().accept(actualStatus, gas), spec, txnSubmitted, false);
        }
        if (resultObserver != null) {
            doObservedLookup(spec, txnSubmitted, record -> {
                final var function = CallTransaction.Function.fromJsonInterface(abi);
                final var result = function.decodeResult(record
                        .getContractCallResult()
                        .getContractCallResult()
                        .toByteArray());
                resultObserver.accept(result);
            });
        }
    }

    @Override
    protected List<Function<HapiApiSpec, Key>> defaultSigners() {
        final var signers = new ArrayList<Function<HapiApiSpec, Key>>();
        signers.add(spec -> spec.registry().getKey(effectivePayer(spec)));
        for (final var added : otherSigs) {
            signers.add(spec -> spec.registry().getKey(added));
        }
        return signers;
    }

    static void doGasLookup(
            final LongConsumer gasObserver,
            final HapiApiSpec spec,
            final Transaction txn,
            final boolean isCreate
    ) throws Throwable {
        doObservedLookup(spec, txn, record -> {
            final var gasUsed = isCreate
                    ? record.getContractCreateResult().getGasUsed()
                    : record.getContractCallResult().getGasUsed();
            gasObserver.accept(gasUsed);
        });
    }

    static void doObservedLookup(
            final HapiApiSpec spec,
            final Transaction txn,
            Consumer<TransactionRecord> observer
    ) throws Throwable {
        final var txnId = extractTxnId(txn);
        final var lookup = getTxnRecord(txnId)
                .assertingNothing()
                .noLogging()
                .payingWith(GENESIS)
                .nodePayment(1)
                .exposingTo(observer);
        allRunFor(spec, lookup);
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("contract", contract)
                .add("abi", abi)
                .add("params", Arrays.toString(params));
    }
}
