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

package com.hedera.node.app.bbm.utils;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.state.submerkle.FixedFeeSpec;
import com.hedera.node.app.service.mono.state.submerkle.FractionalFeeSpec;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.submerkle.RoyaltyFeeSpec;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.crypto.CryptographyHolder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ThingsToStrings {

    private ThingsToStrings() {
        // Utility class
    }

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

    public static boolean toStringOfEntityNumPair(
            @NonNull final StringBuilder sb, @Nullable final EntityNumPair entityNumPair) {
        if (entityNumPair == null || entityNumPair.equals(EntityNumPair.MISSING_NUM_PAIR)) return false;

        sb.append("(");
        sb.append(entityNumPair.getHiOrderAsLong());
        sb.append(",");
        sb.append(entityNumPair.getLowOrderAsLong());
        sb.append(")");
        return true;
    }

    public static boolean toStringOfEntityNum(@NonNull final StringBuilder sb, @Nullable final EntityNum entityNum) {
        if (entityNum == null || entityNum.equals(EntityNum.MISSING_NUM)) return false;

        sb.append(entityNum.longValue());
        return true;
    }

    public static String toStringOfEntityId(@NonNull EntityId entityId) {
        if (entityId.equals(EntityId.MISSING_ENTITY_ID)) return "";
        return entityId.toAbbrevString();
    }

    public static boolean toStringOfAccountId(@NonNull final StringBuilder sb, @Nullable AccountID accountID) {
        if (accountID == null) {
            return false;
        }

        sb.append(String.format(
                "%d.%d.%s",
                accountID.shardNum(), accountID.realmNum(), accountID.account().value()));
        return true;
    }

    public static boolean toStringOfTokenId(StringBuilder sb, TokenID tokenID) {
        if (tokenID == null) {
            return false;
        }

        sb.append(String.format("%d.%d.%s", tokenID.shardNum(), tokenID.realmNum(), tokenID.tokenNum()));
        return true;
    }

    public static boolean toStringOfEntityId(@NonNull final StringBuilder sb, @Nullable final EntityId entityId) {
        if (entityId == null || entityId.equals(EntityId.MISSING_ENTITY_ID)) return false;

        sb.append(entityId.toAbbrevString());
        return true;
    }

    public static boolean toStringOfIntArray(@NonNull final StringBuilder sb, @Nullable final int[] ints) {
        if (ints == null || ints.length == 0) return false;

        sb.append(Arrays.stream(ints).mapToObj(Integer::toString).collect(Collectors.joining(",", "(", ")")));
        return true;
    }

    public static boolean toStructureSummaryOfJKey(@NonNull final StringBuilder sb, @Nullable final JKey jkey) {
        if (jkey == null || jkey.isEmpty()) return false;
        try {
            final var key = JKey.mapJKey(jkey);
            if (null == key) return false; // This is some kind of _invalid_ key; should it say so somehow?
            sb.append("Key[");
            toStructureSummaryOfKey(sb, key);
            sb.append("]");
        } catch (InvalidKeyException unknown) {
            sb.append("<invalid-key>");
        }
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

    /** Writes a cryptographic hash of the actual key */
    @SuppressWarnings(
            "java:S5738") // 'deprecated' code marked for removal - it's practically impossible to use the platform sdk
    // these days w/o running into deprecated methods
    @NonNull
    public static String toStringOfJKey(@NonNull final JKey jkey) {
        if (jkey.isEmpty()) return "";
        try {
            final var ser = jkey.serialize();
            final var hash = CryptographyHolder.get().digestSync(ser).getValue();
            return toStringOfByteArray(hash);

        } catch (final IOException ex) {
            return "**EXCEPTION SERIALIZING JKEY**";
        }
    }

    /** Writes a cryptographic hash of the actual key */
    @SuppressWarnings(
            "java:S5738") // 'deprecated' code marked for removal - it's practically impossible to use the platform sdk
    // these days w/o running into deprecated methods
    public static boolean toStringOfJKey(@NonNull final StringBuilder sb, @Nullable final JKey jkey) {
        if (jkey == null || jkey.isEmpty()) return false;

        try {
            final var ser = jkey.serialize();
            final var hash = CryptographyHolder.get().digestSync(ser).getValue();
            toStringOfByteArray(sb, hash);
        } catch (final IOException ex) {
            sb.append("**EXCEPTION SERIALIZING JKEY**");
        }
        return true;
    }

    public static boolean toStringOfContractKey(@NonNull final StringBuilder sb, @Nullable final ContractKey ckey) {
        if (ckey == null) return false;

        sb.append("(");
        sb.append(ckey.getContractId());
        sb.append(",");
        sb.append(ckey.getKeyAsBigInteger());
        sb.append(")");
        return true;
    }

    public static boolean toStringOfKey(
            @NonNull final StringBuilder sb, @Nullable final com.hedera.hapi.node.base.Key key) {
        if (key == null) {
            return false;
        }
        try {
            return toStringOfJKey(sb, JKey.convertKey(key, 15 /*JKey.MAX_KEY_DEPTH*/));
        } catch (final InvalidKeyException ignored) {
            sb.append("<INVALID KEY>");
            return true;
        }
    }

    public static boolean toStringOfFcTokenAllowanceId(
            @NonNull final StringBuilder sb, @Nullable final FcTokenAllowanceId id) {
        if (id == null) return false;

        var r = true;
        sb.append("(");
        r &= toStringOfEntityNum(sb, id.getTokenNum());
        sb.append(",");
        r &= toStringOfEntityNum(sb, id.getSpenderNum());
        sb.append(")");
        return r;
    }

    public static boolean toStringOfFcTokenAllowanceIdSet(
            @NonNull final StringBuilder sb, @Nullable final Set<FcTokenAllowanceId> ids) {
        if (ids == null || ids.isEmpty()) return false;

        final var orderedIds = ids.stream().sorted().toList();
        sb.append("(");
        for (final var id : orderedIds) {
            toStringOfFcTokenAllowanceId(sb, id);
            sb.append(",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        return true;
    }

    public static boolean toStringOfApprovalForAllAllowances(
            @NonNull final StringBuilder sb, @Nullable List<AccountApprovalForAllAllowance> approvals) {
        if (approvals == null || approvals.isEmpty()) {
            return false;
        }

        final var orderedApprovals = approvals.stream().sorted().toList();
        sb.append("(");
        for (final var approval : orderedApprovals) {
            toStringOfApprovalForAllAllowance(sb, approval);
            sb.append(",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        return true;
    }

    private static void toStringOfApprovalForAllAllowance(StringBuilder sb, AccountApprovalForAllAllowance approval) {
        if (approval == null) {
            return;
        }

        sb.append("(");
        toStringOfTokenId(sb, approval.tokenId());
        sb.append(",");
        toStringOfAccountId(sb, approval.spenderId());
        sb.append(")");
    }

    public static boolean toStringOfAccountCryptoAllowances(
            @NonNull final StringBuilder sb, @Nullable List<AccountCryptoAllowance> allowances) {
        if (allowances == null || allowances.isEmpty()) {
            return false;
        }

        final var orderedAllowances = allowances.stream().sorted().toList();
        sb.append("(");
        for (final var allowance : orderedAllowances) {
            toStringOfAccountCryptoAllowance(sb, allowance);
            sb.append(",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        return true;
    }

    private static void toStringOfAccountCryptoAllowance(StringBuilder sb, AccountCryptoAllowance allowance) {
        if (allowance == null) {
            return;
        }

        sb.append("(");
        toStringOfAccountId(sb, allowance.spenderId());
        sb.append(",");
        sb.append(allowance.amount());
        sb.append(")");
    }

    public static boolean toStringOfAccountFungibleTokenAllowances(
            @NonNull final StringBuilder sb, @Nullable List<AccountFungibleTokenAllowance> allowances) {
        if (allowances == null || allowances.isEmpty()) {
            return false;
        }

        final var orderedAllowances = allowances.stream().sorted().toList();
        sb.append("(");
        for (final var allowance : orderedAllowances) {
            toStringOfAccountFungibleTokenAllowance(sb, allowance);
            sb.append(",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        return true;
    }

    private static void toStringOfAccountFungibleTokenAllowance(
            StringBuilder sb, AccountFungibleTokenAllowance allowance) {
        if (allowance == null) {
            return;
        }

        sb.append("(");
        toStringOfTokenId(sb, allowance.tokenId());
        sb.append(",");
        toStringOfAccountId(sb, allowance.spenderId());
        sb.append(",");
        sb.append(allowance.amount());
        sb.append(")");
    }

    public static boolean toStringOfMapEnLong(
            @NonNull final StringBuilder sb, @Nullable final Map<EntityNum, Long> map) {
        if (map == null || map.isEmpty()) return false;

        final var orderedEntries = new TreeMap<>(map);
        sb.append("(");
        for (final var kv : orderedEntries.entrySet()) {
            toStringOfEntityNum(sb, kv.getKey());
            sb.append("->");
            sb.append(kv.getValue());
            sb.append(",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        return true;
    }

    public static boolean toStringOfMapFcLong(
            @NonNull final StringBuilder sb, @Nullable final Map<FcTokenAllowanceId, Long> map) {
        if (map == null || map.isEmpty()) return false;

        final var orderedEntries = new TreeMap<>(map);
        sb.append("(");
        for (final var kv : orderedEntries.entrySet()) {
            toStringOfFcTokenAllowanceId(sb, kv.getKey());
            sb.append("->");
            sb.append(kv.getValue());
            sb.append(",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        return true;
    }

    public enum FeeProfile {
        FULL,
        SKETCH
    }

    @NonNull
    public static String toStringOfFcCustomFee(@NonNull final FcCustomFee fee) {
        return toStringOfFcCustomFee(fee, FeeProfile.FULL);
    }

    @NonNull
    public static String toSketchyStringOfFcCustomFee(@NonNull final FcCustomFee fee) {
        return toStringOfFcCustomFee(fee, FeeProfile.SKETCH);
    }

    @NonNull
    public static String toStringOfFcCustomFee(@NonNull final FcCustomFee fee, @NonNull final FeeProfile profile) {
        final EntityId feeCollector = fee.getFeeCollector();
        final boolean allCollectorsAreExempt = fee.getAllCollectorsAreExempt();
        final String actualFee =
                switch (fee.getFeeType()) {
                    case FIXED_FEE -> toStringOfFixedFee(fee.getFixedFeeSpec(), profile);
                    case FRACTIONAL_FEE -> toStringOfFractionalFee(fee.getFractionalFeeSpec(), profile);
                    case ROYALTY_FEE -> toStringOfRoyaltyFee(fee.getRoyaltyFeeSpec(), profile);
                };
        return switch (profile) {
            case FULL -> "Fee[%s,%s%s]"
                    .formatted(
                            actualFee, toStringOfEntityId(feeCollector), allCollectorsAreExempt ? ",ALL-EXEMPT" : "");
            case SKETCH -> "Fee[%s%s]".formatted(actualFee, allCollectorsAreExempt ? ",ALL-EXEMPT" : "");
        };
    }

    @NonNull
    public static String toStringOfFixedFee(@NonNull final FixedFeeSpec fee, @NonNull final FeeProfile profile) {
        final long unitsToCollect = fee.getUnitsToCollect();
        final EntityId tokenDenomination = fee.getTokenDenomination();
        final boolean usedDenomWildcard = fee.usedDenomWildcard();

        return switch (profile) {
            case FULL -> "Fixed[%d,%s%s]"
                    .formatted(
                            unitsToCollect,
                            tokenDenomination != null ? toStringOfEntityId(tokenDenomination) : "NO-TOKEN-DENOMINATION",
                            usedDenomWildcard ? ",*" : "");
            case SKETCH -> "FIX[%s%s]"
                    .formatted(null == tokenDenomination ? "NO-TOKEN-DENOMINATION" : "", usedDenomWildcard ? "*" : "");
        };
    }

    @NonNull
    public static String toStringOfFractionalFee(
            @NonNull final FractionalFeeSpec fee, @NonNull final FeeProfile profile) {
        final long numerator = fee.getNumerator();
        final long denominator = fee.getDenominator();
        final long minimumUnitsToCollect = fee.getMinimumAmount();
        final long maximumUnitsToCollect = fee.getMaximumUnitsToCollect();
        final boolean isNetOfTransfers = fee.isNetOfTransfers();

        return switch (profile) {
            case FULL -> "Fractional[%d/%d (min %d max %d)%s]"
                    .formatted(
                            numerator,
                            denominator,
                            minimumUnitsToCollect,
                            maximumUnitsToCollect,
                            isNetOfTransfers ? ", NET" : "");
            case SKETCH -> "FRAC[%s]".formatted(isNetOfTransfers ? "NET" : "");
        };
    }

    @NonNull
    public static String toStringOfRoyaltyFee(@NonNull final RoyaltyFeeSpec fee, @NonNull final FeeProfile profile) {
        final long numerator = fee.numerator();
        final long denominator = fee.denominator();
        final FixedFeeSpec fallbackFee = fee.fallbackFee();

        return switch (profile) {
            case FULL -> "Royalty[%d/%d%s]"
                    .formatted(
                            numerator,
                            denominator,
                            fee.hasFallbackFee() ? "," + toStringOfFixedFee(fallbackFee, profile) : "");
            case SKETCH -> fee.hasFallbackFee()
                    ? "ROYAL+FALLBACK[%s]".formatted(toStringOfFixedFee(fallbackFee, FeeProfile.SKETCH))
                    : "ROYAL[]";
        };
    }

    @NonNull
    public static String toStringOfRichInstant(@NonNull final RichInstant instant) {
        return "%d.%d".formatted(instant.getSeconds(), instant.getNanos());
    }

    public static String toStringOfTimestamp(@NonNull final Timestamp timestamp) {
        return "%d.%d".formatted(timestamp.seconds(), timestamp.nanos());
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

    public static boolean getMaybeStringifyByteString(@NonNull final StringBuilder sb, @Nullable final Bytes bytes) {
        if (bytes == null) {
            return false;
        }
        sb.append(toStringPossibleHumanReadableByteArray(";", bytes.toByteArray()));
        return true;
    }
}
