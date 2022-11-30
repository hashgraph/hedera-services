package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import org.apache.tuweni.bytes.Bytes;

public interface EvmNonFungibleTokenInfoPrecompile {

   Function GET_NON_FUNGIBLE_TOKEN_INFO_FUNCTION =
      new Function("getNonFungibleTokenInfo(address,int64)");
   Bytes GET_NON_FUNGIBLE_TOKEN_INFO_SELECTOR =
      Bytes.wrap(GET_NON_FUNGIBLE_TOKEN_INFO_FUNCTION.selector());
  ABIType<Tuple> GET_NON_FUNGIBLE_TOKEN_INFO_DECODER =
      TypeFactory.create("(bytes32,int64)");

  public static TokenInfoWrapper decodeGetNonFungibleTokenInfo(final Bytes input) {
    final Tuple decodedArguments =
        decodeFunctionCall(
            input,
            GET_NON_FUNGIBLE_TOKEN_INFO_SELECTOR,
            GET_NON_FUNGIBLE_TOKEN_INFO_DECODER);

    final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
    final var serialNum = (long) decodedArguments.get(1);
    return TokenInfoWrapper.forNonFungibleToken(tokenID, serialNum);
  }

}
