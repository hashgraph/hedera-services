/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stream;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.SemanticVersions;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class CurrentRecordStreamType implements RecordStreamType {
    private static final Logger log = LogManager.getLogger(CurrentRecordStreamType.class);

    private final SemanticVersions semanticVersions;
    private final GlobalDynamicProperties dynamicProperties;

    private int[] fileHeader = null;

    @Inject
    public CurrentRecordStreamType(
            final SemanticVersions semanticVersions,
            final GlobalDynamicProperties dynamicProperties) {
        this.semanticVersions = semanticVersions;
        this.dynamicProperties = dynamicProperties;
    }

    @Override
    public int[] getFileHeader() {
        if (fileHeader == null) {
            final var deployed = semanticVersions.getDeployed();
            final var protoSemVer = deployed.protoSemVer();
            if (SemanticVersion.getDefaultInstance().equals(protoSemVer)) {
                log.error(
                        "Failed to load HAPI proto versions, record stream files may be unusable");
            }
            fileHeader =
                    new int[] {
                        dynamicProperties.recordFileVersion(),
                        protoSemVer.getMajor(),
                        protoSemVer.getMinor(),
                        protoSemVer.getPatch()
                    };
            log.info("Record stream file header is {}", () -> Arrays.toString(fileHeader));
        }
        return fileHeader;
    }

    @Override
    public byte[] getSigFileHeader() {
        return new byte[] {(byte) dynamicProperties.recordSignatureFileVersion()};
    }
}
