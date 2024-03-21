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

package com.hedera.services.bdd.spec.queries.contract;

import static com.hedera.services.bdd.spec.HapiSpec.ensureDir;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.rethrowSummaryError;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

import com.google.common.base.MoreObjects;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSink;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.hedera.node.app.hapi.utils.fee.FeeBuilder;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.ErroringAssertsProvider;
import com.hedera.services.bdd.spec.exceptions.HapiQueryCheckStateException;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.ContractGetRecordsQuery;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class HapiGetContractRecords extends HapiQueryOp<HapiGetContractRecords> {
    private static final Logger log = LogManager.getLogger(HapiGetContractRecords.class);
    private Optional<String> snapshotDirPath = Optional.empty();
    private Optional<String> saveRecordNum = Optional.empty();
    private Optional<String> expectationsDirPath = Optional.empty();
    private Optional<BiConsumer<Logger, List<TransactionRecord>>> customLog = Optional.empty();

    private final String contract;
    private Optional<ErroringAssertsProvider<List<TransactionRecord>>> expectation = Optional.empty();

    public HapiGetContractRecords has(ErroringAssertsProvider<List<TransactionRecord>> provider) {
        expectation = Optional.of(provider);
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.ContractGetRecords;
    }

    @Override
    protected HapiGetContractRecords self() {
        return this;
    }

    public HapiGetContractRecords(String contract) {
        this.contract = contract;
    }

    public HapiGetContractRecords withLogging(BiConsumer<Logger, List<TransactionRecord>> customLog) {
        verboseLoggingOn = true;
        this.customLog = Optional.of(customLog);
        return this;
    }

    public HapiGetContractRecords savingTo(String dirPath) {
        snapshotDirPath = Optional.of(dirPath);
        return this;
    }

    public HapiGetContractRecords saveRecordNumToRegistry(String key) {
        saveRecordNum = Optional.of(key);
        return this;
    }

    public HapiGetContractRecords checkingAgainst(String dirPath) {
        expectationsDirPath = Optional.of(dirPath);
        return this;
    }

    @Override
    protected void assertExpectationsGiven(HapiSpec spec) throws Throwable {
        if (expectation.isPresent()) {
            List<TransactionRecord> actualRecords =
                    response.getContractGetRecordsResponse().getRecordsList();
            List<Throwable> errors = expectation.get().assertsFor(spec).errorsIn(actualRecords);
            rethrowSummaryError(log, "Bad contract records!", errors);
        }
    }

    @Override
    protected void submitWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getContractRecordsQuery(spec, payment, false);
        response = spec.clients().getScSvcStub(targetNodeFor(spec), useTls).getTxRecordByContractID(query);
        List<TransactionRecord> records =
                response.getContractGetRecordsResponse().getRecordsList();
        if (verboseLoggingOn) {
            if (customLog.isPresent()) {
                customLog.get().accept(log, records);
            } else {
                log.info(records);
            }
        }
        if (snapshotDirPath.isPresent()) {
            saveSnapshots(spec, records);
        }
        if (saveRecordNum.isPresent()) {
            spec.registry().saveIntValue(saveRecordNum.get(), records.size());
        }
        if (expectationsDirPath.isPresent()) {
            checkExpectations(spec, records);
        }
    }

    @Override
    protected long lookupCostWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getContractRecordsQuery(spec, payment, true);
        Response response =
                spec.clients().getScSvcStub(targetNodeFor(spec), useTls).getTxRecordByContractID(query);
        return costFrom(response);
    }

    private Query getContractRecordsQuery(HapiSpec spec, Transaction payment, boolean costOnly) {
        var id = TxnUtils.asContractId(contract, spec);
        ContractGetRecordsQuery opQuery = ContractGetRecordsQuery.newBuilder()
                .setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
                .setContractID(id)
                .build();
        return Query.newBuilder().setContractGetRecords(opQuery).build();
    }

    @Override
    protected long costOnlyNodePayment(HapiSpec spec) throws Throwable {
        return spec.fees().forOp(HederaFunctionality.ContractGetRecords, FeeBuilder.getCostForQueryByIdOnly());
    }

    @Override
    protected boolean needsPayment() {
        return true;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper().add("contract", contract);
        Optional.ofNullable(response)
                .ifPresent(r -> helper.add(
                        "# records",
                        r.getContractGetRecordsResponse().getRecordsList().size()));
        return helper;
    }

    private void saveSnapshots(HapiSpec spec, List<TransactionRecord> records) {
        String specSnapshotDir = specScopedDir(spec, snapshotDirPath);
        ensureDir(specSnapshotDir);
        String snapshotDir = specSnapshotDir + "/" + contract;
        ensureDir(snapshotDir);

        try {
            File countFile = new File(snapshotDir + "/n.txt");
            CharSink charSink = Files.asCharSink(countFile, Charset.forName("UTF-8"));
            int n = records.size();
            charSink.write("" + n);

            for (int i = 0; i < n; i++) {
                File recordFile = new File(snapshotDir + "/record" + i + ".bin");
                ByteSink byteSink = Files.asByteSink(recordFile);
                byteSink.write(records.get(i).toByteArray());
            }
            String message = String.format("Saved %d records to %s", n, snapshotDir);
            log.info(message);
        } catch (Exception e) {
            log.error("Couldn't save record snapshots!", e);
        }
    }

    private String specScopedDir(HapiSpec spec, Optional<String> prefix) {
        return prefix.map(d -> d + "/" + spec.getName()).get();
    }

    private void checkExpectations(HapiSpec spec, List<TransactionRecord> records) throws Throwable {
        String specExpectationsDir = specScopedDir(spec, expectationsDirPath);
        try {
            String expectationsDir = specExpectationsDir + "/" + contract;
            File countFile = new File(expectationsDir + "/n.txt");
            CharSource charSource = Files.asCharSource(countFile, Charset.forName("UTF-8"));
            int n = Integer.parseInt(charSource.readFirstLine());
            Assertions.assertEquals(n, records.size(), "Bad number of records!");
            for (int i = 0; i < n; i++) {
                File recordFile = new File(expectationsDir + "/record" + i + ".bin");
                ByteSource byteSource = Files.asByteSource(recordFile);
                TransactionRecord expected = TransactionRecord.parseFrom(byteSource.read());
                Assertions.assertEquals(expected, records.get(i), "Wrong record #" + i);
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.error("Something amiss with the expected records...", e);
            } else {
                log.error("Something amiss with the expected records {}", records);
            }
            throw new HapiQueryCheckStateException("Impossible to meet expectations (on records)!");
        }
    }
}
