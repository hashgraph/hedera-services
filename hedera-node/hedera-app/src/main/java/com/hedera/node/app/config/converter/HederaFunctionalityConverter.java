package com.hedera.node.app.config.converter;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.config.api.converter.ConfigConverter;

public class HederaFunctionalityConverter implements ConfigConverter<HederaFunctionality> {

  @Override
  public HederaFunctionality convert(final String value)
      throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return HederaFunctionality.valueOf(value);
  }
}
