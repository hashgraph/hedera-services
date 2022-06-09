package com.hedera.services.bdd.spec.util;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.suites.contract.Utils.convertAliasToHeadlongAddress;
import static org.ethereum.crypto.HashUtil.sha3;

public class EncodingUtil {
    public static byte[] encodeParametersWithTuple(final String abi, final Object[] params) {
        byte[] callData = new byte[] { };

        Function function = Function.fromJson(abi);

        final var signature = getSignature(function);

        var argumentTypes = function.getInputs().toString();

        convertAddressToHeadlong(params);

        Tuple paramsAsTuple = Tuple.EMPTY;
        if (params.length > 0 && params[0] instanceof Tuple) {
            paramsAsTuple = (Tuple) params[0];
        } else if (params.length > 0){
            argumentTypes = convertArgumentTypes(argumentTypes, params);
            paramsAsTuple = Tuple.of(params);
        }

        var encodedParams = getEncodedParams(argumentTypes, paramsAsTuple);

        callData = ByteUtil.merge(callData, encodedParams);

        return ByteUtil.merge(encodeSignature(signature), callData);
    }

    private static String convertArgumentTypes(final String argumentTypesToConvert, final Object[] params) {
        var removeOpeningBracket = argumentTypesToConvert.replace("(", "");
        var removeClosingBracket = removeOpeningBracket.replace(")", "");
        final var splittedArgumentTypes = removeClosingBracket.split(",");

        final var convertedArgumentTypes = new String[splittedArgumentTypes.length];
        for(int i = 0; i < params.length; i++) {
            if(params[i] instanceof Integer && !("uint32".equals(splittedArgumentTypes[i]))) {
                convertedArgumentTypes[i] = "uint32";
            } else if((params[i] instanceof Long && !"int64".equals(splittedArgumentTypes[i])) || "int32".equals(splittedArgumentTypes[i])) {
                convertedArgumentTypes[i] = "int64";
            } else if(params[i] instanceof BigInteger && !("uint64".equals(splittedArgumentTypes[i])) || "int128".equals(splittedArgumentTypes[i])
                    || "uint128".equals(splittedArgumentTypes[i]) || "int256".equals(splittedArgumentTypes[i]) || "uint256".equals(splittedArgumentTypes[i])) {
                convertedArgumentTypes[i] = "uint256";
            } else {
                convertedArgumentTypes[i] = splittedArgumentTypes[i];
            }
        }

        return "("  + Arrays.stream(convertedArgumentTypes).collect(Collectors.joining(",")).concat(")");
    }

    private static Object[] convertAddressToHeadlong(final Object[] params) {

        for (int i = 0; i < params.length; i++) {
            if(params[i] instanceof String){
                params[i] = convertAliasToHeadlongAddress((String) params[i]);
            }
        }


        return params;
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
