package com.hedera.node.app.config.converter;

import com.hedera.services.stream.proto.SidecarType;
import com.swirlds.config.api.converter.ConfigConverter;

public class SidecarTypeConverter implements ConfigConverter<SidecarType> {

  @Override
  public SidecarType convert(final String value) throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return SidecarType.valueOf(value);
  }
}
