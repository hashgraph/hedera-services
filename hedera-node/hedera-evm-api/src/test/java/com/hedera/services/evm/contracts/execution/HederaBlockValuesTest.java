package com.hedera.services.evm.contracts.execution;

import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaBlockValuesTest {
  HederaBlockValues subject;

  @Test
  void instancing() {
    final var gasLimit = 1L;
    final var blockNo = 10001L;
    final var consTime = Instant.ofEpochSecond(1_234_567L, 890);

    subject = new HederaBlockValues(gasLimit, blockNo, consTime);
    Assertions.assertEquals(gasLimit, subject.getGasLimit());
    Assertions.assertEquals(consTime.getEpochSecond(), subject.getTimestamp());
    Assertions.assertEquals(Optional.of(Wei.ZERO), subject.getBaseFee());
    Assertions.assertEquals(UInt256.ZERO, subject.getDifficultyBytes());
    Assertions.assertEquals(blockNo, subject.getNumber());
  }
}
