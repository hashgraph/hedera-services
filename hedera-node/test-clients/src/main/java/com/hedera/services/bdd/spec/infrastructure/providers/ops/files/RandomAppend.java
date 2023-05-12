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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RandomAppend implements OpProvider {
    private static final byte[] MORE_BYTES = "This is really something else!".getBytes();

    private final ResponseCodeEnum[] permissiblePrechecks =
            standardPrechecksAnd(FILE_DELETED, INVALID_FILE_ID, INSUFFICIENT_TX_FEE);

    private final ResponseCodeEnum[] permissibleOutcomes =
            standardOutcomesAnd(FILE_DELETED, INVALID_FILE_ID, INSUFFICIENT_TX_FEE, FAIL_INVALID);

    private final EntityNameProvider<FileID> files;

    public RandomAppend(EntityNameProvider<FileID> files) {
        this.files = files;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return Collections.emptyList();
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var target = files.getQualifying();
        if (target.isEmpty()) {
            return Optional.empty();
        }

        var op = TxnVerbs.fileAppend(target.get())
                .hasPrecheckFrom(permissiblePrechecks)
                .hasKnownStatusFrom(permissibleOutcomes)
                .content(MORE_BYTES);

        return Optional.of(op);
    }
}
