/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.assertions;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractInfo;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.bdd.suites.utils.contracts.ContractCallResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.swirlds.common.utility.CommonUtils;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.core.CallTransaction;
import org.junit.jupiter.api.Assertions;

public class ContractFnResultAsserts extends BaseErroringAssertsProvider<ContractFunctionResult> {
    static final Logger log = LogManager.getLogger(ContractFnResultAsserts.class);

    public static ContractFnResultAsserts resultWith() {
        return new ContractFnResultAsserts();
    }

    public ContractFnResultAsserts resultThruAbi(
            String abi, Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> provider) {
        registerProvider(
                (spec, o) -> {
                    Object[] actualObjs =
                            viaAbi(
                                    abi,
                                    ((ContractFunctionResult) o)
                                            .getContractCallResult()
                                            .toByteArray());
                    Optional<Throwable> error = provider.apply(spec).apply(actualObjs);
                    if (error.isPresent()) {
                        throw error.get();
                    }
                });
        return this;
    }

    /*  Note:
     This method utilizes algorithmic extraction of a function ABI by the name of the function and the contract
     and should replace the "resultThruAbi" method, which depends on function ABI, passed as String literal.
    */
    public ContractFnResultAsserts resultViaFunctionName(
            final String functionName,
            final String contractName,
            final Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> provider) {
        final var abi = Utils.getABIFor(FUNCTION, functionName, contractName);
        registerProvider(
                (spec, o) -> {
                    Object[] actualObjs =
                            viaAbi(
                                    abi,
                                    ((ContractFunctionResult) o)
                                            .getContractCallResult()
                                            .toByteArray());
                    Optional<Throwable> error = provider.apply(spec).apply(actualObjs);
                    if (error.isPresent()) {
                        throw error.get();
                    }
                });
        return this;
    }

    public static Object[] viaAbi(String abi, byte[] bytes) {
        CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(abi);
        return function.decodeResult(bytes);
    }

    public ContractFnResultAsserts contract(String contract) {
        registerIdLookupAssert(contract, r -> r.getContractID(), ContractID.class, "Bad contract!");
        return this;
    }

    public ContractFnResultAsserts hexedEvmAddress(String expected) {
        return evmAddress(ByteString.copyFrom(CommonUtils.unhex(expected)));
    }

    public ContractFnResultAsserts evmAddress(ByteString expected) {
        registerProvider(
                (spec, o) -> {
                    final var result = (ContractFunctionResult) o;
                    Assertions.assertTrue(
                            result.hasEvmAddress(), "Missing EVM address, expected " + expected);
                    final var actual = result.getEvmAddress().getValue();
                    Assertions.assertEquals(expected, actual, "Bad EVM address");
                });
        return this;
    }

    public ContractFnResultAsserts logs(ErroringAssertsProvider<List<ContractLoginfo>> provider) {
        registerProvider(
                (spec, o) -> {
                    List<ContractLoginfo> logs = ((ContractFunctionResult) o).getLogInfoList();
                    ErroringAsserts<List<ContractLoginfo>> asserts = provider.assertsFor(spec);
                    List<Throwable> errors = asserts.errorsIn(logs);
                    AssertUtils.rethrowSummaryError(log, "Bad logs!", errors);
                });
        return this;
    }

    public ContractFnResultAsserts error(String msg) {
        registerProvider(
                (spec, o) -> {
                    ContractFunctionResult result = (ContractFunctionResult) o;
                    Assertions.assertEquals(
                            msg,
                            Optional.ofNullable(result.getErrorMessage()).orElse(""),
                            "Wrong contract function error!");
                });
        return this;
    }

    public ContractFnResultAsserts approxGasUsed(
            final long expected, final double allowedPercentDeviation) {
        registerProvider(
                (spec, o) -> {
                    ContractFunctionResult result = (ContractFunctionResult) o;
                    final var actual = result.getGasUsed();
                    final var epsilon = allowedPercentDeviation * actual / 100.0;
                    Assertions.assertEquals(
                            expected, result.getGasUsed(), epsilon, "Wrong amount of gas used");
                });
        return this;
    }

    public ContractFnResultAsserts gasUsed(long gasUsed) {
        registerProvider(
                (spec, o) -> {
                    ContractFunctionResult result = (ContractFunctionResult) o;
                    Assertions.assertEquals(
                            gasUsed, result.getGasUsed(), "Wrong amount of Gas was used!");
                });
        return this;
    }

    public ContractFnResultAsserts contractCallResult(ContractCallResult contractCallResult) {
        registerProvider(
                (spec, o) -> {
                    ContractFunctionResult result = (ContractFunctionResult) o;
                    Assertions.assertEquals(
                            ByteString.copyFrom(contractCallResult.getBytes().toArray()),
                            result.getContractCallResult(),
                            "Wrong contract call result!");
                });
        return this;
    }

