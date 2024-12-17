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

package com.swirlds.platform.base.example.server;

import com.swirlds.platform.base.example.ext.BaseContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * A PostHandler that does not return a result to the client
 */
public class PostTriggerHandler<T> extends GenericHandler<T> {

    public PostTriggerHandler(
            @NonNull final String path,
            @NonNull final BaseContext context,
            @NonNull final Class<T> consumeType,
            @NonNull final Consumer<T> consumer) {
        super(path, context, consumeType);
        setPostHandler(v -> {
            consumer.accept(v);
            return null;
        });
    }
}
