package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BYTES32;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT_BOOL_PAIR;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import org.apache.tuweni.bytes.Bytes;

public interface EvmIsTokenPrecompile {
  Function IS_TOKEN_FUNCTION =
      new Function("isToken(address)", INT_BOOL_PAIR);
  Bytes IS_TOKEN_FUNCTION_SELECTOR =
      Bytes.wrap(IS_TOKEN_FUNCTION.selector());
   ABIType<Tuple> IS_TOKEN_DECODER = TypeFactory.create(BYTES32);

  public static TokenInfoWrapper decodeIsToken(final Bytes input) {
    final Tuple decodedArguments =
        decodeFunctionCall(input, IS_TOKEN_FUNCTION_SELECTOR, IS_TOKEN_DECODER);
    final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
    return TokenInfoWrapper.forToken(tokenID);
  }

}
