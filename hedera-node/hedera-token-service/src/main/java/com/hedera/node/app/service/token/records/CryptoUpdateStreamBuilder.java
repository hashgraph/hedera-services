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

package com.hedera.node.app.service.token.records;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code StreamBuilder} specialization for tracking the side effects of a {@code CryptoUpdate}
 * transaction.
 */
public interface CryptoUpdateStreamBuilder extends StreamBuilder {
    /**
     * Tracks update of a new account by number. Even if someday we support creating multiple
     * accounts within a smart contract call, we will still only need to track one created account
     * per child record.
     *
     * @param accountID the {@link AccountID} of the new account
     * @return this builder
     */
    @NonNull
    CryptoUpdateStreamBuilder accountID(@NonNull AccountID accountID);
}
