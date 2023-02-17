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

package com.hedera.services.bdd.spec.utilops.inventory;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.Key;
import java.util.function.UnaryOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SpecKeyFromMutation extends UtilOp {
    private static final Logger log = LogManager.getLogger(SpecKeyFromMutation.class);

    private final String name;
    private final String mutated;
    private UnaryOperator<Key> mutation = k -> k;

    public SpecKeyFromMutation(String name, String mutated) {
        this.name = name;
        this.mutated = mutated;
    }

    public SpecKeyFromMutation changing(UnaryOperator<Key> mutation) {
        this.mutation = mutation;
        return this;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        var source = spec.registry().getKey(mutated);
        var sink = mutation.apply(source);
        spec.registry().saveKey(name, sink);
        spec.keys().setControl(sink, spec.keys().controlFor(source));
        return false;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        var helper = super.toStringHelper();
        helper.add("name", name).add("mutated", mutated);
        return helper;
    }
}
