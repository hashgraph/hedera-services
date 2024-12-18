// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.suites.file;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sendModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedQueryIds;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class FileAppendSuite {
    @HapiTest
    final Stream<DynamicTest> appendIdVariantsTreatedAsExpected() {
        return hapiTest(
                fileCreate("file").contents("ABC"),
                submitModified(withSuccessivelyVariedBodyIds(), () -> fileAppend("file")
                        .content("DEF")));
    }

    @HapiTest
    final Stream<DynamicTest> getContentsIdVariantsTreatedAsExpected() {
        return hapiTest(
                fileCreate("file").contents("ABC"),
                sendModified(withSuccessivelyVariedQueryIds(), () -> getFileContents("file")));
    }

    @HapiTest
    final Stream<DynamicTest> getInfoIdVariantsTreatedAsExpected() {
        return hapiTest(
                fileCreate("file").contents("ABC"),
                sendModified(withSuccessivelyVariedQueryIds(), () -> getFileInfo("file")));
    }

    @HapiTest
    final Stream<DynamicTest> vanillaAppendSucceeds() {
        final byte[] first4K = randomUtf8Bytes(BYTES_4K);
        final byte[] next4k = randomUtf8Bytes(BYTES_4K);
        final byte[] all8k = new byte[2 * BYTES_4K];
        System.arraycopy(first4K, 0, all8k, 0, BYTES_4K);
        System.arraycopy(next4k, 0, all8k, BYTES_4K, BYTES_4K);

        return hapiTest(
                fileCreate("test").contents(first4K),
                fileAppend("test").content(next4k),
                getFileContents("test").hasContents(ignore -> all8k));
    }

    @LeakyHapiTest(overrides = {"files.maxSizeKb"})
    final Stream<DynamicTest> handleRejectsOversized() {
        byte[] BYTES_3K_MINUS1 = new byte[3 * 1024 - 1];
        Arrays.fill(BYTES_3K_MINUS1, (byte) 0xAB);
        byte[] BYTES_1 = new byte[] {(byte) 0xAB};

        return hapiTest(
                overriding("files.maxSizeKb", "3"),
                fileCreate("file").contents(BYTES_3K_MINUS1),
                fileAppend("file").content(BYTES_1),
                fileAppend("file").content(BYTES_1).hasKnownStatus(MAX_FILE_SIZE_EXCEEDED));
    }

    private final int BYTES_4K = 4 * (1 << 10);

    private byte[] randomUtf8Bytes(int n) {
        byte[] data = new byte[n];
        int i = 0;
        while (i < n) {
            byte[] rnd = UUID.randomUUID().toString().getBytes();
            System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
            i += rnd.length;
        }
        return data;
    }
}
