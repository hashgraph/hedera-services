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

package com.hedera.services.cli.signedstate;

import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.services.cli.signedstate.DumpStateCommand.Format;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.ThingsToStrings;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
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

// TODO: SPS/TSL -1 is default, not 0
// TODO: Validate both CSV and elided output does output all fields correctly

@SuppressWarnings("java:S106") // "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
public class DumpAccountsSubcommand {

    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path accountPath,
            final int limit,
            @NonNull final Format format,
            @NonNull final Verbosity verbosity) {
        new DumpAccountsSubcommand(state, accountPath, limit, format, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path accountPath;

    @NonNull
    final Verbosity verbosity;

    @NonNull
    final Format format;

    final int limit;

    DumpAccountsSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path accountPath,
            final int limit,
            @NonNull final Format format,
            @NonNull final Verbosity verbosity) {
        this.state = state;
        this.accountPath = accountPath;
        this.limit = limit >= 0 ? limit : Integer.MAX_VALUE;
        this.format = format;
        this.verbosity = verbosity;
    }

    void doit() {
        final var accountsStore = state.getAccounts();
        System.out.printf(
                "=== %d accounts %s%n", accountsStore.size(), accountsStore.areOnDisk() ? "on disk" : "in memory");

        final var accountsArr = gatherAccounts(accountsStore);

        final var sb = new StringBuilder(1000);
        final var reportSize = new int[1];

        try (final var fileWriter = new FileWriter(accountPath.toFile(), StandardCharsets.UTF_8);
                final var writer = new BufferedWriter(fileWriter)) {

            if (format == Format.CSV) {
                writer.write("account#");
                writer.write(FIELD_SEPARATOR);
                writer.write(formatCsvHeader(allFieldNamesInOrder()));
                writer.newLine();
            }

            Arrays.stream(accountsArr).map(a -> formatAccount(sb, a)).forEachOrdered(s -> {
                try {
                    writer.write(s);
                    writer.newLine();
                } catch (final IOException ex) {
                    System.err.printf("Error writing to '%s':%n", accountPath);
                    throw new UncheckedIOException(ex);
                }
                reportSize[0] += s.length() + 1;
            });
        } catch (final IOException ex) {
            System.err.printf("Error creating or closing '%s'%n", accountPath);
            throw new UncheckedIOException(ex); // CLI program: Java will print the exception + stacktrace
        }

        System.out.printf("=== report is %d bytes%n", reportSize[0]);
    }

    @NonNull
    HederaAccount[] gatherAccounts(@NonNull AccountStorageAdapter accountStore) {
        final var accounts = new ConcurrentLinkedQueue<HederaAccount>();
        final var processed = new AtomicInteger();
        accountStore.forEach((entityNum, hederaAccount) -> {
            final var n = processed.incrementAndGet();
            if (n > limit) return;
            accounts.add(hederaAccount);
        });
        final var accountsArr = accounts.toArray(new HederaAccount[0]);
        Arrays.parallelSort(accountsArr, Comparator.comparingInt(HederaAccount::number));
        System.out.printf(
                "=== %d accounts iterated over (%d saved, %d limit)%n", processed.get(), accountsArr.length, limit);
        return accountsArr;
    }

    static final String FIELD_SEPARATOR = ";";
    static final String SUBFIELD_SEPARATOR = ",";
    static final String NAME_TO_VALUE_SEPARATOR = ":";

    @NonNull
    String formatCsvHeader(@NonNull final List<String> names) {
        return String.join(FIELD_SEPARATOR, names);
    }

    @NonNull
    List<String> allFieldNamesInOrder() {
        final var r = new ArrayList<String>(50);
        r.addAll(getFieldNamesInOrder(booleanFieldsMapping));
        r.addAll(getFieldNamesInOrder(intFieldsMapping));
        r.addAll(getFieldNamesInOrder(longFieldsMapping));
        r.addAll(getFieldNamesInOrder(getFieldAccessors(new StringBuilder(), getMockAccount()), false));
        return r.stream().map(s -> fieldNameMap.getOrDefault(s, s)).toList();
    }

    @NonNull
    HederaAccount getMockAccount() {
        return (HederaAccount) Proxy.newProxyInstance(
                HederaAccount.class.getClassLoader(), new Class<?>[] {HederaAccount.class}, (p, m, as) -> null);
    }

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
                sb, a, name, booleanFieldsMapping, b -> !b, DumpAccountsSubcommand::tagOnlyFieldFormatter);
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
                sb, a, name, intFieldsMapping, n -> n == 0, DumpAccountsSubcommand::taggedFieldFormatter);
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
            Pair.of("SPS", HederaAccount::getStakePeriodStart),
            Pair.of("STM", HederaAccount::getStakedToMe),
            Pair.of("TS", HederaAccount::totalStake),
            Pair.of("TSL", HederaAccount::totalStakeAtStartOfLastRewardedPeriod));

    /** Formats all the `long`-valued fields of an account, using the mapping `longFieldsMapping`. */
    void formatAccountLongs(
            @NonNull final StringBuilder sb, @NonNull final HederaAccount a, @NonNull final String name) {
        formatAccountFieldsForDifferentOutputFormats(
                sb, a, name, longFieldsMapping, n -> n == 0, DumpAccountsSubcommand::taggedFieldFormatter);
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
                Field.of("memo", a::getMemo, s -> { if (s.isEmpty()) return false; sb.append(ThingsToStrings.quoteForCsv(s)); return true; }),
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
                            yield fieldAccessor.apply();
                        case ELIDED_DEFAULT_FIELDS: {
                            formatFieldSep(sb, fieldAccessor.name());
                            yield fieldAccessor.apply();
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
                                ignored -> false,
                                DumpAccountsSubcommand::fieldOnlyFieldFormatter,
                                noWrappingJoiner);
                        yield true;
                    case ELIDED_DEFAULT_FIELDS:
                        formatFieldSep(sb, name);
                        formatAccountFields(sb, a, mapping, isDefaultValue, formatField, parenWrappingJoiner);
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
            @NonNull Predicate<T> isDefaultValue,
            @NonNull Function<Pair<String, T>, String> formatField,
            @NonNull Collector<CharSequence, ?, String> joinFields) {
        sb.append(mapping.stream()
                .map(p -> Pair.of(p.getLeft(), applySwallowingExceptions(p.getRight(), a)))
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

    /** Exceptions coming out of lambdas need to be swallowed.  This is ok because the cause is always a missing field
     * that should not have been accessed, and the check is always made by the caller to see if anything got added to
     * the accumulating stringbuffer, or not.
     */
    @Nullable
    static <R> R applySwallowingExceptions(
            @NonNull final Function<HederaAccount, R> fn, @NonNull final HederaAccount a) {
        try {
            return fn.apply(a);
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    /** A Field formatter that emits fields as "name:value". Used for non-boolean fields in compressed format. */
    @NonNull
    static <T> String taggedFieldFormatter(Pair<String, T> p) {
        return p.getLeft() + NAME_TO_VALUE_SEPARATOR + p.getRight();
    }

    /** A field formatter that only emits the _name_ of the field.  Used for boolean fields in compressed format. */
    @NonNull
    static <T> String tagOnlyFieldFormatter(Pair<String, T> p) {
        return p.getLeft();
    }

    /** A field formatter that only emits the value of the field itself.  Used for CSV output. */
    @NonNull
    static <T> String fieldOnlyFieldFormatter(Pair<String, T> p) {
        return p.getRight().toString();
    }

    /** A field joiner that joins _subfields_ with the CSV field separator. */
    static final Collector<CharSequence, ?, String> noWrappingJoiner = Collectors.joining(FIELD_SEPARATOR);

    /** A field joiner that joins _subfields_ with `,` (i.e., _not_ the CSV field separator) and wraps the entire
     * thing in parentheses. */
    static final Collector<CharSequence, ?, String> parenWrappingJoiner =
            Collectors.joining(SUBFIELD_SEPARATOR, "(", ")");
}
