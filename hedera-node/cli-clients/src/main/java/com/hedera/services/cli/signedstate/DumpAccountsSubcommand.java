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

package com.hedera.services.cli.signedstate;

import static com.hedera.services.cli.utils.ThingsToStrings.toStructureSummaryOfJKey;
import static java.util.function.Predicate.not;

import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.services.cli.signedstate.DumpStateCommand.Format;
import com.hedera.services.cli.signedstate.DumpStateCommand.KeyDetails;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.ThingsToStrings;
import com.hedera.services.cli.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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

/** Dump all Hedera account objects, from a signed state file, to a text file, in deterministic order.
 * Can output in either CSV format (actually: semicolon-separated) or in an "elided field" format where fields are
 * dumped in "name:value" pairs and missing fields or fields with default values are skipped.
 */
@SuppressWarnings("java:S106") // "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
public class DumpAccountsSubcommand {

    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path accountPath,
            final int lowLimit,
            final int highLimit,
            @NonNull final EnumSet<KeyDetails> keyDetails,
            @NonNull final Format format,
            @NonNull final Verbosity verbosity) {
        new DumpAccountsSubcommand(state, accountPath, lowLimit, highLimit, keyDetails, format, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path accountPath;

    @NonNull
    final Verbosity verbosity;

    @NonNull
    final Format format;

    final int lowLimit;

    final int highLimit;

    @NonNull
    final EnumSet<KeyDetails> keyDetails;

    DumpAccountsSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path accountPath,
            final int lowLimit,
            final int highLimit,
            @NonNull final EnumSet<KeyDetails> keyDetails,
            @NonNull final Format format,
            @NonNull final Verbosity verbosity) {
        this.state = state;
        this.accountPath = accountPath;
        this.lowLimit = Math.max(lowLimit, 0);
        this.highLimit = Math.max(highLimit, 0);
        this.keyDetails = keyDetails;
        this.format = format;
        this.verbosity = verbosity;
    }

    void doit() {
        final var accountsStore = state.getAccounts();
        System.out.printf(
                "=== %d accounts (%s)%n", accountsStore.size(), accountsStore.areOnDisk() ? "on disk" : "in memory");

        final var accountsArr = gatherAccounts(accountsStore);

        int reportSize;
        try (@NonNull final var writer = new Writer(accountPath)) {
            reportOnAccounts(writer, accountsArr);
            if (keyDetails.contains(KeyDetails.STRUCTURE) || keyDetails.contains(KeyDetails.STRUCTURE_WITH_IDS))
                reportOnKeyStructure(writer, accountsArr);
            reportSize = writer.getSize();
        }

        System.out.printf("=== accounts report is %d bytes%n", reportSize);
        System.out.printf("=== fields with exceptions: %s%n", String.join(",", fieldsWithExceptions));
    }

    void reportOnAccounts(@NonNull final Writer writer, @NonNull final HederaAccount[] accountsArr) {
        if (format == Format.CSV) {
            writer.write("account#");
            writer.write(FIELD_SEPARATOR);
            writer.write(formatCsvHeader(allFieldNamesInOrder()));
            writer.newLine();
        }

        final var sb = new StringBuilder();
        Arrays.stream(accountsArr).map(a -> formatAccount(sb, a)).forEachOrdered(s -> {
            writer.write(s);
            writer.newLine();
        });
    }

    void reportOnKeyStructure(@NonNull final Writer writer, @NonNull final HederaAccount[] accountsArr) {
        final var eoaKeySummary = new HashMap<String, Integer>();
        accumulateSummaries(not(HederaAccount::isSmartContract), eoaKeySummary, accountsArr);
        writeKeySummaryReport(writer, "EOA", eoaKeySummary);

        final var scKeySummary = new HashMap<String, Integer>();
        accumulateSummaries(HederaAccount::isSmartContract, scKeySummary, accountsArr);
        writeKeySummaryReport(writer, "Smart Contract", scKeySummary);
    }

    @SuppressWarnings(
            "java:S135") // Loops should not contain more than a single "break" or "continue" statement - disagree it
    // would make things clearer here
    void accumulateSummaries(
            @NonNull final Predicate<HederaAccount> filter,
            @NonNull final HashMap<String, Integer> structureSummary,
            @NonNull final HederaAccount[] accountsArr) {
        for (@NonNull final var ha : accountsArr) {
            if (ha.isDeleted()) continue;
            if (!filter.test(ha)) continue;
            final var jkey = ha.getAccountKey();
            final var sb = new StringBuilder();
            final var b = toStructureSummaryOfJKey(sb, jkey);
            if (!b) {
                sb.setLength(0);
                sb.append("NULL-KEY");
            }
            structureSummary.merge(sb.toString(), 1, Integer::sum);
        }
    }

    void writeKeySummaryReport(
            @NonNull final Writer writer, @NonNull final String kind, @NonNull final Map<String, Integer> keySummary) {

        writer.write("=== %s Key Summary ===%n".formatted(kind));
        keySummary.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> writer.write("%7d: %s%n".formatted(e.getValue(), e.getKey())));
    }

    /** Traverses the dehydrated signed state file to pull out all the accounts for later processing.
     *
     * Currently, we pull out _all_ of the accounts at once and _sort_ them before processing any. This is because we
     * want to output accounts in a deterministic order and the traversal - multithreaded - gives you accounts in a
     * non-deterministic order.
     *
     * But another approach would be to _format_ the accounts into strings as you do the traversal, saving those
     * strings, and then after the traversal is complete sorting _them_ into a deterministic order.  Not sure which
     * way is better.  No need to find out, either:  This approach works, is fast enough, and runs on current mainnet
     * state on my laptop.  If in fact it doesn't run (say, memory issues) on larger states (e.g., testnet) then we can
     * try another approach that may save memory.  (But the fact is the entire state must be resident in memory
     * anyway - except for the on-disk portions (for the mono service) and so we're talking about the difference
     * between the `HederaAccount` objects and the things they own vs. the formatted strings.)
     */
    @NonNull
    HederaAccount[] gatherAccounts(@NonNull AccountStorageAdapter accountStore) {
        final var accounts = new ConcurrentLinkedQueue<HederaAccount>();
        final var processed = new AtomicInteger();
        accountStore.forEach((entityNum, hederaAccount) -> {
            processed.incrementAndGet();
            if (lowLimit <= entityNum.longValue() && entityNum.longValue() <= highLimit) accounts.add(hederaAccount);
        });
        final var accountsArr = accounts.toArray(new HederaAccount[0]);
        Arrays.parallelSort(accountsArr, Comparator.comparingInt(HederaAccount::number));
        System.out.printf(
                "=== %d accounts iterated over (%d saved%s)%n",
                processed.get(),
                accountsArr.length,
                lowLimit > 0 || highLimit < Integer.MAX_VALUE
                        ? ", limits: [%d..%d]".formatted(lowLimit, highLimit)
                        : "");
        return accountsArr;
    }

    /** String that separates all fields in the CSV format, and also the primitive-typed fields from each other and
     * the other-typed fields from each other in the compressed format.
     */
    static final String FIELD_SEPARATOR = ";";

    /** String that separates sub-fields (the primitive-type fields) in the compressed format. */
    static final String SUBFIELD_SEPARATOR = ",";

    /** String that separates field names from field values in the compressed format */
    static final String NAME_TO_VALUE_SEPARATOR = ":";

    /** Produces the CSV header line: A CSV line from all the field names in the deterministic order. */
    @NonNull
    String formatCsvHeader(@NonNull final List<String> names) {
        return String.join(FIELD_SEPARATOR, names);
    }

    /** Returns the list of _all_ field names in the deterministic order, expanding the abbreviations to the full
     * field name.
     */
    @NonNull
    List<String> allFieldNamesInOrder() {
        final var r = new ArrayList<String>(50);
        r.addAll(getFieldNamesInOrder(booleanFieldsMapping));
        r.addAll(getFieldNamesInOrder(intFieldsMapping));
        r.addAll(getFieldNamesInOrder(longFieldsMapping));
        r.addAll(getFieldNamesInOrder(getFieldAccessors(new StringBuilder(), getMockAccount()), false));
        return r.stream().map(s -> fieldNameMap.getOrDefault(s, s)).toList();
    }

    /** For the purpose of getting the field names from the field accessors, via the existing method, I need a dummy
     * account.  This is the way to get one.  (Only done once per execution of this tool.)
     */
    @NonNull
    HederaAccount getMockAccount() {
        return (HederaAccount) Proxy.newProxyInstance(
                HederaAccount.class.getClassLoader(), new Class<?>[] {HederaAccount.class}, (p, m, as) -> null);
    }

    /** Formats an entire account as a text string.  First field of the string is the account number, followed by all
     * of its fields.
     */
    @NonNull
    String formatAccount(@NonNull final StringBuilder sb, @NonNull final HederaAccount a) {
        sb.setLength(0);
        sb.append(a.number());
        formatAccountBooleans(sb, a, "bools");
        formatAccountInts(sb, a, "ints");
        formatAccountLongs(sb, a, "longs");
        formatAccountOtherFields(sb, a);
        return sb.toString();
    }

    /** For the compressed field output we want to have field name abbreviations (for compactness), but for the CSV
     * output we can afford the full names.  This maps between them.  (Only the primitive-valued fields have the
     * abbreviations.)
     */
    static final Map<String, String> fieldNameMap = toMap(
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
    static Map<String, String> toMap(String... es) {
        if (0 != es.length % 2)
            throw new IllegalArgumentException(
                    "must have even number of args to `toMap`, %d given".formatted(es.length));
        final var r = new TreeMap<String, String>();
        for (int i = 0; i < es.length; i += 2) {
            r.put(es[i], es[i + 1]);
        }
        return r;
    }

    /** A simple formatter for field names in the compressed fields case: writes the field separator then `{name}:` */
    void formatFieldSep(@NonNull final StringBuilder sb, @NonNull final String name) {
        sb.append(FIELD_SEPARATOR);
        sb.append(name);
        sb.append(NAME_TO_VALUE_SEPARATOR);
    }

    /** A mapping for all `boolean`-valued fields that takes the field name to the field extractor. */
    static final List<Pair<String, Function<HederaAccount, Boolean>>> booleanFieldsMapping = List.of(
            Pair.of("AR", HederaAccount::hasAutoRenewAccount),
            Pair.of("BR", HederaAccount::hasBeenRewardedSinceLastStakeMetaChange),
            Pair.of("DL", HederaAccount::isDeleted),
            Pair.of("DR", HederaAccount::isDeclinedReward),
            Pair.of("ER", HederaAccount::isExpiredAndPendingRemoval),
            Pair.of("HA", HederaAccount::hasAlias),
            Pair.of("IM", HederaAccount::isImmutable),
            Pair.of("PR", HederaAccount::mayHavePendingReward),
            Pair.of("RSR", HederaAccount::isReceiverSigRequired),
            Pair.of("SC", HederaAccount::isSmartContract),
            Pair.of("TT", HederaAccount::isTokenTreasury));

    /** Formats all the `boolean`-valued fields of an account, using the mapping `booleanFieldsMapping`. */
    void formatAccountBooleans(
            @NonNull final StringBuilder sb, @NonNull final HederaAccount a, @NonNull final String name) {
        formatAccountFieldsForDifferentOutputFormats(
                sb, a, name, booleanFieldsMapping, false, b -> !b, DumpAccountsSubcommand::tagOnlyFieldFormatter);
    }

    /** A mapping for all `int`-valued fields that takes the field name to the field extractor. */
    static final List<Pair<String, Function<HederaAccount, Integer>>> intFieldsMapping = List.of(
            Pair.of("#+B", HederaAccount::getNumPositiveBalances),
            Pair.of("#A", HederaAccount::getNumAssociations),
            Pair.of("#KV", HederaAccount::getNumContractKvPairs),
            Pair.of("#TT", HederaAccount::getNumTreasuryTitles),
            Pair.of("#UAA", HederaAccount::getUsedAutoAssociations),
            Pair.of("^AA", HederaAccount::getMaxAutomaticAssociations));

    /** Formats all the `int`-valued fields of an account, using the mapping `intFieldsMapping`. */
    void formatAccountInts(
            @NonNull final StringBuilder sb, @NonNull final HederaAccount a, @NonNull final String name) {
        formatAccountFieldsForDifferentOutputFormats(
                sb, a, name, intFieldsMapping, 0, n -> n == 0, DumpAccountsSubcommand::taggedFieldFormatter);
    }

    /** A mapping for all `long`-valued fields that takes the field name to the field extractor. */
    static final List<Pair<String, Function<HederaAccount, Long>>> longFieldsMapping = List.of(
            Pair.of("#NFT", HederaAccount::getNftsOwned),
            Pair.of("ARS", HederaAccount::getAutoRenewSecs),
            Pair.of("B", HederaAccount::getBalance),
            Pair.of("EX", HederaAccount::getExpiry),
            Pair.of("HNSN", HederaAccount::getHeadNftSerialNum),
            Pair.of("HNTN", HederaAccount::getHeadNftTokenNum),
            Pair.of("HTI", HederaAccount::getHeadTokenId),
            Pair.of("N", HederaAccount::getEthereumNonce),
            Pair.of("SID", HederaAccount::getStakedId),
            Pair.of("SNID", HederaAccount::getStakedNodeAddressBookId),
            Pair.of("SPS", coerceMinus1ToBeDefault(HederaAccount::getStakePeriodStart)),
            Pair.of("STM", HederaAccount::getStakedToMe),
            Pair.of("TS", HederaAccount::totalStake),
            Pair.of("TSL", coerceMinus1ToBeDefault(HederaAccount::totalStakeAtStartOfLastRewardedPeriod)));

    /** Unfortunately this is a hack to handle the two long-valued fields where `-1` is used as the "missing" marker.
     * Probably all the primitive-valued fields should be changed to use `Field` descriptors, which would then be
     * enhanced to have a per-field "is default value?" predicate.  But not now.)
     */
    @SuppressWarnings(
            "java:S4276") // Functional interfaces should be as specialized as possible - except not in this case, for
    // consistency
    @NonNull
    static Function<HederaAccount, Long> coerceMinus1ToBeDefault(@NonNull final Function<HederaAccount, Long> fn) {
        return a -> {
            final var v = fn.apply(a);
            return v == -1 ? 0 : v;
        };
    }

    /** Formats all the `long`-valued fields of an account, using the mapping `longFieldsMapping`. */
    void formatAccountLongs(
            @NonNull final StringBuilder sb, @NonNull final HederaAccount a, @NonNull final String name) {
        formatAccountFieldsForDifferentOutputFormats(
                sb, a, name, longFieldsMapping, 0L, n -> n == 0L, DumpAccountsSubcommand::taggedFieldFormatter);
    }

    /** A mapping for all account fields that are _not_ of primitive type.  Takes the field name to a `Field`, which
     * holds the field name, the field extractor ,and the field formatter. And it _is_ a "mapping" even though it isn't
     * actually a `Map` data structure like the other mappings for primitive typed fields. */
    @SuppressWarnings({"java:S1452", "java:S2681"})
    // 1452: generic wildcard types should not be used in return types - yes, but this is a collection of `Field`s
    // of unrelated types, yet `Object` is not appropriate either
    // 2681: a complaint about no braces around `then` clause - yep, intentional, and correct
    // spotless:off
    List<Field<?>> getFieldAccessors(@NonNull final StringBuilder sb, @NonNull final HederaAccount a) {
        return Stream.of(
                Field.of("1stContractStorageKey", a::getFirstContractStorageKey, doWithBuilder(sb, ThingsToStrings::toStringOfContractKey)),
                Field.of("accountKey", a::getAccountKey, doWithBuilder(sb, ThingsToStrings::toStringOfJKey)),
                Field.of("alias", a::getAlias, doWithBuilder(sb, ThingsToStrings::toStringOfByteString)),
                Field.of("approveForAllNfts", a::getApproveForAllNfts, doWithBuilder(sb, ThingsToStrings::toStringOfFcTokenAllowanceIdSet)),
                Field.of("autoRenewAccount", a::getAutoRenewAccount, doWithBuilder(sb, ThingsToStrings::toStringOfEntityId)),
                Field.of("cryptoAllowances", a::getCryptoAllowances, doWithBuilder(sb, ThingsToStrings::toStringOfMapEnLong)),
                Field.of("firstUint256Key", a::getFirstUint256Key, doWithBuilder(sb, ThingsToStrings::toStringOfIntArray)),
                Field.of("fungibleTokenAllowances", a::getFungibleTokenAllowances, doWithBuilder(sb, ThingsToStrings::toStringOfMapFcLong)),
                Field.of("headNftKey", a::getHeadNftKey, doWithBuilder(sb, ThingsToStrings::toStringOfEntityNumPair)),
                Field.of("latestAssociation", a::getLatestAssociation, doWithBuilder(sb, ThingsToStrings::toStringOfEntityNumPair)),
                Field.of("memo", a::getMemo, s -> { if (s.isEmpty()) return false; sb.append(ThingsToStrings.quoteForCsv(FIELD_SEPARATOR,  s)); return true; }),
                Field.of("proxy", a::getProxy, doWithBuilder(sb, ThingsToStrings::toStringOfEntityId))
        ).sorted(Comparator.comparing(Field::name)).toList();
    }
    // spotless:on

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

    /** Apply a formatter, given a `StringBuilder` and return whether (or not) the field _existed_ and should be
     * emitted. */
    <T> Predicate<T> doWithBuilder(@NonNull final StringBuilder sb, @NonNull final BiPredicate<StringBuilder, T> bifn) {
        return t -> bifn.test(sb, t);
    }

    /** Given a mapping from field names to both a field extraction function (extract from an account) and a field
     * formatter (type-specific), produce the formatted form of all the fields given in the mapping.  Can do either of
     * the `Format`s: CSV or compressed fields.
     */
    void formatAccountOtherFields(@NonNull final StringBuilder sb, @NonNull HederaAccount a) {
        final var fieldAccessors = getFieldAccessors(sb, a);
        for (final var fieldAccessor : fieldAccessors) {
            final var l = sb.length();
            final var r =
                    switch (format) {
                        case CSV:
                            sb.append(FIELD_SEPARATOR);
                            applySwallowingExceptions(fieldAccessor);
                            yield true;
                        case ELIDED_DEFAULT_FIELDS: {
                            formatFieldSep(sb, fieldAccessor.name());
                            yield applySwallowingExceptions(fieldAccessor);
                        }
                    };
            if (!r) sb.setLength(l);
        }
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
    <T extends Comparable<T>> void formatAccountFieldsForDifferentOutputFormats(
            @NonNull final StringBuilder sb,
            @NonNull final HederaAccount a,
            @NonNull final String name,
            @NonNull List<Pair<String, Function<HederaAccount, T>>> mapping,
            @NonNull T missingValue,
            @NonNull Predicate<T> isDefaultValue,
            @NonNull Function<Pair<String, T>, String> formatField) {
        final var l = sb.length();
        final var r =
                switch (format) {
                    case CSV:
                        sb.append(FIELD_SEPARATOR);
                        formatAccountFields(
                                sb,
                                a,
                                mapping,
                                missingValue,
                                ignored -> false,
                                getNonDefaultOnlyFieldFormatter(isDefaultValue),
                                noWrappingJoiner);
                        yield true;
                    case ELIDED_DEFAULT_FIELDS:
                        formatFieldSep(sb, name);
                        formatAccountFields(
                                sb, a, mapping, missingValue, isDefaultValue, formatField, parenWrappingJoiner);
                        yield sb.length() - l > 0;
                };
        if (!r) sb.setLength(l);
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
    <T extends Comparable<T>> void formatAccountFields(
            @NonNull final StringBuilder sb,
            @NonNull final HederaAccount a,
            @NonNull List<Pair<String, Function<HederaAccount, T>>> mapping,
            @NonNull T missingValue,
            @NonNull Predicate<T> isDefaultValue,
            @NonNull Function<Pair<String, T>, String> formatField,
            @NonNull Collector<CharSequence, ?, String> joinFields) {
        sb.append(mapping.stream()
                .map(p -> Pair.of(p.getLeft(), applySwallowingExceptions(p.getLeft(), p.getRight(), a, missingValue)))
                .filter(p -> p.getRight() != null && !isDefaultValue.test(p.getRight()))
                .sorted(Comparator.comparing(Pair::getLeft))
                .map(formatField)
                .collect(joinFields));
    }

    /** Given one of the primitive-type mappings above, extract the field names, and sort them */
    <T extends Comparable<T>> List<String> getFieldNamesInOrder(
            @NonNull List<Pair<String, Function<HederaAccount, T>>> mapping) {
        return mapping.stream().map(Pair::getLeft).sorted().toList();
    }

    /** Given the field mappings above, extract the field names, and sort them */
    // (Overload needed because of type erasure; ugly but seemed to me less ugly than an alternate name, YMMV)
    @SuppressWarnings("java:S1172") // "remove unused method parameter 'ignored'" - nope, needed as described aboved
    List<String> getFieldNamesInOrder(@NonNull final List<Field<?>> fields, final boolean ignored) {
        return fields.stream().map(Field::name).sorted().toList();
    }

    final Set<String> fieldsWithExceptions = new TreeSet<>();

    /** Exceptions coming out of lambdas need to be swallowed.  This is ok because the cause is always a missing field
     * that should not have been accessed, and the check for that is always made by the caller: The caller sees if
     * anything got added to the accumulating stringbuffer, or not.
     */
    @NonNull
    <R> R applySwallowingExceptions(
            @NonNull final String name,
            @NonNull final Function<HederaAccount, R> fn,
            @NonNull final HederaAccount a,
            @NonNull R missingValue) {
        try {
            return fn.apply(a);
        } catch (final RuntimeException ex) {
            fieldsWithExceptions.add(name);
            return missingValue;
        }
    }

    <R> boolean applySwallowingExceptions(@NonNull final Field<R> field) {
        try {
            return field.apply();
        } catch (final RuntimeException ex) {
            fieldsWithExceptions.add(field.name());
            return false;
        }
    }

    /** A Field formatter that emits fields as "name:value". Used for non-boolean fields in compressed format. */
    @NonNull
    static <T> String taggedFieldFormatter(@NonNull final Pair<String, T> p) {
        return p.getLeft() + NAME_TO_VALUE_SEPARATOR + p.getRight();
    }

    /** A field formatter that only emits the _name_ of the field.  Used for boolean fields in compressed format. */
    @NonNull
    static <T> String tagOnlyFieldFormatter(@NonNull final Pair<String, T> p) {
        return p.getLeft();
    }

    /** A field formatter that only emits the value of the field itself.  Used for CSV output. */
    @NonNull
    static <T> String fieldOnlyFieldFormatter(@NonNull final Pair<String, T> p) {
        return p.getRight().toString();
    }

    @NonNull
    static <T> Function<Pair<String, T>, String> getNonDefaultOnlyFieldFormatter(
            @NonNull final Predicate<T> isDefaultValue) {
        return p -> isDefaultValue.test(p.getRight()) ? "" : p.getRight().toString();
    }

    /** A field joiner that joins _subfields_ with the CSV field separator. */
    static final Collector<CharSequence, ?, String> noWrappingJoiner = Collectors.joining(FIELD_SEPARATOR);

    /** A field joiner that joins _subfields_ with `,` (i.e., _not_ the CSV field separator) and wraps the entire
     * thing in parentheses. */
    static final Collector<CharSequence, ?, String> parenWrappingJoiner =
            Collectors.joining(SUBFIELD_SEPARATOR, "(", ")");
}
