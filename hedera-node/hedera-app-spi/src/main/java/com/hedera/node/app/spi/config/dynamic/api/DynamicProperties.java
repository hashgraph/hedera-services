package com.hedera.node.app.spi.config.dynamic.api;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Stream;

public interface DynamicProperties {

  Stream<String> getPropertyNames();

  boolean exists(String name);

  <T> Property<T> getProperty(String name, Class<T> type)
      throws NoSuchElementException, IllegalArgumentException;

  <T> Property<T> getProperty(String name, Class<T> type, T defaultValue)
      throws IllegalArgumentException;

  <T> DynamicList<T> getValues(String name, Class<T> type)
      throws NoSuchElementException, IllegalArgumentException;

  <T> DynamicList<T> getValues(String name, Class<T> type, List<T> defaultValues)
      throws IllegalArgumentException;

  <T extends Record> T getDynamicData(Class<T> type);

  Collection<Class<? extends Record>> getDynamicDataTypes();

  Runnable registerObserver(Consumer<List<String>> consumer);
}
