/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sysfiles.domain.throttling;

import com.hederahashgraph.api.proto.java.HederaFunctionality;

public class HapiThrottleUtils {
    public static ThrottleBucket<HederaFunctionality> hapiBucketFromProto(
            final com.hederahashgraph.api.proto.java.ThrottleBucket bucket) {
        return new ThrottleBucket<>(
                bucket.getBurstPeriodMs(),
                bucket.getName(),
                bucket.getThrottleGroupsList().stream()
                        .map(HapiThrottleUtils::hapiGroupFromProto)
                        .toList());
    }

    public static com.hederahashgraph.api.proto.java.ThrottleBucket hapiBucketToProto(
            final ThrottleBucket<HederaFunctionality> bucket) {
        return com.hederahashgraph.api.proto.java.ThrottleBucket.newBuilder()
                .setName(bucket.getName())
                .setBurstPeriodMs(bucket.impliedBurstPeriodMs())
                .addAllThrottleGroups(
                        bucket.getThrottleGroups().stream()
                                .map(HapiThrottleUtils::hapiGroupToProto)
                                .toList())
                .build();
    }

    public static ThrottleGroup<HederaFunctionality> hapiGroupFromProto(
            final com.hederahashgraph.api.proto.java.ThrottleGroup group) {
        return new ThrottleGroup<>(group.getMilliOpsPerSec(), group.getOperationsList());
    }

    public static com.hederahashgraph.api.proto.java.ThrottleGroup hapiGroupToProto(
            final ThrottleGroup<HederaFunctionality> group) {
        return com.hederahashgraph.api.proto.java.ThrottleGroup.newBuilder()
                .setMilliOpsPerSec(group.impliedMilliOpsPerSec())
                .addAllOperations(group.getOperations())
                .build();
    }

    private HapiThrottleUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
