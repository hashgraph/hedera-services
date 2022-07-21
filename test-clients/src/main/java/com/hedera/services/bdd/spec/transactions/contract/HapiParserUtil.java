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
package com.hedera.services.bdd.spec.transactions.contract;

import static org.ethereum.crypto.HashUtil.sha3;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import java.util.Arrays;
import org.ethereum.util.ByteUtil;

public class HapiParserUtil {

  private HapiParserUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  private static final String ADDRESS_ABI_TYPE = "address";
  private static final String ADDRESS_ENCODE_TYPE = "bytes32";

  public static byte[] encodeParametersWithTuple(final Object[] params, final String abi) {
    byte[] callData = new byte[] { };

    final var abiFunction = Function.fromJson(abi);
    final var signatureParameters = abiFunction.getInputs().toString();
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

  private static byte[] getTupleAsBytes(final Tuple argumentValues, final String argumentTypes) {
    final TupleType tupleType = TupleType.parse(argumentTypes);
    return tupleType.encode(argumentValues.get(0)).array();
  }

  private static byte[] encodeSignature(final String functionSignature) {
    return Arrays.copyOfRange(encodeSignatureLong(functionSignature), 0, 4);
  }

  private static byte[] encodeSignatureLong(final String functionSignature) {
    return sha3(functionSignature.getBytes());
  }
}
