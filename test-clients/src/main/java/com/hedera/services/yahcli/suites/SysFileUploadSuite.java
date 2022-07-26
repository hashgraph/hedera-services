/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.yahcli.suites;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateSpecialFile;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.StandardSerdes.SYS_FILE_SERDES;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;
import static com.hedera.services.yahcli.suites.Utils.isSpecialFile;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.SysFileSerde;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SysFileUploadSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(SysFileUploadSuite.class);

    private static final int NOT_APPLICABLE = -1;

    private final int bytesPerOp;
    private final int appendsPerBurst;
    private final long sysFileId;
    private final String srcDir;
    private final boolean isDryRun;
    private final Map<String, String> specConfig;

    private ByteString uploadData;

    public SysFileUploadSuite(
            final String srcDir,
            final Map<String, String> specConfig,
            final String sysFile,
            final boolean isDryRun) {
        this(NOT_APPLICABLE, NOT_APPLICABLE, srcDir, specConfig, sysFile, isDryRun);
    }

    public SysFileUploadSuite(
            final int bytesPerOp,
            final int appendsPerBurst,
            final String srcDir,
            final Map<String, String> specConfig,
            final String sysFile,
            final boolean isDryRun) {
        this.bytesPerOp = bytesPerOp;
        this.appendsPerBurst = appendsPerBurst;
        this.srcDir = srcDir;
        this.isDryRun = isDryRun;
        this.specConfig = specConfig;
        this.sysFileId = Utils.rationalized(sysFile);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        uploadData = appropriateContents(sysFileId);
        return isDryRun
                ? Collections.emptyList()
                : List.of(
                        new HapiApiSpec[] {
                            uploadSysFiles(),
                        });
    }

    private HapiApiSpec uploadSysFiles() {
        final var name = String.format("UploadSystemFile-%s", sysFileId);
        final var fileId = String.format("0.0.%d", sysFileId);

        return customHapiSpec(name)
                .withProperties(specConfig)
                .given()
                .when()
                .then(
                        isSpecialFile(sysFileId)
                                ? updateSpecialFile(
                                        DEFAULT_PAYER,
                                        fileId,
                                        uploadData,
                                        bytesPerOp,
                                        appendsPerBurst)
                                : updateLargeFile(
                                        DEFAULT_PAYER,
                                        fileId,
                                        uploadData,
                                        true,
                                        OptionalLong.of(10_000_000_000L),
                                        updateOp ->
                                                updateOp.alertingPre(
                                                                COMMON_MESSAGES::uploadBeginning)
                                                        .alertingPost(
                                                                COMMON_MESSAGES::uploadEnding),
                                        (appendOp, appendsLeft) ->
                                                appendOp.alertingPre(
                                                                COMMON_MESSAGES::appendBeginning)
                                                        .alertingPost(
                                                                code ->
                                                                        COMMON_MESSAGES
                                                                                .appendEnding(
                                                                                        code,
                                                                                        appendsLeft))));
    }

    private ByteString appropriateContents(final Long fileNum) {
        if (isSpecialFile(fileNum)) {
            return specialFileContents(fileNum);
        }

        final SysFileSerde<String> serde = SYS_FILE_SERDES.get(fileNum);
        final String name = serde.preferredFileName();
        final String loc = srcDir + File.separator + name;
        try {
            final var stylized = Files.readString(Paths.get(loc));
            final var contents = ByteString.copyFrom(serde.toValidatedRawFile(stylized));
            if (isDryRun) {
                final var contentsLoc = binVersionOfPath(loc);
                Files.write(Paths.get(contentsLoc), contents.toByteArray());
            }
            return contents;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read update file @ '" + loc + "'!", e);
        }
    }

    private ByteString specialFileContents(long num) {
        final var loc = Utils.specialFileLoc(srcDir, num);
        try {
            return ByteString.copyFrom(Files.readAllBytes(Paths.get(loc)));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read update file @ '" + loc + "'!", e);
        }
    }

    private String binVersionOfPath(final String loc) {
        final int lastDot = loc.lastIndexOf(".");
        if (lastDot == -1) {
            return loc + ".bin";
        } else {
            return loc.substring(0, lastDot) + ".bin";
        }
    }
}
