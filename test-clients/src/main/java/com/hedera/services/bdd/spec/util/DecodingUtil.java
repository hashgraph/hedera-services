package com.hedera.services.bdd.spec.util;
/*-
 * ‌
 * Hedera Services Node
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

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.google.common.primitives.Longs;
import com.hedera.services.bdd.spec.HapiApiSpec;

import java.util.Arrays;

public class DecodingUtil {

    static ResultType resultType = ResultType.NOT_SPECIFIED;

    public static Tuple decodeResult(byte[] encodedReturn, String json) {

        if (json.contains("address")) {
            json = json.replaceAll("address", "bytes32");
        }

        Function function = Function.fromJson(json);

        TupleType result = function.getOutputs();

        if (encodedReturn != null && encodedReturn.length > 0){
            return result.decode(encodedReturn);
        }

        return getResult(result);
    }

    public enum ResultType {
        ADDRESS, STRING, ARRAY, BIGINTEGER, INTEGER, LONG, NOT_SPECIFIED
    }

    public static Tuple getResult(TupleType type) {
        Tuple result;

        resultType = ResultType.valueOf(type.get(0).getCanonicalType().toUpperCase());

        switch (resultType) {
            case STRING -> result = Tuple.of("");
            case INTEGER, LONG, BIGINTEGER -> result = Tuple.of(0);
            case ARRAY -> result = Tuple.EMPTY;
            case ADDRESS -> result = Tuple.of("0x0000000000000000000000000000000000123456");
            default -> result = Tuple.of(Tuple.EMPTY);
        }
        return result;
    }

    public static <T> T getValueFromRegistry(HapiApiSpec spec, String from, String json) {
        byte[] value = spec.registry().getBytes(from);

        T decodedReturnedValue;
        var retResults = decodeResult(value, json);

        decodedReturnedValue = retResults.get(0);

        return decodedReturnedValue;
    }

    public static long realmFromEvmAddress(final byte[] bytes) {
        return Longs.fromByteArray(Arrays.copyOfRange(bytes, 4, 12));
    }

    public static long numFromEvmAddress(final byte[] bytes) {
        return Longs.fromByteArray(Arrays.copyOfRange(bytes, 12, 20));
    }
}

