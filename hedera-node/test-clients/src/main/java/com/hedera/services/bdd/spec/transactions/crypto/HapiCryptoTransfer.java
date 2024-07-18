/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.transactions.crypto;

import static com.hedera.services.bdd.spec.infrastructure.meta.InitialAccountIdentifiers.throwIfNotEcdsa;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.*;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.HBAR_SENTINEL_TOKEN_ID;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.summingLong;
import static java.util.stream.Collectors.toList;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.google.protobuf.UInt32Value;
import com.hedera.node.app.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.node.app.hapi.fees.usage.crypto.CryptoTransferMeta;
import com.hedera.node.app.hapi.fees.usage.state.UsageAccumulator;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiCryptoTransfer extends HapiTxnOp<HapiCryptoTransfer> {
    static final Logger log = LogManager.getLogger(HapiCryptoTransfer.class);

    private String token;
    private String fromAccount;

    private String PARTITION_TOKEN_OPERATION_MODE;

    private static final List<TokenMovement> MISSING_TOKEN_AWARE_PROVIDERS = Collections.emptyList();
    private static final Function<HapiSpec, TransferList> MISSING_HBAR_ONLY_PROVIDER = null;

    private static final Comparator<AccountID> ACCOUNT_NUM_COMPARATOR = Comparator.comparingLong(
                    AccountID::getAccountNum)
            .thenComparingLong(AccountID::getShardNum)
            .thenComparingLong(AccountID::getRealmNum);
    private static final Comparator<AccountID> ACCOUNT_NUM_OR_ALIAS_COMPARATOR = (a, b) -> {
        if (!a.getAlias().isEmpty() || !b.getAlias().isEmpty()) {
            return ByteString.unsignedLexicographicalComparator().compare(a.getAlias(), b.getAlias());
        } else {
            return ACCOUNT_NUM_COMPARATOR.compare(a, b);
        }
    };
    private static final Comparator<AccountAmount> ACCOUNT_AMOUNT_COMPARATOR = Comparator.comparingLong(
                    AccountAmount::getAmount)
            .thenComparing(AccountAmount::getAccountID, ACCOUNT_NUM_OR_ALIAS_COMPARATOR);
    private static final Comparator<NftTransfer> NFT_TRANSFER_COMPARATOR = Comparator.comparing(
                    NftTransfer::getSenderAccountID, ACCOUNT_NUM_OR_ALIAS_COMPARATOR)
            .thenComparing(NftTransfer::getReceiverAccountID, ACCOUNT_NUM_OR_ALIAS_COMPARATOR)
            .thenComparingLong(NftTransfer::getSerialNumber);
    private static final Comparator<TokenID> TOKEN_ID_COMPARATOR = Comparator.comparingLong(TokenID::getTokenNum);
    private static final Comparator<TokenTransferList> TOKEN_TRANSFER_LIST_COMPARATOR =
            (o1, o2) -> Objects.compare(o1.getToken(), o2.getToken(), TOKEN_ID_COMPARATOR);

    private boolean logResolvedStatus = false;
    private boolean breakNetZeroTokenChangeInvariant = false;

    private List<TokenMovement> tokenAwareProviders = MISSING_TOKEN_AWARE_PROVIDERS;
    private Function<HapiSpec, TransferList> hbarOnlyProvider = MISSING_HBAR_ONLY_PROVIDER;
    private Optional<String> tokenWithEmptyTransferAmounts = Optional.empty();
    private Optional<Pair<String[], Long>> appendedFromTo = Optional.empty();
    private Optional<AtomicReference<FeeObject>> feesObserver = Optional.empty();
    private Optional<BiConsumer<HapiSpec, CryptoTransferTransactionBody.Builder>> explicitDef = Optional.empty();
    private boolean fullyAggregateTokenTransfers = true;
    private static boolean transferToKey = false;

    private Optional<Pair<String[], Long>> appendedPartitionFromTo = Optional.empty();
    private Optional<Pair<String[], Long>> appendedNFTPartitionFromTo = Optional.empty();

    private Optional<Pair<String[], Long>> appendedPartitionFromTowithSign = Optional.empty();

    private long[] serialNums;

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.CryptoTransfer;
    }

    public HapiCryptoTransfer showingResolvedStatus() {
        logResolvedStatus = true;
        return this;
    }

    public HapiCryptoTransfer breakingNetZeroInvariant() {
        breakNetZeroTokenChangeInvariant = true;
        return this;
    }

    public HapiCryptoTransfer exposingFeesTo(final AtomicReference<FeeObject> obs) {
        feesObserver = Optional.of(obs);
        return this;
    }

    private static Collector<TransferList, ?, TransferList> transferCollector(
            final BinaryOperator<List<AccountAmount>> reducer) {
        return collectingAndThen(
                reducing(Collections.emptyList(), TransferList::getAccountAmountsList, reducer),
                aList -> TransferList.newBuilder().addAllAccountAmounts(aList).build());
    }

    private static Collector<TransferList, ?, TransferList> sortedTransferCollector(
            final BinaryOperator<List<AccountAmount>> reducer) {
        return collectingAndThen(
                reducing(Collections.emptyList(), TransferList::getAccountAmountsList, reducer),
                aList -> TransferList.newBuilder()
                        .addAllAccountAmounts(
                                aList.stream().sorted(ACCOUNT_AMOUNT_COMPARATOR).toList())
                        .build());
    }

    private static final BinaryOperator<List<AccountAmount>> accountMerge = (a, b) -> Stream.of(a, b)
            .flatMap(List::stream)
            .collect(collectingAndThen(
                    groupingBy(
                            AccountAmount::getAccountID,
                            (groupingBy(AccountAmount::getIsApproval, mapping(AccountAmount::getAmount, toList())))),
                    aMap -> aMap.entrySet().stream()
                            .flatMap(entry -> {
                                List<AccountAmount> accountAmounts = new ArrayList<>();
                                for (var entrySet : entry.getValue().entrySet()) {
                                    var aa = AccountAmount.newBuilder()
                                            .setAccountID(entry.getKey())
                                            .setIsApproval(entrySet.getKey())
                                            .setAmount(entrySet.getValue().stream()
                                                    .mapToLong(l -> l)
                                                    .sum())
                                            .build();
                                    accountAmounts.add(aa);
                                }
                                return accountAmounts.stream();
                            })
                            .sorted(ACCOUNT_AMOUNT_COMPARATOR)
                            .toList()));
    private static final Collector<TransferList, ?, TransferList> mergingAccounts = transferCollector(accountMerge);
    private static final Collector<TransferList, ?, TransferList> mergingSortedAccounts =
            sortedTransferCollector(accountMerge);

    public HapiCryptoTransfer(final BiConsumer<HapiSpec, CryptoTransferTransactionBody.Builder> def) {
        explicitDef = Optional.of(def);
    }

    @SafeVarargs
    public HapiCryptoTransfer(final Function<HapiSpec, TransferList>... providers) {
        this(false, providers);
    }

    @SafeVarargs
    public HapiCryptoTransfer(final boolean sortTransferList, final Function<HapiSpec, TransferList>... providers) {
        if (providers.length == 0) {
            hbarOnlyProvider = ignore -> TransferList.getDefaultInstance();
        } else if (providers.length == 1) {
            hbarOnlyProvider = providers[0];
        } else {
            if (sortTransferList) {
                this.hbarOnlyProvider =
                        spec -> Stream.of(providers).map(p -> p.apply(spec)).collect(mergingSortedAccounts);
            } else {
                this.hbarOnlyProvider =
                        spec -> Stream.of(providers).map(p -> p.apply(spec)).collect(mergingAccounts);
            }
        }
    }

    public HapiCryptoTransfer(final TokenMovement... sources) {
        this.tokenAwareProviders = List.of(sources);
    }

    public HapiCryptoTransfer(
            final String account, final String fromPartitionToken, final String toPartitionToken, final long amount) {
        this.token = fromPartitionToken;
        this.PARTITION_TOKEN_OPERATION_MODE = "PartitionMoveWithoutUserSignature";
        this.appendedPartitionFromTo =
                Optional.of(Pair.of(new String[] {account, fromPartitionToken, toPartitionToken}, amount));
    }

    public HapiCryptoTransfer(
            final String fromAccount,
            final String fromPartitionToken,
            final String toAccount,
            final String toPartitionToken,
            final long amount) {
        this.token = fromPartitionToken;
        this.fromAccount = fromAccount;
        this.PARTITION_TOKEN_OPERATION_MODE = "PartitionMoveWithUserSignature";
        this.appendedPartitionFromTowithSign = Optional.of(
                Pair.of(new String[] {fromAccount, fromPartitionToken, toAccount, toPartitionToken}, amount));
    }

    public HapiCryptoTransfer(
            final String account,
            final String fromPartitionToken,
            final String toPartitionToken,
            final long... serialNos) {
        this.token = fromPartitionToken;
        this.PARTITION_TOKEN_OPERATION_MODE = "PartitionNFTMoveWithoutUserSignature";
        this.appendedNFTPartitionFromTo =
                Optional.of(Pair.of(new String[] {account, fromPartitionToken, toPartitionToken}, null));
        this.serialNums = serialNos;
    }

    public HapiCryptoTransfer dontFullyAggregateTokenTransfers() {
        this.fullyAggregateTokenTransfers = false;
        return this;
    }

    public HapiCryptoTransfer withEmptyTokenTransfers(final String token) {
        tokenWithEmptyTransferAmounts = Optional.of(token);
        return this;
    }

    public HapiCryptoTransfer appendingTokenFromTo(
            final String token, final String from, final String to, final long amount) {
        appendedFromTo = Optional.of(Pair.of(new String[] {token, from, to}, amount));
        return this;
    }

    @Override
    protected Function<HapiSpec, List<Key>> variableDefaultSigners() {
        if (hbarOnlyProvider != MISSING_HBAR_ONLY_PROVIDER) {
            return hbarOnlyVariableDefaultSigners();
        } else if (this.PARTITION_TOKEN_OPERATION_MODE == "PartitionMoveWithoutUserSignature"
                || this.PARTITION_TOKEN_OPERATION_MODE == "PartitionMoveWithUserSignature"
                || this.PARTITION_TOKEN_OPERATION_MODE == "PartitionNFTMoveWithoutUserSignature") {
            return partitionMoveSignatures();
        } else {
            return tokenAwareVariableDefaultSigners();
        }
    }

    public static Function<HapiSpec, TransferList> allowanceTinyBarsFromTo(
            final String from, final String to, final long amount) {
        return spec -> {
            final AccountID toAccount = asId(to, spec);
            final AccountID fromAccount = asId(from, spec);
            return TransferList.newBuilder()
                    .addAllAccountAmounts(Arrays.asList(
                            AccountAmount.newBuilder()
                                    .setAccountID(toAccount)
                                    .setAmount(amount)
                                    .setIsApproval(true)
                                    .build(),
                            AccountAmount.newBuilder()
                                    .setAccountID(fromAccount)
                                    .setAmount(-1L * amount)
                                    .setIsApproval(true)
                                    .build()))
                    .build();
        };
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromTo(
            final String from, final String to, final long amount) {
        return tinyBarsFromTo(from, to, ignore -> amount);
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromTo(
            final String from, final ByteString to, final long amount) {
        return tinyBarsFromTo(from, to, ignore -> amount);
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromTo(
            final ByteString from, final ByteString to, final long amount) {
        return tinyBarsFromTo(from, to, ignore -> amount);
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromTo(
            final String from, final ByteString to, final ToLongFunction<HapiSpec> amountFn) {
        return spec -> {
            final long amount = amountFn.applyAsLong(spec);
            final AccountID toAccount = asIdWithAlias(to);
            final AccountID fromAccount = asId(from, spec);
            return TransferList.newBuilder()
                    .addAllAccountAmounts(Arrays.asList(
                            AccountAmount.newBuilder()
                                    .setAccountID(toAccount)
                                    .setAmount(amount)
                                    .build(),
                            AccountAmount.newBuilder()
                                    .setAccountID(fromAccount)
                                    .setAmount(-1L * amount)
                                    .build()))
                    .build();
        };
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromTo(
            final ByteString from, final ByteString to, final ToLongFunction<HapiSpec> amountFn) {
        return spec -> {
            long amount = amountFn.applyAsLong(spec);
            AccountID fromAccount = asIdWithAlias(from);
            AccountID toAccount = asIdWithAlias(to);
            return TransferList.newBuilder()
                    .addAllAccountAmounts(Arrays.asList(
                            AccountAmount.newBuilder()
                                    .setAccountID(toAccount)
                                    .setAmount(amount)
                                    .build(),
                            AccountAmount.newBuilder()
                                    .setAccountID(fromAccount)
                                    .setAmount(-1L * amount)
                                    .build()))
                    .build();
        };
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromTo(
            final String from, final String to, final ToLongFunction<HapiSpec> amountFn) {
        return spec -> {
            final long amount = amountFn.applyAsLong(spec);
            final AccountID toAccount = asId(to, spec);
            final AccountID fromAccount = asId(from, spec);
            return TransferList.newBuilder()
                    .addAllAccountAmounts(Arrays.asList(
                            AccountAmount.newBuilder()
                                    .setAccountID(toAccount)
                                    .setAmount(amount)
                                    .build(),
                            AccountAmount.newBuilder()
                                    .setAccountID(fromAccount)
                                    .setAmount(-1L * amount)
                                    .build()))
                    .build();
        };
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromToWithAlias(
            final String from, final String to, final long amount) {
        transferToKey = true;
        return tinyBarsFromToWithAlias(from, to, ignore -> amount);
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromAccountToAlias(
            final String from, final String to, final long amount) {
        return spec -> {
            final var fromId = asId(from, spec);
            final var toId = spec.registry().keyAliasIdFor(to);
            return xFromTo(fromId, toId, amount);
        };
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromAccountToAlias(
            final String from, final String to, final long amount, final boolean toEvmAddress) {
        return spec -> {
            final var fromId = asId(from, spec);
            if (toEvmAddress) {
                final var key = spec.registry().getKey(to);
                throwIfNotEcdsa(key);
                final var address = EthSigsUtils.recoverAddressFromPubKey(
                        key.getECDSASecp256K1().toByteArray());
                final var toAccId = AccountID.newBuilder()
                        .setAlias(ByteString.copyFrom(address))
                        .build();
                return xFromTo(fromId, toAccId, amount);
            }
            final var toId = spec.registry().keyAliasIdFor(to);
            return xFromTo(fromId, toId, amount);
        };
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromToWithAlias(
            final String from, final String to, final ToLongFunction<HapiSpec> amountFn) {
        return spec -> {
            final long amount = amountFn.applyAsLong(spec);
            final AccountID toAccount;
            final AccountID fromAccount;

            if (transferToKey) {
                fromAccount = asIdForKeyLookUp(from, spec);
                toAccount = asIdForKeyLookUp(to, spec);
            } else {
                fromAccount = asId(from, spec);
                toAccount = asId(to, spec);
            }

            return TransferList.newBuilder()
                    .addAllAccountAmounts(Arrays.asList(
                            AccountAmount.newBuilder()
                                    .setAccountID(toAccount)
                                    .setAmount(amount)
                                    .build(),
                            AccountAmount.newBuilder()
                                    .setAccountID(fromAccount)
                                    .setAmount(-1L * amount)
                                    .build()))
                    .build();
        };
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromToWithInvalidAmounts(
            final String from, final String to, final long amount) {
        return tinyBarsFromToWithInvalidAmounts(from, to, ignore -> amount);
    }

    public static Function<HapiSpec, TransferList> tinyBarsFromToWithInvalidAmounts(
            final String from, final String to, final ToLongFunction<HapiSpec> amountFn) {
        return spec -> {
            final long amount = amountFn.applyAsLong(spec);
            final AccountID toAccount = asId(to, spec);
            final AccountID fromAccount = asId(from, spec);
            return TransferList.newBuilder()
                    .addAllAccountAmounts(Arrays.asList(
                            AccountAmount.newBuilder()
                                    .setAccountID(toAccount)
                                    .setAmount(amount)
                                    .build(),
                            AccountAmount.newBuilder()
                                    .setAccountID(fromAccount)
                                    .setAmount(-1L * amount + 1L)
                                    .build()))
                    .build();
        };
    }

    private static TransferList xFromTo(final AccountID from, final AccountID to, final long amount) {
        return TransferList.newBuilder()
                .addAllAccountAmounts(List.of(
                        AccountAmount.newBuilder()
                                .setAccountID(from)
                                .setAmount(-amount)
                                .build(),
                        AccountAmount.newBuilder()
                                .setAccountID(to)
                                .setAmount(+amount)
                                .build()))
                .build();
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(final HapiSpec spec) throws Throwable {
        final CryptoTransferTransactionBody opBody = spec.txns()
                .<CryptoTransferTransactionBody, CryptoTransferTransactionBody.Builder>body(
                        CryptoTransferTransactionBody.class, b -> {
                            if (explicitDef.isPresent()) {
                                explicitDef.get().accept(spec, b);
                            } else if (hbarOnlyProvider != MISSING_HBAR_ONLY_PROVIDER) {
                                b.setTransfers(hbarOnlyProvider.apply(spec));
                            } else {
                                final var xfers = transfersAllFor(spec);
                                for (final TokenTransferList scopedXfers : xfers) {
                                    if (scopedXfers.getToken() == HBAR_SENTINEL_TOKEN_ID) {
                                        b.setTransfers(TransferList.newBuilder()
                                                .addAllAccountAmounts(scopedXfers.getTransfersList())
                                                .build());
                                    } else {
                                        b.addTokenTransfers(scopedXfers);
                                    }
                                }
                                misconfigureIfRequested(b, spec);
                            }
                        });
        return builder -> builder.setCryptoTransfer(opBody);
    }

    private void FungibleTokenPartitionMove(final CryptoTransferTransactionBody.Builder b, final HapiSpec spec) {
        final var extra = (this.PARTITION_TOKEN_OPERATION_MODE == "PartitionMoveWithoutUserSignature")
                ? appendedPartitionFromTo.get()
                : appendedPartitionFromTowithSign.get();
        final var involved = extra.getLeft();
        final var fromAccount = TxnUtils.asId(involved[0], spec);
        final var fromPartitionToken = TxnUtils.asTokenId(involved[1], spec);
        final var toAccount = (this.PARTITION_TOKEN_OPERATION_MODE == "PartitionMoveWithoutUserSignature")
                ? fromAccount
                : TxnUtils.asId(involved[2], spec);
        final var toPartitionToken = (this.PARTITION_TOKEN_OPERATION_MODE == "PartitionMoveWithoutUserSignature")
                ? TxnUtils.asTokenId(involved[2], spec)
                : TxnUtils.asTokenId(involved[3], spec);
        final var amount = extra.getRight();
        final var reduceUnits = TokenTransferList.newBuilder()
                .setToken(fromPartitionToken)
                .addTransfers(
                        AccountAmount.newBuilder().setAccountID(fromAccount).setAmount(-amount));
        b.addTokenTransfers(reduceUnits);
        final var appendUnits = TokenTransferList.newBuilder()
                .setToken(toPartitionToken)
                .addTransfers(AccountAmount.newBuilder().setAccountID(toAccount).setAmount(+amount));
        b.addTokenTransfers(appendUnits);
    }

    private void NonFungibleTokenPartitionMove(final CryptoTransferTransactionBody.Builder b, final HapiSpec spec) {
        final var extra = appendedNFTPartitionFromTo.get();
        final var involved = extra.getLeft();
        final var fromAccount = TxnUtils.asId(involved[0], spec);
        final var fromPartitionToken = TxnUtils.asTokenId(involved[1], spec);
        final var toAccount = fromAccount;
        final var toPartitionToken = TxnUtils.asTokenId(involved[2], spec);
        final var reduceUnits = TokenTransferList.newBuilder().setToken(fromPartitionToken);
        final var appendUnits = TokenTransferList.newBuilder().setToken(toPartitionToken);
        for (long serialNum : serialNums) {
            reduceUnits.addTransfers(
                    AccountAmount.newBuilder().setAccountID(fromAccount).setAmount(-serialNum));
            b.addTokenTransfers(reduceUnits);
            appendUnits.addTransfers(
                    AccountAmount.newBuilder().setAccountID(toAccount).setAmount(+serialNum));
            b.addTokenTransfers(appendUnits);
        }
    }

    private void misconfigureIfRequested(final CryptoTransferTransactionBody.Builder b, final HapiSpec spec) {
        if (tokenWithEmptyTransferAmounts.isPresent()) {
            final var empty = tokenWithEmptyTransferAmounts.get();
            final var emptyToken = TxnUtils.asTokenId(empty, spec);
            final var emptyList = TokenTransferList.newBuilder().setToken(emptyToken);
            b.addTokenTransfers(emptyList);
        }
        if (appendedFromTo.isPresent()) {
            final var extra = appendedFromTo.get();
            final var involved = extra.getLeft();
            final var token = TxnUtils.asTokenId(involved[0], spec);
            final var sender = TxnUtils.asId(involved[1], spec);
            final var receiver = TxnUtils.asId(involved[2], spec);
            final var amount = extra.getRight();
            final var appendList = TokenTransferList.newBuilder()
                    .setToken(token)
                    .addTransfers(
                            AccountAmount.newBuilder().setAccountID(sender).setAmount(-amount))
                    .addTransfers(
                            AccountAmount.newBuilder().setAccountID(receiver).setAmount(+amount));
            b.addTokenTransfers(appendList);
        }

        if (appendedPartitionFromTo.isPresent() || appendedPartitionFromTowithSign.isPresent()) {
            FungibleTokenPartitionMove(b, spec);
        }

        if (appendedNFTPartitionFromTo.isPresent()) {
            NonFungibleTokenPartitionMove(b, spec);
        }

        if (breakNetZeroTokenChangeInvariant && b.getTokenTransfersCount() > 0) {
            for (int i = 0, n = b.getTokenTransfersCount(); i < n; i++) {
                final var changesHere = b.getTokenTransfersBuilder(i);
                if (changesHere.getTransfersCount() > 0) {
                    final var mutated = changesHere.getTransfersBuilder(0);
                    mutated.setAmount(mutated.getAmount() + 1_234);
                    b.setTokenTransfers(i, changesHere);
                    break;
                }
            }
        }
    }

    @Override
    protected long feeFor(final HapiSpec spec, final Transaction txn, final int numPayerKeys) throws Throwable {
        if (feesObserver.isPresent()) {
            return spec.fees()
                    .forActivityBasedOpWithDetails(
                            HederaFunctionality.CryptoTransfer,
                            (_txn, _svo) ->
                                    usageEstimate(_txn, _svo, spec.fees().tokenTransferUsageMultiplier()),
                            txn,
                            numPayerKeys,
                            feesObserver.get());
        }
        return spec.fees()
                .forActivityBasedOp(
                        HederaFunctionality.CryptoTransfer,
                        (_txn, _svo) -> usageEstimate(_txn, _svo, spec.fees().tokenTransferUsageMultiplier()),
                        txn,
                        numPayerKeys);
    }

    public static FeeData usageEstimate(final TransactionBody txn, final SigValueObj svo, final int multiplier) {
        final var op = txn.getCryptoTransfer();

        final var baseMeta = new BaseTransactionMeta(
                txn.getMemoBytes().size(), op.getTransfers().getAccountAmountsCount());

        int numTokensInvolved = 0, numTokenTransfers = 0, numNftOwnershipChanges = 0;
        for (final var tokenTransfers : op.getTokenTransfersList()) {
            numTokensInvolved++;
            numTokenTransfers += tokenTransfers.getTransfersCount();
            numNftOwnershipChanges += tokenTransfers.getNftTransfersCount();
        }
        final var xferUsageMeta =
                new CryptoTransferMeta(multiplier, numTokensInvolved, numTokenTransfers, numNftOwnershipChanges);

        final var accumulator = new UsageAccumulator();
        cryptoOpsUsage.cryptoTransferUsage(suFrom(svo), xferUsageMeta, baseMeta, accumulator);

        final var feeData = AdapterUtils.feeDataFrom(accumulator);
        return feeData.toBuilder().setSubType(xferUsageMeta.getSubType()).build();
    }

    @Override
    protected HapiCryptoTransfer self() {
        return this;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        final MoreObjects.ToStringHelper helper = super.toStringHelper();
        if (txnSubmitted != null) {
            try {
                final TransactionBody txn = CommonUtils.extractTransactionBody(txnSubmitted);
                helper.add(
                        "transfers",
                        TxnUtils.readableTransferList(txn.getCryptoTransfer().getTransfers()));
                helper.add(
                        "tokenTransfers",
                        TxnUtils.readableTokenTransfers(txn.getCryptoTransfer().getTokenTransfersList()));
            } catch (final Exception ignore) {
            }
        }
        return helper;
    }

    private Function<HapiSpec, List<Key>> tokenAwareVariableDefaultSigners() {
        return spec -> {
            final Set<Key> partyKeys = new HashSet<>();
            final Map<String, Long> partyInvolvements =
                    Optional.ofNullable(tokenAwareProviders).orElse(Collections.emptyList()).stream()
                            .map(TokenMovement::generallyInvolved)
                            .flatMap(List::stream)
                            .collect(groupingBy(Map.Entry::getKey, summingLong(Map.Entry<String, Long>::getValue)));
            partyInvolvements.forEach((account, value) -> {
                final int divider = account.indexOf("|");
                final var key = account.substring(divider + 1);
                if (value < 0 || spec.registry().isSigRequired(key)) {
                    partyKeys.add(spec.registry().getKey(key));
                }
            });
            return new ArrayList<>(partyKeys);
        };
    }

    protected Function<HapiSpec, List<Key>> partitionMoveSignatures() {
        return spec -> {
            final List<Key> partyKeys = new ArrayList<>();
            partyKeys.add(spec.registry().getPartitionMoveKey(this.token));
            if (this.PARTITION_TOKEN_OPERATION_MODE == "PartitionMoveWithUserSignature") {
                final int divider = this.fromAccount.indexOf("|");
                final var key = this.fromAccount.substring(divider + 1);
                if (spec.registry().isSigRequired(key)) {
                    partyKeys.add(spec.registry().getKey(key));
                }
                partyKeys.add(spec.registry().getKey(this.token));
            }
            return partyKeys;
        };
    }

    private Function<HapiSpec, List<Key>> hbarOnlyVariableDefaultSigners() {
        return spec -> {
            final List<Key> partyKeys = new ArrayList<>();
            final TransferList transfers = hbarOnlyProvider.apply(spec);
            final var registry = spec.registry();
            transfers.getAccountAmountsList().forEach(accountAmount -> {
                final var accountId = accountAmount.getAccountID();
                if (!registry.hasAccountIdName(accountId)) {
                    return;
                }
                final var account = spec.registry().getAccountIdName(accountId);
                final boolean isPayer = (accountAmount.getAmount() < 0L);
                if (isPayer || spec.registry().isSigRequired(account)) {
                    partyKeys.add(spec.registry().getKey(account));
                }
            });
            return partyKeys;
        };
    }

    private List<TokenTransferList> transfersAllFor(final HapiSpec spec) {
        return Stream.concat(transfersFor(spec).stream(), transfersForNft(spec).stream())
                .sorted(TOKEN_TRANSFER_LIST_COMPARATOR)
                .toList();
    }

    private List<TokenTransferList> transfersFor(final HapiSpec spec) {
        final Map<TokenID, Pair<Integer, List<AccountAmount>>> aggregated;
        if (fullyAggregateTokenTransfers) {
            aggregated = fullyAggregateTokenTransfersList(spec);
        } else {
            aggregated = aggregateOnTokenIds(spec);
        }

        return aggregated.entrySet().stream()
                .map(entry -> {
                    final var builder = TokenTransferList.newBuilder()
                            .setToken(entry.getKey())
                            .addAllTransfers(entry.getValue().getRight());
                    if (entry.getValue().getLeft() > 0) {
                        builder.setExpectedDecimals(
                                UInt32Value.of(entry.getValue().getLeft().intValue()));
                    }
                    return builder.build();
                })
                .sorted(TOKEN_TRANSFER_LIST_COMPARATOR)
                .toList();
    }

    private Map<TokenID, Pair<Integer, List<AccountAmount>>> aggregateOnTokenIds(final HapiSpec spec) {
        final Map<TokenID, Pair<Integer, List<AccountAmount>>> map = new HashMap<>();
        for (final TokenMovement tm : tokenAwareProviders) {
            if (tm.isFungibleToken()) {
                final var list = tm.specializedFor(spec);

                if (map.containsKey(list.getToken())) {
                    final var existingVal = map.get(list.getToken());
                    final List<AccountAmount> newList = Stream.of(existingVal.getRight(), list.getTransfersList())
                            .flatMap(Collection::stream)
                            .sorted(ACCOUNT_AMOUNT_COMPARATOR)
                            .toList();

                    map.put(list.getToken(), Pair.of(existingVal.getLeft(), newList));
                } else {
                    map.put(list.getToken(), Pair.of(list.getExpectedDecimals().getValue(), list.getTransfersList()));
                }
            }
        }
        return map;
    }

    private Map<TokenID, Pair<Integer, List<AccountAmount>>> fullyAggregateTokenTransfersList(final HapiSpec spec) {
        final Map<TokenID, Pair<Integer, List<AccountAmount>>> map = new HashMap<>();
        for (final TokenMovement xfer : tokenAwareProviders) {
            if (xfer.isFungibleToken()) {
                final var list = xfer.specializedFor(spec);

                if (map.containsKey(list.getToken())) {
                    final var existingVal = map.get(list.getToken());
                    final List<AccountAmount> newList = Stream.of(existingVal.getRight(), list.getTransfersList())
                            .flatMap(Collection::stream)
                            .sorted(ACCOUNT_AMOUNT_COMPARATOR)
                            .toList();

                    map.put(list.getToken(), Pair.of(existingVal.getLeft(), aggregateTransfers(newList)));
                } else {
                    map.put(
                            list.getToken(),
                            Pair.of(
                                    list.getExpectedDecimals().getValue(),
                                    aggregateTransfers(list.getTransfersList())));
                }
            }
        }
        return map;
    }

    private List<AccountAmount> aggregateTransfers(final List<AccountAmount> list) {
        return list.stream()
                .collect(groupingBy(
                        AccountAmount::getAccountID,
                        groupingBy(AccountAmount::getIsApproval, mapping(AccountAmount::getAmount, toList()))))
                .entrySet()
                .stream()
                .flatMap(entry -> {
                    final List<AccountAmount> accountAmounts = new ArrayList<>();
                    for (final var entrySet : entry.getValue().entrySet()) {
                        final var aa = AccountAmount.newBuilder()
                                .setAccountID(entry.getKey())
                                .setIsApproval(entrySet.getKey())
                                .setAmount(entrySet.getValue().stream()
                                        .mapToLong(l -> l)
                                        .sum())
                                .build();
                        accountAmounts.add(aa);
                    }
                    return accountAmounts.stream();
                })
                .sorted(ACCOUNT_AMOUNT_COMPARATOR)
                .toList();
    }

    private List<TokenTransferList> transfersForNft(final HapiSpec spec) {
        final var uniqueCount = tokenAwareProviders.stream()
                .filter(Predicate.not(TokenMovement::isFungibleToken))
                .map(TokenMovement::getToken)
                .distinct()
                .count();
        final Map<TokenID, List<NftTransfer>> aggregated = tokenAwareProviders.stream()
                .filter(Predicate.not(TokenMovement::isFungibleToken))
                .map(p -> p.specializedForNft(spec))
                .collect(Collectors.toMap(
                        TokenTransferList::getToken,
                        TokenTransferList::getNftTransfersList,
                        (left, right) -> Stream.of(left, right)
                                .flatMap(Collection::stream)
                                .sorted(NFT_TRANSFER_COMPARATOR)
                                .toList(),
                        LinkedHashMap::new));
        if (aggregated.size() != 0 && uniqueCount != aggregated.size()) {
            throw new RuntimeException("Aggregation seems to have failed (expected "
                    + uniqueCount
                    + " distinct unique token types, got "
                    + aggregated.size()
                    + ")");
        }
        return aggregated.entrySet().stream()
                .map(entry -> TokenTransferList.newBuilder()
                        .setToken(entry.getKey())
                        .addAllNftTransfers(entry.getValue())
                        .build())
                .sorted(TOKEN_TRANSFER_LIST_COMPARATOR)
                .toList();
    }

    @Override
    protected void updateStateOf(final HapiSpec spec) throws Throwable {
        if (logResolvedStatus) {
            log.info("Resolved to {}", actualStatus);
        }
    }
}
