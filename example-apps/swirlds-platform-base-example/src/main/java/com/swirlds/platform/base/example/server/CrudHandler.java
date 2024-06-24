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

/**
 * A http handler that delegates to a {@link CrudService}
 */
public class CrudHandler<T> extends GenericHandler<T> {

    /**
     * Creates a {@link HttpHandlerDefinition} that delegates to a {@link CrudService}
     *
     * @param path the path this handler will manage
     * @param context {@link BaseContext} to use
     * @param service the delegated {@link CrudService}
     */
    public CrudHandler(
            @NonNull final String path, @NonNull final BaseContext context, @NonNull final CrudService<T> service) {
        super(path, context, service.getResultType());
        this.setDeleteHandler(service::delete);
        this.setPostHandler(service::create);
        this.setPutHandler(service::update);
        this.setGetHandler((id, queryParameters) -> {
            if (id != null) {
                return service.retrieve(id);
            } else {
                return service.retrieveAll(queryParameters);
            }
        });
    }
}
