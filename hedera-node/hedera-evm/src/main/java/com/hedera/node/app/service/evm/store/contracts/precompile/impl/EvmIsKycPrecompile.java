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
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public interface EvmIsKycPrecompile {

   Function IS_KYC_TOKEN_FUNCTION =
      new Function("isKyc(address,address)", INT_BOOL_PAIR);
   Bytes IS_KYC_TOKEN_FUNCTION_SELECTOR =
      Bytes.wrap(IS_KYC_TOKEN_FUNCTION.selector());
   ABIType<Tuple> IS_KYC_TOKEN_FUNCTION_DECODER =
      TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

  public static GrantRevokeKycWrapper decodeIsKyc(
      final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
    final Tuple decodedArguments =
        decodeFunctionCall(
            input, IS_KYC_TOKEN_FUNCTION_SELECTOR, IS_KYC_TOKEN_FUNCTION_DECODER);

    final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
    final var accountID =
        convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);

    return new GrantRevokeKycWrapper(tokenID, accountID);
  }

}
