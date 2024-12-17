/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.grouping;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.utilops.UtilOp;

public class InBlockingOrder extends UtilOp implements GroupedOps<InBlockingOrder> {
    private final SpecOperation[] ops;

    public InBlockingOrder(SpecOperation... ops) {
        this.ops = ops;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) {
        allRunFor(spec, ops);
        return false;
    }

    public SpecOperation last() {
        return ops[ops.length - 1];
    }

    @Override
    public String toString() {
        return "InBlockingOrder";
    }

    @Override
    public InBlockingOrder failOnErrors() {
        return this;
    }
}
