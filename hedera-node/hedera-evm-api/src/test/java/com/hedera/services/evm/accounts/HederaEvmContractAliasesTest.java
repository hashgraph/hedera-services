package com.hedera.services.evm.accounts;

import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HederaEvmContractAliasesTest {

  private final MockedHederaEvmContractAliases hederaEvmContractAliases = new MockedHederaEvmContractAliases();
  byte[] byteArray = new byte[20];

  @Test
  void non20ByteStringCannotBeMirror() {
    assertFalse(hederaEvmContractAliases.isMirror(new byte[] {(byte) 0xab, (byte) 0xcd}));
    assertFalse(hederaEvmContractAliases.isMirror(unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbbde")));
   }

  @Test
   void with20Byte() {
     assertTrue(hederaEvmContractAliases.isMirror(byteArray));
   }

}
