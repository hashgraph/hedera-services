package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BYTES32;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenGetCustomFeesWrapper;
import org.apache.tuweni.bytes.Bytes;

public interface EvmTokenGetCustomFeesPrecompile {

   Function TOKEN_GET_CUSTOM_FEES_FUNCTION =
      new Function("getTokenCustomFees(address)");
   Bytes TOKEN_GET_CUSTOM_FEES_SELECTOR =
      Bytes.wrap(TOKEN_GET_CUSTOM_FEES_FUNCTION.selector());
   ABIType<Tuple> TOKEN_GET_CUSTOM_FEES_DECODER = TypeFactory.create(BYTES32);

  public static TokenGetCustomFeesWrapper decodeTokenGetCustomFees(final Bytes input) {
    final Tuple decodedArguments =
        decodeFunctionCall(
            input, TOKEN_GET_CUSTOM_FEES_SELECTOR, TOKEN_GET_CUSTOM_FEES_DECODER);

    final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
    return new TokenGetCustomFeesWrapper(tokenID);
  }

}
