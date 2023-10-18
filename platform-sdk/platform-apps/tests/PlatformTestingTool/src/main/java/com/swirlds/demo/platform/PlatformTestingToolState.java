/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import static com.swirlds.common.io.streams.SerializableStreamConstants.NULL_CLASS_ID;
import static com.swirlds.common.metrics.FloatFormats.FORMAT_11_0;
import static com.swirlds.common.units.UnitConstants.MICROSECONDS_TO_NANOSECONDS;
import static com.swirlds.common.units.UnitConstants.NANOSECONDS_TO_MICROSECONDS;
import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.demo.platform.fs.stresstest.proto.TestTransaction.BodyCase.FCMTRANSACTION;
import static com.swirlds.logging.LogMarker.*;
import static com.swirlds.merkle.map.test.lifecycle.SaveExpectedMapHandler.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.protobuf.InvalidProtocolBufferException;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.*;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.utility.ThresholdLimitingHandler;
import com.swirlds.demo.merkle.map.FCMConfig;
import com.swirlds.demo.merkle.map.FCMFamily;
import com.swirlds.demo.merkle.map.FCMTransactionHandler;
import com.swirlds.demo.merkle.map.FCMTransactionUtils;
import com.swirlds.demo.merkle.map.internal.ExpectedFCMFamily;
import com.swirlds.demo.merkle.map.internal.ExpectedFCMFamilyImpl;
import com.swirlds.demo.platform.actions.Action;
import com.swirlds.demo.platform.actions.QuorumResult;
import com.swirlds.demo.platform.actions.QuorumTriggeredAction;
import com.swirlds.demo.platform.expiration.ExpirationRecordEntry;
import com.swirlds.demo.platform.expiration.ExpirationUtils;
import com.swirlds.demo.platform.freeze.FreezeTransactionHandler;
import com.swirlds.demo.platform.fs.stresstest.proto.*;
import com.swirlds.demo.platform.iss.IssLeaf;
import com.swirlds.demo.platform.nft.NftId;
import com.swirlds.demo.platform.nft.NftLedger;
import com.swirlds.demo.platform.nft.NftLedgerStatistics;
import com.swirlds.demo.platform.nft.ReferenceNftLedger;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapKey;
import com.swirlds.demo.virtualmerkle.map.account.AccountVirtualMapValue;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapKey;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode.SmartContractByteCodeMapValue;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapKey;
import com.swirlds.demo.virtualmerkle.map.smartcontracts.data.SmartContractMapValue;
import com.swirlds.demo.virtualmerkle.transaction.handler.VirtualMerkleTransactionHandler;
import com.swirlds.logging.payloads.ApplicationDualStatePayload;
import com.swirlds.logging.payloads.SoftwareVersionPayload;
import com.swirlds.merkle.map.test.lifecycle.EntityType;
import com.swirlds.merkle.map.test.lifecycle.TransactionState;
import com.swirlds.merkle.map.test.lifecycle.TransactionType;
import com.swirlds.merkle.map.test.pta.MapKey;
import com.swirlds.platform.ParameterProvider;
import com.swirlds.platform.Utilities;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * This demo tests platform features and collects statistics on the running of the network and consensus systems. It
 * writes them to the screen, and also saves them to disk in a comma separated value (.csv) file. Each transaction
 * consists of an optional sequence number and random bytes.
 */
public class PlatformTestingToolState extends PartialNaryMerkleInternal implements MerkleInternal, SwirldState {

    private static final long CLASS_ID = 0xc0900cfa7a24db76L;
    private static final Logger logger = LogManager.getLogger(PlatformTestingToolState.class);
    private static final Marker LOGM_DEMO_INFO = MarkerManager.getMarker("DEMO_INFO");
    private static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");
    private static final Marker LOGM_EXPIRATION = MarkerManager.getMarker("EXPIRATION");
    private static final Marker LOGM_STARTUP = MarkerManager.getMarker("STARTUP");
    private static final long EXCEPTION_RATE_THRESHOLD = 10;

    static final String STAT_TIMER_THREAD_NAME = "stat timer PTTState";

    /**
     * The fraction (out of 1.0) of NFT tokens to track in the reference data structure.
     */
    private static final double NFT_TRACKING_FRACTION = 0.001;
    /**
     * Defined in settings. the consensus timestamp of a transaction is guaranteed to be at least this many nanoseconds
     * later than that of the transaction immediately before it in consensus order, and to be a multiple of this
     */
    private static final long minTransTimestampIncrNanos = 1_000;

    /**
     * statistics for handleTransaction
     */
    private static final String HANDLE_TRANSACTION_CATEGORY = "HandleTransaction";

    private static long htCountFCM;
    private static long htFCMSumNano;
    private static final RunningAverageMetric.Config HT_FCM_MICRO_SEC_CONFIG = new RunningAverageMetric.Config(
                    HANDLE_TRANSACTION_CATEGORY, "htFCMTimeMicroSec")
            .withDescription("average handleTransaction (FCM) Time, microseconds");
    private static RunningAverageMetric htFCMMicroSec;
    private static long htCountFCQ;
    private static long htFCQSumNano;

    private static final RunningAverageMetric.Config HT_FCQ_MICRO_SEC_CONFIG = new RunningAverageMetric.Config(
                    HANDLE_TRANSACTION_CATEGORY, "htFCQTimeMicroSec")
            .withDescription("average handleTransaction (FCQ) Time, microseconds");
    private static RunningAverageMetric htFCQMicroSec;

    /**
     * Has init() been called on this copy or an ancestor copy of this object?
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private static long htCountExpiration;
    private static long htFCQExpirationSumMicro;

    private static final RunningAverageMetric.Config HT_FCQ_EXPIRATION_MICRO_SEC_CONFIG =
            new RunningAverageMetric.Config(HANDLE_TRANSACTION_CATEGORY, "htFCQExpirationMicroSec")
                    .withDescription("FCQ Expiration Time per call, microseconds");
    private static RunningAverageMetric htFCQExpirationMicroSec;

    private static final RunningAverageMetric.Config HT_FCM_SIZE_CONFIG = new RunningAverageMetric.Config(
                    HANDLE_TRANSACTION_CATEGORY, "htFCMSize")
            .withDescription("FCM Tree Size (accounts)")
            .withFormat(FORMAT_11_0);
    private static RunningAverageMetric htFCMSize;

    ///////////////////////////////////////////
    // Transaction Handlers
    private static long htFCMAccounts;

    private static final RunningAverageMetric.Config HT_FCQ_SIZE_CONFIG = new RunningAverageMetric.Config(
                    HANDLE_TRANSACTION_CATEGORY, "htFCQSize")
            .withDescription("FCQ Tree Size (accounts)")
            .withFormat(FORMAT_11_0);
    private static RunningAverageMetric htFCQSize;

    private static long htFCQAccounts;

    private static final RunningAverageMetric.Config HT_FCQ_RECORDS_CONFIG = new RunningAverageMetric.Config(
                    HANDLE_TRANSACTION_CATEGORY, "htFCQRecords")
            .withDescription("FCQ Transaction Records")
            .withFormat(FORMAT_11_0);
    private static RunningAverageMetric htFCQRecords;

    private static long htFCQRecordsCount;
    /**
     * Used for validation of part (or all) of the data in the NFT ledger.
     */
    private final ReferenceNftLedger referenceNftLedger;
    ///////////////////////////////////////////
    // Non copyable shared variables
    private Platform platform;
    ///////////////////////////////////////////
    // Copyable variables
    private long lastTranTimeStamp = 0;
    private ThresholdLimitingHandler<Throwable> exceptionRateLimiter;
    private ProgressCfg progressCfg;
    ///////////////////////////////////////////
    // Variables not used for state copyTo
    protected long roundCounter = 0;
    private long lastFileTranFinishTimeStamp = 0;
    ///////////////////////////////////////////
    // Variables used for check and validate result
    @JsonIgnore
    private ExpectedFCMFamily expectedFCMFamily;
    // is used for evicting expired records in the beginning of .handleTransaction
    private BlockingQueue<ExpirationRecordEntry> expirationQueue;
    // contains MapKeys which has an entry in ExpirationQueue
    private Set<MapKey> accountsWithExpiringRecords;
    // last timestamp purging records
    private long lastPurgeTimestamp = 0;
    /**
     * The instant of the previously handled transaction. Null if no transactions have yet been handled by this state
     * instance. Used to verify that each transaction happens at a later instant than its predecessor.
     */
    private Instant previousTimestamp;
    /**
     * Handles quorum determinations for all {@link ControlTransaction} processed by the handle method.
     */
    private QuorumTriggeredAction<ControlAction> controlQuorum;

