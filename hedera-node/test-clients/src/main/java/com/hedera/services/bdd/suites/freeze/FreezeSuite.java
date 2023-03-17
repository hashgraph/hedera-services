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

package com.hedera.services.bdd.suites.freeze;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.suites.utils.ZipUtil.createZip;

import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class FreezeSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(FreezeSuite.class);

    private static final String UPLOAD_PATH_PREFIX = "src/main/resource/testfiles/updateFeature/";
    private static final String UPDATE_NEW_FILE = UPLOAD_PATH_PREFIX + "addNewFile/newFile.zip";

    private static String uploadPath = "updateSettings";

    public static void main(final String... args) {
        if (args.length > 0) {
            uploadPath = args[0];
        }
        new FreezeSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(uploadNewFile());
    }

    private HapiSpec uploadNewFile() {
        String uploadFile = UPDATE_NEW_FILE;
        if (uploadPath != null) {
            log.info("Creating zip file from {}", uploadPath);
            final var zipFile = "Archive.zip";
            createZip(UPLOAD_PATH_PREFIX + uploadPath, zipFile, null);
            uploadFile = zipFile;
        }

        log.info("Uploading file {}", uploadFile);
        final File f = new File(uploadFile);
        byte[] bytes = new byte[0];
        try {
            bytes = Files.readAllBytes(f.toPath());
        } catch (final IOException e) {
            e.printStackTrace();
        }
        final byte[] hash = CommonUtils.noThrowSha384HashOf(bytes);

        // mnemonic for file 0.0.150
        final var fileIDString = "UPDATE_FEATURE";
        return defaultHapiSpec("uploadFileAndUpdate")
                .given(fileUpdate(fileIDString).path(uploadFile).payingWith(GENESIS))
                .when(freezeOnly()
                        .withUpdateFile(fileIDString)
                        .havingHash(hash)
                        .payingWith(GENESIS)
                        .startingIn(60)
                        .seconds())
                .then();
    }
}
