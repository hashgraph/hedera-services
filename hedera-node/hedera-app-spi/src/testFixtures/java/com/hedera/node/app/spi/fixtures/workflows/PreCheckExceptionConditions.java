/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.spi.fixtures.workflows;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;
import org.assertj.core.api.Condition;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * A collection of {@link Condition} objects for asserting the state of {@link PreCheckException} objects.
 */
public class PreCheckExceptionConditions {

    private PreCheckExceptionConditions() {}

    /**
     * Returns a {@link Condition} that asserts that the {@link PreCheckException} has the given
     * {@link ResponseCodeEnum}.
     * <p>
     * The type of the {@link Condition} is {@link Throwable} because
     * {@link org.assertj.core.api.Assertions#assertThatThrownBy(ThrowingCallable)} expects a
     * {@link Condition} of type {@link Throwable}.
     *
     * @param responseCode the expected {@link ResponseCodeEnum}
     * @return the {@link Condition}
     */
    @NonNull
    public static Condition<Throwable> responseCode(@NonNull final ResponseCodeEnum responseCode) {
        return new Condition<>(getResponseCodeCheck(responseCode), "responseCode " + responseCode);
    }

    @NonNull
    private static Predicate<Throwable> getResponseCodeCheck(@NonNull final ResponseCodeEnum responseCode) {
        return e -> {
            if (e instanceof PreCheckException exception) {
                return exception.responseCode() == responseCode;
            }
            return false;
        };
    }
}
