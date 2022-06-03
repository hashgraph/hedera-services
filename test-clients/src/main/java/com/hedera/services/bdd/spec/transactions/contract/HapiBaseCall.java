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

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import org.ethereum.util.ByteUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.bdd.suites.HapiApiSuite.SECP_256K1_SOURCE_KEY;
import static org.ethereum.crypto.HashUtil.sha3;

public abstract class HapiBaseCall<T extends HapiTxnOp<T>> extends HapiTxnOp<T> {

    public static final int HEXED_EVM_ADDRESS_LEN = 40;
    protected static final String FALLBACK_ABI = "<empty>";
    protected static final String ADDRESS_ABI_TYPE = "address";
    protected static final String ADDRESS_ENCODE_TYPE = "bytes32";
    protected final static ObjectMapper DEFAULT_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);

    protected boolean tryAsHexedAddressIfLenMatches = true;
    protected Object[] params;
    protected String abi;
    protected String contract;
    protected Optional<Long> gas = Optional.empty();
    protected String privateKeyRef = SECP_256K1_SOURCE_KEY;

    protected byte[] encodeParametersWithTuple(final Object[] params) throws Throwable {
        byte[] callData = new byte[] { };
        Function function = Function.fromJson(abi);

        final var signature = function.getName() + function.getInputs().getCanonicalType();

        final var argumentTypes = function.getInputs().toString();

        Tuple paramsAsTuple;
        if (params.length > 0 && params[0] instanceof Tuple) {
            paramsAsTuple = (Tuple) params[0];
        } else {
            paramsAsTuple = Tuple.of(params);
        }

//        asHeadlongAddress(paramsAsTuple.get(0));

        //We have inputs -> signature prop - done
        //We have args -> paramsAsTuple

        final TupleType tupleType = TupleType.parse(argumentTypes);
        var encodedReturn =  tupleType.encode(paramsAsTuple).array();

        callData = ByteUtil.merge(callData, encodedReturn);

//        function.encodeCallWithArgs();

        return ByteUtil.merge(encodeSignature(signature), callData);
    }

    private byte[] getTupleAsBytes(final Tuple argumentValues, final String argumentTypes) {
        final TupleType tupleType = TupleType.parse(argumentTypes);
        return tupleType.encode(argumentValues.get(0)).array();
    }

    private byte[] encodeSignature(final String functionSignature) {
        return Arrays.copyOfRange(encodeSignatureLong(functionSignature), 0, 4);
    }

    private byte[] encodeSignatureLong(final String functionSignature) {
        return sha3(functionSignature.getBytes());
    }

    private String getParametersForSignature(final String jsonABI) throws Throwable {
        final var abiFunction = DEFAULT_MAPPER.readValue(jsonABI, HapiBaseCall.AbiFunction.class);
        final var parametersBuilder = new StringBuilder();
        parametersBuilder.append("(");
        for (final HapiBaseCall.InputOutput input : abiFunction.getInputs()) {
            parametersBuilder.append(getArgumentTypesForInput(input));
        }

        parametersBuilder.append(")");
        return parametersBuilder.toString().replace(",)", ")");
    }

    private String getArgumentTypesForInput(final HapiBaseCall.InputOutput input) {
        final var argumentTypeBuilder = new StringBuilder();
        if (input.getComponents() != null) {
            argumentTypeBuilder.append(getOpenCharacterForInput(input));
            argumentTypeBuilder.append(getArgumentTypesForComponents(input.getComponents()));
            argumentTypeBuilder.append(getClosingCharacterForInput(input));
        } else {
            argumentTypeBuilder.append(input.getType()).append(",");
        }

        return argumentTypeBuilder.toString();
    }

    private String getOpenCharacterForInput(final HapiBaseCall.InputOutput input) {
        switch (input.getType()) {
            case "tuple[]":
            case "tuple":
                return "(";
            default:
                return "";
        }
    }

    private String getClosingCharacterForInput(final HapiBaseCall.InputOutput input) {
        switch (input.getType()) {
            case "tuple[]":
                return ")[],";
            case "tuple":
                return "),";
            default:
                return "";
        }
    }

    private String getArgumentTypesForComponents(final List<HapiBaseCall.Component> components) {
        final var componentsTypeBuilder = new StringBuilder();
        for (final HapiBaseCall.Component component : components) {
            if (component.getComponents() != null && !component.getComponents().isEmpty()) {
                componentsTypeBuilder.append("(");
                for (final HapiBaseCall.Component nestedComponent : component.getComponents()) {
                    componentsTypeBuilder.append(nestedComponent.getType()).append(",");
                }
                componentsTypeBuilder.append("tuple[]".equals(component.getType()) ? ")[]," : "),");
            } else {
                componentsTypeBuilder.append(component.getType()).append(",");
            }
        }

        return componentsTypeBuilder.toString();
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
