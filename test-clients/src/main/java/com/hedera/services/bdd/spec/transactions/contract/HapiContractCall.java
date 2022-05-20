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
import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCall;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.*;
import com.swirlds.common.utility.CommonUtils;
import org.ethereum.core.CallTransaction;

import java.util.*;
import java.util.function.*;

import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.extractTxnId;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;

public class HapiContractCall extends HapiBaseCall<HapiContractCall> {
	protected List<String> otherSigs = Collections.emptyList();
	private Optional<Long> gas = Optional.empty();
	private Optional<String> details = Optional.empty();
	private Optional<Function<HapiApiSpec, Object[]>> paramsFn = Optional.empty();
	private Optional<ObjLongConsumer<ResponseCodeEnum>> gasObserver = Optional.empty();
	private Optional<Supplier<String>> explicitHexedParams = Optional.empty();
	private Optional<Long> valueSent = Optional.of(0L);
	private boolean convertableToEthCall = true;
	private Consumer<Object[]> resultObserver = null;

	public HapiContractCall withExplicitParams(final Supplier<String> supplier) {
		explicitHexedParams = Optional.of(supplier);
		return this;
	}

	public static HapiContractCall fromDetails(String actionable) {
		HapiContractCall call = new HapiContractCall();
		call.details = Optional.of(actionable);
		return call;
	}

	private HapiContractCall() {
	}

	public HapiContractCall(String contract) {
		this.abi = FALLBACK_ABI;
		this.params = new Object[0];
		this.contract = contract;
	}

	public HapiContractCall notTryingAsHexedliteral() {
		tryAsHexedAddressIfLenMatches = false;
		return this;
	}

	public HapiContractCall(String abi, String contract, Object... params) {
		this.abi = abi;
		this.params = params;
		this.contract = contract;
	}

	public HapiContractCall(String abi, String contract, Function<HapiApiSpec, Object[]> fn) {
		this(abi, contract);
		paramsFn = Optional.of(fn);
	}

	public HapiContractCall exposingResultTo(final Consumer<Object[]> observer) {
		resultObserver = observer;
		return this;
	}

	public HapiContractCall exposingGasTo(ObjLongConsumer<ResponseCodeEnum> gasObserver) {
		this.gasObserver = Optional.of(gasObserver);
		return this;
	}

	public HapiContractCall refusingEthConversion() {
		convertableToEthCall = false;
		return this;
	}

	public HapiContractCall gas(long amount) {
		gas = Optional.of(amount);
		return this;
	}

	public HapiContractCall alsoSigningWithFullPrefix(String... keys) {
		otherSigs = List.of(keys);
		return sigMapPrefixes(uniqueWithFullPrefixesFor(keys));
	}

	public HapiContractCall sending(long amount) {
		valueSent = Optional.of(amount);
		return this;
	}

	public HapiContractCall signingWith(String signingWith) {
		privateKeyRef = signingWith;
		return this;
	}

	@Override
	protected HapiContractCall self() {
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.ContractCall;
	}

	public boolean isConvertableToEthCall() {
		return convertableToEthCall;
	}

	public Consumer<Object[]> getResultObserver() {
		return resultObserver;
	}

	public String getContract() {
		return contract;
	}

	public String getAbi() {
		return abi;
	}

	public Object[] getParams() {
		return params;
	}

	public String getTxnName() {
		return txnName;
	}

	public Optional<Long> getGas() {
		return gas;
	}

	public List<String> getOtherSigs() {
		return otherSigs;
	}

	public Optional<String> getPayer() {
		return payer;
	}

	public Optional<String> getMemo() {
		return memo;
	}

	public Optional<Long> getValueSent() {
		return valueSent;
	}

	public Optional<Function<Transaction, Transaction>> getFiddler() {
		return fiddler;
	}

	public Optional<Long> getFee() {
		return fee;
	}

	public Optional<Long> getSubmitDelay() {
		return submitDelay;
	}

	public Optional<Long> getValidDurationSeconds() {
		return validDurationSecs;
	}

	public Optional<String> getCustomTxnId() {
		return customTxnId;
	}

	public Optional<AccountID> getNode() {
		return node;
	}

	public OptionalDouble getUsdFee() {
		return usdFee;
	}

	public Optional<Integer> getRetryLimits() {
		return retryLimits;
	}

	public Optional<Supplier<String>> getExplicitHexedParams() {
		return explicitHexedParams;
	}

	public String getPrivateKeyRef() {
		return privateKeyRef;
	}

	public boolean getDeferStatusResolution() {
		return deferStatusResolution;
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getScSvcStub(targetNodeFor(spec), useTls)::contractCallMethod;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(HederaFunctionality.ContractCall,
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

		ContractCallTransactionBody opBody = spec
				.txns()
				.<ContractCallTransactionBody, ContractCallTransactionBody.Builder>body(
						ContractCallTransactionBody.class, builder -> {
							if (!tryAsHexedAddressIfLenMatches) {
								builder.setContractID(spec.registry().getContractId(contract));
							} else {
								builder.setContractID(TxnUtils.asContractId(contract, spec));
							}
							builder.setFunctionParameters(ByteString.copyFrom(callData));
							valueSent.ifPresent(builder::setAmount);
							gas.ifPresent(builder::setGas);
						}
				);
		return b -> b.setContractCall(opBody);
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