package com.hedera.services.bdd.spec.util;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import org.ethereum.util.ByteUtil;

import java.util.Arrays;

import static org.ethereum.crypto.HashUtil.sha3;

public class EncodingUtil {
    public static byte[] encodeParametersWithTuple(String abi, final Object[] params) {
        byte[] callData = new byte[] { };

        Function function = Function.fromJson(abi);

        final var signature = getSignature(function);

        final var argumentTypes = function.getInputs().toString();

        Tuple paramsAsTuple;
        if (params.length > 0 && params[0] instanceof Tuple) {
            paramsAsTuple = (Tuple) params[0];
        } else {
            paramsAsTuple = Tuple.of(params);
        }

        var encodedParams = getEncodedParams(argumentTypes, paramsAsTuple);

        callData = ByteUtil.merge(callData, encodedParams);

        return ByteUtil.merge(encodeSignature(signature), callData);
    }

    private static String getSignature(Function function) {
        return function.getName() + function.getInputs().getCanonicalType();
    }

    private static byte[] getEncodedParams(String argumentTypes, Tuple paramsAsTuple) {
        final TupleType tupleType = TupleType.parse(argumentTypes);
        return tupleType.encode(paramsAsTuple).array();
    }

    private static byte[] encodeSignature(final String functionSignature) {
        return Arrays.copyOfRange(encodeSignatureLong(functionSignature), 0, 4);
    }

    private static byte[] encodeSignatureLong(final String functionSignature) {
        return sha3(functionSignature.getBytes());
    }
}
