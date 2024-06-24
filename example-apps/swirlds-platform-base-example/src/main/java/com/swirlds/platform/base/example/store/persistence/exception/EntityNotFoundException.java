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

package com.swirlds.platform.base.example.store.persistence.exception;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Exception that indicates that the searched entity was not found in the system
 */
public class EntityNotFoundException extends RuntimeException {
    private final Class<?> entityType;
    private final String id;

    public EntityNotFoundException(@NonNull final Class<?> entityClass, @NonNull final String id) {
        this.entityType = entityClass;
        this.id = id;
    }

    @NonNull
    public String getEntityType() {
        return entityType.getSimpleName();
    }

    @NonNull
    public String getId() {
        return id;
    }
}
