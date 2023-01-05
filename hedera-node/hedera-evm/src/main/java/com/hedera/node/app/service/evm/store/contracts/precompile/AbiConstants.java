package com.hedera.node.app.service.evm.store.contracts.precompile;

import org.apache.tuweni.bytes.Bytes;

public class AbiConstants {

  // **** HIP-218 + HIP-376 function selectors and event signatures ****
  // redirectForToken(address token, bytes memory data)
  public static final int ABI_ID_REDIRECT_FOR_TOKEN = 0x618dc65e;

  // isFrozen(address token, address account)
  public static final int ABI_ID_IS_FROZEN = 0x46de0fb1;
  // getTokenKey(address token, uint tokenType)
  public static final int ABI_ID_GET_TOKEN_KEY = 0x3c4dd32e;

  // **** HIP-514 function selectors ****
  // getFungibleTokenInfo(address token)
  public static final int ABI_ID_GET_FUNGIBLE_TOKEN_INFO = 0x3f28a19b;
  // getTokenInfo(address token)
  public static final int ABI_ID_GET_TOKEN_INFO = 0x1f69565f;
  // getNonFungibleTokenInfo(address token, int64 serialNumber)
  public static final int ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO = 0x287e1da8;
  // getTokenDefaultFreezeStatus(address token)
  public static final int ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS = 0xa7daa18d;
  // getTokenDefaultKycStatus(address token)
  public static final int ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS = 0x335e04c1;
  // isKyc(address token, address account)
  public static final int ABI_ID_IS_KYC = 0xf2c31ff4;

  // getTokenCustomFees(address token)
  public static final int ABI_ID_GET_TOKEN_CUSTOM_FEES = 0xae7611a0;
  // isToken(address token)
  public static final int ABI_ID_IS_TOKEN = 0x19f37361;
  // getTokenType(address token)
  public static final int ABI_ID_GET_TOKEN_TYPE = 0x93272baf;
  // getTokenExpiryInfo(address token)
  public static final int ABI_ID_GET_TOKEN_EXPIRY_INFO = 0xd614cdb8;
}
