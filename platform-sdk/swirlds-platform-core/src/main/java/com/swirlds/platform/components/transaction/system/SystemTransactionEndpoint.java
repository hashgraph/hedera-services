/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.transaction.system;

import java.util.List;

/**
 * Should be implemented by objects that wish to handle system transactions
 */
public interface SystemTransactionEndpoint {
    /**
     * Reports which system transactions the implementing class wishes to consume, the methods it wishes to
     * consume them with, and at what stage the handle methods should be executed
     *
     * @return a list of {@link TypedSystemTransactionHandler}s
     */
    List<TypedSystemTransactionHandler<?>> getHandleMethods();
}
