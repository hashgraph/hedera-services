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

package com.hedera.node.app.service.admin.impl.handlers;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A collection of all admin handlers
 */
@Singleton
public class AdminHandlers {

    private final FreezeHandler freezeHandler;

    /**
     * Creates a new AdminHandlers
     */
    @Inject
    public AdminHandlers(@NonNull final FreezeHandler freezeHandler) {
        this.freezeHandler = Objects.requireNonNull(freezeHandler, "freezeHandler must not be null");
    }

    /**
     * Returns the freeze handler
     *
     * @return the freeze handler
     */
    public FreezeHandler freezeHandler() {
        return freezeHandler;
    }
}
