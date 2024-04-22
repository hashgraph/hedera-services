/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit;

import com.hedera.node.app.service.mono.statedumpers.accounts.BBMHederaAccount;
import com.hedera.services.stream.proto.RecordStreamFile;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;

public interface RecordStreamValidator {
    default void validateFiles(List<RecordStreamFile> files) {
        // No-op
    }

    default void validateRecordsAndSidecars(List<RecordWithSidecars> records) {
        // No-op
    }

    default void validateRecordsAndSidecarsHapi(HapiTestEnv env, List<RecordWithSidecars> records)
            throws InvocationTargetException, IllegalAccessException {
        // No-op
    }

    default void validateAccountAliases(Set<BBMHederaAccount> accountsFromState, List<RecordWithSidecars> records) {
        // No-op
    }
}
