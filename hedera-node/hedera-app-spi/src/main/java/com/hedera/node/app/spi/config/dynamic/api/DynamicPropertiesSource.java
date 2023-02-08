package com.hedera.node.app.spi.config.dynamic.api;

import java.time.Period;
import java.util.NoSuchElementException;
import java.util.Set;

public interface DynamicPropertiesSource {

  int DEFAULT_ORDINAL = 100;

  Set<String> getUpdatedPropertyNames();

  String getValue(String name) throws NoSuchElementException;

  Period getUpdatePeriode();

  default int getOrdinal() {
    return DEFAULT_ORDINAL;
  }

  default String getName() {
    return this.getClass().getName();
  }
}
