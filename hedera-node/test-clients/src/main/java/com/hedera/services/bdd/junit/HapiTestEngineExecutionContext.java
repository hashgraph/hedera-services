/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit;

import org.junit.platform.engine.support.hierarchical.EngineExecutionContext;

/**
 * An {@link EngineExecutionContext} for HAPI tests.
 *
 * <p>The context is a place to store any information we need per test. For example, we could store the port of the
 * server here. But the HAPI test system stores that information statically, so we don't need to do it here. So for
 * now this class is just empty (we need it to satisfy the API, but we don't use it).
 */
public class HapiTestEngineExecutionContext implements EngineExecutionContext {

    private HapiTestEnv env;

    /**
     * Set the {@link HapiTestEnv} associated with this test run.
     */
    public void setEnv(HapiTestEnv env) {
        this.env = env;
    }

    /**
     * Get the {@link HapiTestEnv} associated with this test run.
     */
    public HapiTestEnv getEnv() {
        return env;
    }
}
