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

package com.hedera.services.bdd.spec.fees;

import static com.hedera.services.bdd.spec.HapiPropertySource.asFileString;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTransferList;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.toReadableString;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.transactions.TxnFactory;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.FileGetContentsQuery;
import com.hederahashgraph.api.proto.java.FileGetContentsResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FeesAndRatesProvider {
    private static final Logger log = LogManager.getLogger(FeesAndRatesProvider.class);

    private static final int NUM_DOWNLOAD_ATTEMPTS = 10;

    private static final BigDecimal USD_DIVISOR = BigDecimal.valueOf(100L);
    private static final BigDecimal HBAR_DIVISOR = BigDecimal.valueOf(100_000_000L);

    private TxnFactory txns;
    private KeyFactory keys;
    private HapiSpecSetup setup;
    private HapiApiClients clients;
    private HapiSpecRegistry registry;
    private static FeeSchedule feeSchedule;
    private static ExchangeRateSet rateSet;
    private final ScheduleTypePatching typePatching = new ScheduleTypePatching();

    public FeesAndRatesProvider(
            TxnFactory txns, KeyFactory keys, HapiSpecSetup setup, HapiApiClients clients, HapiSpecRegistry registry) {
        this.txns = txns;
        this.keys = keys;
        this.setup = setup;
        this.clients = clients;
        this.registry = registry;
    }

    public void init() throws IOException, ReflectiveOperationException, GeneralSecurityException {
        if (setup.useFixedFee()) {
            return;
        }
        if (setup.clientExchangeRatesFromDisk()) {
            readRateSet();
        } else {
            downloadRateSet();
        }
        if (setup.clientFeeScheduleFromDisk()) {
            readFeeSchedule();
        } else {
            downloadFeeSchedule();
        }
    }

    public FeeSchedule currentSchedule() {
        return feeSchedule;
    }

    public ExchangeRate rates() {
        return activeRates();
    }

    public void updateRateSet(ExchangeRateSet newSet) {
        rateSet = newSet;
        final String message = String.format("Updating rates! Now :: %s", rateSetAsString(newSet));
        log.info(message);
    }

    public ExchangeRateSet rateSet() {
        return rateSet;
    }

    private ExchangeRate activeRates() {
        boolean useCurrent = Instant.now().getEpochSecond()
                < rateSet.getCurrentRate().getExpirationTime().getSeconds();

        return useCurrent ? rateSet.getCurrentRate() : rateSet.getNextRate();
    }

    private void readRateSet() throws IOException {
        File f = new File(setup.clientExchangeRatesPath());
        byte[] bytes = Files.readAllBytes(f.toPath());
        rateSet = ExchangeRateSet.parseFrom(bytes);
        String newSetAsString = rateSetAsString(rateSet);

        final String message =
                String.format("The exchange rates from '%s' are :: %s", f.getAbsolutePath(), newSetAsString);
        log.info(message);
    }

    public boolean hasRateSet() {
        return rateSet != null;
    }

    private void downloadRateSet() throws IOException, GeneralSecurityException, ReflectiveOperationException {
        long queryFee = lookupDownloadFee(setup.exchangeRatesId());
        FileGetContentsResponse response = downloadWith(queryFee, false, setup.exchangeRatesId());
        byte[] bytes = response.getFileContents().getContents().toByteArray();
        rateSet = ExchangeRateSet.parseFrom(bytes);
        String newSetAsString = rateSetAsString(rateSet);

        final String message = String.format("The exchange rates are :: %s", newSetAsString);
        log.info(message);
    }

    private void readFeeSchedule() throws IOException {
        File f = new File(setup.clientFeeSchedulePath());
        byte[] bytes = Files.readAllBytes(f.toPath());
        CurrentAndNextFeeSchedule wrapper = CurrentAndNextFeeSchedule.parseFrom(bytes);
        feeSchedule = wrapper.getCurrentFeeSchedule();
        final String message = String.format(
                "The fee schedule from '%s' covers %s ops.",
                f.getAbsolutePath(), feeSchedule.getTransactionFeeScheduleList().size());
        log.info(message);
    }

    private void downloadFeeSchedule() throws IOException, GeneralSecurityException, ReflectiveOperationException {
        long queryFee = lookupDownloadFee(setup.feeScheduleId());
        FileGetContentsResponse response = downloadWith(queryFee, false, setup.feeScheduleId());
        byte[] bytes = response.getFileContents().getContents().toByteArray();
        CurrentAndNextFeeSchedule wrapper = CurrentAndNextFeeSchedule.parseFrom(bytes);
        feeSchedule = typePatching.withPatchedTypesIfNecessary(wrapper.getCurrentFeeSchedule());
        String message = String.format(
                "The fee schedule covers %s ops.",
                feeSchedule.getTransactionFeeScheduleList().size());
        log.info(message);
    }

    private long lookupDownloadFee(FileID fileId)
            throws IOException, GeneralSecurityException, ReflectiveOperationException {
        return downloadWith(setup.feeScheduleFetchFee(), true, fileId)
                .getHeader()
                .getCost();
    }

    private FileGetContentsResponse downloadWith(long queryFee, boolean costOnly, FileID fid)
            throws IOException, GeneralSecurityException, ReflectiveOperationException {
        int attemptsLeft = NUM_DOWNLOAD_ATTEMPTS;
        ResponseCodeEnum status;
        FileGetContentsResponse response;
        do {
            var payment = defaultPayerSponsored(queryFee);
            var query = downloadQueryWith(payment, costOnly, fid);
            response = clients.getFileSvcStub(setup.defaultNode(), setup.getConfigTLS())
                    .getFileContent(query)
                    .getFileGetContents();
            status = response.getHeader().getNodeTransactionPrecheckCode();
            if (status != OK) {
                log.warn(
                        "'{}' download attempt paid with {} got status {}; retrying {}...",
                        asFileString(fid),
                        toReadableString(payment),
                        status,
                        attemptsLeft);
                simpleSleep(1);
            }
        } while (status != OK && --attemptsLeft > 0);
        if (status != OK) {
            throw new IllegalStateException(
                    String.format("Could not download '%s' final status %s", asFileString(fid), status));
        }
        return response;
    }

    // Should really be in a utility somewhere, but it doesn't seem to be.
    private void simpleSleep(final int secondsToSleep) {
        if (secondsToSleep <= Byte.MAX_VALUE) {
            try {
                TimeUnit.SECONDS.sleep(secondsToSleep);
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Query downloadQueryWith(Transaction payment, boolean costOnly, FileID fileId) {
        FileGetContentsQuery costQuery = FileGetContentsQuery.newBuilder()
                .setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
                .setFileID(fileId)
                .build();
        return Query.newBuilder().setFileGetContents(costQuery).build();
    }

    private Transaction defaultPayerSponsored(long queryFee)
            throws IOException, GeneralSecurityException, ReflectiveOperationException {
        TransferList transfers = asTransferList(tinyBarsFromTo(queryFee, setup.defaultPayer(), setup.defaultNode()));
        CryptoTransferTransactionBody opBody =
                txns.<CryptoTransferTransactionBody, CryptoTransferTransactionBody.Builder>body(
                        CryptoTransferTransactionBody.class, b -> b.setTransfers(transfers));
        Transaction.Builder txnBuilder = txns.getReadyToSign(b -> {
            b.setTransactionID(
                    TransactionID.newBuilder().mergeFrom(b.getTransactionID()).setAccountID(setup.defaultPayer()));
            b.setCryptoTransfer(opBody);
        });

        return keys.signWithFullPrefixEd25519Keys(
                txnBuilder,
                List.of(
                        flattenedMaybeList(registry.getKey(setup.defaultPayerName())),
                        flattenedMaybeList(registry.getKey(setup.defaultPayerName()))));
    }

    private Key flattenedMaybeList(final Key k) {
        if (k.hasKeyList()) {
            return k.getKeyList().getKeys(0);
        } else {
            return k;
        }
    }

    public ExchangeRateSet rateSetWith(int curHbarEquiv, int curCentEquiv) {
        ExchangeRate.Builder curRate =
                rateSet.getCurrentRate().toBuilder().setHbarEquiv(curHbarEquiv).setCentEquiv(curCentEquiv);

        ExchangeRateSet perturbedSet =
                rateSet.toBuilder().setCurrentRate(curRate).build();
        String message = String.format("Computed a new rate set :: %s", rateSetAsString(perturbedSet));
        log.info(message);

        return perturbedSet;
    }

    public ExchangeRateSet rateSetWith(int curHbarEquiv, int curCentEquiv, int nextHbarEquiv, int nextCentEquiv) {
        ExchangeRate.Builder curRate =
                rateSet.getCurrentRate().toBuilder().setHbarEquiv(curHbarEquiv).setCentEquiv(curCentEquiv);

        ExchangeRate.Builder nextRate =
                rateSet.getCurrentRate().toBuilder().setHbarEquiv(nextHbarEquiv).setCentEquiv(nextCentEquiv);

        ExchangeRateSet perturbedSet = rateSet.toBuilder()
                .setCurrentRate(curRate)
                .setNextRate(nextRate)
                .build();
        String message = String.format("Computed a new rate set :: %s", rateSetAsString(perturbedSet));
        log.info(message);

        return perturbedSet;
    }

    public double toUsdWithActiveRates(long tb) {
        return BigDecimal.valueOf(tb)
                .divide(HBAR_DIVISOR)
                .divide(USD_DIVISOR)
                .multiply(BigDecimal.valueOf(activeRates().getCentEquiv()))
                .divide(BigDecimal.valueOf(activeRates().getHbarEquiv()), 5, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public long toTbWithActiveRates(long tc) {
        return tc * activeRates().getHbarEquiv() / activeRates().getCentEquiv();
    }

    public static String rateSetAsString(ExchangeRateSet set) {
        return "[Current hbar/cent equiv "
                + set.getCurrentRate().getHbarEquiv()
                + " <-> "
                + set.getCurrentRate().getCentEquiv()
                + " expiry @ "
                + set.getCurrentRate().getExpirationTime().getSeconds()
                + ", "
                + "Next hbar/cent equiv "
                + set.getNextRate().getHbarEquiv()
                + " <-> "
                + set.getNextRate().getCentEquiv()
                + " expiry @ "
                + set.getNextRate().getExpirationTime().getSeconds()
                + "]";
    }
}
