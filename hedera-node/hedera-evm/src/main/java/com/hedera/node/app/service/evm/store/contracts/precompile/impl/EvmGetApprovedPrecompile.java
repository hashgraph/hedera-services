package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.UINT256;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetApprovedWrapper;
import com.hederahashgraph.api.proto.java.TokenID;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;

public interface EvmGetApprovedPrecompile {

    Function ERC_GET_APPROVED_FUNCTION =
      new Function("getApproved(uint256)", INT);
   Bytes ERC_GET_APPROVED_FUNCTION_SELECTOR =
      Bytes.wrap(ERC_GET_APPROVED_FUNCTION.selector());
    ABIType<Tuple> ERC_GET_APPROVED_FUNCTION_DECODER =
      TypeFactory.create(UINT256);
   Function HAPI_GET_APPROVED_FUNCTION =
      new Function("getApproved(address,uint256)", "(int,int)");
    Bytes HAPI_GET_APPROVED_FUNCTION_SELECTOR =
      Bytes.wrap(HAPI_GET_APPROVED_FUNCTION.selector());
   ABIType<Tuple> HAPI_GET_APPROVED_FUNCTION_DECODER =
      TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);

  public static GetApprovedWrapper decodeGetApproved(
      final Bytes input, final TokenID impliedTokenId) {
    final var offset = impliedTokenId == null ? 1 : 0;

    final Tuple decodedArguments =
        decodeFunctionCall(
            input,
            offset == 0
                ? ERC_GET_APPROVED_FUNCTION_SELECTOR
                : HAPI_GET_APPROVED_FUNCTION_SELECTOR,
            offset == 0
                ? ERC_GET_APPROVED_FUNCTION_DECODER
                : HAPI_GET_APPROVED_FUNCTION_DECODER);

    final var tId =
        offset == 0
            ? impliedTokenId
            : convertAddressBytesToTokenID(decodedArguments.get(0));

    final var serialNo = (BigInteger) decodedArguments.get(offset);
    return new GetApprovedWrapper(tId, serialNo.longValueExact());
  }

}
