package com.hedera.services.store.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KvUsageInfoTest {

  @Test
  void hasExpectedSemantics() {
    final var initUsage = 123;
    final var subject = new KvUsageInfo(initUsage);

    assertEquals(initUsage, subject.pendingUsage());
    assertEquals(0, subject.pendingUsageDelta());

    subject.updatePendingBy(5);
    subject.updatePendingBy(-2);

    assertEquals(initUsage + 3, subject.pendingUsage());
    assertEquals(3, subject.pendingUsageDelta());
  }
}