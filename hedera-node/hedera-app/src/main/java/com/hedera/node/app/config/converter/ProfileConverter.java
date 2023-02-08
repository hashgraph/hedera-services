package com.hedera.node.app.config.converter;

import com.hedera.node.app.spi.config.Profile;
import com.swirlds.config.api.converter.ConfigConverter;

public class ProfileConverter implements ConfigConverter<Profile> {

  @Override
  public Profile convert(final String value) throws IllegalArgumentException, NullPointerException {
    if (value == null) {
      throw new NullPointerException("null can not be converted");
    }

    try {
      final int i = Integer.parseInt(value);
      if (i == 0) {
        return Profile.DEV;
      } else if (i == 1) {
        return Profile.PROD;
      } else if (i == 2) {
        return Profile.TEST;
      }
    } catch (final Exception e) {
      //ignore
    }

    return Profile.valueOf(value.toUpperCase());
  }
}
