package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_TRIO_RAW_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BOOL;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT_BOOL_PAIR;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.IsApproveForAllWrapper;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public interface EvmIsApprovedForAllPrecompile {

   Function ERC_IS_APPROVED_FOR_ALL =
      new Function("isApprovedForAll(address,address)", BOOL);
   Bytes ERC_IS_APPROVED_FOR_ALL_SELECTOR =
      Bytes.wrap(ERC_IS_APPROVED_FOR_ALL.selector());
   ABIType<Tuple> ERC_IS_APPROVED_FOR_ALL_DECODER =
      TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);
  Function HAPI_IS_APPROVED_FOR_ALL =
      new Function("isApprovedForAll(address,address,address)", INT_BOOL_PAIR);
   Bytes HAPI_IS_APPROVED_FOR_ALL_SELECTOR =
      Bytes.wrap(HAPI_IS_APPROVED_FOR_ALL.selector());
   ABIType<Tuple> HAPI_IS_APPROVED_FOR_ALL_DECODER =
      TypeFactory.create(ADDRESS_TRIO_RAW_TYPE);

  static IsApproveForAllWrapper decodeIsApprovedForAll(
      final Bytes input,
      final TokenID impliedTokenId,
      final UnaryOperator<byte[]> aliasResolver) {
    final var offset = impliedTokenId == null ? 1 : 0;

    final Tuple decodedArguments =
        decodeFunctionCall(
            input,
            offset == 0
                ? ERC_IS_APPROVED_FOR_ALL_SELECTOR
                : HAPI_IS_APPROVED_FOR_ALL_SELECTOR,
            offset == 0
                ? ERC_IS_APPROVED_FOR_ALL_DECODER
                : HAPI_IS_APPROVED_FOR_ALL_DECODER);

    final var tId =
        offset == 0
            ? impliedTokenId
            : convertAddressBytesToTokenID(decodedArguments.get(0));

    final var owner =
        convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);
    final var operator =
        convertLeftPaddedAddressToAccountId(
            decodedArguments.get(offset + 1), aliasResolver);

    return new IsApproveForAllWrapper(tId, owner, operator);
  }

}
