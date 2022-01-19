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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCall;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.extractTxnId;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;
import static org.ethereum.crypto.HashUtil.sha3;

public class HapiContractCall extends HapiTxnOp<HapiContractCall> {
	private static final String FALLBACK_ABI = "<empty>";
	private static final String ADDRESS_ABI_TYPE = "address";
	private static final String ADDRESS_ENCODE_TYPE = "bytes32";
	private final static ObjectMapper DEFAULT_MAPPER = new ObjectMapper()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);

	private Object[] params;
	private String abi;
	private String contract;
	private List<String> otherSigs = Collections.emptyList();
	private Optional<Long> gas = Optional.empty();
	private Optional<Long> sentTinyHbars = Optional.of(0L);
	private Optional<String> details = Optional.empty();
	private Optional<Function<HapiApiSpec, Object[]>> paramsFn = Optional.empty();
	private Optional<ObjLongConsumer<ResponseCodeEnum>> gasObserver = Optional.empty();

	private String resultAbi = null;
	private Consumer<Object[]> resultObserver = null;

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.ContractCall;
	}

	@Override
	protected HapiContractCall self() {
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

	public HapiContractCall gas(long amount) {
		gas = Optional.of(amount);
		return this;
	}

	public HapiContractCall alsoSigningWithFullPrefix(String... keys) {
		otherSigs = List.of(keys);
		return sigMapPrefixes(uniqueWithFullPrefixesFor(keys));
	}

	public HapiContractCall sending(long amount) {
		sentTinyHbars = Optional.of(amount);
		return this;
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
		final var paramsList = Arrays.asList(params);
		final var tupleExist =
				paramsList.stream().anyMatch(p -> p instanceof Tuple || p instanceof Tuple[]);
		if(tupleExist) {
			callData = encodeParametersWithTuple(params);
		} else {
			callData = (abi != FALLBACK_ABI)
					? CallTransaction.Function.fromJsonInterface(abi).encode(params) : new byte[]{};
		}

		final var id = TxnUtils.asContractId(contract, spec);
		ContractCallTransactionBody opBody = spec
				.txns()
				.<ContractCallTransactionBody, ContractCallTransactionBody.Builder>body(
						ContractCallTransactionBody.class, builder -> {
							builder.setContractID(id);
							builder.setFunctionParameters(ByteString.copyFrom(callData));
							sentTinyHbars.ifPresent(a -> builder.setAmount(a));
							gas.ifPresent(a -> builder.setGas(a));
						}
				);
		return b -> b.setContractCall(opBody);
	}

	private byte[] encodeParametersWithTuple(final Object[] params) throws Throwable {
		byte[] callData = new byte[]{};
		var abiFunction = DEFAULT_MAPPER.readValue(abi, AbiFunction.class);
		final var signatureParameters = getParametersForSignature(abi);
		final var signature = abiFunction.getName() + signatureParameters;
		final var argumentTypes = signatureParameters.replace(
				ADDRESS_ABI_TYPE,
				ADDRESS_ENCODE_TYPE);
		final var paramsAsTuple = Tuple.of(params);

		final var tupleEncoded = getTupleAsBytes(paramsAsTuple,
				argumentTypes);
		callData = ByteUtil.merge(callData, tupleEncoded);

		return ByteUtil.merge(encodeSignature(signature), callData);
	}

	private String getParametersForSignature(final String jsonABI) throws Throwable {
		final var abiFunction = DEFAULT_MAPPER.readValue(jsonABI, AbiFunction.class);
		final var parametersBuilder = new StringBuilder();
		parametersBuilder.append("(");
		for (final InputOutput input : abiFunction.getInputs()) {
			parametersBuilder.append(getArgumentTypesForInput(input));
		}

		parametersBuilder.append(")");
		return parametersBuilder.toString().replace(",)", ")");
	}

	private String getArgumentTypesForInput(final InputOutput input) {
		final var argumentTypeBuilder = new StringBuilder();
		if(input.getComponents()!=null) {
			argumentTypeBuilder.append(getOpenCharacterForInput(input));
			argumentTypeBuilder.append(getArgumentTypesForComponents(input.getComponents()));
			argumentTypeBuilder.append(getClosingCharacterForInput(input));
		} else {
			argumentTypeBuilder.append(input.getType()).append(",");
		}

		return argumentTypeBuilder.toString();
	}

	private String getOpenCharacterForInput(final InputOutput input) {
		switch(input.getType()) {
			case "tuple[]":
			case "tuple":
				return "(";
			default:
				return "";
		}
	}

	private String getClosingCharacterForInput(final InputOutput input) {
		switch(input.getType()) {
			case "tuple[]":
				return ")[],";
			case "tuple":
				return "),";
			default:
				return "";
		}
	}

	private String getArgumentTypesForComponents(final List<Component> components) {
		final var componentsTypeBuilder = new StringBuilder();
		for (final Component component : components) {
			if (component.getComponents() != null && !component.getComponents().isEmpty()) {
				componentsTypeBuilder.append("(");
				for (final Component nestedComponent : component.getComponents()) {
					componentsTypeBuilder.append(nestedComponent.getType()).append(",");
				}
				componentsTypeBuilder.append("tuple[]".equals(component.getType()) ? ")[]," : "),");
			} else {
				componentsTypeBuilder.append(component.getType()).append(",");
			}
		}

		return componentsTypeBuilder.toString();
	}

	public byte[] encodeSignature(final String functionSignature) {
		return Arrays.copyOfRange(encodeSignatureLong(functionSignature), 0, 4);
	}

	public byte[] encodeSignatureLong(final String functionSignature) {
		return sha3(functionSignature.getBytes());
	}

	private static byte[] getTupleAsBytes(final Tuple argumentValues, final String argumentTypes) {
		final TupleType tupleType = TupleType.parse(argumentTypes);
		return tupleType.encode((Tuple)argumentValues.get(0)).array();
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

	private static class AbiFunction {
		private List<InputOutput> outputs;
		private List<InputOutput> inputs;
		private String name;
		private String stateMutability;
		private String type;

		public List<InputOutput> getOutputs() {
			return outputs;
		}

		public List<InputOutput> getInputs() {
			return inputs;
		}

		public String getName() {
			return name;
		}

		public String getStateMutability() {
			return stateMutability;
		}

		public String getType() {
			return type;
		}
	}

	private static class InputOutput {
		private List<Component> components;
		private String internalType;
		private String name;
		private String type;

		public List<Component> getComponents() {
			return components;
		}

		public String getInternalType() {
			return internalType;
		}

		public String getName() {
			return name;
		}

		public String getType() {
			return type;
		}
	}


	private static class Component {
		private List<Component> components;
		private String internalType;
		private String name;
		private String type;

		public List<Component> getComponents() {
			return components;
		}

		public String getInternalType() {
			return internalType;
		}

		public String getName() {
			return name;
		}

		public String getType() {
			return type;
		}
	}
}