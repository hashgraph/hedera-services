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
package com.hedera.services.bdd.suites.file;

import static com.hedera.services.bdd.spec.HapiSpec.defaultFailingHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileDeleteSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(FileDeleteSuite.class);

    public static void main(String... args) {
        new FileDeleteSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveTests(), negativeTests());
    }

    private List<HapiSpec> positiveTests() {
        return Arrays.asList(getDeletedFileInfo());
    }

    private List<HapiSpec> negativeTests() {
        return Arrays.asList(canDeleteWithAnyOneOfTopLevelKeyList());
    }

    private HapiSpec canDeleteWithAnyOneOfTopLevelKeyList() {
        KeyShape shape = listOf(SIMPLE, threshOf(1, 2), listOf(2));
        SigControl deleteSigs = shape.signedWith(sigs(ON, sigs(OFF, OFF), sigs(ON, OFF)));

        return defaultFailingHapiSpec("CanDeleteWithAnyOneOfTopLevelKeyList")
                .given(fileCreate("test").waclShape(shape))
                .when()
                .then(fileDelete("test").sigControl(forKey("test", deleteSigs)));
    }

    private HapiSpec getDeletedFileInfo() {
        return defaultHapiSpec("getDeletedFileInfo")
                .given(fileCreate("deletedFile").logged())
                .when(fileDelete("deletedFile").logged())
                .then(getFileInfo("deletedFile").hasAnswerOnlyPrecheck(OK).hasDeleted(true));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
