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

package com.hedera.node.app.service.addressbook.impl.handlers;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Class to hold all the addressbook handlers.
 */
@Singleton
public class AddressBookHandlers {

    private final NodeCreateHandler nodeCreateHandler;

    private final NodeDeleteHandler nodeDeleteHandler;

    private final NodeUpdateHandler nodeUpdateHandler;

    /**
     * Constructor for AddressBookHandlers.
     */
    @Inject
    public AddressBookHandlers(
            @NonNull final NodeCreateHandler nodeCreateHandler,
            @NonNull final NodeDeleteHandler nodeDeleteHandler,
            @NonNull final NodeUpdateHandler nodeUpdateHandler) {
        this.nodeCreateHandler = Objects.requireNonNull(nodeCreateHandler, "nodeCreateHandler must not be null");
        this.nodeDeleteHandler = Objects.requireNonNull(nodeDeleteHandler, "nodeDeleteHandler must not be null");
        this.nodeUpdateHandler = Objects.requireNonNull(nodeUpdateHandler, "nodeUpdateHandler must not be null");
    }

    /**
     * Get the nodeCreateHandler.
     *
     * @return the nodeCreateHandler
     */
    public NodeCreateHandler nodeCreateHandler() {
        return nodeCreateHandler;
    }

    /**
     * Get the nodeDeleteHandler.
     *
     * @return the nodeDeleteHandler
     */
    public NodeDeleteHandler nodeDeleteHandler() {
        return nodeDeleteHandler;
    }

    /**
     * Get the nodeUpdateHandler.
     *
     * @return the nodeUpdateHandler
     */
    public NodeUpdateHandler nodeUpdateHandler() {
        return nodeUpdateHandler;
    }
}
