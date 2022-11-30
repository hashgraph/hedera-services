package com.hedera.node.app.service.evm.store.contracts.precompile.impl;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BYTES32;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.BalanceOfWrapper;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import com.esaulpaugh.headlong.abi.Function;

public interface EvmBalanceOfPrecompile {

  Function BALANCE_OF_TOKEN_FUNCTION = new Function("balanceOf(address)", INT);
  Bytes BALANCE_OF_TOKEN_SELECTOR = Bytes.wrap(BALANCE_OF_TOKEN_FUNCTION.selector());
  ABIType<Tuple> BALANCE_OF_TOKEN_DECODER = TypeFactory.create(BYTES32);

  static BalanceOfWrapper decodeBalanceOf(
      final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
    final Tuple decodedArguments =
        decodeFunctionCall(input, BALANCE_OF_TOKEN_SELECTOR, BALANCE_OF_TOKEN_DECODER);

    final var account =
        convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);

    return new BalanceOfWrapper(account);
  }
}
