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

package com.hedera.node.app.service.token.records;

import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The base interface for Token Service record builders that record operations on Tokens.
 */
public interface TokenBaseStreamBuilder extends StreamBuilder {
    /**
     * Sets the {@link TokenType} of the token the recorded transaction created or modified.
     * @param tokenType the token type
     * @return this builder
     */
    TokenBaseStreamBuilder tokenType(@NonNull TokenType tokenType);
}
