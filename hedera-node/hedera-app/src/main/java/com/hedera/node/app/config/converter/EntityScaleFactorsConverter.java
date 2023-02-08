package com.hedera.node.app.config.converter;

import com.hedera.node.app.service.mono.fees.calculation.EntityScaleFactors;
import com.swirlds.config.api.converter.ConfigConverter;

public class EntityScaleFactorsConverter implements ConfigConverter<EntityScaleFactors> {

  @Override
  public EntityScaleFactors convert(final String value)
      throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return EntityScaleFactors.from(value);
  }
}
