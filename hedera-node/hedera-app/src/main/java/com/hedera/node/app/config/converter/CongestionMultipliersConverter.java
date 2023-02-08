package com.hedera.node.app.config.converter;

import com.hedera.node.app.service.mono.fees.calculation.CongestionMultipliers;
import com.swirlds.config.api.converter.ConfigConverter;

public class CongestionMultipliersConverter implements ConfigConverter<CongestionMultipliers> {

  @Override
  public CongestionMultipliers convert(final String value)
      throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return CongestionMultipliers.from(value);
  }
}
