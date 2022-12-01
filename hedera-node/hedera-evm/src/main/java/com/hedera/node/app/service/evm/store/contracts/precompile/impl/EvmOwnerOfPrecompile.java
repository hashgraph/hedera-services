package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.UINT256;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.OwnerOfAndTokenURIWrapper;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;

public interface EvmOwnerOfPrecompile {

   Function OWNER_OF_NFT_FUNCTION = new Function("ownerOf(uint256)", INT);
   Bytes OWNER_OF_NFT_SELECTOR = Bytes.wrap(OWNER_OF_NFT_FUNCTION.selector());
   ABIType<Tuple> OWNER_OF_NFT_DECODER = TypeFactory.create(UINT256);

  public static OwnerOfAndTokenURIWrapper decodeOwnerOf(final Bytes input) {
    final Tuple decodedArguments =
        decodeFunctionCall(input, OWNER_OF_NFT_SELECTOR, OWNER_OF_NFT_DECODER);

    final var tokenId = (BigInteger) decodedArguments.get(0);

    return new OwnerOfAndTokenURIWrapper(tokenId.longValueExact());
  }

}
