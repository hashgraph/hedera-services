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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PermissionSemanticsSpec extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PermissionSemanticsSpec.class);
    public static final String NEVER_TO_BE_USED = "neverToBeUsed";
    public static final String CIVILIAN = "civilian";
    public static final String ETERNAL = "eternal";
    public static final String WACL = "wacl";

    public static void main(String... args) {
        new PermissionSemanticsSpec().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                allowsDeleteWithOneTopLevelSig(),
                supportsImmutableFiles(),
                addressBookAdminExemptFromFeesGivenAuthorizedOps());
    }

    private HapiSpec addressBookAdminExemptFromFeesGivenAuthorizedOps() {
        long amount = 100 * 100_000_000L;
        AtomicReference<byte[]> origContents = new AtomicReference<>();
        return defaultHapiSpec("AddressBookAdminExemptFromFeesGivenAuthorizedOps")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, amount))
                                .fee(ONE_HUNDRED_HBARS),
                        fileCreate("tbu"),
                        getFileContents(NODE_DETAILS).consumedBy(origContents::set))
                .when(
                        fileUpdate(NODE_DETAILS)
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .contents(ignore -> ByteString.copyFrom(origContents.get()))
                                .via("authorizedTxn"),
                        fileUpdate("tbu")
                                .payingWith(ADDRESS_BOOK_CONTROL)
                                .contents("This is something new.")
                                .via("unauthorizedTxn"))
                .then(
                        getTxnRecord("unauthorizedTxn")
                                .hasPriority(recordWith().feeDifferentThan(0L)),
                        getTxnRecord("authorizedTxn").hasPriority(recordWith().fee(0L)));
    }

    private HapiSpec supportsImmutableFiles() {
        long extensionSecs = 666L;
        AtomicLong approxExpiry = new AtomicLong();

        return defaultHapiSpec("SupportsImmutableFiles")
                .given(
                        newKeyNamed(NEVER_TO_BE_USED).type(KeyFactory.KeyType.LIST),
                        cryptoCreate(CIVILIAN),
                        fileCreate(ETERNAL).payingWith(CIVILIAN).unmodifiable())
                .when(
                        fileDelete(ETERNAL)
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN)
                                .hasKnownStatus(UNAUTHORIZED),
                        fileAppend(ETERNAL)
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN)
                                .content("Ignored.")
                                .hasKnownStatus(UNAUTHORIZED),
                        fileUpdate(ETERNAL)
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN)
                                .contents("Ignored.")
                                .hasKnownStatus(UNAUTHORIZED),
                        fileUpdate(ETERNAL)
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN, NEVER_TO_BE_USED)
                                .wacl(NEVER_TO_BE_USED)
                                .hasKnownStatus(UNAUTHORIZED))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    approxExpiry.set(
                                            spec.registry().getTimestamp(ETERNAL).getSeconds());
                                }),
                        fileUpdate(ETERNAL)
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN)
                                .extendingExpiryBy(extensionSecs),
                        getFileInfo(ETERNAL)
                                .isUnmodifiable()
                                .hasExpiryPassing(
                                        l -> Math.abs(l - approxExpiry.get() - extensionSecs) < 5));
    }

    private HapiSpec allowsDeleteWithOneTopLevelSig() {
        KeyShape wacl = KeyShape.listOf(KeyShape.SIMPLE, KeyShape.listOf(2));

        var deleteSig = wacl.signedWith(sigs(ON, sigs(OFF, OFF)));
        var failedDeleteSig = wacl.signedWith(sigs(OFF, sigs(OFF, ON)));

        var updateSig = wacl.signedWith(sigs(ON, sigs(ON, ON)));
        var failedUpdateSig = wacl.signedWith(sigs(ON, sigs(OFF, ON)));

        return defaultHapiSpec("AllowsDeleteWithOneTopLevelSig")
                .given(newKeyNamed(WACL).shape(wacl))
                .when(fileCreate("tbd").key(WACL))
                .then(
                        fileUpdate("tbd")
                                .contents("Some more contents!")
                                .signedBy(GENESIS, WACL)
                                .sigControl(ControlForKey.forKey(WACL, failedUpdateSig))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        fileUpdate("tbd")
                                .contents("Some new contents!")
                                .signedBy(GENESIS, WACL)
                                .sigControl(ControlForKey.forKey(WACL, updateSig)),
                        fileDelete("tbd")
                                .signedBy(GENESIS, WACL)
                                .sigControl(ControlForKey.forKey(WACL, failedDeleteSig))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        fileDelete("tbd")
                                .signedBy(GENESIS, WACL)
                                .sigControl(ControlForKey.forKey(WACL, deleteSig)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
