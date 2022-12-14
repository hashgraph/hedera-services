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
package com.hedera.services.bdd.spec.assertions;

import com.hedera.services.bdd.spec.HapiSpec;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class AssertUtils {
    public static <T> ErroringAssertsProvider<List<T>> notEmpty() {
        return spec ->
                (List<T> instance) -> {
                    try {
                        Assertions.assertTrue(!instance.isEmpty(), "List shouldn't be empty!");
                    } catch (Throwable t) {
                        return List.of(t);
                    }
                    return Collections.EMPTY_LIST;
                };
    }

    @SafeVarargs
    public static <T> ErroringAssertsProvider<List<T>> inOrder(
            ErroringAssertsProvider<T>... providers) {
        return spec ->
                (List<T> instance) -> {
                    try {
                        Assertions.assertEquals(
                                providers.length, instance.size(), "Bad list size!");
                        for (int i = 0; i < providers.length; i++) {
                            List<Throwable> errorsHere =
                                    providers[i].assertsFor(spec).errorsIn(instance.get(i));
                            if (!errorsHere.isEmpty()) {
                                return errorsHere;
                            }
                        }
                    } catch (Throwable t) {
                        return List.of(t);
                    }
                    return Collections.EMPTY_LIST;
                };
    }

    public static void rethrowSummaryError(Logger log, String summaryPrefix, List<Throwable> errors)
            throws Throwable {
        if (errors.isEmpty()) {
            return;
        }
        String summary =
                summaryPrefix
                        + " :: "
                        + errors.stream()
                                .map(Throwable::getMessage)
                                .collect(Collectors.joining(", "));
        throw new Exception(summary);
    }

    @FunctionalInterface
    interface ThrowingAssert {
        void assertThrowable(HapiSpec spec, Object o) throws Throwable;
    }
}
