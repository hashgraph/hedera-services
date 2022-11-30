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

public interface EvmFungibleTokenInfoPrecompile {

  Function GET_FUNGIBLE_TOKEN_INFO_FUNCTION =
      new Function("getFungibleTokenInfo(address)");
  Bytes GET_FUNGIBLE_TOKEN_INFO_SELECTOR =
      Bytes.wrap(GET_FUNGIBLE_TOKEN_INFO_FUNCTION.selector());
  ABIType<Tuple> GET_FUNGIBLE_TOKEN_INFO_DECODER =
      TypeFactory.create(BYTES32);

  static TokenInfoWrapper decodeGetFungibleTokenInfo(final Bytes input) {
    final Tuple decodedArguments =
        decodeFunctionCall(
            input, GET_FUNGIBLE_TOKEN_INFO_SELECTOR, GET_FUNGIBLE_TOKEN_INFO_DECODER);

    final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
    return TokenInfoWrapper.forFungibleToken(tokenID);
  }


}
