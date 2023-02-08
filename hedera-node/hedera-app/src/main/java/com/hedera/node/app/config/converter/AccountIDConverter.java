package com.hedera.node.app.config.converter;

import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.config.api.converter.ConfigConverter;
import java.util.stream.Stream;

public class AccountIDConverter implements ConfigConverter<AccountID> {

  @Override
  public AccountID convert(final String value)
      throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    try {
      final long[] nums = Stream.of(value.split("[.]")).mapToLong(Long::parseLong).toArray();
      if (nums.length != 3) {
        throw new IllegalArgumentException("Does not match pattern 'A.B.C'");
      }
      if (nums[0] < 0) {
        throw new IllegalArgumentException("Shared num of value is negative");
      }
      if (nums[1] < 0) {
        throw new IllegalArgumentException("Realm num of value is negative");
      }
      if (nums[2] < 0) {
        throw new IllegalArgumentException("Account num of value is negative");
      }
      return AccountID.newBuilder()
          .setShardNum(nums[0])
          .setRealmNum(nums[1])
          .setAccountNum(nums[2])
          .build();
    } catch (final Exception e) {
      throw new IllegalArgumentException(
          "'" + value + "' can not be parsed to " + AccountID.class.getName(), e);
    }
  }
}
