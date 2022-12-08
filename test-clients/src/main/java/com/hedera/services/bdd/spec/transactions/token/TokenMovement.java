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
package com.hedera.services.bdd.spec.transactions.token;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asIdForKeyLookUp;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTokenId;

import com.google.protobuf.UInt32Value;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class TokenMovement {
    private final long amount;
    private final String token;
    private long[] serialNums;
    private Optional<String> sender;
    private Optional<String> receiver;
    private final Optional<List<String>> receivers;
    private final Optional<Function<HapiSpec, String>> senderFn;
    private final Optional<Function<HapiSpec, String>> receiverFn;
    private int expectedDecimals;
    private boolean isApproval = false;

    public static final TokenID HBAR_SENTINEL_TOKEN_ID = TokenID.getDefaultInstance();

    TokenMovement(
            String token,
            Optional<String> sender,
            long amount,
            Optional<String> receiver,
            Optional<List<String>> receivers) {
        this.token = token;
        this.sender = sender;
        this.amount = amount;
        this.receiver = receiver;
        this.receivers = receivers;

        senderFn = Optional.empty();
        receiverFn = Optional.empty();
        expectedDecimals = -1;
    }

    TokenMovement(
            String token,
            Function<HapiSpec, String> senderFn,
            long amount,
            Function<HapiSpec, String> receiverFn) {
        this.token = token;
        this.senderFn = Optional.of(senderFn);
        this.amount = amount;
        this.receiverFn = Optional.of(receiverFn);

        sender = Optional.empty();
        receiver = Optional.empty();
        receivers = Optional.empty();
        expectedDecimals = -1;
    }

    TokenMovement(
            String token,
            Optional<String> sender,
            long amount,
            long[] serialNums,
            Optional<String> receiver,
            Optional<List<String>> receivers,
            boolean isApproval) {
        this.token = token;
        this.sender = sender;
        this.amount = amount;
        this.serialNums = serialNums;
        this.receiver = receiver;
        this.receivers = receivers;
        this.isApproval = isApproval;

        senderFn = Optional.empty();
        receiverFn = Optional.empty();
        expectedDecimals = -1;
    }

    TokenMovement(
            String token,
            Optional<String> sender,
            long amount,
            Optional<String> receiver,
            Optional<List<String>> receivers,
            int expectedDecimals,
            boolean isApproval) {
        this.token = token;
        this.sender = sender;
        this.amount = amount;
        this.receiver = receiver;
        this.receivers = receivers;
        this.expectedDecimals = expectedDecimals;
        this.isApproval = isApproval;

        senderFn = Optional.empty();
        receiverFn = Optional.empty();
    }

    public String getToken() {
        return token;
    }

    public boolean isTrulyToken() {
        return token != HapiSuite.HBAR_TOKEN_SENTINEL;
    }

    public boolean isFungibleToken() {
        return serialNums == null;
    }

    public List<Map.Entry<String, Long>> generallyInvolved() {
        if (sender.isPresent()) {
            Map.Entry<String, Long> senderEntry =
                    new AbstractMap.SimpleEntry<>(token + "|" + sender.get(), -amount);
            return receiver.isPresent()
                    ? List.of(
                            senderEntry,
                            new AbstractMap.SimpleEntry<>(token + "|" + receiver.get(), +amount))
                    : (receivers.isPresent()
                            ? involvedInDistribution(senderEntry)
                            : List.of(senderEntry));
        }
        return Collections.emptyList();
    }

    private List<Map.Entry<String, Long>> involvedInDistribution(
            Map.Entry<String, Long> senderEntry) {
        List<Map.Entry<String, Long>> all = new ArrayList<>();
        all.add(senderEntry);
        var targets = receivers.get();
        var perTarget = senderEntry.getValue() / targets.size();
        for (String target : targets) {
            all.add(new AbstractMap.SimpleEntry<>(target, perTarget));
        }
        return all;
    }

    public TokenTransferList specializedFor(HapiSpec spec) {
        var scopedTransfers = TokenTransferList.newBuilder();
        var id = isTrulyToken() ? asTokenId(token, spec) : HBAR_SENTINEL_TOKEN_ID;
        scopedTransfers.setToken(id);
        if (senderFn.isPresent()) {
            var specialSender = senderFn.get().apply(spec);
            sender = Optional.of(specialSender);
            scopedTransfers.addTransfers(adjustment(specialSender, -amount, spec));
        } else if (sender.isPresent()) {
            scopedTransfers.addTransfers(adjustment(sender.get(), -amount, spec));
        }
        if (receiverFn.isPresent()) {
            var specialReceiver = receiverFn.get().apply(spec);
            receiver = Optional.of(specialReceiver);
            scopedTransfers.addTransfers(adjustment(specialReceiver, +amount, spec));
        } else if (receiver.isPresent()) {
            scopedTransfers.addTransfers(adjustment(receiver.get(), +amount, spec));
        } else if (receivers.isPresent()) {
            var targets = receivers.get();
            var amountPerReceiver = amount / targets.size();
            for (int i = 0, n = targets.size(); i < n; i++) {
                scopedTransfers.addTransfers(adjustment(targets.get(i), +amountPerReceiver, spec));
            }
        }
        if (expectedDecimals > 0) {
            scopedTransfers.setExpectedDecimals(UInt32Value.of(expectedDecimals));
        }
        return scopedTransfers.build();
    }

    public TokenTransferList specializedForNft(HapiSpec spec) {
        var scopedTransfers = TokenTransferList.newBuilder();
        var id = isTrulyToken() ? asTokenId(token, spec) : HBAR_SENTINEL_TOKEN_ID;
        scopedTransfers.setToken(id);
        if (sender.isPresent() && receiver.isPresent()) {
            for (long serialNum : serialNums) {
                scopedTransfers.addNftTransfers(
                        adjustment(sender.get(), receiver.get(), serialNum, spec));
            }
        }

        return scopedTransfers.build();
    }

    private AccountAmount adjustment(String name, long value, HapiSpec spec) {
        return AccountAmount.newBuilder()
                .setAccountID(asIdForKeyLookUp(name, spec))
                .setAmount(value)
                .setIsApproval(isApproval)
                .build();
    }

    private NftTransfer adjustment(
            String senderName, String receiverName, long value, HapiSpec spec) {
        return NftTransfer.newBuilder()
                .setSenderAccountID(asIdForKeyLookUp(senderName, spec))
                .setReceiverAccountID(asIdForKeyLookUp(receiverName, spec))
                .setSerialNumber(value)
                .setIsApproval(isApproval)
                .build();
    }

    public static class Builder {
        private final long amount;
        private long[] serialNums;
        private final String token;
        private int expectedDecimals;
        private boolean isAllowance = false;

        public Builder(long amount, String token, boolean isAllowance) {
            this.token = token;
            this.amount = amount;
            this.isAllowance = isAllowance;
        }

        public Builder(long amount, String token) {
            this.token = token;
            this.amount = amount;
        }

        public Builder(long amount, String token, int expectedDecimals) {
            this.token = token;
            this.amount = amount;
            this.expectedDecimals = expectedDecimals;
        }

        public Builder(long amount, String token, long... serialNums) {
            this.amount = amount;
            this.token = token;
            this.serialNums = serialNums;
        }

        public Builder(long amount, String token, boolean isAllowance, long... serialNums) {
            this.amount = amount;
            this.token = token;
            this.isAllowance = isAllowance;
            this.serialNums = serialNums;
        }

        public TokenMovement between(String sender, String receiver) {
            return new TokenMovement(
                    token,
                    Optional.of(sender),
                    amount,
                    serialNums,
                    Optional.of(receiver),
                    Optional.empty(),
                    isAllowance);
        }

        public TokenMovement betweenWithDecimals(String sender, String receiver) {
            return new TokenMovement(
                    token,
                    Optional.of(sender),
                    amount,
                    Optional.of(receiver),
                    Optional.empty(),
                    expectedDecimals,
                    isAllowance);
        }

        public TokenMovement between(
                Function<HapiSpec, String> senderFn, Function<HapiSpec, String> receiverFn) {
            return new TokenMovement(token, senderFn, amount, receiverFn);
        }

        public TokenMovement distributing(String sender, String... receivers) {
            return new TokenMovement(
                    token,
                    Optional.of(sender),
                    amount,
                    Optional.empty(),
                    Optional.of(List.of(receivers)));
        }

        public TokenMovement from(String magician) {
            return new TokenMovement(
                    token, Optional.of(magician), amount, Optional.empty(), Optional.empty());
        }

        public TokenMovement to(String receiver) {
            return new TokenMovement(
                    token, Optional.empty(), amount, Optional.of(receiver), Optional.empty());
        }

        public TokenMovement empty() {
            return new TokenMovement(
                    token, Optional.empty(), amount, Optional.empty(), Optional.empty());
        }
    }

    public static Builder moving(long amount, String token) {
        return new Builder(amount, token);
    }

    public static Builder movingWithAllowance(long amount, String token) {
        return new Builder(amount, token, true);
    }

    public static Builder movingWithDecimals(long amount, String token, int expectedDecimals) {
        return new Builder(amount, token, expectedDecimals);
    }

    public static Builder movingUnique(String token, long... serialNums) {
        return new Builder(1, token, serialNums);
    }

    public static Builder movingUniqueWithAllowance(String token, long... serialNums) {
        return new Builder(1, token, true, serialNums);
    }

    public static Builder movingHbar(long amount) {
        return new Builder(amount, HapiSuite.HBAR_TOKEN_SENTINEL);
    }

    public static Builder movingHbarWithAllowance(long amount) {
        return new Builder(amount, HapiSuite.HBAR_TOKEN_SENTINEL, true);
    }
}
