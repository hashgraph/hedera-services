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
package com.hedera.services.fees.calculation;

import static com.hedera.services.fees.calculation.FeeCalcUtils.ZERO_EXPIRY;
import static com.hedera.services.fees.calculation.FeeCalcUtils.clampedAdd;
import static com.hedera.services.fees.calculation.FeeCalcUtils.clampedMultiply;
import static com.hedera.services.fees.calculation.FeeCalcUtils.lookupAccountExpiry;
import static com.hedera.services.fees.calculation.FeeCalcUtils.lookupFileExpiry;
import static com.hedera.services.fees.calculation.FeeCalcUtils.sumOfUsages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.merkle.map.MerkleMap;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class FeeCalcUtilsTest {
    private static final String ARTIFACTS_PREFIX_FILE_CONTENT = "f";
    private static final String ARTIFACTS_PREFIX_FILE_INFO = "k";
    private static final String LEDGER_PATH = "/{0}/";
    private static final EntityNum key = EntityNum.fromInt(1234);

    public static String pathOf(FileID fid) {
        return path(ARTIFACTS_PREFIX_FILE_CONTENT, fid);
    }

    public static String pathOfMeta(final FileID fid) {
        return path(ARTIFACTS_PREFIX_FILE_INFO, fid);
    }

    private static String path(final String buildMarker, final FileID fid) {
        return String.format(
                "%s%s%d",
                buildPath(LEDGER_PATH, "" + fid.getRealmNum()), buildMarker, fid.getFileNum());
    }

    public static String buildPath(final String path, final Object... params) {
        try {
            return MessageFormat.format(path, params);
        } catch (final MissingResourceException ignore) {
        }
        return path;
    }

    @Test
    void returnsAccountExpiryIfAvail() {
        // setup:
        final var account = mock(MerkleAccount.class);
        final MerkleMap<EntityNum, MerkleAccount> accounts = mock(MerkleMap.class);
        Timestamp expected = Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).build();

        given(account.getExpiry()).willReturn(Long.MAX_VALUE);
        given(accounts.get(key)).willReturn(account);

        assertEquals(expected, lookupAccountExpiry(key, accounts));
    }

    @Test
    void returnsZeroFileExpiryIfUnavail() {
        final var view = mock(StateView.class);
        final var fid = IdUtils.asFile("1.2.3");
        given(view.attrOf(fid)).willReturn(Optional.empty());

        assertEquals(ZERO_EXPIRY, lookupFileExpiry(fid, view));
    }

    @Test
    void returnsZeroAccountExpiryIfUnavail() {
        assertEquals(ZERO_EXPIRY, lookupAccountExpiry(null, null));
    }

    @Test
    void returnsFileExpiryIfAvail() throws Exception {
        final var view = mock(StateView.class);
        final var fid = IdUtils.asFile("1.2.3");
        final var wacl =
                JKey.mapKey(
                        Key.newBuilder()
                                .setEd25519(ByteString.copyFrom("YUUP".getBytes()))
                                .build());
        final var jInfo = new HFileMeta(false, wacl, Long.MAX_VALUE);
        final var expected = Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).build();
        given(view.attrOf(fid)).willReturn(Optional.of(jInfo));

        assertEquals(expected, lookupFileExpiry(fid, view));
    }

    @Test
    void constructsExpectedPath() {
        final var fid = IdUtils.asFile("1.2.3");
        final var expected =
                String.format(
                        "%s%s%d",
                        buildPath(LEDGER_PATH, "" + fid.getRealmNum()),
                        ARTIFACTS_PREFIX_FILE_CONTENT,
                        fid.getFileNum());

        assertEquals(expected, pathOf(fid));
    }

    @Test
    void constructsExpectedMetaPath() {
        final var fid = IdUtils.asFile("1.2.3");
        final var expected =
                String.format(
                        "%s%s%d",
                        buildPath(LEDGER_PATH, "" + fid.getRealmNum()),
                        ARTIFACTS_PREFIX_FILE_INFO,
                        fid.getFileNum());

        assertEquals(expected, pathOfMeta(fid));
    }

    @Test
    void sumsAsExpected() {
        final var aComp =
                FeeComponents.newBuilder()
                        .setMin(2)
                        .setMax(1_234_567)
                        .setConstant(1)
                        .setBpt(2)
                        .setVpt(3)
                        .setRbh(4)
                        .setSbh(5)
                        .setGas(6)
                        .setTv(7)
                        .setBpr(8)
                        .setSbpr(9);
        final var bComp =
                FeeComponents.newBuilder()
                        .setMin(1)
                        .setMax(1_234_566)
                        .setConstant(9)
                        .setBpt(8)
                        .setVpt(7)
                        .setRbh(6)
                        .setSbh(5)
                        .setGas(4)
                        .setTv(3)
                        .setBpr(2)
                        .setSbpr(1);
        final var a =
                FeeData.newBuilder()
                        .setNetworkdata(aComp)
                        .setNodedata(aComp)
                        .setServicedata(aComp)
                        .build();
        final var b =
                FeeData.newBuilder()
                        .setNetworkdata(bComp)
                        .setNodedata(bComp)
                        .setServicedata(bComp)
                        .build();

        final var c = sumOfUsages(a, b);
        final var scopedUsages =
                new FeeComponents[] {c.getNodedata(), c.getNetworkdata(), c.getServicedata()};

        for (FeeComponents scopedUsage : scopedUsages) {
            assertEquals(1, scopedUsage.getMin());
            assertEquals(1_234_567, scopedUsage.getMax());
            assertEquals(10, scopedUsage.getConstant());
            assertEquals(10, scopedUsage.getBpt());
            assertEquals(10, scopedUsage.getVpt());
            assertEquals(10, scopedUsage.getRbh());
            assertEquals(10, scopedUsage.getSbh());
            assertEquals(10, scopedUsage.getGas());
            assertEquals(10, scopedUsage.getTv());
            assertEquals(10, scopedUsage.getBpr());
            assertEquals(10, scopedUsage.getSbpr());
        }
    }

    @Test
    void clampedAddWorks() {
        long a = 100L;
        long b = Long.MAX_VALUE;
        assertEquals(Long.MAX_VALUE, clampedAdd(a, b));

        b = 100L;
        assertEquals(200L, clampedAdd(a, b));

        a = -100L;
        b = Long.MIN_VALUE;
        assertEquals(Long.MIN_VALUE, clampedAdd(a, b));
    }

    @Test
    void clampedMultiplicationWorks() {
        long a = 100L;
        long b = Long.MAX_VALUE;
        assertEquals(Long.MAX_VALUE, clampedMultiply(a, b));

        b = 100L;
        assertEquals(10000L, clampedMultiply(a, b));

        a = -100L;
        b = Long.MAX_VALUE;
        assertEquals(Long.MIN_VALUE, clampedMultiply(a, b));
    }
}
