package com.hedera.services.bdd.spec.transactions.contract;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Integers;
import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCall;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ethereum.EthTxSigs;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.common.utility.CommonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jcajce.provider.digest.Keccak;
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
import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiApiSuite.RELAYER;
import static com.hedera.services.bdd.suites.HapiApiSuite.SECP_256K1_SOURCE_KEY;

public class HapiEthereumCall extends HapiBaseCall<HapiEthereumCall> {

    public static final String ETH_HASH_KEY = "EthHash";

    private List<String> otherSigs = Collections.emptyList();
    private Optional<String> details = Optional.empty();
    private Optional<Function<HapiApiSpec, Object[]>> paramsFn = Optional.empty();
    private Optional<ObjLongConsumer<ResponseCodeEnum>> gasObserver = Optional.empty();
    private Optional<Supplier<String>> explicitHexedParams = Optional.empty();

    private static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);
    private EthTxData.EthTransactionType type = EthTxData.EthTransactionType.EIP1559;
    private byte[] chainId = Integers.toBytes(298);
    private long nonce = 0L;
    private long gasPrice = 1L;
    private long maxPriorityGas = 1_000L;
    private Optional<Long> maxGasAllowance = Optional.of(2_000_000L);
    private Optional<BigInteger> valueSent = Optional.of(BigInteger.ZERO);
    private String privateKeyRef = SECP_256K1_SOURCE_KEY;

    private Consumer<Object[]> resultObserver = null;

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

    public HapiEthereumCall(final HapiContractCall contractCall) {
        this.abi = contractCall.getAbi();
        this.params = contractCall.getParams();
        this.contract = contractCall.getContract();
        this.txnName = contractCall.getTxnName();
        this.gas = contractCall.getGas();
        this.expectedStatus = Optional.of(contractCall.getExpectedStatus());
        this.otherSigs = contractCall.getOtherSigs();
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
        if (contractCall.getValueSent().isPresent()) {
            this.valueSent = Optional.of(WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(contractCall.getValueSent().get())));
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
        return this;
    }

    public HapiEthereumCall gasPrice(long gasPrice) {
        this.gasPrice = gasPrice;
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

    public long getNonce() {
        return nonce;
    }

    public void setNonce(long nonce) {
        this.nonce = nonce;
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
                scFees::getContractCallTxFeeMatrices, txn, numPayerKeys);
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

        final ContractID contractID;
        if (!tryAsHexedAddressIfLenMatches) {
            contractID = spec.registry().getContractId(contract);
        } else {
            contractID = TxnUtils.asContractId(contract, spec);
        }

        final var longTuple = TupleType.parse("(int64)");
        final var gasPriceBytes = Bytes.wrap(longTuple.encode(Tuple.of(gasPrice)).array()).toArray();
        final var maxPriorityGasBytes = Bytes.wrap(longTuple.encode(Tuple.of(maxPriorityGas)).array()).toArray();
        final var gasBytes = gas.isEmpty() ? new byte[] {} : Bytes.wrap(longTuple.encode(Tuple.of(gas.get())).array()).toArray();

        final var ethTxData = new EthTxData(null, type, chainId, nonce, gasPriceBytes,
                maxPriorityGasBytes, gasBytes, gas.orElse(100_000L),
                Utils.asAddress(contractID), valueSent.orElse(BigInteger.ZERO), callData, new byte[]{}, 0, null, null, null);

        byte[] privateKeyByteArray = getPrivateKeyFromSpec(spec, privateKeyRef);
        final var signedEthTxData = EthTxSigs.signMessage(ethTxData, privateKeyByteArray);
        spec.registry().saveBytes(ETH_HASH_KEY, ByteString.copyFrom((signedEthTxData.getEthereumHash())));

        final EthereumTransactionBody ethOpBody = spec
                .txns()
                .<EthereumTransactionBody, EthereumTransactionBody.Builder>body(
                        EthereumTransactionBody.class, builder -> {
                            builder.setEthereumData(ByteString.copyFrom(signedEthTxData.encodeTx()));
                            maxGasAllowance.ifPresent(builder::setMaxGasAllowance);
                        }
                );
        return b -> b.setEthereumTransaction(ethOpBody);
    }

    @Override
    protected void updateStateOf(HapiApiSpec spec) throws Throwable {
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