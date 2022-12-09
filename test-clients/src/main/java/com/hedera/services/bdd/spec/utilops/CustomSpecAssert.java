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
package com.hedera.services.bdd.spec.utilops;

import static com.hedera.services.bdd.spec.utilops.UtilStateChange.initializeEthereumAccountForSpec;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.isEthereumAccountCreatedForSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.convertHapiCallsToEthereumCalls;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CustomSpecAssert extends UtilOp {
    static final Logger log = LogManager.getLogger(CustomSpecAssert.class);

    public static void allRunFor(final HapiSpec spec, final List<HapiSpecOperation> ops) {
        if (spec.isUsingEthCalls()) {
            if (!isEthereumAccountCreatedForSpec(spec)) {
                initializeEthereumAccountForSpec(spec);
            }
            executeEthereumOps(spec, ops);
        } else {
            executeHederaOps(spec, ops);
        }
    }

    private static void executeHederaOps(final HapiSpec spec, final List<HapiSpecOperation> ops) {
        for (final HapiSpecOperation op : ops) {
            handleExec(spec, op);
        }
    }

    private static void executeEthereumOps(final HapiSpec spec, final List<HapiSpecOperation> ops) {
        final var convertedOps = convertHapiCallsToEthereumCalls(ops);
        for (final var op : convertedOps) {
            handleExec(spec, op);
        }
    }

    private static void handleExec(final HapiSpec spec, final HapiSpecOperation op) {
        Optional<Throwable> error = op.execFor(spec);
        if (error.isPresent()) {
            log.error("Operation '" + op + "' :: " + error.get().getMessage());
            throw new IllegalStateException(error.get());
        }
    }

    public static void allRunFor(HapiSpec spec, HapiSpecOperation... ops) {
        allRunFor(spec, List.of(ops));
    }

    @FunctionalInterface
    public interface ThrowingConsumer {
        void assertFor(HapiSpec spec, Logger assertLog) throws Throwable;
    }

    private final ThrowingConsumer custom;

    public CustomSpecAssert(ThrowingConsumer custom) {
        this.custom = custom;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        custom.assertFor(spec, log);
        return false;
    }

    @Override
    public String toString() {
        return "CustomSpecAssert";
    }
}
