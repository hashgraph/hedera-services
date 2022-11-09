package com.hedera.services.api.implementation.workflows.ingest;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

public interface IngestChecker {
    void checkSignedTransaction(SignedTransaction tx) throws PreCheckException;
    void checkTransactionBody(TransactionBody txBody, Account account) throws PreCheckException;
    void checkSignatures(ByteString signedTransactionBytes, SignatureMap signatureMap, JKey key) throws PreCheckException;
    void checkThrottles(TransactionBody.DataCase type) throws ThrottleException;
}