    public ContractFnResultAsserts gas(long gas) {
        registerProvider(
                (spec, o) -> {
                    ContractFunctionResult result = (ContractFunctionResult) o;
                    Assertions.assertEquals(gas, result.getGas(), "Wrong amount of initial Gas!");
                });
        return this;
    }

    public ContractFnResultAsserts amount(long amount) {
        registerProvider(
                (spec, o) -> {
                    ContractFunctionResult result = (ContractFunctionResult) o;
                    Assertions.assertEquals(
                            amount, result.getAmount(), "Wrong amount of tinybars!");
                });
        return this;
    }

    public ContractFnResultAsserts functionParameters(Bytes functionParameters) {
        registerProvider(
                (spec, o) -> {
                    ContractFunctionResult result = (ContractFunctionResult) o;
                    Assertions.assertEquals(
                            ByteString.copyFrom(functionParameters.toArray()),
                            result.getFunctionParameters(),
                            "Wrong function parameters!");
                });
        return this;
    }

    public ContractFnResultAsserts senderId(AccountID senderId) {
        registerProvider(
                (spec, o) -> {
                    ContractFunctionResult result = (ContractFunctionResult) o;
                    Assertions.assertEquals(senderId, result.getSenderId(), "Wrong senderID!");
                });
        return this;
    }

    /* Helpers to create the provider for #resultThruAbi. */
    public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> isContractWith(
            ContractInfoAsserts theExpectedInfo) {
        return spec ->
                actualObjs -> {
                    try {
                        Assertions.assertEquals(
                                1, actualObjs.length, "Extra contract function return values!");
                        String implicitContract = "contract" + new Random().nextInt();
                        ContractID contract = TxnUtils.asContractId((byte[]) actualObjs[0]);
                        spec.registry().saveContractId(implicitContract, contract);
                        HapiGetContractInfo op =
                                getContractInfo(implicitContract).has(theExpectedInfo);
                        Optional<Throwable> opError = op.execFor(spec);
                        if (opError.isPresent()) {
                            throw opError.get();
                        }
                    } catch (Throwable t) { // NOSONAR throw from 2 lines above must be caught
                        return Optional.of(t);
                    }
                    return Optional.empty();
                };
    }

    public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> isLiteralResult(
            Object[] objs) {
        return ignore -> actualObjs -> matchErrors(objs, actualObjs);
    }

    public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> isOneOfLiteral(
            Set<Object> values) {
        return ignore ->
                actualObjs -> {
                    try {
                        Assertions.assertEquals(1, actualObjs.length, "Expected a single object");
                        Assertions.assertTrue(
                                values.contains(actualObjs[0]),
                                "Expected one of " + values + " but was " + actualObjs[0]);
                    } catch (Exception e) {
                        return Optional.of(e);
                    }
                    return Optional.empty();
                };
    }

    public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> isRandomResult(
            Object[] objs) {
        return ignore -> actualObjs -> validateRandomResult(objs, actualObjs);
    }

    public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>>
            isLiteralArrayResult(Object[] objs) {
        return ignore -> actualObjs -> matchErrors(objs, (Object[]) actualObjs[0]);
    }

    private static Optional<Throwable> matchErrors(Object[] expecteds, Object[] actuals) {
        try {
            for (int i = 0; i < Math.max(expecteds.length, actuals.length); i++) {
                try {
                    Object expected = expecteds[i];
                    Object actual = actuals[i];
                    Assertions.assertNotNull(expected);
                    Assertions.assertNotNull(actual);
                    Assertions.assertEquals(expected.getClass(), actual.getClass());
                    if (expected instanceof byte[]) {
                        Assertions.assertArrayEquals((byte[]) expected, (byte[]) actual);
                    } else {
                        Assertions.assertEquals(expected, actual);
                    }
                } catch (Exception e) {
                    return Optional.of(e);
                }
            }
        } catch (Exception e) {
            return Optional.of(e);
        }
        return Optional.empty();
    }

    private static Optional<Throwable> validateRandomResult(
            final Object[] expecteds, final Object[] actuals) {
        try {
            for (int i = 0; i < Math.max(expecteds.length, actuals.length); i++) {
                Object expected = expecteds[i];
                Object actual = actuals[i];
                Assertions.assertNotNull(expected);
                Assertions.assertNotNull(actual);
                if (expected instanceof byte[]) {
                    Assertions.assertEquals(((byte[]) expected).length, ((byte[]) actual).length);
                } else if (expected instanceof Integer) {
                    Assertions.assertTrue(
                            ((BigInteger) actual).intValue() >= 0
                                    && ((BigInteger) actual).intValue()
                                            < ((Integer) expected).intValue());
                } else {
                    throw new Exception(
                            String.format(
                                    "Invalid Random result, expected %s , actual %s",
                                    expecteds[i], actuals[i]));
                }
            }
        } catch (Exception e) {
            return Optional.of(e);
        }
        return Optional.empty();
    }
}
