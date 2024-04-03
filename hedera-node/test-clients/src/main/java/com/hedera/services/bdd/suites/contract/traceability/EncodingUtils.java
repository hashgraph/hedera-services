/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.traceability;

import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.stripSelector;
import static com.hedera.services.bdd.suites.contract.Utils.extractBytecodeUnhexed;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.suites.contract.Utils;
import java.math.BigInteger;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class EncodingUtils {

    private EncodingUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static ByteString getInitcode(final String binFileName, final Object... constructorArgs) {
        final var initCode = extractBytecodeUnhexed(getResourcePath(binFileName, ".bin"));
        final var params = constructorArgs.length == 0
                ? new byte[] {}
                : Function.fromJson(getABIFor(Utils.FunctionType.CONSTRUCTOR, StringUtils.EMPTY, binFileName))
                        .encodeCall(Tuple.of(constructorArgs))
                        .array();
        return initCode.concat(ByteStringUtils.wrapUnsafely(params.length > 4 ? stripSelector(params) : params));
    }

    public static ByteString encodeFunctionCall(
            final String contractName, final String functionName, final Object... args) {
        return ByteStringUtils.wrapUnsafely(
                Function.fromJson(getABIFor(Utils.FunctionType.FUNCTION, functionName, contractName))
                        .encodeCallWithArgs(args)
                        .array());
    }

    public static byte[] encodeTuple(final String argumentsSignature, final Object... actualArguments) {
        return TupleType.parse(argumentsSignature)
                .encode(Tuple.of(actualArguments))
                .array();
    }

    public static ByteString uint256ReturnWithValue(final BigInteger value) {
        return ByteStringUtils.wrapUnsafely(encodeTuple("(uint256)", value));
    }

    public static Address hexedSolidityAddressToHeadlongAddress(final String hexedSolidityAddress) {
        return Address.wrap(Address.toChecksumAddress("0x" + hexedSolidityAddress));
    }

    public static ByteString formattedAssertionValue(final long value) {
        return ByteString.copyFrom(
                Bytes.wrap(UInt256.valueOf(value)).trimLeadingZeros().toArrayUnsafe());
    }
}
