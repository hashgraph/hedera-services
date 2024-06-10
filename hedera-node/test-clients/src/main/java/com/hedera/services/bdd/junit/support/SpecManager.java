/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.support;

import static com.hedera.services.bdd.spec.HapiSpec.doTargetSpec;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.SpecOperation;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.SpecStateObserver;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

public class SpecManager {
    private static final String SPEC_NAME = "<MANAGED>";

    private final List<SpecStateObserver.SpecState> sharedStates = new ArrayList<>();

    private final HederaNetwork targetNetwork;

    public SpecManager(@NonNull final HederaNetwork targetNetwork) {
        this.targetNetwork = requireNonNull(targetNetwork);
    }

    public void setup(@NonNull final SpecOperation... ops) throws Throwable {
        final var spec = new HapiSpec(SPEC_NAME, ops);
        doTargetSpec(spec, targetNetwork);
        spec.setSpecStateObserver(sharedStates::add);
        spec.execute();
    }

    public void teardown(@NonNull final HapiSpecOperation... ops) throws Throwable {
        final var spec = new HapiSpec(SPEC_NAME, ops);
        doTargetSpec(spec, targetNetwork);
        spec.execute();
    }

    public List<SpecStateObserver.SpecState> getSharedStates() {
        return sharedStates;
    }
}
