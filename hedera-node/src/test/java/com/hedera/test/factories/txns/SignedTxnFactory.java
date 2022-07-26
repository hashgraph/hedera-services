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
package com.hedera.test.factories.txns;

import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;
import static com.hedera.test.utils.IdUtils.asAccount;
import static java.util.Collections.EMPTY_LIST;
import static java.util.stream.Collectors.toList;

import com.google.protobuf.ByteString;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.sigs.SigFactory;
import com.hedera.test.factories.sigs.SigMapGenerator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public abstract class SignedTxnFactory<T extends SignedTxnFactory<T>> {
    public static final String DEFAULT_MEMO = "This is something else.";
    public static final String DEFAULT_NODE_ID = "0.0.3";
    public static final AccountID DEFAULT_NODE = asAccount(DEFAULT_NODE_ID);
    public static final String DEFAULT_PAYER_ID = "0.0.13257";
    public static final String MASTER_PAYER_ID = "0.0.50";
    public static final String TREASURY_PAYER_ID = "0.0.2";
    public static final AccountID DEFAULT_PAYER = asAccount(DEFAULT_PAYER_ID);
    public static final String STAKING_FUND_ID = "0.0.800";
    public static final AccountID STAKING_FUND = asAccount("0.0.800");
    public static final KeyTree DEFAULT_PAYER_KT = KeyTree.withRoot(list(ed25519()));
    public static final Instant DEFAULT_VALID_START = Instant.now();
    public static final Integer DEFAULT_VALID_DURATION = 60;
    public static final SigFactory DEFAULT_SIG_FACTORY = new SigFactory();

    protected KeyFactory keyFactory = KeyFactory.getDefaultInstance();
    protected FeeBuilder fees = new FeeBuilder();

    String memo = DEFAULT_MEMO;
    String node = DEFAULT_NODE_ID;
    String payer = DEFAULT_PAYER_ID;
    boolean skipTxnId = false;
    boolean skipPayerSig = false;
    Instant start = DEFAULT_VALID_START;
    Integer validDuration = DEFAULT_VALID_DURATION;
    KeyTree payerKt = DEFAULT_PAYER_KT;
    SigFactory sigFactory = DEFAULT_SIG_FACTORY;
    List<KeyTree> otherKts = EMPTY_LIST;
    Optional<Long> customFee = Optional.empty();

    protected abstract T self();

    protected abstract long feeFor(Transaction signedTxn, int numPayerKeys);

    protected abstract void customizeTxn(TransactionBody.Builder txn);

    public Transaction get() throws Throwable {
        Transaction provisional = signed(signableTxn(customFee.orElse(0L)));
        return customFee.isPresent()
                ? provisional
                : signed(signableTxn(feeFor(provisional, payerKt.numLeaves())));
    }

    private Transaction.Builder signableTxn(long fee) {
        TransactionBody.Builder txn = baseTxn();
        customizeTxn(txn);
        txn.setTransactionFee(fee);
        return Transaction.newBuilder()
                .setBodyBytes(ByteString.copyFrom(txn.build().toByteArray()));
    }

    private Transaction signed(Transaction.Builder txnWithSigs) throws Throwable {
        List<KeyTree> signers = allKts();
        return sigFactory.signWithSigMap(txnWithSigs, signers, keyFactory);
    }

    private List<KeyTree> allKts() {
        return Stream.of(
                        skipPayerSig ? Stream.<KeyTree>empty() : Stream.of(payerKt),
                        otherKts.stream())
                .flatMap(Function.identity())
                .collect(toList());
    }

    private TransactionBody.Builder baseTxn() {
        TransactionBody.Builder txn =
                TransactionBody.newBuilder()
                        .setNodeAccountID(asAccount(node))
                        .setTransactionValidDuration(validDuration())
                        .setMemo(memo);
        if (!skipTxnId) {
            txn.setTransactionID(txnId());
        }
        return txn;
    }

    private TransactionID txnId() {
        return TransactionID.newBuilder()
                .setAccountID(asAccount(payer))
                .setTransactionValidStart(validStart())
                .build();
    }

    private Timestamp validStart() {
        return Timestamp.newBuilder()
                .setSeconds(start.getEpochSecond())
                .setNanos(start.getNano())
                .build();
    }

    private Duration validDuration() {
        return Duration.newBuilder().setSeconds(validDuration).build();
    }

    public T payer(String payer) {
        this.payer = payer;
        return self();
    }

    public T payerKt(KeyTree payerKt) {
        this.payerKt = payerKt;
        return self();
    }

    public T nonPayerKts(KeyTree... otherKts) {
        this.otherKts = List.of(otherKts);
        return self();
    }

    public T fee(long amount) {
        customFee = Optional.of(amount);
        return self();
    }

    public T skipPayerSig() {
        skipPayerSig = true;
        return self();
    }

    public T keyFactory(KeyFactory keyFactory) {
        this.keyFactory = keyFactory;
        return self();
    }

    public T sigMapGen(SigMapGenerator sigMapGen) {
        this.sigFactory = new SigFactory(sigMapGen);
        return self();
    }

    public T txnValidStart(Timestamp at) {
        start = Instant.ofEpochSecond(at.getSeconds(), at.getNanos());
        return self();
    }

    public T sansTxnId() {
        skipTxnId = true;
        return self();
    }
}
