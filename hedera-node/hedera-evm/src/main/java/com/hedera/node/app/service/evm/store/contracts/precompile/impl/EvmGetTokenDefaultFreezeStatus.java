package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BYTES32;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenDefaultFreezeStatusWrapper;
import org.apache.tuweni.bytes.Bytes;

public interface EvmGetTokenDefaultFreezeStatus {
   Function GET_TOKEN_DEFAULT_FREEZE_STATUS_FUNCTION =
      new Function("getTokenDefaultFreezeStatus(address)", INT);
  Bytes GET_TOKEN_DEFAULT_FREEZE_STATUS_SELECTOR =
      Bytes.wrap(GET_TOKEN_DEFAULT_FREEZE_STATUS_FUNCTION.selector());
   ABIType<Tuple> GET_TOKEN_DEFAULT_FREEZE_STATUS_DECODER =
      TypeFactory.create(BYTES32);

  public static GetTokenDefaultFreezeStatusWrapper decodeTokenDefaultFreezeStatus(
      final Bytes input) {
    final Tuple decodedArguments =
        decodeFunctionCall(
            input,
            GET_TOKEN_DEFAULT_FREEZE_STATUS_SELECTOR,
            GET_TOKEN_DEFAULT_FREEZE_STATUS_DECODER);

    final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));

    return new GetTokenDefaultFreezeStatusWrapper(tokenID);
  }

}
