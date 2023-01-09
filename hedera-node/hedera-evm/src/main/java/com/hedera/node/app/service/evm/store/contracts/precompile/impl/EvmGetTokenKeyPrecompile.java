package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenKeyWrapper;
import org.apache.tuweni.bytes.Bytes;

import java.math.BigInteger;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_UINT256_RAW_TYPE;

public interface EvmGetTokenKeyPrecompile {
	Function GET_TOKEN_KEYS_FUNCTION =
			new Function("getTokenKey(address,uint256)");
	Bytes GET_TOKEN_KEYS_SELECTOR =
			Bytes.wrap(GET_TOKEN_KEYS_FUNCTION.selector());
	 ABIType<Tuple> GET_TOKEN_KEYS_DECODER =
			TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);

	 static GetTokenKeyWrapper<byte[]> decodeGetTokenKey(final Bytes input) {
		final Tuple decodedArguments =
				decodeFunctionCall(input, GET_TOKEN_KEYS_SELECTOR, GET_TOKEN_KEYS_DECODER);
		final var token = decodedArguments.get(0);
		final var tokenType = ((BigInteger) decodedArguments.get(1)).longValue();
		return new GetTokenKeyWrapper(token, tokenType);
	}
}
