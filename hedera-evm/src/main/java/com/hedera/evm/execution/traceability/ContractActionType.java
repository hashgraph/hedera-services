package com.hedera.evm.execution.traceability;

public enum ContractActionType {

  /** default non-value. */
  NO_ACTION,

  /**
   * Most CALL, CALLCODE, DELEGATECALL, and STATICCALL, and first action of
   * ContractCall/ContractCallLocal to deployed contracts. This does not include calls to system
   * or precompiled contracts.
   */
  CALL,

  /** CREATE, CREATE2, and first action of ContractCreate. */
  CREATE,

  /** like Call, but to precompiled contracts (0x1 to 0x9 as of Berlin) */
  PRECOMPILE,

  /** Call, but to system contract like HTS or ERC20 facades over Token accounts */
  SYSTEM
}
