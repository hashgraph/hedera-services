/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.fees.calculation.file.queries;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.hapi.fees.usage.file.ExtantFileContext;
import com.hedera.node.app.hapi.fees.usage.file.FileOpsUsage;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.QueryResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetFileInfoResourceUsage implements QueryResourceUsageEstimator {
    private final FileOpsUsage fileOpsUsage;

    @Inject
    public GetFileInfoResourceUsage(final FileOpsUsage fileOpsUsage) {
        this.fileOpsUsage = fileOpsUsage;
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasFileGetInfo();
    }

    @Override
    public FeeData usageGiven(final Query query, final StateView view, final Map<String, Object> ignoreCtx) {
        final var op = query.getFileGetInfo();
        final var info = view.infoForFile(op.getFileID());
        /* Given the test in {@code GetFileInfoAnswer.checkValidity}, this can only be empty
         * under the extraordinary circumstance that the desired file expired during the query
         * answer flow (which will now fail downstream with an appropriate status code); so
         * just return the default {@code FeeData} here. */
        if (info.isEmpty()) {
            return FeeData.getDefaultInstance();
        }
        final var details = info.get();
        final var ctx = ExtantFileContext.newBuilder()
                .setCurrentSize(details.getSize())
                .setCurrentWacl(details.getKeys())
                .setCurrentMemo(details.getMemo())
                .setCurrentExpiry(details.getExpirationTime().getSeconds())
                .build();
        return fileOpsUsage.fileInfoUsage(query, ctx);
    }

    public FeeData usageGiven(@NonNull final Query query, @Nullable final File file) {
        requireNonNull(query);
        if (file == null) {
            return FeeData.getDefaultInstance();
        }
        final com.hederahashgraph.api.proto.java.File details = fromPbj(file);
        final var ctx = ExtantFileContext.newBuilder()
                .setCurrentSize(details.getContents().toByteArray().length)
                .setCurrentWacl(details.getKeys())
                .setCurrentMemo(details.getMemo())
                .setCurrentExpiry(details.getExpirationSecond())
                .build();
        return fileOpsUsage.fileInfoUsage(query, ctx);
    }
}
