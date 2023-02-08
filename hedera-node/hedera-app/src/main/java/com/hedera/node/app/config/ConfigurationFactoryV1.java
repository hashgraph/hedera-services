package com.hedera.node.app.config;

import com.hedera.node.app.config.adaptor.PropertySourceBasedConfigurationAdaptor;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

public class ConfigurationFactoryV1 {

  @NonNull
  public Configuration create(@NonNull final PropertySource propertySource) {
    return new PropertySourceBasedConfigurationAdaptor(propertySource);
  }
}
