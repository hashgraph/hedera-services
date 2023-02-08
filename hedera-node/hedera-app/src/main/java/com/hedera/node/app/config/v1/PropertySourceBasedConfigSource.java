package com.hedera.node.app.config.v1;

import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.swirlds.config.api.source.ConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public class PropertySourceBasedConfigSource implements ConfigSource {

  private final PropertySource propertySource;

  public PropertySourceBasedConfigSource(
      @NonNull final PropertySource propertySource) {
    this.propertySource = Objects.requireNonNull(propertySource, "propertySource");
  }

  @Override
  public Set<String> getPropertyNames() {
    return propertySource.allPropertyNames();
  }

  @Override
  public String getValue(final String name) throws NoSuchElementException {
    return propertySource.getRawValue(name);
  }
}
