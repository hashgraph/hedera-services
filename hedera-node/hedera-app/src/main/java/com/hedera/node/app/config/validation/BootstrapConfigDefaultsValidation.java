package com.hedera.node.app.config.validation;

import static com.hedera.node.app.service.mono.context.properties.BootstrapProperties.BOOTSTRAP_PROP_NAMES;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.config.api.validation.ConfigViolation;
import java.util.stream.Stream;

public class BootstrapConfigDefaultsValidation implements ConfigValidator {

  @Override
  public Stream<ConfigViolation> validate(final Configuration configuration) {
    return BOOTSTRAP_PROP_NAMES.stream()
        .filter(name -> !configuration.exists(name))
        .map(name -> new ConfigViolation() {
          @Override
          public String getPropertyName() {
            return name;
          }

          @Override
          public String getMessage() {
            return "Property with name does not exist";
          }

          @Override
          public String getPropertyValue() {
            return null;
          }

          @Override
          public boolean propertyExists() {
            return false;
          }
        });
  }
}
