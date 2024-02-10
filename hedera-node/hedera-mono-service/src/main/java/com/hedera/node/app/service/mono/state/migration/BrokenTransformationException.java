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

package com.hedera.node.app.service.mono.state.migration;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.CompletionException;

/** Exception to indicate the migration failed. */
class BrokenTransformationException extends CompletionException {

    public BrokenTransformationException(@NonNull final String message) {
        super(message);
    }

    public BrokenTransformationException(@NonNull final String message, @NonNull final Throwable t) {
        super(message, t);
    }
}
