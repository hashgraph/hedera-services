/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.yahcli.commands.contract.evminfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.hedera.services.yahcli.commands.contract.utils.SelectorSignatureMapping;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class SelectorSignatureMappingTest {

    static final String smallSelSigCompressedFileHex =
            "1f8b0808ce8417640003736f6d652d7369676e6174757265732e74787400ad54cb6ee23014ddf72bbc0c52172109096856105a4d35b4a584aa0c556599e492b82436b29dc9d0af9f3c887994aea6dedcc83af771ceb98e69d667806250778c2a4a52fa4114e5cce711dc0a9ef99c29414235cb99a21960d79f3c3f0446e7aac9345748829a0a0868cc242ee029310c124502a4bc3e8f3965caeab95fc6d54e81ecbcbe5daf384f3b3fdeefefc1379acbb65d88e23c5b2cfda59ea0db458cab84b278ce03809f20009b0edd8c1f07074c880aaa924890e24625207071932f8c7ddb1664db28232a4ce67c03ec51442046bb61945186378ef3dea25fdf34de45b220db31fc1dc6f12f806d59b73ff2bc6fe0af7b1d7de9aa35eabc66e712b2adab334b59ebcbbe4ed3640668b158e09c090879cce80744984464ab40185289525d8d2428e63cc21915820be398836db52087d4daec55c17c18886f95e5448bb6a96b566bdcf47c49a882944a35e59256fb8cb195db453b834ea93d4fb0e08a28302e4de03afba0730045546e53b2c3244d797131ebb3249e89b65caa8066f582dd72d13e2ddccf5ec6ed6427346b7a47743f2953195a3f96b6490f85848590d6fb2b3156bf1f8aff575e97f790c8e3699ea601a4eb9eeb7803d7b17afa9d79fdca81e645cc78ae2ef9b08c27feb90dde1a8de6f4eef9c930ce77f6d2c85dbb19d1318f83e59cee7633bd6de9d8eefc814fdf442167515e7a4072c5bf7252c32d44848275e5d9a65cb3e6dfd4ed5cadcb038eed55ec83c7c954d0108ce67added97a241c6ffc00c0a22a2d6f58306ff008862b1cc85050000";
    static final byte[] smallSelSigCompressedFile = HexFormat.of().parseHex(smallSelSigCompressedFileHex);
    static final String smallSelSigFile =
            """
        00000009 getInitializationCodeFromContractRuntime_6CLUNS()
        0000000b setPreSigns_weQh((address,address,address,uint256,uint256,uint256,uint256,bytes)[],bool);jMMeC(bytes)
        0000000c gumXZCZ()
        00000011 nothingToSeeHere_04ikDO9()
        0000001c withdrawEther_wEuX(uint256)
        00000033 matchTokenOrderByAdmin_k44j(uint256[])
        00000036 swapDexAggKeeper_8B77((address,address,address,uint256,uint256,uint256,uint256,bytes)[],uint256[],uint256[],(address,bytes,address,uint256),uint256[],(address,uint256,address,bool,uint8,uint256))
        00000039 XXX_unrecognized_adapter(string)
        0000003a good_mirror(uint256,uint32)
        0000004a swapKeeper__oASr((address,address,address,uint256,uint256,uint256,uint256,bytes)[],uint256[],address,bytes)
        00000060 getKeeperWhitelistPosition__2u3w(address)
        00000063 math_rotate(uint256,uint256,uint64,uint64)
        0000006e display_allow(uint256,uint256,uint256,uint32)
        00000070 postSimTokenForContract_8mWD(address[],uint256[],bytes[],address,uint256,uint256,bool,bool)
        00000075 cancelOrders__tYNw((address,address,address,uint256,uint256,uint256,uint256,bytes)[])
        00000077 rugPullSelf564796425()
        00000078 getDexAggRouterWhitelistPosition_ZgLC(address)
        0000007f BTiIUQ((uint256,address,address,address,uint136,uint40,uint40,uint24,uint8,uint256,bytes32,bytes32,uint256)[])
        00000080 conduct_auto(uint256,uint256,uint256)
        00000082 artefact_keep(bytes1)
        ffffe437 getSOLPrice()
        fffff187 removeRewardContract(address)
        """;

    @Test
    void readShortFileSuccessfullyTest() throws IOException {
        assertThat(smallSelSigCompressedFileHex).hasSize(1198);
        assertThat(smallSelSigCompressedFile).hasSize(599);

        String[] expected = smallSelSigFile.split(System.lineSeparator());
        String[] actual;
        try (final var inputStreamReader = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(smallSelSigCompressedFile))))) {
            actual = inputStreamReader.lines().toArray(String[]::new);
        }
        assertThat(actual).containsExactly(expected);
    }

    static class TestSelectorSignatureMapping extends SelectorSignatureMapping {
        @NotNull
        public static Pair<SelectorSignatureMapping, List<String>> readMappingFromTestFile(@NonNull final Path path)
                throws IOException {
            return readMapping(path, TestSelectorSignatureMapping::new);
        }

        @NonNull
        public long[] getSelectors() {
            return Arrays.stream(selectors).asLongStream().toArray();
        }

        @NonNull
        public int[] getLocators() {
            return Arrays.copyOf(signatureLocator, signatureLocator.length);
        }

        @NonNull
        public List<String> getSignaturesBlobs() {
            return signatures.stream().map(StringBuilder::toString).toList();
        }

        @NonNull
        public Optional<String> getSignatureFromEncodedLocator(final int locator) {
            return getSignature(Locator.from(locator));
        }

        public int getMaxSignatureBlobLength() {
            return STRING_HOLDER_LENGTH;
        }
    }

    @Test
    void actuallyReadFileAndReport() {
        final var filepath = "./yahcli/resources/all-4byte.directory-method-signatures-2023-02-09.txt.gz";
        class SUT {
            Pair<SelectorSignatureMapping, List<String>> sut;
            Duration duration;
        }
        final var sut = new SUT();
        assertThatNoException().isThrownBy(() -> {
            final var start = Instant.now();
            sut.sut = TestSelectorSignatureMapping.readMappingFromTestFile(Path.of(filepath));
            final var end = Instant.now();
            sut.duration = Duration.between(start, end);
        });

        assertThat(sut.sut.getLeft()).isNotNull();
        assertThat(sut.sut.getRight()).isNotNull().isEmpty();

        System.out.printf("Time to read selectors file: %fms%n", sut.duration.toMillis() / 1000.0f);

        final var mapping = (TestSelectorSignatureMapping) sut.sut.getLeft();

        assertThat(mapping.getCount()).isEqualTo(916173);

        // A sampling of a few known signatures:
        assertThat(mapping.getSignature(0x00000009L)).contains("getInitializationCodeFromContractRuntime_6CLUNS()");
        assertThat(mapping.getSignature(0x00000077L)).contains("rugPullSelf564796425()");
        assertThat(mapping.getSignature(0x37ea9348L)).contains("krill()");
        assertThat(mapping.getSignature(0x6fc3eaecL)).contains("manualsend()");
        assertThat(mapping.getSignature(0xFFFFE437L)).contains("getSOLPrice()");

        assertThat(mapping.getSelectors()).isNotNull().isSorted().doesNotHaveDuplicates();

        assertThat(mapping.getLocators())
                .isNotNull()
                .hasSize(mapping.getCount())
                .doesNotHaveDuplicates();

        assertThat(Arrays.stream(mapping.getLocators()).mapToObj(mapping::getSignatureFromEncodedLocator))
                .allMatch(Optional::isPresent);
        assertThat(Arrays.stream(mapping.getLocators())
                        .mapToObj(mapping::getSignatureFromEncodedLocator)
                        .map(Optional::orElseThrow)
                        .toList())
                .doesNotHaveDuplicates();

        assertThat(mapping.getSignaturesBlobs())
                .hasSizeLessThanOrEqualTo(50)
                .allMatch(s -> s.length() < mapping.getMaxSignatureBlobLength());
    }
}
