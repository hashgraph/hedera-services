/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.validators;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.List;

/**
 * Utility methods for checking the validity of {@link TokenID} lists.
 */
public final class TokenListChecks {

    private TokenListChecks() {
        throw new UnsupportedOperationException("Utility class only");
    }

    /**
     * Verifies that a list of {@link TokenID}s contains no duplicates.
     *
     * @param tokens the list of {@link TokenID}s to check
     * @return {@code true} if the list contains no duplicates, {@code false} otherwise
     * @throws NullPointerException if {@code tokens} is {@code null}
     */
    public static boolean repeatsItself(@NonNull final List<TokenID> tokens) {
        requireNonNull(tokens);
        return new HashSet<>(tokens).size() < tokens.size();
    }
}
