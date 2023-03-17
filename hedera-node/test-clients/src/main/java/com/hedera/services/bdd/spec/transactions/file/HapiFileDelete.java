/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.transactions.file;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiFileDelete extends HapiTxnOp<HapiFileDelete> {
    static final Logger log = LogManager.getLogger(HapiFileDelete.class);

    private static final String DEFAULT_FILE_NAME = "f";

    private String file = DEFAULT_FILE_NAME;
    private Optional<Supplier<String>> fileSupplier = Optional.empty();
    private boolean shouldPurge = false;

    public HapiFileDelete(String file) {
        this.file = file;
    }

    public HapiFileDelete(Supplier<String> supplier) {
        this.fileSupplier = Optional.of(supplier);
    }

    public HapiFileDelete purging() {
        shouldPurge = true;
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.FileDelete;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        file = fileSupplier.isPresent() ? fileSupplier.get().get() : file;
        var fid = TxnUtils.asFileId(file, spec);
        FileDeleteTransactionBody opBody = spec.txns()
                .<FileDeleteTransactionBody, FileDeleteTransactionBody.Builder>body(
                        FileDeleteTransactionBody.class, builder -> builder.setFileID(fid));
        return builder -> builder.setFileDelete(opBody);
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        return List.of(spec -> spec.registry().getKey(effectivePayer(spec)), spec -> spec.registry()
                .getKey(file));
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiSpec spec) {
        return spec.clients().getFileSvcStub(targetNodeFor(spec), useTls)::deleteFile;
    }

    @Override
    protected void updateStateOf(HapiSpec spec) throws Throwable {
        if (verboseLoggingOn) {
            log.info("Actual status was " + actualStatus);
            log.info("Deleted file {} with ID {} ", file, spec.registry().getFileId(file));
        }
        if (actualStatus != ResponseCodeEnum.SUCCESS) {
            return;
        }
        if (shouldPurge) {
            spec.registry().removeTimestamp(file);
            spec.registry().removeFileId(file);
            spec.registry().removeKey(file);
        }
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return spec.fees()
                .forActivityBasedOp(
                        HederaFunctionality.FileDelete, fileFees::getFileDeleteTxFeeMatrices, txn, numPayerKeys);
    }

    @Override
    protected HapiFileDelete self() {
        return this;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("fileName", file);
    }
}
