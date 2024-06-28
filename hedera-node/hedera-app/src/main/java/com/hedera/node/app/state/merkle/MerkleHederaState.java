/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle;

import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_HEDERASTATE_STATENODE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVACCOUNTS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVALIASES;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVBLOBS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVEVMBYTECODE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVEVMSTORAGE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVNFTS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVNODES;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVSCHEDULESBYEQUALITY;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVSCHEDULESBYEXPIRYSEC;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVSCHEDULESBYID;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVSTAKINGINFO;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVTOKENRELS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVTOKENS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KVTOPICS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_QTXNRECORD;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_QUPGRADEDATA;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_SBLOCKINFO;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_SCONGESTIONLEVELSTARTS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_SENTITYID;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_SFREEZETIME;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_SMIDNIGHTRATES;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_SRUNNINGHASHES;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_SSTAKINGNETWORKREWARDS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_STHROTTLEUSAGESNAPSHOTS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_SUPGRADEFILEHASH;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_HEDERASTATE_STATENODE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVACCOUNTS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVALIASES;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVBLOBS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVEVMBYTECODE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVEVMSTORAGE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVNFTS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVNODES;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVSCHEDULESBYEQUALITY;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVSCHEDULESBYEXPIRYSEC;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVSCHEDULESBYID;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVSTAKINGINFO;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVTOKENRELS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVTOKENS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_KVTOPICS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_QTXNRECORD;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_QUPGRADEDATA;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_SBLOCKINFO;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_SCONGESTIONLEVELSTARTS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_SENTITYID;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_SFREEZETIME;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_SMIDNIGHTRATES;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_SRUNNINGHASHES;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_SSTAKINGNETWORKREWARDS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_STHROTTLEUSAGESNAPSHOTS;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.NUM_STATENODE_SUPGRADEFILEHASH;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.Hedera;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.fees.schemas.V0490FeeSchema;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.schemas.V0490EntityIdSchema;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.records.schemas.V0490BlockRecordSchema;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.spi.state.CommittableWritableStates;
import com.hedera.node.app.spi.state.EmptyReadableStates;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.throttle.schemas.V0490CongestionThrottleSchema;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.base.function.CheckedFunction;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.exceptions.MerkleSerializationException;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.utility.Labeled;
import com.swirlds.common.utility.ValueReference;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.serialize.ValueSerializer;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.merkle.StateUtils;
import com.swirlds.platform.state.merkle.disk.OnDiskKeySerializer;
import com.swirlds.platform.state.merkle.disk.OnDiskReadableKVState;
import com.swirlds.platform.state.merkle.disk.OnDiskValueSerializer;
import com.swirlds.platform.state.merkle.disk.OnDiskWritableKVState;
import com.swirlds.platform.state.merkle.memory.InMemoryReadableKVState;
import com.swirlds.platform.state.merkle.memory.InMemoryWritableKVState;
import com.swirlds.platform.state.merkle.queue.QueueNode;
import com.swirlds.platform.state.merkle.queue.ReadableQueueStateImpl;
import com.swirlds.platform.state.merkle.queue.WritableQueueStateImpl;
import com.swirlds.platform.state.merkle.singleton.ReadableSingletonStateImpl;
import com.swirlds.platform.state.merkle.singleton.SingletonNode;
import com.swirlds.platform.state.merkle.singleton.WritableSingletonStateImpl;
import com.swirlds.platform.state.spi.WritableKVStateBase;
import com.swirlds.platform.state.spi.WritableQueueStateBase;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.state.notifications.NewRecoveredStateListener;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableQueueState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link SwirldState} and {@link HederaState}. The Hashgraph Platform
 * communicates with the application through {@link SwirldMain} and {@link
 * SwirldState}. The Hedera application, after startup, only needs the ability to get {@link
 * ReadableStates} and {@link WritableStates} from this object.
 *
 * <p>Among {@link MerkleHederaState}'s child nodes are the various {@link
 * com.swirlds.merkle.map.MerkleMap}'s and {@link com.swirlds.virtualmap.VirtualMap}'s that make up
 * the service's states. Each such child node has a label specified that is computed from the
 * metadata for that state. Since both service names and state keys are restricted to characters
 * that do not include the period, we can use it to separate service name from state key. When we
 * need to find all states for a service, we can do so by iteration and string comparison.
 *
 * <p>NOTE: The implementation of this class must change before we can support state proofs
 * properly. In particular, a wide n-ary number of children is less than ideal, since the hash of
 * each child must be part of the state proof. It would be better to have a binary tree. We should
 * consider nesting service nodes in a MerkleMap, or some other such approach to get a binary tree.
 */
