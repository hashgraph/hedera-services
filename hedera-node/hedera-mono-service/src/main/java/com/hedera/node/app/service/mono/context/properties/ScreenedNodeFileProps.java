/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.node.app.service.mono.context.properties;

import static com.hedera.node.app.service.mono.context.properties.BootstrapProperties.NODE_PROPS;
import static com.hedera.node.app.service.mono.context.properties.BootstrapProperties.transformFor;
import static com.hedera.node.app.service.mono.context.properties.PropUtils.loadOverride;
import static com.hedera.node.app.spi.config.PropertyNames.DEV_DEFAULT_LISTENING_NODE_ACCOUNT;
import static com.hedera.node.app.spi.config.PropertyNames.DEV_ONLY_DEFAULT_NODE_LISTENS;
import static com.hedera.node.app.spi.config.PropertyNames.GRPC_PORT;
import static com.hedera.node.app.spi.config.PropertyNames.GRPC_TLS_PORT;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_PROFILES_ACTIVE;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_IS_ENABLED;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_LOG_DIR;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_QUEUE_CAPACITY;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_FLOW_CONTROL_WINDOW;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_KEEP_ALIVE_TIME;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_KEEP_ALIVE_TIMEOUT;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONCURRENT_CALLS;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE_GRACE;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONNECTION_IDLE;
import static java.util.Map.entry;

import com.hedera.node.app.spi.config.Profile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ScreenedNodeFileProps implements PropertySource {

  static Logger log = LogManager.getLogger(ScreenedNodeFileProps.class);

  private static final Profile[] LEGACY_ENV_ORDER = {Profile.DEV, Profile.PROD, Profile.TEST};

  private static final Map<String, String> STANDARDIZED_NAMES =
      Map.ofEntries(
          entry("nettyFlowControlWindow", NETTY_PROD_FLOW_CONTROL_WINDOW),
          entry("nettyMaxConnectionAge", NETTY_PROD_MAX_CONNECTION_AGE),
          entry("nettyMaxConnectionAgeGrace", NETTY_PROD_MAX_CONNECTION_AGE_GRACE),
          entry("nettyMaxConnectionIdle", NETTY_PROD_MAX_CONNECTION_IDLE),
          entry("nettyMaxConcurrentCalls", NETTY_PROD_MAX_CONCURRENT_CALLS),
          entry("nettyKeepAliveTime", NETTY_PROD_KEEP_ALIVE_TIME),
          entry("nettyKeepAliveTimeOut", NETTY_PROD_KEEP_ALIVE_TIMEOUT),
          entry("port", GRPC_PORT),
          entry("recordStreamQueueCapacity", HEDERA_RECORD_STREAM_QUEUE_CAPACITY),
          entry("enableRecordStreaming", HEDERA_RECORD_STREAM_IS_ENABLED),
          entry("recordLogDir", HEDERA_RECORD_STREAM_LOG_DIR),
          entry("recordLogPeriod", HEDERA_RECORD_STREAM_LOG_PERIOD),
          entry("tlsPort", GRPC_TLS_PORT),
          entry("environment", HEDERA_PROFILES_ACTIVE),
          entry("defaultListeningNodeAccount", DEV_DEFAULT_LISTENING_NODE_ACCOUNT),
          entry("uniqueListeningPortFlag", DEV_ONLY_DEFAULT_NODE_LISTENS));
  private static final Map<String, UnaryOperator<String>> STANDARDIZED_FORMATS =
      Map.ofEntries(
          entry(
              "environment",
              legacy -> LEGACY_ENV_ORDER[Integer.parseInt(legacy)].toString()));

  static String nodePropsLoc = "data/config/node.properties";
  static String legacyNodePropsLoc = "data/config/application.properties";
  static final String MISPLACED_PROP_TPL =
      "Property '%s' is not node-local, please find it a proper home!";
  static final String DEPRECATED_PROP_TPL =
      "Property name '%s' is deprecated, please use '%s' in '%s' instead!";
  static final String UNPARSEABLE_PROP_TPL =
      "Value '%s' is unparseable for '%s' (%s), being ignored!";
  static final String UNTRANSFORMABLE_PROP_TPL =
      "Value '%s' is untransformable for deprecated '%s' (%s), being " + "ignored!";

  static ThrowingStreamProvider fileStreamProvider = loc -> Files.newInputStream(Paths.get(loc));

  private final Map<String, Object> fromFile = new HashMap<>();

  private final Properties rawProperties = new Properties();

  @Inject
  public ScreenedNodeFileProps() {
    loadFrom(legacyNodePropsLoc, false);
    loadFrom(nodePropsLoc, true);
    final var msg =
        "Node-local properties overridden on disk are:\n  "
            + NODE_PROPS.stream()
            .filter(fromFile::containsKey)
            .sorted()
            .map(name -> String.format("%s=%s", name, fromFile.get(name)))
            .collect(Collectors.joining("\n  "));
    log.info(msg);
  }

  private void loadFrom(final String loc, final boolean warnOnMisplacedProp) {
    final var overrideProps = new Properties();
    loadOverride(loc, overrideProps, fileStreamProvider, log);
    for (final String prop : overrideProps.stringPropertyNames()) {
      tryOverriding(prop, overrideProps.getProperty(prop), warnOnMisplacedProp);
    }
  }

  private void tryOverriding(String name, String value, final boolean warnOnMisplacedProp) {
    if (STANDARDIZED_NAMES.containsKey(name)) {
      final var standardName = STANDARDIZED_NAMES.get(name);
      if (STANDARDIZED_FORMATS.containsKey(name)) {
        try {
          value = STANDARDIZED_FORMATS.get(name).apply(value);
        } catch (final Exception reason) {
          log.warn(
              String.format(
                  UNTRANSFORMABLE_PROP_TPL,
                  value,
                  name,
                  reason.getClass().getSimpleName()));
        }
      }
      log.warn(String.format(DEPRECATED_PROP_TPL, name, standardName, nodePropsLoc));
      name = standardName;
    }
    if (!NODE_PROPS.contains(name)) {
      if (warnOnMisplacedProp) {
        log.warn(String.format(MISPLACED_PROP_TPL, name));
      }
      return;
    }
    try {
      rawProperties.put(name, value);
      fromFile.put(name, transformFor(name).apply(value));
    } catch (final Exception reason) {
      log.warn(
          String.format(
              UNPARSEABLE_PROP_TPL, value, name, reason.getClass().getSimpleName()));
    }
  }

  @Override
  public boolean containsProperty(final String name) {
    return fromFile.containsKey(name);
  }

  @Override
  public Object getProperty(final String name) {
    return fromFile.get(name);
  }

  @Override
  public Set<String> allPropertyNames() {
    return fromFile.keySet();
  }

  @Override
  public String getRawValue(final String name) {
    if (rawProperties.contains(name)) {
      return rawProperties.getProperty(name);
    }
    throw new NoSuchElementException("Property of name '" + name + "' can not be found!");
  }

  Map<String, Object> getFromFile() {
    return fromFile;
  }
}
