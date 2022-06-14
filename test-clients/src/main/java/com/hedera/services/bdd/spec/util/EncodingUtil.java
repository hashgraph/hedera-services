package com.hedera.services.bdd.spec.util;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.suites.contract.Utils.asHeadlongAddress;
import static com.hedera.services.bdd.suites.contract.Utils.convertAliasToHeadlongAddress;
import static org.ethereum.crypto.HashUtil.sha3;

public class EncodingUtil {
    public static byte[] encodeParametersWithTuple(final String abi, final Object[] params) {
        byte[] callData = new byte[] { };

        Function function = Function.fromJson(abi);

        final var signature = getSignature(function);

        var argumentTypes = function.getInputs().toString();

        var correctParams = convertAddressToHeadlong(params);

        Tuple paramsAsTuple = Tuple.EMPTY;
        if (correctParams.length > 0 && correctParams[0] instanceof Tuple) {
            paramsAsTuple = (Tuple) correctParams[0];
        } else if (params.length > 0){
            argumentTypes = convertArgumentTypes(argumentTypes, correctParams);
            paramsAsTuple = Tuple.of(correctParams);
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
                convertedArgumentTypes[i] = "int32";
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
        Object[] result = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if(params[i] instanceof String && ((String) params[i]).length() > 10){
                result[i] = convertAliasToHeadlongAddress((String) params[i]);
            } else if(params[i] instanceof byte[] && (((byte[]) params[i]).length == 20)) {
                result[i] = asHeadlongAddress((byte[]) params[i]);
            } else if(params[i] instanceof byte[] && (((byte[]) params[i]).length == 40)) {
                result[i] = convertAliasToHeadlongAddress((String) params[i]);
            } else if (params[i] instanceof List) {
                if(((List<?>) params[i]).get(0) instanceof byte[]){
                    Address[] objectsInside = new Address[((List<?>) params[i]).size()];
                    for (int j = 0; j < ((List<?>) params[i]).size(); j++) {
                        if(((byte[]) ((List<?>) params[i]).get(j)).length == 20) {
                            var instance = ((List<?>) params[i]).get(j);
                            instance = asHeadlongAddress((byte[]) instance);
                            objectsInside[j] = (Address) instance;
                        }
                        if(((byte[]) ((List<?>) params[i]).get(j)).length == 40) {
                            var instance = ((List<?>) params[i]).get(j);
                            instance = convertAliasToHeadlongAddress((String) instance);
                            objectsInside[j] = (Address) instance;
                        }
                    }
                    result[i] = objectsInside;
                } else {
//                    //TODO: Find a way to crate arrays from List
//                    ((List<?>) params[i]).toArray();
//                    for (int j = 0; j < ((List<?>) params[i]).size(); j++) {
//
//                    }
                    result[i] = ((List<?>) params[i]).stream().mapToLong(l -> (long) l).toArray();
                }
            } else {
                result[i] = params[i];
            }
        }

        return result;
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
