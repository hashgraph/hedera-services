package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BYTES32;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import org.apache.tuweni.bytes.Bytes;

public interface EvmGetTokenTypePrecompile {
   Function GET_TOKEN_TYPE_FUNCTION =
      new Function("getTokenType(address)", "(int,int32)");
   Bytes GET_TOKEN_TYPE_SELECTOR =
      Bytes.wrap(GET_TOKEN_TYPE_FUNCTION.selector());
  ABIType<Tuple> GET_TOKEN_TYPE_DECODER = TypeFactory.create(BYTES32);

  public static TokenInfoWrapper decodeGetTokenType(final Bytes input) {
    final Tuple decodedArguments =
        decodeFunctionCall(input, GET_TOKEN_TYPE_SELECTOR, GET_TOKEN_TYPE_DECODER);
    final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
    return TokenInfoWrapper.forToken(tokenID);
  }

}
