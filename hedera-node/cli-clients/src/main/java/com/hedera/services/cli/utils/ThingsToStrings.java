/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.utils;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ThingsToStrings {

    /** Quotes a string to be a valid field in a CSV (comma-separated file), as defined in RFC-4180
     * (https://datatracker.ietf.org/doc/html/rfc4180) _except_ that we allow the field separator to
     * be something other than "," (e.g., ";", if we have a lot of fields that contain embedded ",").
     */
    @NonNull
    public static String quoteForCsv(@NonNull final String fieldSeparator, @Nullable String s) {
        if (s == null) s = "";
        s = s.replace("\"", "\"\""); // quote double-quotes
        if (Pattern.compile("[\"\r\n" + fieldSeparator + "]").matcher(s).find()) s = '"' + s + '"';
        return s;
    }

    @NonNull
    public static String squashLinesToEscapes(@Nullable String s) {
        if (s == null) return "";
        return s.replace("\r", "\\r").replace("\n", "\\n");
    }

    // All of these converters from something to a String will check to see if that something is a "null" or is
    // an "empty" (whatever "empty" might mean for that thing).  They return `true` if the thing _existed_, otherwise
    // (null or "empty") return false.  (Thus, they are predicates.)

    // P.S. I had started by naming nearly all of them simply `toString`.  (The couple of exceptions were the ones
    // taking a `Set` or `Map` which couldn't be named that way because of erasure: They'd collide.) But I soon had
    // to give them different names as it turns out that method references and overloading don't mix, with Java.

    private static final HexFormat hexer = HexFormat.of().withUpperCase();

    public static boolean toStringOfByteString(@NonNull final StringBuilder sb, @Nullable final ByteString bs) {
        if (bs == null || bs.size() == 0) return false;

        final var a = bs.toByteArray();
        hexer.formatHex(sb, a);
        return true;
    }

    @NonNull
    public static String toStringOfByteArray(@NonNull final byte[] bs) {
        if (bs.length == 0) return "";
        return hexer.formatHex(bs);
    }

    public static boolean toStringOfByteArray(@NonNull final StringBuilder sb, @Nullable final byte[] bs) {
        if (bs == null || bs.length == 0) return false;

        hexer.formatHex(sb, bs);
        return true;
    }

    public static boolean toStringOfIntArray(@NonNull final StringBuilder sb, @Nullable final int[] ints) {
        if (ints == null || ints.length == 0) return false;

        sb.append(Arrays.stream(ints).mapToObj(Integer::toString).collect(Collectors.joining(",", "(", ")")));
        return true;
    }

    public static void toStructureSummaryOfKey(@NonNull final StringBuilder sb, @NonNull final Key key) {
        switch (key.getKeyCase()) {
            case CONTRACTID -> sb.append("CID");
            case ED25519 -> sb.append("ED");
            case RSA_3072 -> sb.append("RSA-3072");
            case ECDSA_384 -> sb.append("ECDSA-384");
            case THRESHOLDKEY -> {
                final var tk = key.getThresholdKey();
                final var th = tk.getThreshold();
                final var kl = tk.getKeys();
                final var n = kl.getKeysCount();
                sb.append("TH[");
                sb.append(th);
                sb.append("-of-");
                sb.append(n);
                for (int i = 0; i < n; i++) {
                    sb.append(",");
                    toStructureSummaryOfKey(sb, kl.getKeys(i));
                }
                sb.append("]");
            }
            case KEYLIST -> {
                final var kl = key.getKeyList();
                final var n = kl.getKeysCount();
                sb.append("KL[#");
                sb.append(key.getKeyList().getKeysCount());
                for (int i = 0; i < n; i++) {
                    sb.append(",");
                    toStructureSummaryOfKey(sb, kl.getKeys(i));
                }
                sb.append("]");
            }
            case ECDSA_SECP256K1 -> sb.append("EC");
            case DELEGATABLE_CONTRACT_ID -> sb.append("dCID");
            case KEY_NOT_SET -> sb.append("MISSING-KEY");
        }
    }

    public enum FeeProfile {
        FULL,
        SKETCH
    }

    @NonNull
    public static String toStringOfRichInstant(@NonNull final RichInstant instant) {
        return "%d.%d".formatted(instant.getSeconds(), instant.getNanos());
    }

    public static boolean is7BitAscii(@NonNull final byte[] bs) {
        for (byte b : bs) if (b < 0) return false;
        return true;
    }

    @NonNull
    public static String to7BitAscii(@NonNull final byte[] bs) {
        return new String(bs, StandardCharsets.US_ASCII);
    }

    static CharsetDecoder toUTF8 = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);

    @NonNull
    public static Optional<String> maybeToUTF8(@NonNull final byte[] bs) {
        try {
            return Optional.of(toUTF8.decode(ByteBuffer.wrap(bs)).toString());
        } catch (final CharacterCodingException ignored) {
            return Optional.empty();
        }
    }

    @NonNull
    public static String toStringPossibleHumanReadableByteArray(
            @NonNull final String fieldSeparator, @NonNull final byte[] bs) {
        final var maybeUTF8 = maybeToUTF8(bs);
        return maybeUTF8.map(s -> quoteForCsv(fieldSeparator, s)).orElseGet(() -> toStringOfByteArray(bs));
    }

    @NonNull
    public static Function<byte[], String> getMaybeStringifyByteString(@NonNull final String fieldSeparator) {
        return bs -> toStringPossibleHumanReadableByteArray(fieldSeparator, bs);
    }

    private ThingsToStrings() {}
}
