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
package com.hedera.services.bdd.spec.utilops.grouping;

import static java.util.stream.Collectors.joining;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.RegistryNotFound;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class ParallelSpecOps extends UtilOp {
    private static final Logger log = LogManager.getLogger(HapiSpecOperation.class);

    private boolean failOnErrors = false;
    private final HapiSpecOperation[] subs;
    private final Map<String, Throwable> subErrors = new HashMap<>();

    public ParallelSpecOps(HapiSpecOperation... subs) {
        this.subs = subs;
    }

    public ParallelSpecOps failOnErrors() {
        failOnErrors = true;
        return this;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        CompletableFuture<Void> future =
                CompletableFuture.allOf(
                        Stream.of(subs)
                                .map(
                                        op ->
                                                CompletableFuture.runAsync(
                                                        () ->
                                                                op.execFor(spec)
                                                                        .map(
                                                                                t ->
                                                                                        subErrors
                                                                                                .put(
                                                                                                        op
                                                                                                                .toString(),
                                                                                                        t)),
                                                        HapiSpec.getCommonThreadPool()))
                                .toArray(CompletableFuture[]::new));
        future.join();

        if (subErrors.size() > 0) {
            final var message = describeSubErrors();
            if (message.length() > 0) {
                log.error("Problem(s) with sub-operation(s): {}", message);
            }
        }

        return failOnErrors;
    }

    @Override
    @SuppressWarnings("java:S5960")
    protected void assertExpectationsGiven(final HapiSpec spec) throws Throwable {
        if (failOnErrors && subErrors.size() > 0) {
            Assertions.fail(describeSubErrors());
        }
    }

    private String describeSubErrors() {
        return subErrors.entrySet().stream()
                .filter(e -> !(e.getValue() instanceof RegistryNotFound))
                .peek(e -> e.getValue().printStackTrace())
                .map(e -> e.getKey() + " :: " + e.getValue().getMessage())
                .collect(joining(", "));
    }

    @Override
    public boolean requiresFinalization(HapiSpec spec) {
        return Stream.of(subs).anyMatch(operation -> operation.requiresFinalization(spec));
    }

    @Override
    public void finalizeExecFor(HapiSpec spec) throws Throwable {
        for (HapiSpecOperation op : subs) {
            if (op.requiresFinalization(spec)) {
                op.finalizeExecFor(spec);
            }
        }
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("numSubOps", subs.length);
    }
}