public class MerkleHederaState extends PartialNaryMerkleInternal
        implements MerkleInternal, SwirldState, HederaState, MerkleRoot {
    private static final Logger logger = LogManager.getLogger(MerkleHederaState.class);

    /**
     * Used when asked for a service's readable states that we don't have
     */
    private static final ReadableStates EMPTY_READABLE_STATES = new EmptyReadableStates();

    // For serialization
    /**
     * This class ID is returned IF the default constructor was used. This is a nasty workaround for
     * {@link ConstructableRegistry}. The registry does classpath scanning, and finds this class. It then invokes
     * the default constructor and then asks for the class ID, and then throws away the object. It sticks this
     * mapping of class ID to class name into a map. Later (in {@link Hedera}) we define an actual mapping for the
     * {@link ConstructableRegistry} that maps {@link #CLASS_ID} to {@link MerkleHederaState} using a lambda that will
     * create the instancing using the non-default constructor. But the {@link ConstructableRegistry} doesn't
     * actually register the second mapping! So we will trick it. We will return this bogus class ID if the default
     * constructor is used, or {@link #CLASS_ID} otherwise.
     */
    private static final long DO_NOT_USE_IN_REAL_LIFE_CLASS_ID = 0x0000deadbeef0000L;

    //    private static final long CLASS_ID = 0x2de3ead3caf06392L;
    // Uncomment the following class ID to run a mono -> modular state migration
    // NOTE: also change class ID of ServicesState
    private static final long CLASS_ID = 0x8e300b0dfdafbb1aL;
    private static final int VERSION_1 = 30;
    private static final int CURRENT_VERSION = VERSION_1;

    // This is a temporary fix to deal with the inefficient implementation of findNodeIndex(). It caches looked up
    // indices globally, assuming these indices do not change that often. We need to re-think index lookup,
    // but at this point all major rewrites seem to risky.
    private static final Map<String, Integer> INDEX_LOOKUP = new ConcurrentHashMap<>();

    // Only used for deserialization
    private final ServicesRegistry servicesRegistry;

    private long classId;

    /**
     * The callbacks for Hedera lifecycle events.
     */
    private final HederaLifecycles lifecycles;

    public Map<String, Map<String, StateMetadata<?, ?>>> getServices() {
        return services;
    }

    private Metrics metrics;

    /**
     * Maintains information about each service, and each state of each service, known by this
     * instance. The key is the "service-name.state-key".
     */
    private final Map<String, Map<String, StateMetadata<?, ?>>> services = new HashMap<>();

    /**
     * Cache of used {@link ReadableStates}.
     */
    private final Map<String, ReadableStates> readableStatesMap = new ConcurrentHashMap<>();

    /**
     * Cache of used {@link WritableStates}.
     */
    private final Map<String, MerkleWritableStates> writableStatesMap = new HashMap<>();

    /**
     * Create a new instance. This constructor must be used for all creations of this class.
     *
     * @param lifecycles The lifecycle callbacks. Cannot be null.
     */
    public MerkleHederaState(@NonNull final HederaLifecycles lifecycles) {
        this.lifecycles = requireNonNull(lifecycles);
        // FUTURE WORK: set servicesRegistry
        this.servicesRegistry = null;
        this.classId = CLASS_ID;
    }

    /**
     * This constructor ONLY exists for the benefit of the ConstructableRegistry. It is not actually
     * used except by the registry to create an instance of this class for the purpose of getting
     * the class ID. It should never be used for any other purpose. And one day we won't need it
     * anymore and can remove it.
     *
     * @deprecated This constructor is only for use by the ConstructableRegistry.
     */
    @Deprecated(forRemoval = true)
    public MerkleHederaState() {
        // ConstructableRegistry requires a "working" no-arg constructor
        this.lifecycles = null;
        this.servicesRegistry = null;
        this.classId = DO_NOT_USE_IN_REAL_LIFE_CLASS_ID;
    }

    public MerkleHederaState(
            final @NonNull HederaLifecycles lifecycles,
            final @NonNull ServicesRegistry servicesRegistry,
            final @NonNull ReadableSequentialData in,
            final Path artifactsDir)
            throws MerkleSerializationException {
        this.lifecycles = requireNonNull(lifecycles);
        this.servicesRegistry = servicesRegistry;
        protoDeserialize(in, artifactsDir);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Called by the platform whenever the state should be initialized. This can happen at genesis startup,
     * on restart, on reconnect, or any other time indicated by the {@code trigger}.
     */
    @Override
    public void init(
            final Platform platform,
            final PlatformState platformState,
            final InitTrigger trigger,
            final SoftwareVersion deserializedVersion) {
        metrics = platform.getContext().getMetrics();

        // If we are initialized for event stream recovery, we have to register an
        // extra listener to make sure we call all the required Hedera lifecycles
        if (trigger == EVENT_STREAM_RECOVERY) {
            final var notificationEngine = platform.getNotificationEngine();
            notificationEngine.register(
                    NewRecoveredStateListener.class,
                    notification -> lifecycles.onNewRecoveredState(notification.getSwirldState()));
        }
        // At some point this method will no longer be defined on SwirldState2, because we want to move
        // to a model where SwirldState/SwirldState2 are simply data objects, without this lifecycle.
        // Instead, this method will be a callback the app registers with the platform. So for now,
        // we simply call the callback handler, which is implemented by the app.
        lifecycles.onStateInitialized(this, platform, platformState, trigger, deserializedVersion);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public AddressBook updateWeight(
            @NonNull final AddressBook configAddressBook, @NonNull final PlatformContext context) {
        lifecycles.onUpdateWeight(this, configAddressBook, context);
        return configAddressBook;
    }

    /**
     * Private constructor for fast-copy.
     *
     * @param from The other state to fast-copy from. Cannot be null.
     */
    private MerkleHederaState(@NonNull final MerkleHederaState from) {
        // Copy the Merkle route from the source instance
        super(from);

        this.classId = from.classId;
        this.lifecycles = from.lifecycles;
        this.servicesRegistry = null; // only used, when a state is deserialized

        // Copy over the metadata
        for (final var entry : from.services.entrySet()) {
            this.services.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }

        // Copy the non-null Merkle children from the source (should also be handled by super, TBH).
        // Note we don't "compress" -- null children remain in here unless we manually remove them
        // (which would cause massive re-hashing).
        for (int childIndex = 0, n = from.getNumberOfChildren(); childIndex < n; childIndex++) {
            final var childToCopy = from.getChild(childIndex);
            if (childToCopy != null) {
                setChild(childIndex, childToCopy.copy());
            }
        }
    }

    @Override
    public long getClassId() {
        return classId;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    // Protobuf serialization

    private static final String LABEL_SENTITYID = EntityIdService.NAME + "." + V0490EntityIdSchema.ENTITY_ID_STATE_KEY;
    private static final String LABEL_SBLOCKINFO = BlockRecordService.NAME + "." + V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;
    private static final String LABEL_SMIDNIGHTRATES = FeeService.NAME + "." + V0490FeeSchema.MIDNIGHT_RATES_STATE_KEY;
    private static final String LABEL_SRUNNINGHASHES = BlockRecordService.NAME + "." + V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY;
    private static final String LABEL_STHROTTLEUSAGESNAPSHOTS = CongestionThrottleService.NAME + "." + V0490CongestionThrottleSchema.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY;
    private static final String LABEL_SCONGESTIONLEVELSTARTS = CongestionThrottleService.NAME + "." + V0490CongestionThrottleSchema.CONGESTION_LEVEL_STARTS_STATE_KEY;
    private static final String LABEL_SSTAKINGNETWORKREWARDS = TokenService.NAME + "." + V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY;
    private static final String LABEL_SUPGRADEFILEHASH = FreezeService.NAME + "." + V0490FreezeSchema.UPGRADE_FILE_HASH_KEY;
    private static final String LABEL_SFREEZETIME = FreezeService.NAME + "." + V0490FreezeSchema.FREEZE_TIME_KEY;

    private static final String LABEL_QTXNRECORD = RecordCacheService.NAME + "." + V0490RecordCacheSchema.TXN_RECORD_QUEUE;
    private static final String LABEL_QUPGRADEDATA = FileService.NAME + "." + V0490FileSchema.UPGRADE_DATA_KEY;

    @Override
    protected int getProtoChildSizeInBytes(final int index) {
        // Hedera state nodes are wrapped into StateNode proto message to overcome (lack of)
        // support for repeated oneof fields in protobuf
        final MerkleNode stateNode = getChild(index);
        return getProtoChildSizeInBytes(stateNode);
    }

    private int getProtoChildSizeInBytes(final MerkleNode stateNode) {
        final FieldDefinition childField = getStateNodeProtoField(stateNode);
        final int stateNodeSize = ProtoWriterTools.sizeOfDelimited(childField, stateNode.getProtoSizeInBytes());
        // Now wrap it into StateNode
        return ProtoWriterTools.sizeOfDelimited(FIELD_HEDERASTATE_STATENODE, stateNodeSize);
    }

    private FieldDefinition getStateNodeProtoField(final MerkleNode stateNode) {
        if (!(stateNode instanceof Labeled labeled)) {
            throw new IllegalStateException("Child node must be a Labeled");
        }
        final String label = labeled.getLabel();
        final StateDefinition def = servicesRegistry.getStateDefinition(label);
        if (def == null) {
            throw new IllegalStateException("Unknown state: " + label);
        }
        if (def.singleton()) {
            return switch (label) {
                case LABEL_SENTITYID -> FIELD_STATENODE_SENTITYID;
                case LABEL_SBLOCKINFO -> FIELD_STATENODE_SBLOCKINFO;
                case LABEL_SMIDNIGHTRATES -> FIELD_STATENODE_SMIDNIGHTRATES;
                case LABEL_SRUNNINGHASHES -> FIELD_STATENODE_SRUNNINGHASHES;
                case LABEL_STHROTTLEUSAGESNAPSHOTS -> FIELD_STATENODE_STHROTTLEUSAGESNAPSHOTS;
                case LABEL_SCONGESTIONLEVELSTARTS -> FIELD_STATENODE_SCONGESTIONLEVELSTARTS;
                case LABEL_SSTAKINGNETWORKREWARDS -> FIELD_STATENODE_SSTAKINGNETWORKREWARDS;
                case LABEL_SUPGRADEFILEHASH -> FIELD_STATENODE_SUPGRADEFILEHASH;
                case LABEL_SFREEZETIME -> FIELD_STATENODE_SFREEZETIME;
                default -> throw new IllegalStateException("Unknown singleton state: " + label);
            };
        } else if (def.queue()) {
            return switch (label) {
                case LABEL_QTXNRECORD -> FIELD_STATENODE_QTXNRECORD;
                case LABEL_QUPGRADEDATA -> FIELD_STATENODE_QUPGRADEDATA;
                default -> throw new IllegalStateException("Unknown queue state: " + label);
            };
        } else if (def.onDisk()) {
            return switch (label) {
                case TokenService.NAME + "." + V0490TokenSchema.TOKENS_KEY -> FIELD_STATENODE_KVTOKENS;
                case TokenService.NAME + "." + V0490TokenSchema.ACCOUNTS_KEY -> FIELD_STATENODE_KVACCOUNTS;
                case TokenService.NAME + "." + V0490TokenSchema.ALIASES_KEY -> FIELD_STATENODE_KVALIASES;
                case TokenService.NAME + "." + V0490TokenSchema.NFTS_KEY-> FIELD_STATENODE_KVNFTS;
                case TokenService.NAME + "." + V0490TokenSchema.TOKEN_RELS_KEY -> FIELD_STATENODE_KVTOKENRELS;
                case TokenService.NAME + "." + V0490TokenSchema.STAKING_INFO_KEY -> FIELD_STATENODE_KVSTAKINGINFO;
                case ScheduleService.NAME + "." + V0490ScheduleSchema.SCHEDULES_BY_ID_KEY -> FIELD_STATENODE_KVSCHEDULESBYID;
                case ScheduleService.NAME + "." + V0490ScheduleSchema.SCHEDULES_BY_EXPIRY_SEC_KEY -> FIELD_STATENODE_KVSCHEDULESBYEXPIRYSEC;
                case ScheduleService.NAME + "." + V0490ScheduleSchema.SCHEDULES_BY_EQUALITY_KEY -> FIELD_STATENODE_KVSCHEDULESBYEQUALITY;
                case FileService.NAME + "." + V0490FileSchema.BLOBS_KEY -> FIELD_STATENODE_KVBLOBS;
                case ContractService.NAME + "." + V0490ContractSchema.STORAGE_KEY -> FIELD_STATENODE_KVEVMSTORAGE;
                case ContractService.NAME + "." + V0490ContractSchema.BYTECODE_KEY -> FIELD_STATENODE_KVEVMBYTECODE;
                case ConsensusService.NAME + "." + ConsensusServiceImpl.TOPICS_KEY -> FIELD_STATENODE_KVTOPICS;
                case AddressBookService.NAME + "." + AddressBookServiceImpl.NODES_KEY -> FIELD_STATENODE_KVNODES;
                default -> throw new IllegalStateException("Unknown onDisk state: " + label);
            };
        } else {
            throw new IllegalStateException("Unknown state type: " + def);
        }
    }

    @Override
    protected FieldDefinition getChildProtoField(final int childIndex) {
        // FUTURE WORK: check childIndex vs actual number of child nodes?
        return FIELD_HEDERASTATE_STATENODE;
    }

    @Override
    protected void protoSerializeChild(
            @NonNull final WritableSequentialData out,
            final Path artifactsDir,
            final int childIndex,
            @NonNull FieldDefinition childField)
            throws MerkleSerializationException {
        assert childField == FIELD_HEDERASTATE_STATENODE;
        final MerkleNode stateNode = getChild(childIndex);
        // Wrap the child node into StateNode, this is a workaround to unsupported repeated oneof
        // protobuf fields
        final ValueReference<MerkleSerializationException> ex = new ValueReference<>();
        ProtoWriterTools.writeDelimited(out, childField, getProtoChildSizeInBytes(stateNode), o -> {
            ProtoWriterTools.writeDelimited(o, getStateNodeProtoField(stateNode), stateNode.getProtoSizeInBytes(), p -> {
                try {
                    stateNode.protoSerialize(out, artifactsDir);
                } catch (final MerkleSerializationException e) {
                    ex.setValue(e);
                }
            });
        });
        if (ex.getValue() != null) {
            throw ex.getValue();
        }
    }

    @Override
    protected boolean isChildNodeProtoTag(int fieldNum) {
        return fieldNum == NUM_HEDERASTATE_STATENODE;
    }

    @Override
    protected MerkleNode protoDeserializeNextChild(
            @NonNull final ReadableSequentialData in,
            final Path artifactsDir)
            throws MerkleSerializationException {
        try {
            final int tag = in.readVarInt(false);
            final int fieldNum = tag >> ProtoParserTools.TAG_FIELD_OFFSET;
            final int wireType = tag & ProtoConstants.TAG_WIRE_TYPE_MASK;
            if (fieldNum != NUM_HEDERASTATE_STATENODE) {
                throw new MerkleSerializationException("Unknown child field: " + tag);
            }
            if (wireType != ProtoConstants.WIRE_TYPE_DELIMITED.ordinal()) {
                throw new MerkleSerializationException("Unexpected wire type: " + tag);
            }
            final int len = in.readVarInt(false);
            final long oldLimit = in.limit();
            try {
                in.limit(in.position() + len);
                final int stateNodeTag = in.readVarInt(false);
                final int stateNodeFieldNum = stateNodeTag >> ProtoParserTools.TAG_FIELD_OFFSET;
                final int stateNodeWireType = stateNodeTag & ProtoConstants.TAG_WIRE_TYPE_MASK;
                if (stateNodeWireType != ProtoConstants.WIRE_TYPE_DELIMITED.ordinal()) {
                    throw new MerkleSerializationException("Unexpected wire type: " + stateNodeTag);
                }
                final int stateNodeLen = in.readVarInt(false);
                in.limit(in.position() + stateNodeLen);
                if (stateNodeFieldNum == NUM_STATENODE_SENTITYID) {
                    return protoDeserializeSingleton(in, artifactsDir, servicesRegistry.getStateDefinition(LABEL_SENTITYID));
                } else if (stateNodeFieldNum == NUM_STATENODE_SBLOCKINFO) {
                    return protoDeserializeSingleton(in, artifactsDir, servicesRegistry.getStateDefinition(LABEL_SBLOCKINFO));
                } else if (stateNodeFieldNum == NUM_STATENODE_SMIDNIGHTRATES) {
                    return protoDeserializeSingleton(in, artifactsDir, servicesRegistry.getStateDefinition(LABEL_SMIDNIGHTRATES));
                } else if (stateNodeFieldNum == NUM_STATENODE_SRUNNINGHASHES) {
                    return protoDeserializeSingleton(in, artifactsDir, servicesRegistry.getStateDefinition(LABEL_SRUNNINGHASHES));
                } else if (stateNodeFieldNum == NUM_STATENODE_STHROTTLEUSAGESNAPSHOTS) {
                    return protoDeserializeSingleton(in, artifactsDir, servicesRegistry.getStateDefinition(LABEL_STHROTTLEUSAGESNAPSHOTS));
                } else if (stateNodeFieldNum == NUM_STATENODE_SCONGESTIONLEVELSTARTS) {
                    return protoDeserializeSingleton(in, artifactsDir, servicesRegistry.getStateDefinition(LABEL_SCONGESTIONLEVELSTARTS));
                } else if (stateNodeFieldNum == NUM_STATENODE_SSTAKINGNETWORKREWARDS) {
                    return protoDeserializeSingleton(in, artifactsDir, servicesRegistry.getStateDefinition(LABEL_SSTAKINGNETWORKREWARDS));
                } else if (stateNodeFieldNum == NUM_STATENODE_SUPGRADEFILEHASH) {
                    return protoDeserializeSingleton(in, artifactsDir, servicesRegistry.getStateDefinition(LABEL_SUPGRADEFILEHASH));
                } else if (stateNodeFieldNum == NUM_STATENODE_SFREEZETIME) {
                    return protoDeserializeSingleton(in, artifactsDir, servicesRegistry.getStateDefinition(LABEL_SFREEZETIME));
                } else if (stateNodeFieldNum == NUM_STATENODE_QTXNRECORD) {
                    return protoDeserializeQueue(in, artifactsDir, servicesRegistry.getStateDefinition(LABEL_QTXNRECORD));
                } else if (stateNodeFieldNum == NUM_STATENODE_QUPGRADEDATA) {
                    return protoDeserializeQueue(in, artifactsDir, servicesRegistry.getStateDefinition(LABEL_QUPGRADEDATA));
                } else if (stateNodeFieldNum == NUM_STATENODE_KVTOKENS) {
                    return protoDeserializeVirtualMap(in, artifactsDir, TokenService.NAME, V0490TokenSchema.TOKENS_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVACCOUNTS) {
                    return protoDeserializeVirtualMap(in, artifactsDir, TokenService.NAME, V0490TokenSchema.ACCOUNTS_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVALIASES) {
                    return protoDeserializeVirtualMap(in, artifactsDir, TokenService.NAME, V0490TokenSchema.ALIASES_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVNFTS) {
                    return protoDeserializeVirtualMap(in, artifactsDir, TokenService.NAME, V0490TokenSchema.NFTS_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVTOKENRELS) {
                    return protoDeserializeVirtualMap(in, artifactsDir, TokenService.NAME, V0490TokenSchema.TOKEN_RELS_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVSTAKINGINFO) {
                    return protoDeserializeVirtualMap(in, artifactsDir, TokenService.NAME, V0490TokenSchema.STAKING_INFO_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVSCHEDULESBYID) {
                    return protoDeserializeVirtualMap(in, artifactsDir, ScheduleService.NAME, V0490ScheduleSchema.SCHEDULES_BY_ID_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVSCHEDULESBYEXPIRYSEC) {
                    return protoDeserializeVirtualMap(in, artifactsDir, ScheduleService.NAME, V0490ScheduleSchema.SCHEDULES_BY_EXPIRY_SEC_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVSCHEDULESBYEQUALITY) {
                    return protoDeserializeVirtualMap(in, artifactsDir, ScheduleService.NAME, V0490ScheduleSchema.SCHEDULES_BY_EQUALITY_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVBLOBS) {
                    return protoDeserializeVirtualMap(in, artifactsDir, FileService.NAME, V0490FileSchema.BLOBS_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVEVMSTORAGE) {
                    return protoDeserializeVirtualMap(in, artifactsDir, ContractService.NAME, V0490ContractSchema.STORAGE_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVEVMBYTECODE) {
                    return protoDeserializeVirtualMap(in, artifactsDir, ContractService.NAME, V0490ContractSchema.BYTECODE_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVTOPICS) {
                    return protoDeserializeVirtualMap(in, artifactsDir, ConsensusService.NAME, ConsensusServiceImpl.TOPICS_KEY);
                } else if (stateNodeFieldNum == NUM_STATENODE_KVNODES) {
                    return protoDeserializeVirtualMap(in, artifactsDir, ConsensusService.NAME, AddressBookServiceImpl.NODES_KEY);
                } else {
                    throw new MerkleSerializationException("Unknown state node type: " + stateNodeTag);
                }
            } finally {
                in.limit(oldLimit);
            }
        } catch (final Exception e) {
            throw new MerkleSerializationException("Cannot deserialize a state child node", e);
        }
    }

    private MerkleNode protoDeserializeSingleton(
            @NonNull final ReadableSequentialData in,
            final Path artifactsDir,
            @NonNull final StateDefinition stateDef)
            throws MerkleSerializationException {
        return new SingletonNode<>(in, artifactsDir, stateDef.valueCodec());
    }

    private MerkleNode protoDeserializeQueue(
            @NonNull final ReadableSequentialData in,
            final Path artifactsDir,
            @NonNull final StateDefinition stateDef)
            throws MerkleSerializationException {
        return new QueueNode<>(in, artifactsDir, stateDef.valueCodec());
    }

    // https://github.com/hashgraph/hedera-services/issues/13781
    /*
    private MerkleNode protoDeserializeKeyValue(
            @NonNull final ReadableSequentialData in,
            final Path artifactsDir)
            throws MerkleSerializationException {
        throw new UnsupportedOperationException("TO IMPLEMENT: protoDeserializeKeyValue()");
    }
    */

    @SuppressWarnings({"rawtypes", "unchecked"})
    private MerkleNode protoDeserializeVirtualMap(
            @NonNull final ReadableSequentialData in,
            final Path artifactsDir,
            final String serviceName,
            final String stateKey)
            throws MerkleSerializationException {
        final String stateLabel = serviceName + "." + stateKey;
        final StateDefinition stateDef = servicesRegistry.getStateDefinition(stateLabel);
        if (stateDef == null) {
            throw new MerkleSerializationException("Unknown state key: " + stateLabel);
        }
        if (!stateDef.onDisk()) {
            throw new MerkleSerializationException("State type is not onDisk: " + stateLabel);
        }
        final Codec keyCodec = stateDef.keyCodec();
        final Codec valueCodec = stateDef.valueCodec();
        final KeySerializer keySerializer = new OnDiskKeySerializer(0, 0, keyCodec);
        final ValueSerializer valueSerializer = new OnDiskValueSerializer(0, 0, valueCodec);
        final MerkleDbDataSourceBuilder builder = new MerkleDbDataSourceBuilder(keySerializer, valueSerializer, null);
        final CheckedFunction<ReadableSequentialData, ?, Exception> keyReader = keyCodec::parse;
        final CheckedFunction<ReadableSequentialData, ?, Exception> valueReader = valueCodec::parse;
        return new VirtualMap(in, artifactsDir, builder, keyReader, valueReader);
    }



    /**
     * To be called ONLY at node shutdown. Attempts to gracefully close any virtual maps. This method is a bit of a
     * hack, ideally there would be something more generic at the platform level that virtual maps could hook into
     * to get shutdown in an orderly way.
     */
    public void close() {
        logger.info("Closing MerkleHederaState");
        for (final var svc : services.values()) {
            for (final var md : svc.values()) {
                final var index = findNodeIndex(md.serviceName(), extractStateKey(md));
                if (index >= 0) {
                    final var node = getChild(index);
                    if (node instanceof VirtualMap<?, ?> virtualMap) {
                        try {
                            virtualMap.getDataSource().close();
                        } catch (IOException e) {
                            logger.warn("Unable to close data source for virtual map {}", md.serviceName(), e);
                        }
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ReadableStates getReadableStates(@NonNull String serviceName) {
        return readableStatesMap.computeIfAbsent(serviceName, s -> {
            final var stateMetadata = services.get(s);
            return stateMetadata == null ? EMPTY_READABLE_STATES : new MerkleReadableStates(stateMetadata);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public WritableStates getWritableStates(@NonNull final String serviceName) {
        throwIfImmutable();
        return writableStatesMap.computeIfAbsent(serviceName, s -> {
            final var stateMetadata = services.getOrDefault(s, Map.of());
            return new MerkleWritableStates(serviceName, stateMetadata);
        });
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public MerkleHederaState copy() {
        throwIfImmutable();
        throwIfDestroyed();
        setImmutable(true);
        return new MerkleHederaState(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(@NonNull final Round round, @NonNull final PlatformState platformState) {
        throwIfImmutable();
        lifecycles.onHandleConsensusRound(round, platformState, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preHandle(@NonNull final Event event) {
        lifecycles.onPreHandle(event, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleNode migrate(final int ignored) {
        // Always return this node, we never want to replace MerkleHederaState node in the tree
        return this;
    }

    /**
     * Puts the defined service state and its associated node into the merkle tree. The precondition
     * for calling this method is that node MUST be a {@link MerkleMap} or {@link VirtualMap} and
     * MUST have a correct label applied.
     *
     * @param md The metadata associated with the state
     * @param nodeSupplier Returns the node to add. Cannot be null. Can be used to create the node on-the-fly.
     * @throws IllegalArgumentException if the node is neither a merkle map nor virtual map, or if
     * it doesn't have a label, or if the label isn't right.
     */
    <K, V> void putServiceStateIfAbsent(
            @NonNull final StateMetadata<K, V> md, @NonNull final Supplier<MerkleNode> nodeSupplier) {

        // Validate the inputs
        throwIfImmutable();
        requireNonNull(md);
        requireNonNull(nodeSupplier);

        // Put this metadata into the map
        final var def = md.stateDefinition();
        final var serviceName = md.serviceName();
        final var stateMetadata = services.computeIfAbsent(serviceName, k -> new HashMap<>());
        stateMetadata.put(def.stateKey(), md);

        // We also need to add/update the metadata of the service in the writableStatesMap so that
        // it isn't stale or incomplete (e.g. in a genesis case)
        readableStatesMap.put(serviceName, new MerkleReadableStates(stateMetadata));
        writableStatesMap.put(serviceName, new MerkleWritableStates(serviceName, stateMetadata));

        // Look for a node, and if we don't find it, then insert the one we were given
        // If there is not a node there, then set it. I don't want to overwrite the existing node,
        // because it may have been loaded from state on disk, and the node provided here in this
        // call is always for genesis. So we may just ignore it.
        if (findNodeIndex(serviceName, def.stateKey()) == -1) {
            final var node = requireNonNull(nodeSupplier.get());
            final var label = node instanceof Labeled labeled ? labeled.getLabel() : null;
            if (label == null) {
                throw new IllegalArgumentException("`node` must be a Labeled and have a label");
            }

            if (def.onDisk() && !(node instanceof VirtualMap<?, ?>)) {
                throw new IllegalArgumentException(
                        "Mismatch: state definition claims on-disk, but " + "the merkle node is not a VirtualMap");
            }

            if (label.isEmpty()) {
                // It looks like both MerkleMap and VirtualMap do not allow for a null label.
                // But I want to leave this check in here anyway, in case that is ever changed.
                throw new IllegalArgumentException("A label must be specified on the node");
            }

            if (!label.equals(StateUtils.computeLabel(serviceName, def.stateKey()))) {
                throw new IllegalArgumentException(
                        "A label must be computed based on the same " + "service name and state key in the metadata!");
            }

            setChild(getNumberOfChildren(), node);
        }
    }

    /**
     * Removes the node and metadata from the state merkle tree.
     *
     * @param serviceName The service name. Cannot be null.
     * @param stateKey The state key
     */
    void removeServiceState(@NonNull final String serviceName, @NonNull final String stateKey) {
        throwIfImmutable();
        requireNonNull(serviceName);
        requireNonNull(stateKey);

        // Remove the metadata entry
        final var stateMetadata = services.get(serviceName);
        if (stateMetadata != null) {
            stateMetadata.remove(stateKey);
        }

        // Eventually remove the cached WritableState
        final var writableStates = writableStatesMap.get(serviceName);
        if (writableStates != null) {
            writableStates.remove(stateKey);
        }

        // Remove the node
        final var index = findNodeIndex(serviceName, stateKey);
        if (index != -1) {
            setChild(index, null);
        }
    }

    /**
     * Simple utility method that finds the state node index.
     *
     * @param serviceName the service name
     * @param stateKey the state key
     * @return -1 if not found, otherwise the index into the children
     */
    public int findNodeIndex(@NonNull final String serviceName, @NonNull final String stateKey) {
        final var label = StateUtils.computeLabel(serviceName, stateKey);

        final Integer index = INDEX_LOOKUP.get(label);
        if (index != null && checkNodeIndex(index, label)) {
            return index;
        }

        for (int i = 0, n = getNumberOfChildren(); i < n; i++) {
            if (checkNodeIndex(i, label)) {
                INDEX_LOOKUP.put(label, i);
                return i;
            }
        }

        INDEX_LOOKUP.remove(label);
        return -1;
    }

    private boolean checkNodeIndex(final int index, @NonNull final String label) {
        final var node = getChild(index);
        return node instanceof Labeled labeled && Objects.equals(label, labeled.getLabel());
    }

    /**
     * Base class implementation for states based on MerkleTree
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private abstract class MerkleStates implements ReadableStates {
        protected final Map<String, StateMetadata<?, ?>> stateMetadata;
        protected final Map<String, ReadableKVState<?, ?>> kvInstances;
        protected final Map<String, ReadableSingletonState<?>> singletonInstances;
        protected final Map<String, ReadableQueueState<?>> queueInstances;
        private final Set<String> stateKeys;

        /**
         * Create a new instance
         *
         * @param stateMetadata cannot be null
         */
        MerkleStates(@NonNull final Map<String, StateMetadata<?, ?>> stateMetadata) {
            this.stateMetadata = requireNonNull(stateMetadata);
            this.stateKeys = Collections.unmodifiableSet(stateMetadata.keySet());
            this.kvInstances = new HashMap<>();
            this.singletonInstances = new HashMap<>();
            this.queueInstances = new HashMap<>();
        }

        @NonNull
        @Override
        public <K, V> ReadableKVState<K, V> get(@NonNull String stateKey) {
            final ReadableKVState<K, V> instance = (ReadableKVState<K, V>) kvInstances.get(stateKey);
            if (instance != null) {
                return instance;
            }

            final var md = stateMetadata.get(stateKey);
            if (md == null || md.stateDefinition().singleton()) {
                throw new IllegalArgumentException("Unknown k/v state key '" + stateKey + ";");
            }

            final var node = findNode(md);
            if (node instanceof VirtualMap v) {
                final var ret = createReadableKVState(md, v);
                kvInstances.put(stateKey, ret);
                return ret;
            } else if (node instanceof MerkleMap m) {
                final var ret = createReadableKVState(md, m);
                kvInstances.put(stateKey, ret);
                return ret;
            } else {
                // This exception should never be thrown. Only if "findNode" found the wrong node!
                throw new IllegalStateException("Unexpected type for k/v state " + stateKey);
            }
        }

        @NonNull
        @Override
        public <T> ReadableSingletonState<T> getSingleton(@NonNull String stateKey) {
            final ReadableSingletonState<T> instance = (ReadableSingletonState<T>) singletonInstances.get(stateKey);
            if (instance != null) {
                return instance;
            }

            final var md = stateMetadata.get(stateKey);
            if (md == null || !md.stateDefinition().singleton()) {
                throw new IllegalArgumentException("Unknown singleton state key '" + stateKey + "'");
            }

            final var node = findNode(md);
            if (node instanceof SingletonNode s) {
                final var ret = createReadableSingletonState(md, s);
                singletonInstances.put(stateKey, ret);
                return ret;
            } else {
                // This exception should never be thrown. Only if "findNode" found the wrong node!
                throw new IllegalStateException("Unexpected type for singleton state " + stateKey);
            }
        }

        @NonNull
        @Override
        public <E> ReadableQueueState<E> getQueue(@NonNull String stateKey) {
            final ReadableQueueState<E> instance = (ReadableQueueState<E>) queueInstances.get(stateKey);
            if (instance != null) {
                return instance;
            }

            final var md = stateMetadata.get(stateKey);
            if (md == null || !md.stateDefinition().queue()) {
                throw new IllegalArgumentException("Unknown queue state key '" + stateKey + "'");
            }

            final var node = findNode(md);
            if (node instanceof QueueNode q) {
                final var ret = createReadableQueueState(md, q);
                queueInstances.put(stateKey, ret);
                return ret;
            } else {
                // This exception should never be thrown. Only if "findNode" found the wrong node!
                throw new IllegalStateException("Unexpected type for queue state " + stateKey);
            }
        }

        @Override
        public boolean contains(@NonNull final String stateKey) {
            return stateMetadata.containsKey(stateKey);
        }

        @NonNull
        @Override
        public Set<String> stateKeys() {
            return stateKeys;
        }

        @NonNull
        protected abstract ReadableKVState createReadableKVState(@NonNull StateMetadata md, @NonNull VirtualMap v);

        @NonNull
        protected abstract ReadableKVState createReadableKVState(@NonNull StateMetadata md, @NonNull MerkleMap m);

        @NonNull
        protected abstract ReadableSingletonState createReadableSingletonState(
                @NonNull StateMetadata md, @NonNull SingletonNode<?> s);

        @NonNull
        protected abstract ReadableQueueState createReadableQueueState(
                @NonNull StateMetadata md, @NonNull QueueNode<?> q);

        /**
         * Utility method for finding and returning the given node. Will throw an ISE if such a node
         * cannot be found!
         *
         * @param md The metadata
         * @return The found node
         */
        @NonNull
        MerkleNode findNode(@NonNull final StateMetadata<?, ?> md) {
            final var index = findNodeIndex(md.serviceName(), extractStateKey(md));
            if (index == -1) {
                // This can only happen if there WAS a node here, and it was removed!
                throw new IllegalStateException("State '"
                        + extractStateKey(md)
                        + "' for service '"
                        + md.serviceName()
                        + "' is missing from the merkle tree!");
            }

            return getChild(index);
        }
    }

    /**
     * An implementation of {@link ReadableStates} based on the merkle tree.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public final class MerkleReadableStates extends MerkleStates {
        /**
         * Create a new instance
         *
         * @param stateMetadata cannot be null
         */
        MerkleReadableStates(@NonNull final Map<String, StateMetadata<?, ?>> stateMetadata) {
            super(stateMetadata);
        }

        @Override
        @NonNull
        protected ReadableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final VirtualMap v) {
            return new OnDiskReadableKVState<>(
                    extractStateKey(md),
                    md.onDiskKeyClassId(),
                    md.stateDefinition().keyCodec(),
                    v);
        }

        @Override
        @NonNull
        protected ReadableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final MerkleMap m) {
            return new InMemoryReadableKVState<>(extractStateKey(md), m);
        }

        @Override
        @NonNull
        protected ReadableSingletonState<?> createReadableSingletonState(
                @NonNull final StateMetadata md, @NonNull final SingletonNode<?> s) {
            return new ReadableSingletonStateImpl<>(extractStateKey(md), s);
        }

        @NonNull
        @Override
        protected ReadableQueueState createReadableQueueState(@NonNull StateMetadata md, @NonNull QueueNode<?> q) {
            return new ReadableQueueStateImpl(extractStateKey(md), q);
        }
    }

    /**
     * An implementation of {@link WritableStates} based on the merkle tree.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public final class MerkleWritableStates extends MerkleStates implements WritableStates, CommittableWritableStates {

        private final String serviceName;

        /**
         * Create a new instance
         *
         * @param stateMetadata cannot be null
         */
        MerkleWritableStates(
                @NonNull final String serviceName, @NonNull final Map<String, StateMetadata<?, ?>> stateMetadata) {
            super(stateMetadata);
            this.serviceName = requireNonNull(serviceName);
        }

        /**
         * Copies and releases the {@link VirtualMap} for the given state key. This ensures
         * data is continually flushed to disk
         *
         * @param stateKey the state key
         */
        public void copyAndReleaseVirtualMap(@NonNull final String stateKey) {
            final var md = stateMetadata.get(stateKey);
            final VirtualMap<?, ?> virtualMap = (VirtualMap<?, ?>) findNode(md);
            final var mutableCopy = virtualMap.copy();
            if (metrics != null) {
                mutableCopy.registerMetrics(metrics);
            }
            setChild(findNodeIndex(serviceName, stateKey), mutableCopy);
            kvInstances.put(stateKey, createReadableKVState(md, mutableCopy));
        }

        @NonNull
        @Override
        public <K, V> WritableKVState<K, V> get(@NonNull String stateKey) {
            return (WritableKVState<K, V>) super.get(stateKey);
        }

        @NonNull
        @Override
        public <T> WritableSingletonState<T> getSingleton(@NonNull String stateKey) {
            return (WritableSingletonState<T>) super.getSingleton(stateKey);
        }

        @NonNull
        @Override
        public <E> WritableQueueState<E> getQueue(@NonNull String stateKey) {
            return (WritableQueueState<E>) super.getQueue(stateKey);
        }

        @Override
        @NonNull
        protected WritableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final VirtualMap v) {
            return new OnDiskWritableKVState<>(
                    extractStateKey(md),
                    md.onDiskKeyClassId(),
                    md.stateDefinition().keyCodec(),
                    md.onDiskValueClassId(),
                    md.stateDefinition().valueCodec(),
                    v);
        }

        @Override
        @NonNull
        protected WritableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final MerkleMap m) {
            return new InMemoryWritableKVState<>(
                    extractStateKey(md),
                    md.inMemoryValueClassId(),
                    md.stateDefinition().keyCodec(),
                    md.stateDefinition().valueCodec(),
                    m);
        }

        @Override
        @NonNull
        protected WritableSingletonState<?> createReadableSingletonState(
                @NonNull final StateMetadata md, @NonNull final SingletonNode<?> s) {
            return new WritableSingletonStateImpl<>(extractStateKey(md), s);
        }

        @NonNull
        @Override
        protected WritableQueueState<?> createReadableQueueState(
                @NonNull final StateMetadata md, @NonNull final QueueNode<?> q) {
            return new WritableQueueStateImpl<>(extractStateKey(md), q);
        }

        @Override
        public void commit() {
            for (final ReadableKVState kv : kvInstances.values()) {
                ((WritableKVStateBase) kv).commit();
            }
            for (final ReadableSingletonState s : singletonInstances.values()) {
                ((WritableSingletonStateBase) s).commit();
            }
            for (final ReadableQueueState q : queueInstances.values()) {
                ((WritableQueueStateBase) q).commit();
            }
            readableStatesMap.remove(serviceName);
        }

        /**
         * This method is called when a state is removed from the state merkle tree. It is used to
         * remove the cached instances of the state.
         *
         * @param stateKey the state key
         */
        public void remove(String stateKey) {
            stateMetadata.remove(stateKey);
            kvInstances.remove(stateKey);
            singletonInstances.remove(stateKey);
            queueInstances.remove(stateKey);
        }
    }

    @NonNull
    private static String extractStateKey(@NonNull final StateMetadata<?, ?> md) {
        return md.stateDefinition().stateKey();
    }

    // FUTURE USE: the following code will become relevant with
    // https://github.com/hashgraph/hedera-services/issues/11773
    @NonNull
    @Override
    public SwirldState getSwirldState() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformState getPlatformState() {
        throw new UnsupportedOperationException(
                "To be implemented with https://github.com/hashgraph/hedera-services/issues/11773");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPlatformState(@NonNull final PlatformState platformState) {
        throw new UnsupportedOperationException(
                "To be implemented with https://github.com/hashgraph/hedera-services/issues/11773");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getInfoString(final int hashDepth) {
        return State.createInfoString(hashDepth, getPlatformState(), getHash(), this);
    }
}
