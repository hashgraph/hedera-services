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

package com.hedera.services.bdd.spec.utilops;

import com.hedera.services.bdd.spec.HapiSpec;

/**
 * A util op that simple runs a runnable.
 */
public class RunnableOp extends UtilOp {
    private final Runnable runnable;

    public RunnableOp(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        runnable.run();
        return false;
    }

    @Override
    public String toString() {
        return "RunnableOp";
    }
}
