package com.hedera.node.app.config.converter;

import com.hederahashgraph.api.proto.java.ShardID;
import com.swirlds.config.api.converter.ConfigConverter;

public class ShardIDConverter implements ConfigConverter<ShardID> {

  @Override
  public ShardID convert(final String value) throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return ShardID.newBuilder().setShardNum(Long.parseLong(value)).build();
  }
}