    public PlatformTestingToolState() {
        super(ChildIndices.CHILD_COUNT);

        expectedFCMFamily = new ExpectedFCMFamilyImpl();

        referenceNftLedger = new ReferenceNftLedger(NFT_TRACKING_FRACTION);
    }

    protected PlatformTestingToolState(final PlatformTestingToolState sourceState) {
        super(sourceState);

        this.initialized.set(sourceState.initialized.get());
        this.platform = sourceState.platform;

        if (sourceState.getConfig() != null) {
            setConfig(sourceState.getConfig().copy());
        }

        if (sourceState.getNextSeqCons() != null) {
            setNextSeqCons(new NextSeqConsList(sourceState.getNextSeqCons()));
        }

        if (sourceState.getFcmFamily() != null) {
            setFcmFamily(sourceState.getFcmFamily().copy());
        } else {
            setFcmFamily(new FCMFamily(true));
        }

        if (sourceState.getVirtualMap() != null) {
            setVirtualMap(sourceState.getVirtualMap().copy());
        }

        if (sourceState.getVirtualMapForSmartContracts() != null) {
            setVirtualMapForSmartContracts(
                    sourceState.getVirtualMapForSmartContracts().copy());
        }

        if (sourceState.getVirtualMapForSmartContractsByteCode() != null) {
            setVirtualMapForSmartContractsByteCode(
                    sourceState.getVirtualMapForSmartContractsByteCode().copy());
        }

        this.lastFileTranFinishTimeStamp = sourceState.lastFileTranFinishTimeStamp;
        this.lastTranTimeStamp = sourceState.lastTranTimeStamp;

        // Only a small fraction of tokens are tracked in this reference leger, so this deep copy should be fast.
        this.referenceNftLedger = sourceState.referenceNftLedger.copy();

        // not part of copy to and from stream
        this.expectedFCMFamily = sourceState.expectedFCMFamily;
        this.expirationQueue = sourceState.expirationQueue;
        this.accountsWithExpiringRecords = sourceState.accountsWithExpiringRecords;

        // begin enhanced control structures - not part of state but required to be passed by reference
        this.controlQuorum = sourceState.controlQuorum;
        this.exceptionRateLimiter = sourceState.exceptionRateLimiter;
        // end enhanced control structures

        this.progressCfg = sourceState.progressCfg;
        this.roundCounter = sourceState.roundCounter;

        if (sourceState.getTransactionCounter() != null) {
            final int size = sourceState.getTransactionCounter().size();
            setTransactionCounter(new TransactionCounterList(size));
            for (int index = 0; index < size; index++) {
                getTransactionCounter()
                        .add(
                                index,
                                sourceState.getTransactionCounter().get(index).copy());
            }
        }

        if (sourceState.getIssLeaf() != null) {
            setIssLeaf(sourceState.getIssLeaf().copy());
        }

        if (sourceState.getNftLedger() != null) {
            setNftLedger(sourceState.getNftLedger().copy());
        }

        // set the current value of QuorumResult from source state
        if (sourceState.getQuorumResult() != null) {
            setQuorumResult(sourceState.getQuorumResult().copy());
        }
        if (controlQuorum != null) {
            controlQuorum.setQuorumResult(getQuorumResult().copy());
        }

        setImmutable(false);
        sourceState.setImmutable(true);
    }

