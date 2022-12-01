package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_TRIO_RAW_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenAllowanceWrapper;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public interface EvmAllowancePrecompile {

   Function ERC_ALLOWANCE_FUNCTION =
      new Function("allowance(address,address)", INT);
   Bytes ERC_ALLOWANCE_SELECTOR =
      Bytes.wrap(ERC_ALLOWANCE_FUNCTION.selector());
   ABIType<Tuple> ERC_ALLOWANCE_DECODER =
      TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);
   Function HAPI_ALLOWANCE_FUNCTION =
      new Function("allowance(address,address,address)", "(int,int)");
   Bytes HAPI_ALLOWANCE_SELECTOR =
      Bytes.wrap(HAPI_ALLOWANCE_FUNCTION.selector());
   ABIType<Tuple> HAPI_ALLOWANCE_DECODER =
      TypeFactory.create(ADDRESS_TRIO_RAW_TYPE);

  static TokenAllowanceWrapper decodeTokenAllowance(
      final Bytes input,
      final TokenID impliedTokenId,
      final UnaryOperator<byte[]> aliasResolver) {
    final var offset = impliedTokenId == null ? 1 : 0;

    final Tuple decodedArguments =
        decodeFunctionCall(
            input,
            offset == 0 ? ERC_ALLOWANCE_SELECTOR : HAPI_ALLOWANCE_SELECTOR,
            offset == 0 ? ERC_ALLOWANCE_DECODER : HAPI_ALLOWANCE_DECODER);

    final var tokenId =
        offset == 0
            ? impliedTokenId
            : convertAddressBytesToTokenID(decodedArguments.get(0));
    final var owner =
        convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);
    final var spender =
        convertLeftPaddedAddressToAccountId(
            decodedArguments.get(offset + 1), aliasResolver);

    return new TokenAllowanceWrapper(tokenId, owner, spender);
  }

}
