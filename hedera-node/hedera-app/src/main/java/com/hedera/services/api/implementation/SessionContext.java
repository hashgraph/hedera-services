package com.hedera.services.api.implementation;

import com.google.protobuf.Parser;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

// Put the per-thread stuff here like parsers
public record SessionContext(
        Parser<Query> queryParser,
        Parser<Transaction> txParser,
        Parser<SignedTransaction> signedParser,
        Parser<TransactionBody> txBodyParser) {
}
