/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.files;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RandomFileDeletion implements OpProvider {
    private final RegistrySourcedNameProvider<FileID> files;

    private final ResponseCodeEnum[] permissiblePrechecks = standardPrechecksAnd(FILE_DELETED, INVALID_FILE_ID);
    private final ResponseCodeEnum[] permissibleOutcomes =
            standardOutcomesAnd(FILE_DELETED, INVALID_FILE_ID, FAIL_INVALID);

    public RandomFileDeletion(RegistrySourcedNameProvider<FileID> files) {
        this.files = files;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var tbd = files.getQualifying();
        if (tbd.isEmpty()) {
            return Optional.empty();
        }
        if (tbd.get().endsWith("-bytecode")) {
            return Optional.empty();
        }
        var op = fileDelete(tbd.get())
                .purging()
                .hasPrecheckFrom(permissiblePrechecks)
                .hasKnownStatusFrom(permissibleOutcomes);

        return Optional.of(op);
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return Collections.emptyList();
    }
}
