package com.hedera.node.app.config.converter;

import com.hedera.node.app.hapi.utils.sysfiles.domain.KnownBlockValues;
import com.swirlds.config.api.converter.ConfigConverter;

public class KnownBlockValuesConverter implements ConfigConverter<KnownBlockValues> {

  @Override
  public KnownBlockValues convert(final String value)
      throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return KnownBlockValues.from(value);
  }
}
