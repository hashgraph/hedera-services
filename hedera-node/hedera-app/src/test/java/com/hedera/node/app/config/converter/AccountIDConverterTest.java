package com.hedera.node.app.config.converter;

import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountIDConverterTest {

  @Test
  void testNullValue() {
    //given
    final AccountIDConverter converter = new AccountIDConverter();

    //then
    Assertions.assertThrows(NullPointerException.class, () -> converter.convert(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", ":", "...", "1.2.", "-3.2.4", "1.-2.4", "1.2.-3", "a.1.2", "1.2",
      "1.a.2", "a.2.a"})
  void testIllegalValuesValue(final String value) {
    //given
    final AccountIDConverter converter = new AccountIDConverter();

    //then
    Assertions.assertThrows(IllegalArgumentException.class, () -> converter.convert(value));
  }

  @Test
  void testConversion() {
    //given
    final AccountIDConverter converter = new AccountIDConverter();
    final String value = "1.2.3";

    //when
    final AccountID id = converter.convert(value);

    //then
    Assertions.assertNotNull(id);
    Assertions.assertEquals(1, id.getShardNum());
    Assertions.assertEquals(2, id.getRealmNum());
    Assertions.assertEquals(3, id.getAccountNum());
  }
}