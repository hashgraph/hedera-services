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

package com.hedera.node.app.statedumpers.accounts;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.statedumpers.DumpCheckpoint;
import com.hedera.node.app.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
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
    public static final String FIELD_SEPARATOR = ";";

    /** String that separates sub-fields (the primitive-type fields) in the compressed format. */
    public static final String SUBFIELD_SEPARATOR = ",";

    /** String that separates field names from field values in the compressed format */
    public static final String NAME_TO_VALUE_SEPARATOR = ":";

    /** A field joiner that joins _subfields_ with `,` (i.e., _not_ the CSV field separator) and wraps the entire
     * thing in parentheses. */
    public static final Collector<CharSequence, ?, String> parenWrappingJoiner =
            Collectors.joining(SUBFIELD_SEPARATOR, "(", ")");

    private AccountDumpUtils() {
        // Utility class
    }

    public static void dumpModAccounts(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            BBMHederaAccount[] dumpableAccounts = gatherAccounts(accounts);
            reportOnAccounts(writer, dumpableAccounts);
            System.out.printf(
                    "=== mod accounts report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    public static BBMHederaAccount[] gatherAccounts(
            @NonNull VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts) {
        final var accountsToReturn = new ConcurrentLinkedQueue<BBMHederaAccount>();
        final var threadCount = 8;
        final var processed = new AtomicInteger();

        try {
            VirtualMapMigration.extractVirtualMapData(
                    getStaticThreadManager(),
                    accounts,
                    p -> {
                        processed.incrementAndGet();
                        accountsToReturn.add(fromMod(p.right()));
                    },
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of accounts virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        final var accountsArr = accountsToReturn.toArray(new BBMHederaAccount[0]);
        Arrays.parallelSort(
                accountsArr, Comparator.comparingLong(a -> a.accountId().accountNum()));
        System.out.printf("=== %d accounts iterated over (%d saved)%n", processed.get(), accountsArr.length);

        return accountsArr;
    }

    public static BBMHederaAccount fromMod(OnDiskValue<Account> account) {
        return new BBMHederaAccount(
                account.getValue().accountId(),
                account.getValue().alias(),
                account.getValue().key(),
                account.getValue().expirationSecond(),
                account.getValue().tinybarBalance(),
                account.getValue().memo(),
                account.getValue().deleted(),
                account.getValue().stakedToMe(),
                account.getValue().stakePeriodStart(),
                account.getValue().stakedId(),
                account.getValue().declineReward(),
                account.getValue().receiverSigRequired(),
                account.getValue().headTokenId(),
                account.getValue().headNftId(),
                account.getValue().headNftSerialNumber(),
                account.getValue().numberOwnedNfts(),
                account.getValue().maxAutoAssociations(),
                account.getValue().usedAutoAssociations(),
                account.getValue().numberAssociations(),
                account.getValue().smartContract(),
                account.getValue().numberPositiveBalances(),
                account.getValue().ethereumNonce(),
                account.getValue().stakeAtStartOfLastRewardedPeriod(),
                account.getValue().autoRenewAccountId(),
                account.getValue().autoRenewSeconds(),
                account.getValue().contractKvPairsNumber(),
                account.getValue().cryptoAllowances(),
                account.getValue().approveForAllNftAllowances(),
                account.getValue().tokenAllowances(),
                account.getValue().numberTreasuryTitles(),
                account.getValue().expiredAndPendingRemoval(),
                account.getValue().firstContractStorageKey(),
                account.isImmutable(),
                account.getValue().hasStakedNodeId() ? account.getValue().stakedNodeId() : -1);
    }

    public static void reportOnAccounts(@NonNull final Writer writer, @NonNull final BBMHederaAccount[] accountsArr) {
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

    @NonNull
    public static String formatCsvHeader(@NonNull final List<String> names) {
        return String.join(FIELD_SEPARATOR, names);
    }

    /** Returns the list of _all_ field names in the deterministic order, expanding the abbreviations to the full
     * field name.
     */
    @NonNull
    public static List<String> allFieldNamesInOrder() {
        final var r = new ArrayList<String>(50);
        r.addAll(getFieldNamesInOrder(booleanFieldsMapping));
        r.addAll(getFieldNamesInOrder(intFieldsMapping));
        r.addAll(getFieldNamesInOrder(longFieldsMapping));
        r.addAll(getFieldNamesInOrder(getFieldAccessors(new StringBuilder(), BBMHederaAccount.DUMMY_ACCOUNT), false));
        return r.stream().map(s -> fieldNameMap.getOrDefault(s, s)).toList();
    }

    /** Given one of the primitive-type mappings above, extract the field names, and sort them */
    public static <T extends Comparable<T>> List<String> getFieldNamesInOrder(
            @NonNull List<Pair<String, Function<BBMHederaAccount, T>>> mapping) {
        return mapping.stream().map(Pair::getLeft).sorted().toList();
    }

    /** Given the field mappings above, extract the field names, and sort them */
    // (Overload needed because of type erasure; ugly but seemed to me less ugly than an alternate name, YMMV)
    @SuppressWarnings("java:S1172") // "remove unused method parameter 'ignored'" - nope, needed as described aboved
    public static List<String> getFieldNamesInOrder(@NonNull final List<Field<?>> fields, final boolean ignored) {
        return fields.stream().map(Field::name).sorted().toList();
    }

    /** Formats an entire account as a text string.  First field of the string is the account number, followed by all
     * of its fields.
     */
    @NonNull
    public static String formatAccount(@NonNull final StringBuilder sb, @NonNull final BBMHederaAccount a) {
        sb.setLength(0);
        sb.append(a.accountId().accountNum());
        formatAccountBooleans(sb, a, "bools");
        formatAccountInts(sb, a, "ints");
        formatAccountLongs(sb, a, "longs");
        formatAccountOtherFields(sb, a);
        return sb.toString();
    }

    /** Formats all the `boolean`-valued fields of an account, using the mapping `booleanFieldsMapping`. */
    public static void formatAccountBooleans(
            @NonNull final StringBuilder sb, @NonNull final BBMHederaAccount a, @NonNull final String name) {
        formatAccountFieldsForDifferentOutputFormats(
                sb, a, name, booleanFieldsMapping, false, b -> !b, AccountDumpUtils::tagOnlyFieldFormatter);
    }

    /** A field formatter that only emits the _name_ of the field.  Used for boolean fields in compressed format. */
    @NonNull
    public static <T> String tagOnlyFieldFormatter(@NonNull final Pair<String, T> p) {
        return p.getLeft();
    }

    /** Formats all the `int`-valued fields of an account, using the mapping `intFieldsMapping`. */
    public static void formatAccountInts(
            @NonNull final StringBuilder sb, @NonNull final BBMHederaAccount a, @NonNull final String name) {
        formatAccountFieldsForDifferentOutputFormats(
                sb, a, name, intFieldsMapping, 0, n -> n == 0, AccountDumpUtils::taggedFieldFormatter);
    }

    /** Formats all the `long`-valued fields of an account, using the mapping `longFieldsMapping`. */
    public static void formatAccountLongs(
            @NonNull final StringBuilder sb, @NonNull final BBMHederaAccount a, @NonNull final String name) {
        formatAccountFieldsForDifferentOutputFormats(
                sb, a, name, longFieldsMapping, 0L, n -> n == 0L, AccountDumpUtils::taggedFieldFormatter);
    }

    /** Exceptions coming out of lambdas need to be swallowed.  This is ok because the cause is always a missing field
     * that should not have been accessed, and the check for that is always made by the caller: The caller sees if
     * anything got added to the accumulating stringbuffer, or not.
     */
    @NonNull
    public static <R> R applySwallowingExceptions(
            @NonNull final Function<BBMHederaAccount, R> fn,
            @NonNull final BBMHederaAccount a,
            @NonNull R missingValue) {
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
    public static void formatAccountOtherFields(@NonNull final StringBuilder sb, @NonNull BBMHederaAccount a) {
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

    public static <R> boolean applySwallowingExceptions(@NonNull final AccountDumpUtils.Field<R> field) {
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
    public static List<Field<?>> getFieldAccessors(@NonNull final StringBuilder sb, @NonNull final BBMHederaAccount a) {
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
                AccountDumpUtils.Field.of("proxy", a::getProxy, doWithBuilder(sb, ThingsToStrings::toStringOfAccountId))
        ).sorted(Comparator.comparing(Field::name)).toList();
    }
    // spotless:on

    /** Apply a formatter, given a `StringBuilder` and return whether (or not) the field _existed_ and should be
     * emitted. */
    public static <T> Predicate<T> doWithBuilder(
            @NonNull final StringBuilder sb, @NonNull final BiPredicate<StringBuilder, T> bifn) {
        return t -> bifn.test(sb, t);
    }

    record Field<T>(@NonNull String name, @NonNull Supplier<T> supplier, @NonNull Predicate<T> formatter) {

        static <U> AccountDumpUtils.Field<U> of(
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
    public static final List<Pair<String, Function<BBMHederaAccount, Integer>>> intFieldsMapping = List.of(
            Pair.of("#+B", BBMHederaAccount::numberPositiveBalances),
            Pair.of("#A", BBMHederaAccount::numberAssociations),
            Pair.of("#KV", BBMHederaAccount::contractKvPairsNumber),
            Pair.of("#TT", BBMHederaAccount::numberTreasuryTitles),
            Pair.of("#UAA", BBMHederaAccount::usedAutoAssociations),
            Pair.of("^AA", BBMHederaAccount::maxAutoAssociations));

    /** A mapping for all `boolean`-valued fields that takes the field name to the field extractor. */
    public static final List<Pair<String, Function<BBMHederaAccount, Boolean>>> booleanFieldsMapping = List.of(
            Pair.of("AR", h -> h.autoRenewAccountId() != null),
            Pair.of("BR", a -> a.stakeAtStartOfLastRewardedPeriod() != -1L),
            Pair.of("DL", BBMHederaAccount::deleted),
            Pair.of("DR", BBMHederaAccount::declineReward),
            Pair.of("ER", BBMHederaAccount::expiredAndPendingRemoval),
            Pair.of("HA", a -> a.alias() != null),
            Pair.of("IM", BBMHederaAccount::isImmutable),
            Pair.of("PR", a -> (int) a.stakedId().value() < 0 && !a.declineReward()),
            Pair.of("RSR", BBMHederaAccount::receiverSigRequired),
            Pair.of("SC", BBMHederaAccount::smartContract),
            Pair.of("TT", a -> a.numberTreasuryTitles() > 0));

    /** A mapping for all `long`-valued fields that takes the field name to the field extractor. */
    public static final List<Pair<String, Function<BBMHederaAccount, Long>>> longFieldsMapping = List.of(
            Pair.of("#Balance", BBMHederaAccount::tinybarBalance),
            Pair.of("#NFT", BBMHederaAccount::numberOwnedNfts),
            Pair.of("ARS", BBMHederaAccount::autoRenewSeconds),
            Pair.of("B", a -> (long) a.numberPositiveBalances()),
            Pair.of("EX", BBMHederaAccount::expirationSecond),
            Pair.of("HNSN", a -> a.headNftId().serialNumber()),
            Pair.of("HNTN", a -> a.headNftId().tokenId().tokenNum()),
            Pair.of("HTI", a -> a.headTokenId().tokenNum()),
            Pair.of("N", BBMHederaAccount::ethereumNonce),
            Pair.of("SID", BBMHederaAccount::stakedIdLong),
            Pair.of("SNID", BBMHederaAccount::stakedNodeAddressBookId),
            Pair.of("SPS", coerceMinus1ToBeDefault(BBMHederaAccount::stakePeriodStart)),
            Pair.of("STM", BBMHederaAccount::stakedToMe),
            Pair.of("TS", BBMHederaAccount::totalStake),
            Pair.of("TSL", coerceMinus1ToBeDefault(BBMHederaAccount::stakeAtStartOfLastRewardedPeriod)));

    /** For the compressed field output we want to have field name abbreviations (for compactness), but for the CSV
     * output we can afford the full names.  This maps between them.  (Only the primitive-valued fields have the
     * abbreviations.)
     */
    public static final Map<String, String> fieldNameMap = toMap(
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
    public static Map<String, String> toMap(String... es) {
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
    public static <T extends Comparable<T>> void formatAccountFieldsForDifferentOutputFormats(
            @NonNull final StringBuilder sb,
            @NonNull final BBMHederaAccount a,
            @NonNull final String name,
            @NonNull List<Pair<String, Function<BBMHederaAccount, T>>> mapping,
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
    public static <T extends Comparable<T>> void formatAccountFields(
            @NonNull final StringBuilder sb,
            @NonNull final BBMHederaAccount a,
            @NonNull List<Pair<String, Function<BBMHederaAccount, T>>> mapping,
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
    public static void formatFieldSep(@NonNull final StringBuilder sb, @NonNull final String name) {
        sb.append(FIELD_SEPARATOR);
        sb.append(name);
        sb.append(NAME_TO_VALUE_SEPARATOR);
    }

    /** A Field formatter that emits fields as "name:value". Used for non-boolean fields in compressed format. */
    @NonNull
    public static <T> String taggedFieldFormatter(@NonNull final Pair<String, T> p) {
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
    public static Function<BBMHederaAccount, Long> coerceMinus1ToBeDefault(
            @NonNull final Function<BBMHederaAccount, Long> fn) {
        return a -> {
            final var v = fn.apply(a);
            return v == -1 ? 0 : v;
        };
    }
}
