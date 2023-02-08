package com.hedera.node.app.config.converter;

import com.hederahashgraph.api.proto.java.RealmID;
import com.swirlds.config.api.converter.ConfigConverter;

public class RealmIDConverter implements ConfigConverter<RealmID> {

  @Override
  public RealmID convert(final String value) throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return RealmID.newBuilder().setRealmNum(Long.parseLong(value)).build();
  }
}
