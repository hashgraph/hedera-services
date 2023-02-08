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

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.hedera.node.app.hapi.utils.sysfiles.domain.KnownBlockValues;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import com.hedera.node.app.service.mono.exceptions.UnparseablePropertyException;
import com.hedera.node.app.service.mono.fees.calculation.CongestionMultipliers;
import com.hedera.node.app.service.mono.fees.calculation.EntityScaleFactors;
import com.hedera.node.app.service.mono.keys.LegacyContractIdActivations;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakeStartupHelper;
import com.hedera.node.app.service.mono.throttling.MapAccessType;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.spi.config.Profile;
import com.hedera.services.stream.proto.SidecarType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Defines a source of arbitrary properties keyed by strings. Provides strongly typed accessors for
 * commonly used property types.
 */
public interface PropertySource {

  Logger log = LogManager.getLogger(PropertySource.class);

  Function<String, Object> AS_INT = s -> Integer.valueOf(s.replace("_", ""));
  Function<String, Object> AS_LONG = s -> Long.valueOf(s.replace("_", ""));
  Function<String, Object> AS_DOUBLE = Double::valueOf;
  Function<String, Object> AS_STRING = s -> s;
  Function<String, Object> AS_PROFILE = v -> Profile.valueOf(v.toUpperCase());
  Function<String, Object> AS_BOOLEAN = Boolean::valueOf;
  Function<String, Object> AS_CS_STRINGS = s -> Arrays.stream(s.split(",")).toList();
  Function<String, Object> AS_NODE_STAKE_RATIOS =
      s ->
          Arrays.stream(s.split(","))
              .map(r -> r.split(":"))
              .filter(
                  e -> {
                    try {
                      return e.length == 2
                          && Long.parseLong(e[0]) >= 0
                          && Long.parseLong(e[1]) > 0;
                    } catch (Exception ignore) {
                      return false;
                    }
                  })
              .collect(toMap(e -> Long.parseLong(e[0]), e -> Long.parseLong(e[1])));
  Function<String, Object> AS_FUNCTIONS =
      s -> Arrays.stream(s.split(",")).map(HederaFunctionality::valueOf).collect(toSet());
  Function<String, Object> AS_CONGESTION_MULTIPLIERS = CongestionMultipliers::from;

  Function<String, Object> AS_LEGACY_ACTIVATIONS = LegacyContractIdActivations::from;
  Function<String, Object> AS_ENTITY_SCALE_FACTORS = EntityScaleFactors::from;
  Function<String, Object> AS_KNOWN_BLOCK_VALUES = KnownBlockValues::from;
  Function<String, Object> AS_THROTTLE_SCALE_FACTOR = ScaleFactor::from;
  Function<String, Object> AS_ENTITY_NUM_RANGE = EntityIdUtils::parseEntityNumRange;
  Function<String, Object> AS_ENTITY_TYPES = EntityType::csvTypeSet;
  Function<String, Object> AS_ACCESS_LIST = MapAccessType::csvAccessList;
  Function<String, Object> AS_SIDECARS =
      s -> asEnumSet(SidecarType.class, SidecarType::valueOf, s);
  Function<String, Object> AS_RECOMPUTE_TYPES =
      s ->
          asEnumSet(
              StakeStartupHelper.RecomputeType.class,
              StakeStartupHelper.RecomputeType::valueOf,
              s);

  static <E extends Enum<E>> Set<E> asEnumSet(
      final Class<E> type, final Function<String, E> valueOf, final String csv) {
    return csv.isEmpty()
        ? Collections.emptySet()
        : Arrays.stream(csv.split(","))
            .map(valueOf)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(type)));
  }

  boolean containsProperty(String name);

  Object getProperty(String name);

  @NonNull
  public Properties getRawProperties();

  default String getRawValue(final String name) {
    final Properties properties = getRawProperties();
    Objects.requireNonNull(properties);
    if (properties.contains(name)) {
      return properties.getProperty(name);
    }
    throw new NoSuchElementException("Property of name '" + name + "' can not be found!");
  }

  Set<String> allPropertyNames();

  default <T> T getTypedProperty(final Class<T> type, final String name) {
    return type.cast(getProperty(name));
  }

  default String getStringProperty(final String name) {
    return getTypedProperty(String.class, name);
  }

  default List<MapAccessType> getAccessListProperty(final String name) {
    return getTypedProperty(List.class, name);
  }

  default Set<StakeStartupHelper.RecomputeType> getRecomputeTypesProperty(final String name) {
    return getTypedProperty(Set.class, name);
  }

  default boolean getBooleanProperty(final String name) {
    return getTypedProperty(Boolean.class, name);
  }

  @SuppressWarnings("unchecked")
  default Set<HederaFunctionality> getFunctionsProperty(final String name) {
    return getTypedProperty(Set.class, name);
  }

  @SuppressWarnings("unchecked")
  default Set<EntityType> getTypesProperty(final String name) {
    return getTypedProperty(Set.class, name);
  }

  @SuppressWarnings("unchecked")
  default Set<SidecarType> getSidecarsProperty(final String name) {
    return getTypedProperty(Set.class, name);
  }

  default CongestionMultipliers getCongestionMultiplierProperty(final String name) {
    return getTypedProperty(CongestionMultipliers.class, name);
  }

  default EntityScaleFactors getEntityScaleFactorsProperty(final String name) {
    return getTypedProperty(EntityScaleFactors.class, name);
  }

  default Map<Long, Long> getNodeStakeRatiosProperty(final String name) {
    return getTypedProperty(Map.class, name);
  }

  default LegacyContractIdActivations getLegacyActivationsProperty(final String name) {
    return getTypedProperty(LegacyContractIdActivations.class, name);
  }

  default ScaleFactor getThrottleScaleFactor(final String name) {
    return getTypedProperty(ScaleFactor.class, name);
  }

  @SuppressWarnings("unchecked")
  default Pair<Long, Long> getEntityNumRange(final String name) {
    return getTypedProperty(Pair.class, name);
  }

  default int getIntProperty(final String name) {
    return getTypedProperty(Integer.class, name);
  }

  @SuppressWarnings("unchecked")
  default List<String> getStringsProperty(final String name) {
    return getTypedProperty(List.class, name);
  }

  default double getDoubleProperty(final String name) {
    return getTypedProperty(Double.class, name);
  }

  default long getLongProperty(final String name) {
    return getTypedProperty(Long.class, name);
  }

  default KnownBlockValues getBlockValuesProperty(final String name) {
    return getTypedProperty(KnownBlockValues.class, name);
  }

  default Profile getProfileProperty(final String name) {
    return getTypedProperty(Profile.class, name);
  }

  default AccountID getAccountProperty(final String name) {
    String value = "";
    try {
      value = getStringProperty(name);
      final long[] nums = Stream.of(value.split("[.]")).mapToLong(Long::parseLong).toArray();
      return AccountID.newBuilder()
          .setShardNum(nums[0])
          .setRealmNum(nums[1])
          .setAccountNum(nums[2])
          .build();
    } catch (final Exception any) {
      log.info(any.getMessage());
      throw new UnparseablePropertyException(name, value);
    }
  }
}
