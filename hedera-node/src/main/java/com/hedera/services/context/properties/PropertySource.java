/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.properties;

import static java.util.stream.Collectors.toSet;

import com.hedera.services.exceptions.UnparseablePropertyException;
import com.hedera.services.fees.calculation.CongestionMultipliers;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.sysfiles.domain.KnownBlockValues;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
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
    Function<String, Object> AS_FUNCTIONS =
            s -> Arrays.stream(s.split(",")).map(HederaFunctionality::valueOf).collect(toSet());
    Function<String, Object> AS_SIDECARS =
            s ->
                    s.isEmpty()
                            ? Collections.emptySet()
                            : Arrays.stream(s.split(","))
                                    .map(SidecarType::valueOf)
                                    .collect(
                                            Collectors.toCollection(
                                                    () -> EnumSet.noneOf(SidecarType.class)));
    Function<String, Object> AS_CONGESTION_MULTIPLIERS = CongestionMultipliers::from;
    Function<String, Object> AS_KNOWN_BLOCK_VALUES = KnownBlockValues::from;
    Function<String, Object> AS_THROTTLE_SCALE_FACTOR = ThrottleReqOpsScaleFactor::from;
    Function<String, Object> AS_ENTITY_NUM_RANGE = EntityIdUtils::parseEntityNumRange;
    Function<String, Object> AS_ENTITY_TYPES = EntityType::csvTypeSet;
    Function<String, Object> AS_INSTANT = Instant::parse;

    boolean containsProperty(String name);

    Object getProperty(String name);

    Set<String> allPropertyNames();

    default <T> T getTypedProperty(Class<T> type, String name) {
        return type.cast(getProperty(name));
    }

    default String getStringProperty(String name) {
        return getTypedProperty(String.class, name);
    }

    default boolean getBooleanProperty(String name) {
        return getTypedProperty(Boolean.class, name);
    }

    @SuppressWarnings("unchecked")
    default Set<HederaFunctionality> getFunctionsProperty(String name) {
        return getTypedProperty(Set.class, name);
    }

    @SuppressWarnings("unchecked")
    default Set<SidecarType> getSidecarsProperty(String name) {
        return getTypedProperty(Set.class, name);
    }

    @SuppressWarnings("unchecked")
    default Set<EntityType> getTypesProperty(String name) {
        return getTypedProperty(Set.class, name);
    }

    default CongestionMultipliers getCongestionMultiplierProperty(String name) {
        return getTypedProperty(CongestionMultipliers.class, name);
    }

    default ThrottleReqOpsScaleFactor getThrottleScaleFactor(String name) {
        return getTypedProperty(ThrottleReqOpsScaleFactor.class, name);
    }

    @SuppressWarnings("unchecked")
    default Pair<Long, Long> getEntityNumRange(String name) {
        return getTypedProperty(Pair.class, name);
    }

    default int getIntProperty(String name) {
        return getTypedProperty(Integer.class, name);
    }

    default double getDoubleProperty(String name) {
        return getTypedProperty(Double.class, name);
    }

    default long getLongProperty(String name) {
        return getTypedProperty(Long.class, name);
    }

    default KnownBlockValues getBlockValuesProperty(String name) {
        return getTypedProperty(KnownBlockValues.class, name);
    }

    default Profile getProfileProperty(String name) {
        return getTypedProperty(Profile.class, name);
    }

    default AccountID getAccountProperty(String name) {
        String value = "";
        try {
            value = getStringProperty(name);
            long[] nums = Stream.of(value.split("[.]")).mapToLong(Long::parseLong).toArray();
            return AccountID.newBuilder()
                    .setShardNum(nums[0])
                    .setRealmNum(nums[1])
                    .setAccountNum(nums[2])
                    .build();
        } catch (Exception any) {
            log.info(any.getMessage());
            throw new UnparseablePropertyException(name, value);
        }
    }
}
