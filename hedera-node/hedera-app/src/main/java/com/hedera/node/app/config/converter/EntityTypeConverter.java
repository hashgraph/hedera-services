package com.hedera.node.app.config.converter;

import com.hedera.node.app.service.mono.context.properties.EntityType;
import com.swirlds.config.api.converter.ConfigConverter;

public class EntityTypeConverter implements ConfigConverter<EntityType> {

  @Override
  public EntityType convert(final String value) throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }
    return EntityType.valueOf(value);
  }
}
