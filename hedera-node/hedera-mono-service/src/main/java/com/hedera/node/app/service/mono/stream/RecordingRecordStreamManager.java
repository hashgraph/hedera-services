package com.hedera.node.app.service.mono.stream;

import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.stats.MiscRunningAvgs;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.protoToPbj;

public class RecordingRecordStreamManager extends RecordStreamManager {
    public static final String RECORDS_ASSET = "records.txt";
    private final ReplayAssetRecording replayAssetRecording;

    public RecordingRecordStreamManager(
            @NonNull final Platform platform,
            @NonNull final MiscRunningAvgs runningAvgs,
            @NonNull final NodeLocalProperties nodeLocalProperties,
            @NonNull final String accountMemo,
            @NonNull final Hash initialHash,
            @NonNull final RecordStreamType streamType,
            @NonNull final GlobalDynamicProperties globalDynamicProperties,
            @NonNull final ReplayAssetRecording replayAssetRecording
    ) throws NoSuchAlgorithmException, IOException {
        super(platform, runningAvgs, nodeLocalProperties, accountMemo, initialHash, streamType, globalDynamicProperties);
        this.replayAssetRecording = replayAssetRecording;
    }

    @Override
    public void addRecordStreamObject(@NonNull final RecordStreamObject recordStreamObject) {
        super.addRecordStreamObject(recordStreamObject);
        final var pbjRecord = protoToPbj(recordStreamObject.getTransactionRecord(), TransactionRecord.class);
        final var encodedRecord = PbjConverter.toB64Encoding(pbjRecord, TransactionRecord.class);
        replayAssetRecording.appendPlaintextToAsset(RECORDS_ASSET, encodedRecord);
    }
}
