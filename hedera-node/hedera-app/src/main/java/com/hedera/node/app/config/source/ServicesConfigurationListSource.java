package com.hedera.node.app.config.source;

import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.swirlds.config.api.source.ConfigSource;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;

public class ServicesConfigurationListSource implements ConfigSource {

  private final Properties properties = new Properties();

  public ServicesConfigurationListSource(
      final ServicesConfigurationList servicesConfigurationList) {
    servicesConfigurationList.getNameValueList().stream()
        .forEach(setting -> {
          properties.put(setting.getName(), setting.getValue());
        });
  }

  @Override
  public Set<String> getPropertyNames() {
    return properties.stringPropertyNames();
  }

  @Override
  public String getValue(final String key) throws NoSuchElementException {
    return properties.getProperty(key);
  }
}
