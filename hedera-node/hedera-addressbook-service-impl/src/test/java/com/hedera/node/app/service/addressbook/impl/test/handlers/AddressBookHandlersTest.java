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

package com.hedera.node.app.service.addressbook.impl.test.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.addressbook.impl.handlers.AddressBookHandlers;
import com.hedera.node.app.service.addressbook.impl.handlers.NodeCreateHandler;
import com.hedera.node.app.service.addressbook.impl.handlers.NodeDeleteHandler;
import com.hedera.node.app.service.addressbook.impl.handlers.NodeUpdateHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressBookHandlersTest {
    private NodeCreateHandler nodeCreateHandler;

    private NodeDeleteHandler nodeDeleteHandler;

    private NodeUpdateHandler nodeUpdateHandler;

    private AddressBookHandlers addressBookHandlers;

    @BeforeEach
    public void setUp() {
        nodeCreateHandler = mock(NodeCreateHandler.class);
        nodeDeleteHandler = mock(NodeDeleteHandler.class);
        nodeUpdateHandler = mock(NodeUpdateHandler.class);

        addressBookHandlers = new AddressBookHandlers(nodeCreateHandler, nodeDeleteHandler, nodeUpdateHandler);
    }

    @Test
    void nodeCreateHandlerReturnsCorrectInstance() {
        assertEquals(
                nodeCreateHandler,
                addressBookHandlers.nodeCreateHandler(),
                "nodeCreateHandler does not return correct instance");
    }

    @Test
    void nodeDeleteHandlerReturnsCorrectInstance() {
        assertEquals(
                nodeDeleteHandler,
                addressBookHandlers.nodeDeleteHandler(),
                "nodeDeleteHandler does not return correct instance");
    }

    @Test
    void nodeUpdateHandlerReturnsCorrectInstance() {
        assertEquals(
                nodeUpdateHandler,
                addressBookHandlers.nodeUpdateHandler(),
                "nodeUpdateHandler does not return correct instance");
    }
}