    /**
     * startup any statistics
     *
     * @param platform Platform
     */
    public static void initStatistics(final Platform platform) {
        if (htFCMMicroSec != null) {
            return;
        }

        /* Add handleTransaction statistics */
        htFCMMicroSec = platform.getContext().getMetrics().getOrCreate(HT_FCM_MICRO_SEC_CONFIG);
        htFCQMicroSec = platform.getContext().getMetrics().getOrCreate(HT_FCQ_MICRO_SEC_CONFIG);
        htFCQExpirationMicroSec = platform.getContext().getMetrics().getOrCreate(HT_FCQ_EXPIRATION_MICRO_SEC_CONFIG);
        htFCMSize = platform.getContext().getMetrics().getOrCreate(HT_FCM_SIZE_CONFIG);
        htFCQSize = platform.getContext().getMetrics().getOrCreate(HT_FCQ_SIZE_CONFIG);
        htFCQRecords = platform.getContext().getMetrics().getOrCreate(HT_FCQ_RECORDS_CONFIG);

        NftLedgerStatistics.register(platform);

        // timer to update output stats
        final int SAMPLING_PERIOD = 5000; // millisecond
        final Timer statTimer = new Timer(STAT_TIMER_THREAD_NAME, true);
        statTimer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        getCurrentTransactionStat();
                    }
                },
                0,
                SAMPLING_PERIOD);
    }

    private static void getCurrentTransactionStat() {
        if (htFCMMicroSec == null) {
            return;
        }

        // fcm, nsec
        if (htCountFCM > 0) {
            htFCMMicroSec.update((double) htFCMSumNano / (double) htCountFCM * NANOSECONDS_TO_MICROSECONDS);
        } else {
            htFCMMicroSec.update(0);
        }
        htFCMSumNano = 0;
        htCountFCM = 0;
        // fcq, nsec
        if (htCountFCQ > 0) {
            htFCQMicroSec.update((double) htFCQSumNano / (double) htCountFCQ * NANOSECONDS_TO_MICROSECONDS);
        } else {
            htFCQMicroSec.update(0);
        }
        htFCQSumNano = 0;
        htCountFCQ = 0;
        // expiration, in microseconds
        if (htCountExpiration > 0) {
            htFCQExpirationMicroSec.update((double) htFCQExpirationSumMicro / (double) htCountExpiration);
        } else {
            htFCQExpirationMicroSec.update(0);
        }
        htFCQExpirationSumMicro = 0;
        htCountExpiration = 0;

        // datastructure sizes
        htFCMSize.update(htFCMAccounts);
        htFCQSize.update(htFCQAccounts);
        htFCQRecords.update(htFCQRecordsCount);
    }

    // count invalid signature ratio
    static AtomicLong totalTransactionSignatureCount = new AtomicLong(0);
    static AtomicLong expectedInvalidSignatureCount = new AtomicLong(0);

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfChildren() {
        return ChildIndices.CHILD_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumChildCount() {
        return ChildIndices.SDK_VERSION_21_CHILD_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaximumChildCount() {
        return ChildIndices.CHILD_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean childHasExpectedType(final int index, final long childClassId) {
        switch (index) {
            case ChildIndices.UNUSED:
                // We used to use this for an address book, but now we don't use this index.
                // Ignore whatever is found at this index.
                return true;
            case ChildIndices.CONFIG:
                return childClassId == PayloadCfgSimple.CLASS_ID;
            case ChildIndices.NEXT_SEQUENCE_CONSENSUS:
                return childClassId == NextSeqConsList.CLASS_ID;
            case ChildIndices.FCM_FAMILY:
                return childClassId == FCMFamily.CLASS_ID || childClassId == NULL_CLASS_ID;
            case ChildIndices.TRANSACTION_COUNTER:
                return childClassId == TransactionCounterList.CLASS_ID;
            case ChildIndices.ISS_LEAF:
                return childClassId == IssLeaf.CLASS_ID;
            case ChildIndices.NFT_LEDGER:
                return childClassId == NftLedger.CLASS_ID;
            case ChildIndices.VIRTUAL_MERKLE:
                return childClassId == VirtualMap.CLASS_ID || childClassId == NULL_CLASS_ID;
            case ChildIndices.VIRTUAL_MERKLE_SMART_CONTRACTS:
                return childClassId == VirtualMap.CLASS_ID || childClassId == NULL_CLASS_ID;
            case ChildIndices.VIRTUAL_MERKLE_SMART_CONTRACTS_BYTE_CODE:
                return childClassId == VirtualMap.CLASS_ID || childClassId == NULL_CLASS_ID;
            case ChildIndices.QUORUM_RESULT:
                return childClassId == QuorumResult.CLASS_ID;
            default:
                return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDeserializedChildren(final List<MerkleNode> children, final int version) {
        if (!children.isEmpty() && children.get(0) instanceof AddressBook) {
            // We used to store an address book here, but we can ignore it now.
            children.set(0, null);
        }

        super.addDeserializedChildren(children, version);
    }

    private PayloadCfgSimple getConfig() {
        return getChild(ChildIndices.CONFIG);
    }

    private void setConfig(final PayloadCfgSimple config) {
        setChild(ChildIndices.CONFIG, config);
    }

    QuorumTriggeredAction<ControlAction> getControlQuorum() {
        return controlQuorum;
    }

    public NextSeqConsList getNextSeqCons() {
        return getChild(ChildIndices.NEXT_SEQUENCE_CONSENSUS);
    }

    private void setNextSeqCons(final NextSeqConsList nextSeqCons) {
        setChild(ChildIndices.NEXT_SEQUENCE_CONSENSUS, nextSeqCons);
    }

    public VirtualMap<AccountVirtualMapKey, AccountVirtualMapValue> getVirtualMap() {
        return getChild(ChildIndices.VIRTUAL_MERKLE);
    }

    public void setVirtualMap(final VirtualMap<AccountVirtualMapKey, AccountVirtualMapValue> virtualMap) {
        setChild(ChildIndices.VIRTUAL_MERKLE, virtualMap);
    }

    public VirtualMap<SmartContractMapKey, SmartContractMapValue> getVirtualMapForSmartContracts() {
        return getChild(ChildIndices.VIRTUAL_MERKLE_SMART_CONTRACTS);
    }

    public void setVirtualMapForSmartContracts(
            final VirtualMap<SmartContractMapKey, SmartContractMapValue> virtualMap) {
        setChild(ChildIndices.VIRTUAL_MERKLE_SMART_CONTRACTS, virtualMap);
    }

    public VirtualMap<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue>
            getVirtualMapForSmartContractsByteCode() {
        return getChild(ChildIndices.VIRTUAL_MERKLE_SMART_CONTRACTS_BYTE_CODE);
    }

    public void setVirtualMapForSmartContractsByteCode(
            final VirtualMap<SmartContractByteCodeMapKey, SmartContractByteCodeMapValue> virtualMap) {
        setChild(ChildIndices.VIRTUAL_MERKLE_SMART_CONTRACTS_BYTE_CODE, virtualMap);
    }

    public FCMFamily getFcmFamily() {
        return getChild(ChildIndices.FCM_FAMILY);
    }

    public QuorumResult<ControlAction> getQuorumResult() {
        return getChild(ChildIndices.QUORUM_RESULT);
    }

    public void setFcmFamily(final FCMFamily fcmFamily) {
        setChild(ChildIndices.FCM_FAMILY, fcmFamily);
    }

    public List<TransactionCounter> getTransactionCounter() {
        return getChild(ChildIndices.TRANSACTION_COUNTER);
    }

    private void setTransactionCounter(final TransactionCounterList transactionCounter) {
        setChild(ChildIndices.TRANSACTION_COUNTER, transactionCounter);
    }

    private IssLeaf getIssLeaf() {
        return getChild(ChildIndices.ISS_LEAF);
    }

    private void setIssLeaf(final IssLeaf leaf) {
        setChild(ChildIndices.ISS_LEAF, leaf);
    }

    public void setQuorumResult(final QuorumResult<ControlAction> quorumResult) {
        setChild(ChildIndices.QUORUM_RESULT, quorumResult);
    }

    /**
     * Get the NFT ledger, a child of this node.
     */
    public NftLedger getNftLedger() {
        return getChild(ChildIndices.NFT_LEDGER);
    }

    /**
     * Set the NFT ledger, a child of this node.
     */
    public void setNftLedger(final NftLedger ledger) {
        setChild(ChildIndices.NFT_LEDGER, ledger);
    }

    /**
     * Get the reference NFT ledger.
     */
    public ReferenceNftLedger getReferenceNftLedger() {
        return referenceNftLedger;
    }

    public BlockingQueue<ExpirationRecordEntry> getExpirationQueue() {
        return expirationQueue;
    }

    // contains MapKeys which has an entry in ExpirationQueue
    public Set<MapKey> getAccountsWithExpiringRecords() {
        return accountsWithExpiringRecords;
    }

    public long getLastPurgeTimestamp() {
        return lastPurgeTimestamp;
    }

    public synchronized void setPayloadConfig(final FCMConfig fcmConfig) {
        expectedFCMFamily.setNodeId(platform.getSelfId().id());
        expectedFCMFamily.setFcmConfig(fcmConfig);
        expectedFCMFamily.setWeightedNodeNum(platform.getAddressBook().getNumberWithWeight());

        referenceNftLedger.setFractionToTrack(this.getNftLedger(), fcmConfig.getNftTrackingFraction());
    }

    public void setProgressCfg(final ProgressCfg progressCfg) {
        this.progressCfg = progressCfg;
    }

    public void initChildren() {
        if (getFcmFamily() == null) { // if not loaded from previous saved state
            setFcmFamily(new FCMFamily(true));
        }

        if (getNftLedger() == null) {
            setNftLedger(new NftLedger());
        }
    }

    void initControlStructures(final Action<Long, ControlAction> action) {
        final int nodeIndex = platform.getAddressBook().getIndexOfNodeId(platform.getSelfId());
        this.controlQuorum = new QuorumTriggeredAction<>(
                () -> nodeIndex,
                platform.getAddressBook()::getSize,
                platform.getAddressBook()::getNumberWithWeight,
                action);

        this.exceptionRateLimiter = new ThresholdLimitingHandler<>(EXCEPTION_RATE_THRESHOLD);
    }

    public void initializeExpirationQueueAndAccountsSet() {
        expirationQueue = new PriorityBlockingQueue<>();
        accountsWithExpiringRecords = new HashSet<>();
    }

    public void resetLastFileTranFinishTimeStamp() {
        lastFileTranFinishTimeStamp = System.currentTimeMillis();
    }

    public synchronized FCMFamily getStateMap() {
        return getFcmFamily();
    }

    @JsonIgnore
    public synchronized ExpectedFCMFamily getStateExpectedMap() {
        return expectedFCMFamily;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized PlatformTestingToolState copy() {
        throwIfImmutable();
        roundCounter++;

        final PlatformTestingToolState mutableCopy = new PlatformTestingToolState(this);

        if (platform != null) {
            UnsafeMutablePTTStateAccessor.getInstance().setMutableState(platform.getSelfId(), mutableCopy);
        }

        return mutableCopy;
    }

    /**
     * Make sure that the timestamp for the submitted transaction is not earlier than (the timestamp for the previous
     * transaction + minTransTimestampIncrNanos).
     */
    private void validateTimestamp(final Instant timestamp) {
        if (previousTimestamp != null) {
            final Instant previousTransTimestampPlusIncr = previousTimestamp.plusNanos(minTransTimestampIncrNanos);
            if (timestamp.isBefore(previousTransTimestampPlusIncr)) {
                logger.error(
                        LOGM_EXCEPTION,
                        "Transaction has timestamp {} which is earlier than previous timestamp {} plus {} nanos: "
                                + "{}",
                        timestamp,
                        previousTimestamp,
                        minTransTimestampIncrNanos,
                        previousTransTimestampPlusIncr);
            }
        }
        previousTimestamp = timestamp;
    }

    /**
     * Check if the signatures on the transaction are invalid.
     *
     * @return true if the signature is invalid
     */
    private boolean checkIfSignatureIsInvalid(final Transaction trans) {
        if (getConfig().isAppendSig()) {
            return validateSignatures(trans);
        }
        return false;
    }

    /**
     * If configured, delay this transaction.
     */
    private void delay() {
        if (getConfig().getDelayCfg() != null) {
            final int delay = getConfig().getDelayCfg().getRandomDelay();
            try {
                Thread.sleep(delay);
            } catch (final InterruptedException e) {
                logger.info(LOGM_DEMO_INFO, "", e);
            }
        }
        SyntheticBottleneckConfig.getActiveConfig()
                .throttleIfNeeded(platform.getSelfId().id());
    }

    /**
     * Instantiate TestTransaction object from transaction byte array.
     *
     * @return the instantiated transaction if no errors, otherwise null
     */
    private Optional<TestTransaction> unpackTransaction(final Transaction trans) {
        try {
            final byte[] payloadBytes = trans.getContents();
            if (getConfig().isAppendSig()) {
                final byte[] testTransactionRawBytes = TestTransactionWrapper.parseFrom(payloadBytes)
                        .getTestTransactionRawBytes()
                        .toByteArray();
                return Optional.of(TestTransaction.parseFrom(testTransactionRawBytes));
            } else {
                return Optional.of(TestTransaction.parseFrom(payloadBytes));
            }
        } catch (final InvalidProtocolBufferException ex) {
            exceptionRateLimiter.handle(
                    ex, (error) -> logger.error(LOGM_EXCEPTION, "InvalidProtocolBufferException", error));
            return Optional.empty();
        }
    }

    /**
     * If configured to do so, purge expired records as needed.
     */
    private void purgeExpiredRecordsIfNeeded(final TestTransaction testTransaction, final Instant timestamp) {
        if (!testTransaction.hasControlTransaction()
                || testTransaction.getControlTransaction().getType() != ControlType.EXIT_VALIDATION) {

            if (getFcmFamily().getAccountFCQMap().size() > 0) {
                // remove expired records from FCQs before handling transaction if accountFCQMap has any entities
                try {
                    purgeExpiredRecords(timestamp.getEpochSecond());
                } catch (final Throwable ex) {
                    exceptionRateLimiter.handle(
                            ex, (error) -> logger.error(LOGM_EXCEPTION, "Failed to purge expired records", error));
                }
            }
        }
    }

    /**
     * Write a special log message if this is the first transaction and total fcm and any file statistics are all
     * zeroes.
     */
    private void logIfFirstTransaction(final NodeId id) {
        final int nodeIndex = platform.getAddressBook().getIndexOfNodeId(id);
        if (progressCfg != null
                && progressCfg.getProgressMarker() > 0
                && getTransactionCounter().get(nodeIndex).getAllTransactionAmount() == 0) {
            logger.info(LOGM_DEMO_INFO, "PlatformTestingDemo HANDLE ALL START");
        }
    }

    /**
     * Handle the random bytes transaction type.
     */
    private void handleBytesTransaction(@NonNull final TestTransaction testTransaction, @NonNull final NodeId id) {
        Objects.requireNonNull(testTransaction, "testTransaction must not be null");
        Objects.requireNonNull(id, "id must not be null");
        final int nodeIndex = platform.getAddressBook().getIndexOfNodeId(id);
        final RandomBytesTransaction bytesTransaction = testTransaction.getBytesTransaction();
        if (bytesTransaction.getIsInserSeq()) {
            final long seq = Utilities.toLong(bytesTransaction.getData().toByteArray());
            if (getNextSeqCons().get(nodeIndex).getValue() != seq) {
                logger.error(
                        LOGM_EXCEPTION,
                        platform.getSelfId() + " error, new (id=" + id
                                + ") seq should be " + getNextSeqCons().get(nodeIndex)
                                + " but is " + seq);
            }
            getNextSeqCons().get(nodeIndex).getAndIncrement();
        }
    }

    /**
     * Handle the Virtual Merkle transaction type.
     */
    private void handleVirtualMerkleTransaction(
            @NonNull final VirtualMerkleTransaction virtualMerkleTransaction,
            @NonNull final NodeId id,
            @NonNull final Instant consensusTimestamp) {
        Objects.requireNonNull(virtualMerkleTransaction, "virtualMerkleTransaction must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(consensusTimestamp, "consensusTimestamp must not be null");
        final int nodeIndex = platform.getAddressBook().getIndexOfNodeId(id);
        VirtualMerkleTransactionHandler.handle(
                consensusTimestamp,
                virtualMerkleTransaction,
                expectedFCMFamily,
                getVirtualMap(),
                getVirtualMapForSmartContracts(),
                getVirtualMapForSmartContractsByteCode());

        if (virtualMerkleTransaction.hasCreateAccount()) {
            getTransactionCounter().get(nodeIndex).vmCreateAmount++;
        } else if (virtualMerkleTransaction.hasUpdateAccount()) {
            getTransactionCounter().get(nodeIndex).vmUpdateAmount++;
        } else if (virtualMerkleTransaction.hasDeleteAccount()) {
            getTransactionCounter().get(nodeIndex).vmDeleteAmount++;
        } else if (virtualMerkleTransaction.hasSmartContract()) {
            getTransactionCounter().get(nodeIndex).vmContractCreateAmount++;
        } else if (virtualMerkleTransaction.hasMethodExecution()) {
            getTransactionCounter().get(nodeIndex).vmContractExecutionAmount++;
        }
    }

    /**
     * Handle the FCM transaction type.
     */
    private void handleFCMTransaction(
            @NonNull final TestTransaction testTransaction,
            @NonNull final NodeId id,
            @NonNull final Instant timestamp,
            final boolean invalidSig) {
        Objects.requireNonNull(testTransaction, "testTransaction must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        final int nodeIndex = platform.getAddressBook().getIndexOfNodeId(id);
        final FCMTransaction fcmTransaction = testTransaction.getFcmTransaction();

        // Handle Activity transaction, which doesn't effect any entity's lifecyle
        if (fcmTransaction.hasActivity()) {
            final Activity.ActivityType activityType =
                    fcmTransaction.getActivity().getType();
            if (nodeIndex == 0 && activityType == Activity.ActivityType.SAVE_EXPECTED_MAP) {
                // Serialize ExpectedMap to disk in JSON format
                TransactionSubmitter.setForcePauseCanSubmitMore(new AtomicBoolean(true));
                serialize(
                        expectedFCMFamily.getExpectedMap(),
                        new File(STORAGE_DIRECTORY),
                        createExpectedMapName(platform.getSelfId().id(), timestamp),
                        false);
                TransactionSubmitter.setForcePauseCanSubmitMore(new AtomicBoolean(false));
                logger.info(LOGM_DEMO_INFO, "handling SAVE_EXPECTED_MAP");

            } else if (activityType == Activity.ActivityType.SAVE_EXPECTED_MAP) {
                logger.info(LOGM_DEMO_INFO, "Received SAVE_EXPECTED_MAP transaction from node {}", id);
            } else {
                logger.info(LOGM_EXCEPTION, "unknown Activity type");
            }
            return;
        }

        if (fcmTransaction.hasDummyTransaction()) {
            return;
        }

        // Extract MapKeys, TransactionType, and EntityType from FCMTransaction
        // which might effect entity's lifecycle status
        final List<MapKey> keys = FCMTransactionUtils.getMapKeys(fcmTransaction);
        final TransactionType transactionType = FCMTransactionUtils.getTransactionType(fcmTransaction);
        final EntityType entityType = FCMTransactionUtils.getEntityType(fcmTransaction);
        final long epochMillis = timestamp.toEpochMilli();

        final long originId = fcmTransaction.getOriginNode();

        if ((keys.isEmpty() && entityType != EntityType.NFT) || transactionType == null || entityType == null) {
            logger.error(
                    LOGM_EXCEPTION,
                    "Invalid Transaction: keys: {}, transactionType: {}, entityType: {}",
                    keys,
                    transactionType,
                    entityType);
            return;
        }

        // if there is any error, we don't handle this transaction
        if (!expectedFCMFamily.shouldHandleForKeys(
                        keys, transactionType, getConfig(), entityType, epochMillis, originId)
                && entityType != EntityType.NFT) {
            return;
        }

        // If signature verification result doesn't match expected result
        // which is set when generating this FCMTransaction, it denotes an error
        if (getConfig().isAppendSig() && invalidSig != fcmTransaction.getInvalidSig()) {
            logger.error(
                    LOGM_EXCEPTION,
                    "Unexpected signature verification result: " + "actual: {}; expected:{}",
                    () -> (invalidSig ? "INVALID" : "VALID"),
                    () -> (fcmTransaction.getInvalidSig() ? "INVALID" : "VALID"));

            // if the entity doesn't exist, put it to expectedMap;
            // set its ExpectedValues's isErrored to be true;
            // set its latestHandledStatus to be INVALID_SIG
            expectedFCMFamily.setLatestHandledStatusForKey(
                    keys.get(0),
                    entityType,
                    null,
                    TransactionState.INVALID_SIG,
                    transactionType,
                    epochMillis,
                    originId,
                    true);
            return;
        }

        // for expected invalid sig,
        // set expectedValue's latestHandledStatus to be EXPECTED_INVALID_SIG
        // and handle this transaction
        if (invalidSig) {
            expectedFCMFamily.setLatestHandledStatusForKey(
                    keys.get(0),
                    entityType,
                    null,
                    TransactionState.EXPECTED_INVALID_SIG,
                    transactionType,
                    epochMillis,
                    originId,
                    false);
        }

        // Handle the transaction and set latestHandledStatus
        try {
            FCMTransactionHandler.performOperation(
                    fcmTransaction,
                    this,
                    this.expectedFCMFamily,
                    originId,
                    epochMillis,
                    entityType,
                    timestamp.getEpochSecond() + getConfig().getFcqTtl(),
                    expirationQueue,
                    accountsWithExpiringRecords);
        } catch (final Exception ex) {
            exceptionRateLimiter.handle(
                    ex,
                    (error) -> logger.error(
                            LOGM_EXCEPTION,
                            "Exceptions while handling transaction: {} {} for {}, originId:{}",
                            transactionType,
                            entityType,
                            keys,
                            originId,
                            error));

            // if the entity doesn't exist, put it to expectedMap;
            // set its ExpectedValues's isErrored to be true;
            // set its latestHandledStatus to be HANDLE_FAILED
            for (final MapKey key : keys) {
                expectedFCMFamily.setLatestHandledStatusForKey(
                        key,
                        entityType,
                        null,
                        TransactionState.HANDLE_FAILED,
                        transactionType,
                        timestamp.toEpochMilli(),
                        originId,
                        true);
            }
        }
        if (fcmTransaction.hasCreateAccount()) {
            getTransactionCounter().get(nodeIndex).fcmCreateAmount++;
            if (progressCfg != null) {
                logProgress(
                        id,
                        progressCfg.getProgressMarker(),
                        PAYLOAD_TYPE.TYPE_FCM_CREATE,
                        progressCfg.getExpectedFCMCreateAmount(),
                        getTransactionCounter().get(nodeIndex).fcmCreateAmount);
            }
        } else if (fcmTransaction.hasTransferBalance()) {
            getTransactionCounter().get(nodeIndex).fcmTransferAmount++;
            if (progressCfg != null) {
                logProgress(
                        id,
                        progressCfg.getProgressMarker(),
                        PAYLOAD_TYPE.TYPE_FCM_TRANSFER,
                        progressCfg.getExpectedFCMTransferAmount(),
                        getTransactionCounter().get(nodeIndex).fcmTransferAmount);
            }
        } else if (fcmTransaction.hasDeleteAccount()) {
            getTransactionCounter().get(nodeIndex).fcmDeleteAmount++;
            if (progressCfg != null) {
                logProgress(
                        id,
                        progressCfg.getProgressMarker(),
                        PAYLOAD_TYPE.TYPE_FCM_DELETE,
                        progressCfg.getExpectedFCMDeleteAmount(),
                        getTransactionCounter().get(nodeIndex).fcmDeleteAmount);
            }
        } else if (fcmTransaction.hasUpdateAccount()) {
            getTransactionCounter().get(nodeIndex).fcmUpdateAmount++;
            if (progressCfg != null) {
                logProgress(
                        id,
                        progressCfg.getProgressMarker(),
                        PAYLOAD_TYPE.TYPE_FCM_UPDATE,
                        progressCfg.getExpectedFCMUpdateAmount(),
                        getTransactionCounter().get(nodeIndex).fcmUpdateAmount);
            }
        } else if (fcmTransaction.hasAssortedAccount()) {
            getTransactionCounter().get(nodeIndex).fcmAssortedAmount++;
            if (progressCfg != null) {
                logProgress(
                        id,
                        progressCfg.getProgressMarker(),
                        PAYLOAD_TYPE.TYPE_FCM_ASSORTED,
                        progressCfg.getExpectedFCMAssortedAmount(),
                        getTransactionCounter().get(nodeIndex).fcmAssortedAmount);
            }
        } else if (fcmTransaction.hasAssortedFCQ()) {
            getTransactionCounter().get(nodeIndex).fcmFCQAssortedAmount++;
        } else if (fcmTransaction.hasCreateAccountFCQ()) {
            getTransactionCounter().get(nodeIndex).fcmFCQCreateAmount++;
        } else if (fcmTransaction.hasUpdateAccountFCQ()) {
            getTransactionCounter().get(nodeIndex).fcmFCQUpdateAmount++;
        } else if (fcmTransaction.hasTransferBalanceFCQ()) {
            getTransactionCounter().get(nodeIndex).fcmFCQTransferAmount++;
        } else if (fcmTransaction.hasDeleteFCQNode()) {
            getTransactionCounter().get(nodeIndex).fcmFCQDeleteAmount++;
        }
    }

    /**
     * Handle the control transaction type.
     */
    private void handleControlTransaction(
            @NonNull final TestTransaction testTransaction,
            @NonNull final NodeId id,
            @NonNull final Instant timestamp) {
        Objects.requireNonNull(testTransaction, "testTransaction must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        final long nodeIndex = platform.getAddressBook().getIndexOfNodeId(id);
        final ControlTransaction msg = testTransaction.getControlTransaction();
        logger.info(
                DEMO_INFO.getMarker(),
                "Handling Control Transaction [ originatingNodeId = {}, type = {}, consensusTimestamp = {} ]",
                () -> id,
                msg::getType,
                () -> timestamp);

        // Must use auto reset here, otherwise if reached quorum EXIT_VALIDATION then PTT restart, QuorumResult would be
        // reloaded from saved state with reaching quorum state EXIT_VALIDATION as true,
        // then TransactionSubmitter won't be able to submit transaction due to some check mechanism.
        controlQuorum.withAutoReset().check(nodeIndex, new ControlAction(timestamp, msg.getType()));
        // updating quorum result after handling control transactions
        setQuorumResult(controlQuorum.getQuorumResult().copy());
    }

    /**
     * Handle the freeze transaction type.
     */
    private void handleFreezeTransaction(final TestTransaction testTransaction, final SwirldDualState swirldDualState) {
        final FreezeTransaction freezeTx = testTransaction.getFreezeTransaction();
        FreezeTransactionHandler.freeze(freezeTx, swirldDualState);
    }

    /**
     * Handle a simple action transaction type
     */
    private void handleSimpleAction(final SimpleAction simpleAction) {
        if (simpleAction == SimpleAction.CAUSE_ISS) {
            getIssLeaf().setWriteRandom(true);
        }
    }

    protected void preHandleTransaction(final Transaction transaction) {
        expandSignatures(transaction);
        transaction.setMetadata(true);
    }

    @Override
    public synchronized void handleConsensusRound(final Round round, final SwirldDualState swirldDualState) {
        throwIfImmutable();
        if (!initialized.get()) {
            throw new IllegalStateException("handleConsensusRound() called before init()");
        }
        delay();
        updateTransactionCounters();
        round.forEachEventTransaction((event, transaction) ->
                handleConsensusTransaction(event, transaction, swirldDualState, round.getRoundNum()));
    }

    /**
     * If the size of the address book has changed, zero out the transaction counters and resize, as needed.
     */
    private void updateTransactionCounters() {
        if (getTransactionCounter() == null
                || getTransactionCounter().size() != platform.getAddressBook().getSize()) {
            setNextSeqCons(new NextSeqConsList(platform.getAddressBook().getSize()));

            logger.info(DEMO_INFO.getMarker(), "resetting transaction counters");

            setTransactionCounter(
                    new TransactionCounterList(platform.getAddressBook().getSize()));
            for (int id = 0; id < platform.getAddressBook().getSize(); id++) {
                getTransactionCounter().add(new TransactionCounter(id));
            }
        }
    }

    private void handleConsensusTransaction(
            final ConsensusEvent event,
            final ConsensusTransaction trans,
            final SwirldDualState dualState,
            final long roundNum) {
        try {
            waitForSignatureValidation(trans);
            handleTransaction(
                    event.getCreatorId(), event.getTimeCreated(), trans.getConsensusTimestamp(), trans, dualState);
        } catch (final InterruptedException e) {
            logger.info(
                    TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
                    "handleConsensusRound Interrupted [ nodeId = {}, round = {} ]. "
                            + "This should happen only during a reconnect",
                    platform.getSelfId().id(),
                    roundNum);
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
            logger.error(EXCEPTION.getMarker(), "Exception while handling transaction", e);
        }
    }

    private static void waitForSignatureValidation(final ConsensusTransaction transaction)
            throws InterruptedException, ExecutionException {
        for (final TransactionSignature sig : transaction.getSignatures()) {
            final Future<Void> future = sig.waitForFuture();

            // Block & Ignore the Void return
            future.get();
        }
    }

    private void handleTransaction(
            @NonNull final NodeId id,
            @NonNull final Instant timeCreated,
            @NonNull final Instant timestamp,
            @NonNull final ConsensusTransaction trans,
            @NonNull final SwirldDualState swirldDualState) {
        if (getConfig().isAppendSig()) {
            try {
                final TestTransactionWrapper testTransactionWrapper =
                        TestTransactionWrapper.parseFrom(trans.getContents());
                final byte[] testTransactionRawBytes =
                        testTransactionWrapper.getTestTransactionRawBytes().toByteArray();
                final byte[] publicKey =
                        testTransactionWrapper.getPublicKeyRawBytes().toByteArray();
                final byte[] signature =
                        testTransactionWrapper.getSignaturesRawBytes().toByteArray();

                // if this is expected manually inject invalid signature
                boolean expectingInvalidSignature = false;
                final TestTransaction testTransaction = TestTransaction.parseFrom(testTransactionRawBytes);
                if (testTransaction.getBodyCase() == FCMTRANSACTION) {
                    final FCMTransaction fcmTransaction = testTransaction.getFcmTransaction();
                    if (fcmTransaction.getInvalidSig()) {
                        expectingInvalidSignature = true;
                    }
                }
                totalTransactionSignatureCount.incrementAndGet();
                for (final TransactionSignature s : trans.getSignatures()) {
                    if (s.getSignatureStatus() != VerificationStatus.VALID && (!expectingInvalidSignature)) {
                        logger.error(
                                EXCEPTION.getMarker(),
                                "Invalid Transaction Signature [status = {}, signatureType = {}, "
                                        + "publicKey = {}, signature = {}, data = {}, "
                                        + "actualPublicKey = {}, actualSignature = {}, actualData = {} ]",
                                s.getSignatureStatus(),
                                s.getSignatureType(),
                                hex(publicKey),
                                hex(signature),
                                hex(testTransactionRawBytes),
                                hex(Arrays.copyOfRange(
                                        s.getContentsDirect(),
                                        s.getPublicKeyOffset(),
                                        s.getPublicKeyOffset() + s.getPublicKeyLength())),
                                hex(Arrays.copyOfRange(
                                        s.getContentsDirect(),
                                        s.getSignatureOffset(),
                                        s.getSignatureOffset() + s.getSignatureLength())),
                                hex(Arrays.copyOfRange(
                                        s.getContentsDirect(),
                                        s.getMessageOffset(),
                                        s.getMessageOffset() + s.getMessageLength())));
                    } else if (s.getSignatureStatus() != VerificationStatus.VALID && expectingInvalidSignature) {
                        expectedInvalidSignatureCount.incrementAndGet();
                    }
                }
            } catch (final InvalidProtocolBufferException ex) {
                exceptionRateLimiter.handle(
                        ex,
                        (error) -> logger.error(
                                LOGM_EXCEPTION, "" + "InvalidProtocolBufferException while chekcing signature", error));
            }
        }

        //////////// start timing/////////////
        final long startTime = System.nanoTime();
        validateTimestamp(timestamp);
        lastTranTimeStamp = System.currentTimeMillis();

        final Optional<TestTransaction> testTransaction = unpackTransaction(trans);
        if (testTransaction.isEmpty()) {
            return;
        }
        final long splitTime1 = System.nanoTime();
        // omit entityExpiration from handleTransaction timing
        purgeExpiredRecordsIfNeeded(testTransaction.get(), timestamp);
        final long splitTime2 = System.nanoTime();

        logIfFirstTransaction(id);

        // Handle based on transaction type
        switch (testTransaction.get().getBodyCase()) {
            case BYTESTRANSACTION:
                handleBytesTransaction(testTransaction.get(), id);
                break;
            case FCMTRANSACTION:
                handleFCMTransaction(testTransaction.get(), id, timestamp, checkIfSignatureIsInvalid(trans));
                break;
            case CONTROLTRANSACTION:
                handleControlTransaction(testTransaction.get(), id, timestamp);
                break;
            case FREEZETRANSACTION:
                handleFreezeTransaction(testTransaction.get(), swirldDualState);
                break;
            case SIMPLEACTION:
                handleSimpleAction(testTransaction.get().getSimpleAction());
                break;
            case VIRTUALMERKLETRANSACTION:
                handleVirtualMerkleTransaction(testTransaction.get().getVirtualMerkleTransaction(), id, timeCreated);
                break;
            default:
                logger.error(LOGM_EXCEPTION, "Unrecognized transaction!");
        }

        //////////// end timing/////////////
        final long htNetTime = System.nanoTime() - splitTime2 + (splitTime1 - startTime);
        if (testTransaction.get().hasFcmTransaction()) {
            final FCMTransaction fcmTransaction = testTransaction.get().getFcmTransaction();
            if (!fcmTransaction.hasActivity() && !fcmTransaction.hasDummyTransaction()) {
                switch (Objects.requireNonNull(FCMTransactionUtils.getEntityType(fcmTransaction))) {
                    case Crypto:
                        htFCMSumNano += htNetTime;
                        htCountFCM++;
                        htFCMAccounts = getFcmFamily().getMap().size();
                        break;
                    case FCQ:
                        htFCQSumNano += htNetTime;
                        htCountFCQ++;
                        htFCQAccounts = getFcmFamily().getAccountFCQMap().size();
                        break;
                }
            }
        }
    }

    /**
     * Do initial genesis setup.
     */
    private void genesisInit() {
        logger.info(LOGM_STARTUP, "Set QuorumResult from genesisInit()");
        setQuorumResult(new QuorumResult<>(platform.getAddressBook().getSize()));

        setIssLeaf(new IssLeaf());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(
            final Platform platform,
            final SwirldDualState swirldDualState,
            final InitTrigger trigger,
            final SoftwareVersion previousSoftwareVersion) {

        this.platform = platform;
        UnsafeMutablePTTStateAccessor.getInstance().setMutableState(platform.getSelfId(), this);

        initialized.set(true);

        TransactionSubmitter.setForcePauseCanSubmitMore(new AtomicBoolean(false));

        // If parameter exists, load PayloadCfgSimple from top level json configuration file
        // Otherwise, load the default setting
        final String[] parameters = ParameterProvider.getInstance().getParameters();
        if (parameters != null && parameters.length > 0) {
            final String jsonFileName = parameters[0];
            final PayloadCfgSimple payloadCfgSimple = PlatformTestingToolMain.getPayloadCfgSimple(jsonFileName);
            setConfig(payloadCfgSimple);
        } else {
            setConfig(new PayloadCfgSimple());
        }

        expectedFCMFamily.setNodeId(platform.getSelfId().id());
        expectedFCMFamily.setWeightedNodeNum(platform.getAddressBook().getNumberWithWeight());

        // initialize data structures used for FCQueue transaction records expiration
        initializeExpirationQueueAndAccountsSet();

        logger.info(LOGM_DEMO_INFO, "Dual state received in init function {}", () -> new ApplicationDualStatePayload(
                        swirldDualState.getFreezeTime(), swirldDualState.getLastFrozenTime())
                .toString());

        logger.info(LOGM_STARTUP, () -> new SoftwareVersionPayload(
                        "Trigger and PreviousSoftwareVersion state received in init function",
                        trigger.toString(),
                        Objects.toString(previousSoftwareVersion))
                .toString());

        if (trigger == InitTrigger.GENESIS) {
            genesisInit();
        }
        this.invalidateHash();
    }

    private MessageDigest createKeccakDigest() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("KECCAK-256");
        } catch (NoSuchAlgorithmException ignored) {
            try {
                digest = MessageDigest.getInstance("SHA3-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        return digest;
    }

    private byte[] keccak256(final byte[] bytes) {
        final MessageDigest keccakDigest = createKeccakDigest();
        keccakDigest.update(bytes);
        return keccakDigest.digest();
    }

    private void expandSignatures(final Transaction trans) {
        if (getConfig().isAppendSig()) {
            try {
                final byte[] payloadBytes = trans.getContents();
                final TestTransactionWrapper testTransactionWrapper = TestTransactionWrapper.parseFrom(payloadBytes);
                final byte[] testTransactionRawBytes =
                        testTransactionWrapper.getTestTransactionRawBytes().toByteArray();
                final byte[] publicKey =
                        testTransactionWrapper.getPublicKeyRawBytes().toByteArray();
                final byte[] signature =
                        testTransactionWrapper.getSignaturesRawBytes().toByteArray();
                final AppTransactionSignatureType AppSignatureType = testTransactionWrapper.getSignatureType();

                final SignatureType signatureType;
                byte[] signaturePayload = testTransactionRawBytes;

                if (AppSignatureType == AppTransactionSignatureType.ED25519) {
                    signatureType = SignatureType.ED25519;
                } else if (AppSignatureType == AppTransactionSignatureType.ECDSA_SECP256K1) {
                    signatureType = SignatureType.ECDSA_SECP256K1;
                    signaturePayload = keccak256(testTransactionRawBytes);
                } else if (AppSignatureType == AppTransactionSignatureType.RSA) {
                    signatureType = SignatureType.RSA;
                } else {
                    throw new UnsupportedOperationException("Unknown application signature type " + AppSignatureType);
                }

                final int msgLen = signaturePayload.length;
                final int sigOffset = msgLen + publicKey.length;

                // concatenate payload with public key and signature
                final byte[] contents = ByteBuffer.allocate(
                                signaturePayload.length + publicKey.length + signature.length)
                        .put(signaturePayload)
                        .put(publicKey)
                        .put(signature)
                        .array();

                trans.add(new TransactionSignature(
                        contents, sigOffset, signature.length, msgLen, publicKey.length, 0, msgLen, signatureType));

                CryptographyHolder.get().verifyAsync(trans.getSignatures());

            } catch (final InvalidProtocolBufferException ex) {
                exceptionRateLimiter.handle(
                        ex, (error) -> logger.error(LOGM_EXCEPTION, "InvalidProtocolBufferException", error));
            }
        }
    }

    /**
     * Report current progress, if currentAmount equals multiple times of markerPercentage of expectedAmount For example
     * if expectedAmount = 88, markerPercentage = 20, the progress will be reported if current progress percentage is
     * 20%, 40%, 60%, 80%, 100%. Accordingly, currentAmount equals to 17, 35, 52, 70, and 88
     * <p>
     * If markerPercentage is 15, then should report progress at 15%, 30%, 45%, 60%, 75%, 90% and 100%
     */
    private void logProgress(
            @NonNull final NodeId id,
            final int markerPercentage,
            @NonNull final PAYLOAD_TYPE type,
            final long expectedAmount,
            final long currentAmount) {
        if (markerPercentage != 0 && currentAmount != 0 && expectedAmount != 0) {
            if (currentAmount == 1) {
                logger.info(LOGM_DEMO_INFO, "PlatformTestingDemo id {} HANDLE {} START", id, type);
            } else if (currentAmount == expectedAmount) {
                logger.info(LOGM_DEMO_INFO, "PlatformTestingDemo id {} HANDLE {} END", id, type);
            } else {
                final int reportTimes = (int) Math.ceil(((double) 100) / markerPercentage);
                for (int i = 1; i <= reportTimes; i++) { // check currentAmount match which marker value
                    final int percentage = Math.min(100, i * markerPercentage);
                    final long reportNumber = expectedAmount * percentage / 100;
                    if (currentAmount == reportNumber) {
                        logger.info(
                                LOGM_DEMO_INFO,
                                "PlatformTestingDemo id {} HANDLE {} {}% currentAmount {} ",
                                id,
                                type,
                                percentage,
                                currentAmount);
                    }
                }
            }
        }
    }

    /**
     * Validate signatures when appendSig is true, if signature status is INVALID set invalidSig flag
     *
     * @param trans Transaction whose signatures needs to be validated
     * @return boolean that shows if the signature status is INVALID
     */
    private static boolean validateSignatures(final Transaction trans) {
        // Verify signatures if appendSig is true
        boolean invalidSig = false;
        final List<TransactionSignature> signatureList = trans.getSignatures();
        if (signatureList != null) {
            for (final TransactionSignature signature : signatureList) {
                if (VerificationStatus.UNKNOWN.equals(signature.getSignatureStatus())) {
                    try {
                        final Future<Void> future = signature.waitForFuture();
                        future.get();
                    } catch (final ExecutionException | InterruptedException ex) {
                        logger.info(LOGM_EXCEPTION, "Error when verifying signature", ex);
                    }
                }
                if (VerificationStatus.INVALID.equals(signature.getSignatureStatus())) {
                    invalidSig = true;
                }
            }
        }
        return invalidSig;
    }

    /**
     * after reconnect/restart, rebuild ExpectedMap from state
     *
     * @param consensusTimestamp consensusTimestamp of the state which is received during reconnect or loaded after
     *                           restart
     * @param isRestart          Whether this is part of a workflow restart
     */
    public void rebuildExpectedMapFromState(final Instant consensusTimestamp, final boolean isRestart) {
        // rebuild ExpectedMap
        logger.info(LOGM_DEMO_INFO, "Start Rebuilding ExpectedMap");
        this.expectedFCMFamily.rebuildExpectedMap(getStateMap(), isRestart, 0);
        logger.info(LOGM_DEMO_INFO, "Finish Rebuilding ExpectedMap [ size = {} ]", () -> expectedFCMFamily
                .getExpectedMap()
                .size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ADD_QUORUM_RESULT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        // We never do migration with this state, so always fail if we are given an older version.
        return getVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void rebuild() {
        final boolean isMMapInitialized = this.getStateMap() != null;
        logger.info(LOGM_DEMO_INFO, "Finished initializing PlatformTestingDemoState with map {}", isMMapInitialized);
        if (isMMapInitialized) {
            logger.info(
                    LOGM_DEMO_INFO,
                    "The fcq MerkleMap has size {}",
                    this.getStateMap().getAccountFCQMap().size());
            logger.info(
                    LOGM_DEMO_INFO,
                    "The account MerkleMap has size {}",
                    this.getStateMap().getMap().size());
        }

        for (final NftId nftId : getNftLedger().getTokenIdToToken().keySet()) {
            expectedFCMFamily.addNftId(nftId);
        }

        getReferenceNftLedger().reload(getNftLedger());
    }

    /**
     * Remove expired records from FCQs
     *
     * @param consensusCurrentTimestamp transaction records whose expiration time is below or equal to this consensus
     *                                  time will be removed
     * @return number of removed records
     */
    public long purgeExpiredRecords(final long consensusCurrentTimestamp) {
        lastPurgeTimestamp = consensusCurrentTimestamp;

        final long startTime = System.nanoTime();
        final long removedNum = removeExpiredRecordsInExpirationQueue(consensusCurrentTimestamp);

        if (removedNum > 0) {
            final long timeTakenMicro = (System.nanoTime() - startTime) / MICROSECONDS_TO_NANOSECONDS;
            logger.info(
                    LOGM_EXPIRATION,
                    "Finish removing expired records from FCQs. Has removed: {} in {} ms",
                    removedNum,
                    timeTakenMicro);
            htCountExpiration += removedNum;
            htFCQExpirationSumMicro += timeTakenMicro;
        }
        return removedNum;
    }

    private long removeExpiredRecordsInExpirationQueue(final long consensusCurrentTimestamp) {
        long removedNumOfRecords = 0;
        while (!expirationQueue.isEmpty()
                && (expirationQueue.peek().getEarliestExpiry() <= consensusCurrentTimestamp)) {
            final ExpirationRecordEntry ere = expirationQueue.poll();
            final MapKey id = ere.getMapKey().copy();
            final FCMFamily fcmFamily = getFcmFamily();
            removedNumOfRecords += ExpirationUtils.purgeTransactionRecords(
                    consensusCurrentTimestamp,
                    expirationQueue,
                    accountsWithExpiringRecords,
                    id,
                    fcmFamily,
                    expectedFCMFamily);
        }
        htFCQRecordsCount = expirationQueue.size();
        return removedNumOfRecords;
    }

    /**
     * Rebuild Expiration Queue of transaction records with earliest expiry time after restart or reconnect
     */
    public void rebuildExpirationQueue() {
        final FCMFamily fcmFamily = getFcmFamily();
        if (fcmFamily == null || fcmFamily.getAccountFCQMap() == null) {
            logger.error(LOGM_EXCEPTION, "FCMFamily is null, so could not rebuild Expiration Queue");
            return;
        }
        if (fcmFamily.getAccountFCQMap().size() == 0) {
            return;
        }
        expirationQueue = new PriorityBlockingQueue<>();
        accountsWithExpiringRecords = new HashSet<>();
        ExpirationUtils.addRecordsDuringRebuild(fcmFamily, expirationQueue, accountsWithExpiringRecords);
    }

    /**
     * Useful for unit tests.
     */
    public void setExpectedFCMFamily(final ExpectedFCMFamily expectedFCMFamily) {
        this.expectedFCMFamily = expectedFCMFamily;
    }

    /**
     * The version history of this class. Versions that have been released must NEVER be given a different value.
     */
    private static class ClassVersion {
        /**
         * In this version, serialization was performed by copyTo/copyToExtra and deserialization was performed by
         * copyFrom/copyFromExtra. This version is not supported by later deserialization methods and must be handled
         * specially by the platform.
         */
        public static final int ORIGINAL = 1;
        /**
         * In this version, serialization was performed by serialize/deserialize.
         */
        public static final int MIGRATE_TO_SERIALIZABLE = 2;
        /**
         * In this version, removed FCMOperations class
         */
        public static final int REMOVED_FCM_OPERATIONS = 3;
        /**
         * In this version the NFT ledger was added to the state.
         */
        public static final int ADDED_NFT_LEDGER = 4;

        /**
         * In this version the quorum result was added to the state.
         */
        public static final int ADD_QUORUM_RESULT = 5;
    }

    private static class ChildIndices {
        public static final int UNUSED = 0;
        public static final int CONFIG = 1;
        /**
         * last sequence by each member for consensus events
         */
        public static final int NEXT_SEQUENCE_CONSENSUS = 2;

        public static final int FCM_FAMILY = 3;
        public static final int TRANSACTION_COUNTER = 4;
        public static final int ISS_LEAF = 5;
        /**
         * Migration test need this value to be able to load state file generated by v21 sdk
         */
        public static final int SDK_VERSION_21_CHILD_COUNT = 7;

        /**
         * The NFT ledger is very large, so adding it as an nary child is memory inefficient (due to merkle route
         * compression). But this is what Hedera is currently doing, so it is better to mimic their pattern and have
         * similar inefficiencies.
         */
        public static final int NFT_LEDGER = 6;

        public static final int VIRTUAL_MERKLE = 7;

        public static final int VIRTUAL_MERKLE_SMART_CONTRACTS = 8;

        public static final int VIRTUAL_MERKLE_SMART_CONTRACTS_BYTE_CODE = 9;

        public static final int QUORUM_RESULT = 10;

        public static final int CHILD_COUNT = 11;
    }

    @Override
    public void preHandle(final Event event) {
        event.forEachTransaction(this::preHandleTransaction);
    }
}
