package com.hedera.node.app.config.validation;

import static com.hedera.node.app.spi.config.PropertyNames.TOKENS_MAX_TOKEN_NAME_UTF8_BYTES;

import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.swirlds.common.config.validators.DefaultConfigViolation;
import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.PropertyMetadata;

public class MaxTokenNameUtf8BytesConstraints implements ConfigPropertyConstraint<Integer> {

  public final static String PROPERTY_NAME = TOKENS_MAX_TOKEN_NAME_UTF8_BYTES;

  public final static Class<Integer> TYPE = Integer.class;


  @Override
  public ConfigViolation check(final PropertyMetadata<Integer> propertyMetadata) {
    final Integer value = propertyMetadata.getValue();
    if (value == null) {
      return DefaultConfigViolation.of(propertyMetadata, "Value should not be null");
    }
    if (value > MerkleToken.UPPER_BOUND_TOKEN_NAME_UTF8_BYTES) {
      return DefaultConfigViolation.of(propertyMetadata, "Value out of bounds");
    }
    return null;
  }
}
