package com.hedera.node.app.config.converter;

import com.hedera.node.app.service.mono.throttling.MapAccessType;
import com.swirlds.config.api.converter.ConfigConverter;

public class MapAccessTypeConverter implements ConfigConverter<MapAccessType> {

  @Override
  public MapAccessType convert(final String value) throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return MapAccessType.valueOf(value);
  }
}
