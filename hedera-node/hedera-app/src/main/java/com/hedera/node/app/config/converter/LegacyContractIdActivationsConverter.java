package com.hedera.node.app.config.converter;

import com.hedera.node.app.service.mono.keys.LegacyContractIdActivations;
import com.swirlds.config.api.converter.ConfigConverter;

public class LegacyContractIdActivationsConverter implements
    ConfigConverter<LegacyContractIdActivations> {

  @Override
  public LegacyContractIdActivations convert(final String value)
      throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return LegacyContractIdActivations.from(value);
  }
}
