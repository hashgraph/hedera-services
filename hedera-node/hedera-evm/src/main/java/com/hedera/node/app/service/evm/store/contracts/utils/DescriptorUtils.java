package com.hedera.node.app.service.evm.store.contracts.utils;

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_KEY;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_FROZEN;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_TOKEN;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;

import org.apache.tuweni.bytes.Bytes;

public class DescriptorUtils {

  public static boolean isTokenProxyRedirect(final Bytes input) {
    return ABI_ID_REDIRECT_FOR_TOKEN == input.getInt(0);
  }

  public static boolean isViewFunction(final Bytes input) {
    int functionId = input.getInt(0);
    return switch (functionId) {
      case ABI_ID_GET_TOKEN_INFO,
          ABI_ID_GET_FUNGIBLE_TOKEN_INFO,
          ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO,
          ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS,
          ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS,
          ABI_ID_IS_FROZEN,
          ABI_ID_IS_KYC,
          ABI_ID_GET_TOKEN_CUSTOM_FEES,
          ABI_ID_GET_TOKEN_KEY,
          ABI_ID_IS_TOKEN,
          ABI_ID_GET_TOKEN_TYPE,
          ABI_ID_GET_TOKEN_EXPIRY_INFO -> true;
      default -> false;
    };
  }

}
