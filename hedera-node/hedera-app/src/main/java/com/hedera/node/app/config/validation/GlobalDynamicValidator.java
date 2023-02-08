package com.hedera.node.app.config.validation;

import static com.hedera.node.app.service.mono.context.properties.BootstrapProperties.GLOBAL_DYNAMIC_PROPS;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.config.api.validation.ConfigViolation;
import java.util.stream.Stream;

public class GlobalDynamicValidator implements ConfigValidator {

  @Override
  public Stream<ConfigViolation> validate(final Configuration configuration) {
    return configuration.getPropertyNames()
        .filter(name -> !GLOBAL_DYNAMIC_PROPS.contains(name))
        .map(name ->
            new ConfigViolation() {
              @Override
              public String getPropertyName() {
                return name;
              }

              @Override
              public String getMessage() {
                return "Property '" + name + "' is not part oif global dynamic properties";
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
