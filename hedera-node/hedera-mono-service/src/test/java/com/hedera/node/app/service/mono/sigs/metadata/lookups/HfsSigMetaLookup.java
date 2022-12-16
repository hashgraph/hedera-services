/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.sigs.metadata.lookups;

import static com.hedera.node.app.service.mono.context.primitives.StateView.EMPTY_WACL;
import static com.hedera.node.app.service.mono.sigs.order.KeyOrderingFailure.MISSING_FILE;

import com.hedera.node.app.service.mono.files.HederaFs;
import com.hedera.node.app.service.mono.sigs.metadata.FileSigningMetadata;
import com.hedera.node.app.service.mono.sigs.metadata.SafeLookupResult;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hederahashgraph.api.proto.java.FileID;

/**
 * Trivial file metadata lookup. Treats special files (numbers 150-159) as immutable to avoid an
 * expensive metadata lookup; this is fine, since the privileged payer requirement makes them
 * effectively so.
 */
public class HfsSigMetaLookup implements FileSigMetaLookup {
    private static final FileSigningMetadata SPECIAL_FILE_META =
            new FileSigningMetadata(EMPTY_WACL);
    private static final SafeLookupResult<FileSigningMetadata> SPECIAL_FILE_RESULT =
            new SafeLookupResult<>(SPECIAL_FILE_META);

    private final HederaFs hfs;
    private final HederaFileNumbers fileNumbers;

    public HfsSigMetaLookup(final HederaFs hfs, final HederaFileNumbers fileNumbers) {
        this.hfs = hfs;
        this.fileNumbers = fileNumbers;
    }

    @Override
    public SafeLookupResult<FileSigningMetadata> safeLookup(final FileID id) {
        if (!hfs.exists(id)) {
            return SafeLookupResult.failure(MISSING_FILE);
        }
        if (fileNumbers.isSoftwareUpdateFile(id.getFileNum())) {
            return SPECIAL_FILE_RESULT;
        }
        return new SafeLookupResult<>(new FileSigningMetadata(hfs.getattr(id).getWacl()));
    }
}
