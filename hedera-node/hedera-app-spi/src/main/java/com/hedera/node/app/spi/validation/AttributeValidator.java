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

package com.hedera.node.app.spi.validation;

import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.Key;

/**
 * A type that any {@link TransactionHandler} can use to validate entity
 * attributes like memos or keys.
 */
public interface AttributeValidator {
    /**
     * Validates the given key.
     *
     * @param key the key to validate
     * @throws HandleStatusException if the key is invalid
     */
    void validateKey(Key key);

    /**
     * Validates the given memo.
     *
     * @param memo the memo to validate
     * @throws HandleStatusException if the key is invalid
     */
    void validateMemo(String memo);
}
