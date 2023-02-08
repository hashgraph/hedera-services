package com.hedera.node.app.config.converter;

import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import com.swirlds.config.api.converter.ConfigConverter;

public class ScaleFactorConverter implements ConfigConverter<ScaleFactor> {

  @Override
  public ScaleFactor convert(final String value)
      throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return ScaleFactor.from(value);
  }
}
