package com.hedera.node.app.config.validation;

import static com.hedera.node.app.spi.config.PropertyNames.RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT;

import com.swirlds.common.config.validators.DefaultConfigViolation;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.PropertyMetadata;

public class IntradayChangeLimitPercentConstraint implements
    ConfigPropertyConstraint<Integer> {

  public final static String PROPERTY_NAME = RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT;

  public final static Class<Integer> TYPE = Integer.class;

  @Override
  public ConfigViolation check(final PropertyMetadata<Integer> propertyMetadata) {
    CommonUtils.throwArgNull(propertyMetadata, "propertyMetadata");
    final Integer value = propertyMetadata.getValue();
    if (value == null) {
      return DefaultConfigViolation.of(propertyMetadata, "Value should not be null");
    }
    if (value <= 0) {
      return DefaultConfigViolation.of(propertyMetadata, "Value out of bounds");
    }
    return null;
  }
}
