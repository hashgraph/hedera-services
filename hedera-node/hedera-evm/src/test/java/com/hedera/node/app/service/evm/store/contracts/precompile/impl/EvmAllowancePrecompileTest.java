package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmAllowancePrecompileTest {

  public static final Bytes ALLOWANCE_INPUT_ERC =
      Bytes.fromHexString(
          "0x618dc65e00000000000000000000000000000000000003ecdd62ed3e00000000000000000000000000000000000000000000000000000000000003e900000000000000000000000000000000000000000000000000000000000003ea");

  @Test
  void decodeAllowanceInputERC() {
    final var decodedInput = EvmAllowancePrecompile.decodeTokenAllowance(ALLOWANCE_INPUT_ERC);

    assertTrue(decodedInput.token().length > 0);
    assertTrue(decodedInput.owner().length > 0);
    assertTrue(decodedInput.spender().length > 0);
  }
}
