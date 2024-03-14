/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.bbm.accounts;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.bbm.DumpCheckpoint;
import com.hedera.node.app.bbm.utils.ThingsToStrings;
import com.hedera.node.app.bbm.utils.Writer;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

public class AccountDumpUtils {

    /** String that separates all fields in the CSV format, and also the primitive-typed fields from each other and
     * the other-typed fields from each other in the compressed format.
     */
    private static final String FIELD_SEPARATOR = ";";

    /** String that separates sub-fields (the primitive-type fields) in the compressed format. */
    private static final String SUBFIELD_SEPARATOR = ",";

    /** String that separates field names from field values in the compressed format */
    private static final String NAME_TO_VALUE_SEPARATOR = ":";

    /** A field joiner that joins _subfields_ with `,` (i.e., _not_ the CSV field separator) and wraps the entire
     * thing in parentheses. */
    private static final Collector<CharSequence, ?, String> parenWrappingJoiner =
            Collectors.joining(SUBFIELD_SEPARATOR, "(", ")");

    private AccountDumpUtils() {
        // Utility class
    }

    public static void dumpMonoAccounts(
            @NonNull final Path path,
            @NonNull final VirtualMap<EntityNumVirtualKey, OnDiskAccount> accounts,
            @NonNull final DumpCheckpoint checkpoint) {

        try (@NonNull final var writer = new Writer(path)) {
            HederaAccount[] dumpableAccounts = gatherAccounts(accounts, HederaAccount::fromMono);
            reportOnAccounts(writer, dumpableAccounts);
            System.out.printf(
                    "=== mod accounts report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    public static void dumpModAccounts(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            HederaAccount[] dumpableAccounts = gatherAccounts(accounts, HederaAccount::fromMod);
            reportOnAccounts(writer, dumpableAccounts);
            System.out.printf(
                    "=== mod accounts report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    public static <K extends VirtualKey, V extends VirtualValue> HederaAccount[] gatherAccounts(
            @NonNull VirtualMap<K, V> accounts, @NonNull Function<V, HederaAccount> mapper) {
        final var accountsToReturn = new ConcurrentLinkedQueue<HederaAccount>();
        final var threadCount = 8;
        final var processed = new AtomicInteger();

        try {
            VirtualMapLike.from(accounts)
                    .extractVirtualMapData(
                            getStaticThreadManager(),
                            p -> {
                                processed.incrementAndGet();
                                accountsToReturn.add(mapper.apply(p.right()));
                            },
                            threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of accounts virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        final var accountsArr = accountsToReturn.toArray(new HederaAccount[0]);
        Arrays.parallelSort(
                accountsArr, Comparator.comparingLong(a -> a.accountId().accountNum()));
        System.out.printf("=== %d accounts iterated over (%d saved)%n", processed.get(), accountsArr.length);

        return accountsArr;
    }

    private static void reportOnAccounts(@NonNull final Writer writer, @NonNull final HederaAccount[] accountsArr) {
        writer.write("account#");
        writer.write(FIELD_SEPARATOR);
        writer.write(formatCsvHeader(allFieldNamesInOrder()));
        writer.newLine();

        final var sb = new StringBuilder();
        Arrays.stream(accountsArr).map(a -> formatAccount(sb, a)).forEachOrdered(s -> {
            writer.write(s);
            writer.newLine();
        });
    }

    /** Produces the CSV header line: A CSV line from all the field names in the deterministic order. */
    @NonNull
    private static String formatCsvHeader(@NonNull final List<String> names) {
        return String.join(FIELD_SEPARATOR, names);
    }

    /** Returns the list of _all_ field names in the deterministic order, expanding the abbreviations to the full
     * field name.
     */
    @NonNull
    private static List<String> allFieldNamesInOrder() {
        final var r = new ArrayList<String>(50);
        r.addAll(getFieldNamesInOrder(booleanFieldsMapping));
        r.addAll(getFieldNamesInOrder(intFieldsMapping));
        r.addAll(getFieldNamesInOrder(longFieldsMapping));
        r.addAll(getFieldNamesInOrder(getFieldAccessors(new StringBuilder(), HederaAccount.DUMMY_ACCOUNT), false));
        return r.stream().map(s -> fieldNameMap.getOrDefault(s, s)).toList();
    }

    /** Given one of the primitive-type mappings above, extract the field names, and sort them */
    private static <T extends Comparable<T>> List<String> getFieldNamesInOrder(
            @NonNull List<Pair<String, Function<HederaAccount, T>>> mapping) {
        return mapping.stream().map(Pair::getLeft).sorted().toList();
    }

    /** Given the field mappings above, extract the field names, and sort them */
    // (Overload needed because of type erasure; ugly but seemed to me less ugly than an alternate name, YMMV)
    @SuppressWarnings("java:S1172") // "remove unused method parameter 'ignored'" - nope, needed as described aboved
    private static List<String> getFieldNamesInOrder(@NonNull final List<Field<?>> fields, final boolean ignored) {
        return fields.stream().map(Field::name).sorted().toList();
    }

    /** Formats an entire account as a text string.  First field of the string is the account number, followed by all
     * of its fields.
     */
    @NonNull
    private static String formatAccount(@NonNull final StringBuilder sb, @NonNull final HederaAccount a) {
        sb.setLength(0);
        sb.append(a.accountId().accountNum());
        formatAccountBooleans(sb, a, "bools");
        formatAccountInts(sb, a, "ints");
        formatAccountLongs(sb, a, "longs");
        formatAccountOtherFields(sb, a);
        return sb.toString();
    }

    /** Formats all the `boolean`-valued fields of an account, using the mapping `booleanFieldsMapping`. */
    private static void formatAccountBooleans(
            @NonNull final StringBuilder sb, @NonNull final HederaAccount a, @NonNull final String name) {
        formatAccountFieldsForDifferentOutputFormats(
                sb, a, name, booleanFieldsMapping, false, b -> !b, AccountDumpUtils::tagOnlyFieldFormatter);
    }

    /** A field formatter that only emits the _name_ of the field.  Used for boolean fields in compressed format. */
    @NonNull
    private static <T> String tagOnlyFieldFormatter(@NonNull final Pair<String, T> p) {
        return p.getLeft();
    }

    /** Formats all the `int`-valued fields of an account, using the mapping `intFieldsMapping`. */
    private static void formatAccountInts(
            @NonNull final StringBuilder sb, @NonNull final HederaAccount a, @NonNull final String name) {
        formatAccountFieldsForDifferentOutputFormats(
                sb, a, name, intFieldsMapping, 0, n -> n == 0, AccountDumpUtils::taggedFieldFormatter);
    }

    /** Formats all the `long`-valued fields of an account, using the mapping `longFieldsMapping`. */
    private static void formatAccountLongs(
            @NonNull final StringBuilder sb, @NonNull final HederaAccount a, @NonNull final String name) {
        formatAccountFieldsForDifferentOutputFormats(
                sb, a, name, longFieldsMapping, 0L, n -> n == 0L, AccountDumpUtils::taggedFieldFormatter);
    }

    /** Exceptions coming out of lambdas need to be swallowed.  This is ok because the cause is always a missing field
     * that should not have been accessed, and the check for that is always made by the caller: The caller sees if
     * anything got added to the accumulating stringbuffer, or not.
     */
    @NonNull
    private static <R> R applySwallowingExceptions(
            @NonNull final Function<HederaAccount, R> fn, @NonNull final HederaAccount a, @NonNull R missingValue) {
        try {
            return fn.apply(a);
        } catch (final RuntimeException ex) {
            return missingValue;
        }
    }

    /** Given a mapping from field names to both a field extraction function (extract from an account) and a field
     * formatter (type-specific), produce the formatted form of all the fields given in the mapping.  Can do either of
     * the `Format`s: CSV or compressed fields.
     */
    private static void formatAccountOtherFields(@NonNull final StringBuilder sb, @NonNull HederaAccount a) {
        final var fieldAccessors = getFieldAccessors(sb, a);
        for (final var fieldAccessor : fieldAccessors) {
            final var l = sb.length();
            formatFieldSep(sb, fieldAccessor.name());
            if (!fieldAccessor.apply()) {
                sb.setLength(l);
                applySwallowingExceptions(fieldAccessor);
            }
        }
    }

    private static <R> boolean applySwallowingExceptions(@NonNull final Field<R> field) {
        try {
            return field.apply();
        } catch (final RuntimeException ex) {
            return false;
        }
    }

    /** A mapping for all account fields that are _not_ of primitive type.  Takes the field name to a `Field`, which
     * holds the field name, the field extractor ,and the field formatter. And it _is_ a "mapping" even though it isn't
     * actually a `Map` data structure like the other mappings for primitive typed fields. */
    @SuppressWarnings({"java:S1452", "java:S2681"})
    // 1452: generic wildcard types should not be used in return types - yes, but this is a collection of `Field`s
    // of unrelated types, yet `Object` is not appropriate either
    // 2681: a complaint about no braces around `then` clause - yep, intentional, and correct
    // spotless:off
    private static List<Field<?>> getFieldAccessors(@NonNull final StringBuilder sb, @NonNull final HederaAccount a) {
        return Stream.of(
            Field.of("1stContractStorageKey", a::firstContractStorageKey,
                doWithBuilder(sb, ThingsToStrings::getMaybeStringifyByteString)),
            Field.of("accountKey", a::key, doWithBuilder(sb, ThingsToStrings::toStringOfKey)),
            Field.of("alias", a::alias, doWithBuilder(sb, ThingsToStrings::getMaybeStringifyByteString)),
            Field.of("approveForAllNfts", a::approveForAllNftAllowances,
                doWithBuilder(sb, ThingsToStrings::toStringOfApprovalForAllAllowances)),
            Field.of("autoRenewAccount", a::autoRenewAccountId,
                doWithBuilder(sb, ThingsToStrings::toStringOfAccountId)),
            Field.of("cryptoAllowances", a::cryptoAllowances,
                doWithBuilder(sb, ThingsToStrings::toStringOfAccountCryptoAllowances)),
            Field.of("firstUint256Key", a::getFirstUint256Key, doWithBuilder(sb, ThingsToStrings::toStringOfIntArray)),
            Field.of("fungibleTokenAllowances", a::tokenAllowances,
                doWithBuilder(sb, ThingsToStrings::toStringOfAccountFungibleTokenAllowances)),
            Field.of("headNftKey", a::getHeadNftKey, doWithBuilder(sb, ThingsToStrings::toStringOfEntityNumPair)),
            Field.of("latestAssociation", a::getLatestAssociation,
                doWithBuilder(sb, ThingsToStrings::toStringOfEntityNumPair)),
            Field.of("memo", a::memo, s -> {
                if (s.isEmpty()) {
                    return false;
                }
                sb.append(ThingsToStrings.quoteForCsv(FIELD_SEPARATOR, s));
                return true;
            }),
            Field.of("proxy", a::getProxy, doWithBuilder(sb, ThingsToStrings::toStringOfAccountId))
        ).sorted(Comparator.comparing(Field::name)).toList();
    }
    // spotless:on

    /** Apply a formatter, given a `StringBuilder` and return whether (or not) the field _existed_ and should be
     * emitted. */
    private static <T> Predicate<T> doWithBuilder(
            @NonNull final StringBuilder sb, @NonNull final BiPredicate<StringBuilder, T> bifn) {
        return t -> bifn.test(sb, t);
    }

    record Field<T>(@NonNull String name, @NonNull Supplier<T> supplier, @NonNull Predicate<T> formatter) {

        static <U> Field<U> of(
                @NonNull final String name,
                @NonNull final Supplier<U> supplier,
                @NonNull final Predicate<U> formatter) {
            return new Field<>(name, supplier, formatter);
        }

        /** Convenience method to extract the field from the account then apply the formatter to it. */
        boolean apply() {
            return formatter.test(supplier.get());
        }
    }

    /** A mapping for all `int`-valued fields that takes the field name to the field extractor. */
    private static final List<Pair<String, Function<HederaAccount, Integer>>> intFieldsMapping = List.of(
            Pair.of("#+B", HederaAccount::numberPositiveBalances),
            Pair.of("#A", HederaAccount::numberAssociations),
            Pair.of("#KV", HederaAccount::contractKvPairsNumber),
            Pair.of("#TT", HederaAccount::numberTreasuryTitles),
            Pair.of("#UAA", HederaAccount::usedAutoAssociations),
            Pair.of("^AA", HederaAccount::maxAutoAssociations));

    /** A mapping for all `boolean`-valued fields that takes the field name to the field extractor. */
    private static final List<Pair<String, Function<HederaAccount, Boolean>>> booleanFieldsMapping = List.of(
            Pair.of("AR", h -> h.autoRenewAccountId() != null),
            Pair.of("BR", a -> a.stakeAtStartOfLastRewardedPeriod() != -1L),
            Pair.of("DL", HederaAccount::deleted),
            Pair.of("DR", HederaAccount::declineReward),
            Pair.of("ER", HederaAccount::expiredAndPendingRemoval),
            Pair.of("HA", a -> a.alias() != null),
            Pair.of("IM", HederaAccount::isImmutable),
            Pair.of("PR", a -> (int) a.stakedId().value() < 0 && !a.declineReward()),
            Pair.of("RSR", HederaAccount::receiverSigRequired),
            Pair.of("SC", HederaAccount::smartContract),
            Pair.of("TT", a -> a.numberTreasuryTitles() > 0));

    /** A mapping for all `long`-valued fields that takes the field name to the field extractor. */
    private static final List<Pair<String, Function<HederaAccount, Long>>> longFieldsMapping = List.of(
            Pair.of("#NFT", HederaAccount::numberOwnedNfts),
            Pair.of("ARS", HederaAccount::autoRenewSeconds),
            Pair.of("B", a -> (long) a.numberPositiveBalances()),
            Pair.of("EX", HederaAccount::expirationSecond),
            Pair.of("HNSN", a -> a.headNftId().serialNumber()),
            Pair.of("HNTN", a -> a.headNftId().tokenId().tokenNum()),
            Pair.of("HTI", a -> a.headTokenId().tokenNum()),
            Pair.of("N", HederaAccount::ethereumNonce),
            Pair.of("SID", a -> (long) a.stakedId().value()),
            Pair.of("SNID", HederaAccount::stakedNodeAddressBookId),
            Pair.of("SPS", coerceMinus1ToBeDefault(HederaAccount::stakePeriodStart)),
            Pair.of("STM", HederaAccount::stakedToMe),
            Pair.of("TS", HederaAccount::totalStake),
            Pair.of("TSL", coerceMinus1ToBeDefault(HederaAccount::stakeAtStartOfLastRewardedPeriod)));

    /** For the compressed field output we want to have field name abbreviations (for compactness), but for the CSV
     * output we can afford the full names.  This maps between them.  (Only the primitive-valued fields have the
     * abbreviations.)
     */
    private static final Map<String, String> fieldNameMap = toMap(
            "#+B", "numPositiveBalances",
            "#A", "numAssociations",
            "#KV", "numContractKvPairs",
            "#NFT", "numNftsOwned",
            "#TT", "numTreasuryTitles",
            "#UAA", "numUsedAutoAssociations",
            "AR", "hasAutoRenewAccount",
            "ARS", "autoRenewSecs",
            "B", "balance",
            "BR", "hasBeenRewardedSinceLastStakeMetaChange",
            "DL", "deleted",
            "DR", "declinedReward",
            "ER", "expiredAndPendingRemoval",
            "EX", "expiry",
            "HA", "hasAlias",
            "HNSN", "headNftSerialNum",
            "HNTN", "headNftTokenNum",
            "HTI", "headTokenId",
            "IM", "immutable",
            "N", "ethereumNonce",
            "PR", "mayHavePendingReward",
            "RSR", "receiverSigRequired",
            "SC", "smartContract",
            "SID", "stakedId",
            "SNID", "stakedNodeAddressBookId",
            "SPS", "stakePeriodStart",
            "STM", "stakedToMe",
            "TS", "totalStake",
            "TSL", "totalStakeAtStartOfLastRewardedPeriod",
            "TT", "tokenTreasury",
            "^AA", "maxAutomaticAssociations");

    /** `Map.of` only has 11 overloads - for up to 10 entries.  After that there's a variadic `Map.ofEntries` which is
     * klunky because it takes `Map.Entry`s.  So this is the variadic form of `Map.of`.  Not sure why the Java people
     * didn't just put this in the `Map` class.
     */
    @NonNull
    private static Map<String, String> toMap(String... es) {
        if (0 != es.length % 2) {
            throw new IllegalArgumentException(
                    "must have even number of args to `toMap`, %d given".formatted(es.length));
        }
        final var r = new TreeMap<String, String>();
        for (int i = 0; i < es.length; i += 2) {
            r.put(es[i], es[i + 1]);
        }
        return r;
    }

    /** Given a mapping from field names (or abbreviations) to a field extraction function (extract from an account)
     * produce the formatted form of all the fields given in the mapping.  Can do either of the `Format`s: CSV or
     * compressed fields.
     * @param sb Accumulating `StringBuffer`
     * @param a Account to get field from
     * @param mapping Mapping of field name (or abbreviation) to its extraction method
     * @param isDefaultValue Predicate to decide if this field has its default value (and can be elided)
     * @param formatField Method taking field name _and_ value to a string
     */
    private static <T extends Comparable<T>> void formatAccountFieldsForDifferentOutputFormats(
            @NonNull final StringBuilder sb,
            @NonNull final HederaAccount a,
            @NonNull final String name,
            @NonNull List<Pair<String, Function<HederaAccount, T>>> mapping,
            @NonNull T missingValue,
            @NonNull Predicate<T> isDefaultValue,
            @NonNull Function<Pair<String, T>, String> formatField) {
        final var l = sb.length();

        formatFieldSep(sb, name);
        formatAccountFields(sb, a, mapping, missingValue, isDefaultValue, formatField, parenWrappingJoiner);

        if (sb.length() - l <= 0) {
            sb.setLength(l);
        }
    }

    /** Given a mapping from field names (or abbreviations) to a field extraction function (extract from an account)
     * produce the formatted form of all the fields given in the mapping. Takes some additional function arguments
     * that "customize" the formatting of this field
     * @param sb Accumulating `StringBuffer`
     * @param a Account to get field from
     * @param mapping Mapping of field name (or abbreviation) to its extraction method
     * @param isDefaultValue Predicate to decide if this field has its default value (and can be elided)
     * @param formatField Method taking field name _and_ value to a string
     * @param joinFields Stream collector to join multiple field values
     */
    private static <T extends Comparable<T>> void formatAccountFields(
            @NonNull final StringBuilder sb,
            @NonNull final HederaAccount a,
            @NonNull List<Pair<String, Function<HederaAccount, T>>> mapping,
            @NonNull T missingValue,
            @NonNull Predicate<T> isDefaultValue,
            @NonNull Function<Pair<String, T>, String> formatField,
            @NonNull Collector<CharSequence, ?, String> joinFields) {
        sb.append(mapping.stream()
                .map(p -> Pair.of(p.getLeft(), applySwallowingExceptions(p.getRight(), a, missingValue)))
                .filter(p -> p.getRight() != null && !isDefaultValue.test(p.getRight()))
                .sorted(Comparator.comparing(Pair::getLeft))
                .map(formatField)
                .collect(joinFields));
    }

    /** A simple formatter for field names in the compressed fields case: writes the field separator then `{name}:` */
    private static void formatFieldSep(@NonNull final StringBuilder sb, @NonNull final String name) {
        sb.append(FIELD_SEPARATOR);
        sb.append(name);
        sb.append(NAME_TO_VALUE_SEPARATOR);
    }

    /** A Field formatter that emits fields as "name:value". Used for non-boolean fields in compressed format. */
    @NonNull
    private static <T> String taggedFieldFormatter(@NonNull final Pair<String, T> p) {
        return p.getLeft() + NAME_TO_VALUE_SEPARATOR + p.getRight();
    }

    /** Unfortunately this is a hack to handle the two long-valued fields where `-1` is used as the "missing" marker.
     * Probably all the primitive-valued fields should be changed to use `Field` descriptors, which would then be
     * enhanced to have a per-field "is default value?" predicate.  But not now.)
     */
    @SuppressWarnings(
            "java:S4276") // Functional interfaces should be as specialized as possible - except not in this case, for
    // consistency
    @NonNull
    private static Function<HederaAccount, Long> coerceMinus1ToBeDefault(
            @NonNull final Function<HederaAccount, Long> fn) {
        return a -> {
            final var v = fn.apply(a);
            return v == -1 ? 0 : v;
        };
    }
}
