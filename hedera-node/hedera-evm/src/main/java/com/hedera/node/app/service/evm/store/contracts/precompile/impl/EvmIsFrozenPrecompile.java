package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT_BOOL_PAIR;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public interface EvmIsFrozenPrecompile {

   Function IS_FROZEN_TOKEN_FUNCTION =
      new Function("isFrozen(address,address)", INT_BOOL_PAIR);
   Bytes IS_FROZEN_TOKEN_FUNCTION_SELECTOR =
      Bytes.wrap(IS_FROZEN_TOKEN_FUNCTION.selector());
   ABIType<Tuple> IS_FROZEN_TOKEN_DECODER =
      TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

  static TokenFreezeUnfreezeWrapper decodeIsFrozen(
      final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
    final Tuple decodedArguments =
        decodeFunctionCall(
            input, IS_FROZEN_TOKEN_FUNCTION_SELECTOR, IS_FROZEN_TOKEN_DECODER);

    final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
    final var accountID =
        convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
    return TokenFreezeUnfreezeWrapper.forIsFrozen(tokenID, accountID);
  }

}
