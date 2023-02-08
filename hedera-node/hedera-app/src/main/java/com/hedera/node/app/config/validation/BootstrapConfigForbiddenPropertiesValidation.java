package com.hedera.node.app.config.validation;

import static com.hedera.node.app.service.mono.context.properties.BootstrapProperties.BOOTSTRAP_PROP_NAMES;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.config.api.validation.ConfigViolation;
import java.util.stream.Stream;

public class BootstrapConfigForbiddenPropertiesValidation implements ConfigValidator {

  @Override
  public Stream<ConfigViolation> validate(final Configuration configuration) {
    return configuration.getPropertyNames()
        .filter(name -> !BOOTSTRAP_PROP_NAMES.contains(name))
        .map(name -> new ConfigViolation() {
          @Override
          public String getPropertyName() {
            return name;
          }

          @Override
          public String getMessage() {
            return "Property with given name is forbidden";
          }

          @Override
          public String getPropertyValue() {
            return configuration.getValue(name);
          }

          @Override
          public boolean propertyExists() {
            return true;
          }
        });
  }
}
