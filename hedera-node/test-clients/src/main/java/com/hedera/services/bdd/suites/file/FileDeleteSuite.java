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

package com.hedera.services.bdd.suites.file;

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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class FileDeleteSuite {
    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(fileCreate("file").contents("ABC"))
                .when()
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> fileDelete("file")));
    }

    @HapiTest
    final Stream<DynamicTest> canDeleteWithAnyOneOfTopLevelKeyList() {
        KeyShape shape = listOf(SIMPLE, threshOf(1, 2), listOf(2));
        SigControl deleteSigs = shape.signedWith(sigs(ON, sigs(OFF, OFF), sigs(ON, OFF)));

        return defaultHapiSpec("CanDeleteWithAnyOneOfTopLevelKeyList")
                .given(fileCreate("test").waclShape(shape))
                .when()
                .then(fileDelete("test").sigControl(forKey("test", deleteSigs)));
    }

    @HapiTest
    final Stream<DynamicTest> getDeletedFileInfo() {
        return defaultHapiSpec("getDeletedFileInfo")
                .given(fileCreate("deletedFile").logged())
                .when(fileDelete("deletedFile").logged())
                .then(getFileInfo("deletedFile").hasAnswerOnlyPrecheck(OK).hasDeleted(true));
    }

    @HapiTest
    final Stream<DynamicTest> handleRejectsMissingFile() {
        return defaultHapiSpec("handleRejectsMissingFile")
                .given()
                .when()
                .then(fileDelete("1.2.3").signedBy(GENESIS).hasKnownStatus(ResponseCodeEnum.INVALID_FILE_ID));
    }

    @HapiTest
    final Stream<DynamicTest> handleRejectsDeletedFile() {
        return defaultHapiSpec("handleRejectsDeletedFile")
                .given(fileCreate("tbd"))
                .when(fileDelete("tbd"))
                .then(fileDelete("tbd").hasKnownStatus(ResponseCodeEnum.FILE_DELETED));
    }
}
