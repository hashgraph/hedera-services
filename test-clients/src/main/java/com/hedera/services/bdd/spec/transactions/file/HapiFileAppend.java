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
package com.hedera.services.bdd.spec.transactions.file;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.currExpiry;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.file.FileAppendMeta;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class HapiFileAppend extends HapiTxnOp<HapiFileAppend> {
    private final String file;
    private Optional<byte[]> contents = Optional.empty();
    private Optional<Supplier<byte[]>> contentsSupplier = Optional.empty();
    private Optional<String> path = Optional.empty();

    private Optional<Consumer<FileID>> preAppendCb = Optional.empty();
    private Optional<Consumer<ResponseCodeEnum>> postAppendCb = Optional.empty();
    @Nullable private UploadProgress uploadProgress;
    private int appendNum;

    public HapiFileAppend(String file) {
        this.file = file;
    }

    public HapiFileAppend trackingProgressIn(
            final UploadProgress uploadProgress, final int appendNum) {
        this.uploadProgress = uploadProgress;
        this.appendNum = appendNum;
        return this;
    }

    public HapiFileAppend alertingPre(Consumer<FileID> preCb) {
        preAppendCb = Optional.of(preCb);
        return this;
    }

    public HapiFileAppend alertingPost(Consumer<ResponseCodeEnum> postCb) {
        postAppendCb = Optional.of(postCb);
        return this;
    }

    public HapiFileAppend content(byte[] data) {
        contents = Optional.of(data);
        return this;
    }

    public HapiFileAppend content(String data) {
        contents = Optional.of(data.getBytes());
        return this;
    }

    public HapiFileAppend path(String to) {
        path = Optional.of(to);
        return this;
    }

    public HapiFileAppend contentFrom(Supplier<byte[]> more) {
        contentsSupplier = Optional.of(more);
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.FileAppend;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
        if (contentsSupplier.isPresent()) {
            contents = Optional.of(contentsSupplier.get().get());
        } else if (path.isPresent()) {
            contents = Optional.of(Files.toByteArray(new File(path.get())));
        }
        var fid = TxnUtils.asFileId(file, spec);
        FileAppendTransactionBody opBody =
                spec.txns()
                        .<FileAppendTransactionBody, FileAppendTransactionBody.Builder>body(
                                FileAppendTransactionBody.class,
                                builder -> {
                                    builder.setFileID(fid);
                                    contents.ifPresent(
                                            b -> builder.setContents(ByteString.copyFrom(b)));
                                });
        preAppendCb.ifPresent(cb -> cb.accept(fid));
        return b -> b.setFileAppend(opBody);
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
        return spec.clients().getFileSvcStub(targetNodeFor(spec), useTls)::appendContent;
    }

    @Override
    protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        var expiry =
                payer.isPresent()
                        ? currExpiry(file, spec, payerToUse(payer.get(), spec))
                        : currExpiry(file, spec);
        FeeCalculator.ActivityMetrics metricsCalc =
                (txBody, sigUsage) -> usageEstimate(txBody, sigUsage, expiry.getSeconds());
        return spec.fees()
                .forActivityBasedOp(HederaFunctionality.FileAppend, metricsCalc, txn, numPayerKeys);
    }

    @Override
    protected List<Function<HapiApiSpec, Key>> defaultSigners() {
        return List.of(
                spec -> spec.registry().getKey(effectivePayer(spec)),
                spec -> spec.registry().getKey(file));
    }

    @Override
    protected void updateStateOf(HapiApiSpec spec) throws Throwable {
        postAppendCb.ifPresent(cb -> cb.accept(actualStatus));
        if (actualStatus == SUCCESS && uploadProgress != null) {
            uploadProgress.markFinished(appendNum);
        }
    }

    @Override
    protected HapiFileAppend self() {
        return this;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("fileName", file);
    }

    private String payerToUse(String designated, HapiApiSpec spec) {
        return isPrivileged(designated, spec) ? spec.setup().genesisAccountName() : designated;
    }

    private boolean isPrivileged(String account, HapiApiSpec spec) {
        return account.equals(spec.setup().addressBookControlName())
                || account.equals(spec.setup().exchangeRatesControlName())
                || account.equals(spec.setup().feeScheduleControlName())
                || account.equals(spec.setup().strongControlName());
    }

    private FeeData usageEstimate(TransactionBody txn, SigValueObj svo, long expiry) {
        final var op = txn.getFileAppend();
        final var baseMeta = new BaseTransactionMeta(txn.getMemoBytes().size(), 0);
        final var effectiveNow = txn.getTransactionID().getTransactionValidStart().getSeconds();
        final var opMeta = new FileAppendMeta(op.getContents().size(), expiry - effectiveNow);

        final var accumulator = new UsageAccumulator();
        fileOpsUsage.fileAppendUsage(suFrom(svo), opMeta, baseMeta, accumulator);

        return AdapterUtils.feeDataFrom(accumulator);
    }
}
