package com.hedera.services.context;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.services.ServicesState;
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.config.EntityNumbers;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.domain.trackers.ConsensusStatusCounts;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.SemanticVersions;
import com.hedera.services.fees.AwareHbarCentExchange;
import com.hedera.services.fees.StandardExemptions;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.contract.queries.ContractCallLocalResourceUsage;
import com.hedera.services.fees.calculation.contract.queries.GetBytecodeResourceUsage;
import com.hedera.services.fees.calculation.contract.queries.GetContractInfoResourceUsage;
import com.hedera.services.fees.calculation.contract.queries.GetContractRecordsResourceUsage;
import com.hedera.services.fees.calculation.schedule.queries.GetScheduleInfoResourceUsage;
import com.hedera.services.fees.calculation.schedule.txns.ScheduleCreateResourceUsage;
import com.hedera.services.fees.calculation.schedule.txns.ScheduleDeleteResourceUsage;
import com.hedera.services.fees.calculation.schedule.txns.ScheduleSignResourceUsage;
import com.hedera.services.fees.calculation.token.queries.GetTokenInfoResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenAssociateResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenBurnResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenCreateResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenDeleteResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenDissociateResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenFreezeResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenGrantKycResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenMintResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenRevokeKycResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenUnfreezeResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenUpdateResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenWipeResourceUsage;
import com.hedera.services.files.EntityExpiryMapFactory;
import com.hedera.services.grpc.controllers.ContractController;
import com.hedera.services.grpc.controllers.FreezeController;
import com.hedera.services.queries.contract.ContractCallLocalAnswer;
import com.hedera.services.queries.contract.GetBySolidityIdAnswer;
import com.hedera.services.grpc.controllers.ScheduleController;
import com.hedera.services.queries.contract.GetContractRecordsAnswer;
import com.hedera.services.queries.schedule.GetScheduleInfoAnswer;
import com.hedera.services.queries.schedule.ScheduleAnswers;
import com.hedera.services.schedules.HederaScheduleStore;
import com.hedera.services.schedules.ScheduleStore;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.grpc.controllers.TokenController;
import com.hedera.services.keys.LegacyEd25519KeyReader;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.ledger.accounts.BackingTokenRels;
import com.hedera.services.ledger.accounts.PureFCMapBackingAccounts;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.queries.answering.ZeroStakeAnswerFlow;
import com.hedera.services.queries.contract.ContractAnswers;
import com.hedera.services.queries.contract.GetBytecodeAnswer;
import com.hedera.services.queries.contract.GetContractInfoAnswer;
import com.hedera.services.queries.token.GetTokenInfoAnswer;
import com.hedera.services.queries.token.TokenAnswers;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.exports.SignedStateBalancesExporter;
import com.hedera.services.state.initialization.BackedSystemAccountsCreator;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hedera.services.contracts.execution.SolidityLifecycle;
import com.hedera.services.contracts.execution.SoliditySigsVerifier;
import com.hedera.services.contracts.execution.TxnAwareSoliditySigsVerifier;
import com.hedera.services.contracts.persistence.BlobStoragePersistence;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.AwareFcfsUsagePrices;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.services.fees.calculation.consensus.queries.GetTopicInfoResourceUsage;
import com.hedera.services.fees.calculation.consensus.txns.CreateTopicResourceUsage;
import com.hedera.services.fees.calculation.consensus.txns.DeleteTopicResourceUsage;
import com.hedera.services.fees.calculation.consensus.txns.SubmitMessageResourceUsage;
import com.hedera.services.fees.calculation.consensus.txns.UpdateTopicResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractCallResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractCreateResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractDeleteResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractUpdateResourceUsage;
import com.hedera.services.fees.calculation.crypto.queries.GetAccountInfoResourceUsage;
import com.hedera.services.fees.calculation.crypto.queries.GetAccountRecordsResourceUsage;
import com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoCreateResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoDeleteResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoTransferResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoUpdateResourceUsage;
import com.hedera.services.fees.calculation.file.queries.GetFileContentsResourceUsage;
import com.hedera.services.fees.calculation.file.queries.GetFileInfoResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileAppendResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileCreateResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileDeleteResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileUpdateResourceUsage;
import com.hedera.services.fees.calculation.file.txns.SystemDeleteFileResourceUsage;
import com.hedera.services.fees.calculation.file.txns.SystemUndeleteFileResourceUsage;
import com.hedera.services.fees.calculation.meta.queries.GetVersionInfoResourceUsage;
import com.hedera.services.fees.calculation.system.txns.FreezeResourceUsage;
import com.hedera.services.fees.charging.ItemizableFeeCharging;
import com.hedera.services.fees.charging.TxnFeeChargingPolicy;
import com.hedera.services.files.DataMapFactory;
import com.hedera.services.files.FileUpdateInterceptor;
import com.hedera.services.files.HederaFs;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.files.interceptors.ConfigListUtils;
import com.hedera.services.files.interceptors.FeeSchedulesManager;
import com.hedera.services.files.interceptors.TxnAwareRatesManager;
import com.hedera.services.files.interceptors.ValidatingCallbackInterceptor;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.grpc.GrpcServerManager;
import com.hedera.services.grpc.NettyGrpcServerManager;
import com.hedera.services.grpc.controllers.ConsensusController;
import com.hedera.services.grpc.controllers.CryptoController;
import com.hedera.services.grpc.controllers.FileController;
import com.hedera.services.grpc.controllers.NetworkController;
import com.hedera.services.keys.StandardSyncActivationCheck;
import com.hedera.services.ledger.accounts.FCMapBackingAccounts;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.ids.SeqNoEntityIdSource;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.queries.answering.StakedAnswerFlow;
import com.hedera.services.queries.consensus.GetTopicInfoAnswer;
import com.hedera.services.queries.consensus.HcsAnswers;
import com.hedera.services.queries.file.FileAnswers;
import com.hedera.services.queries.file.GetFileContentsAnswer;
import com.hedera.services.queries.file.GetFileInfoAnswer;
import com.hedera.services.queries.meta.GetVersionInfoAnswer;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.state.initialization.HfsSystemFilesManager;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.validation.BasedLedgerValidator;
import com.hedera.services.stats.CounterFactory;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.stats.HapiOpSpeedometers;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.stats.RunningAvgFactory;
import com.hedera.services.stats.ServicesStatsManager;
import com.hedera.services.stats.SpeedometerFactory;
import com.hedera.services.throttling.BucketThrottling;
import com.hedera.services.throttling.ThrottlingPropsBuilder;
import com.hedera.services.throttling.TransactionThrottling;

import static com.hedera.services.context.ServicesNodeType.STAKED_NODE;
import static com.hedera.services.context.ServicesNodeType.ZERO_STAKE_NODE;
import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;
import static com.hedera.services.ledger.accounts.BackingTokenRels.RELATIONSHIP_COMPARATOR;
import static com.hedera.services.security.ops.SystemOpAuthorization.AUTHORIZED;
import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.backedLookupsFor;
import static com.hedera.services.sigs.metadata.SigMetadataLookup.REF_LOOKUP_FACTORY;
import static com.hedera.services.state.expiry.NoopExpiringCreations.NOOP_EXPIRING_CREATIONS;
import static com.hedera.services.throttling.bucket.BucketConfig.bucketsIn;
import static com.hedera.services.throttling.bucket.BucketConfig.namedIn;

import com.hedera.services.tokens.HederaTokenStore;
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.txns.SubmissionFlow;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.txns.consensus.SubmitMessageTransitionLogic;
import com.hedera.services.txns.consensus.TopicCreateTransitionLogic;
import com.hedera.services.txns.consensus.TopicDeleteTransitionLogic;
import com.hedera.services.txns.consensus.TopicUpdateTransitionLogic;
import com.hedera.services.txns.contract.ContractCallTransitionLogic;
import com.hedera.services.txns.contract.ContractCreateTransitionLogic;
import com.hedera.services.txns.contract.ContractDeleteTransitionLogic;
import com.hedera.services.txns.contract.ContractSysDelTransitionLogic;
import com.hedera.services.txns.contract.ContractSysUndelTransitionLogic;
import com.hedera.services.txns.contract.ContractUpdateTransitionLogic;
import com.hedera.services.txns.crypto.CryptoCreateTransitionLogic;
import com.hedera.services.txns.crypto.CryptoDeleteTransitionLogic;
import com.hedera.services.txns.crypto.CryptoTransferTransitionLogic;
import com.hedera.services.txns.crypto.CryptoUpdateTransitionLogic;
import com.hedera.services.txns.file.FileAppendTransitionLogic;
import com.hedera.services.txns.file.FileCreateTransitionLogic;
import com.hedera.services.txns.file.FileDeleteTransitionLogic;
import com.hedera.services.txns.file.FileSysDelTransitionLogic;
import com.hedera.services.txns.file.FileSysUndelTransitionLogic;
import com.hedera.services.txns.file.FileUpdateTransitionLogic;
import com.hedera.services.txns.network.FreezeTransitionLogic;
import com.hedera.services.txns.network.UncheckedSubmitTransitionLogic;
import com.hedera.services.txns.schedule.ScheduleCreateTransitionLogic;
import com.hedera.services.txns.schedule.ScheduleDeleteTransitionLogic;
import com.hedera.services.txns.schedule.ScheduleSignTransitionLogic;
import com.hedera.services.txns.submission.PlatformSubmissionManager;
import com.hedera.services.txns.submission.TxnHandlerSubmissionFlow;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hedera.services.txns.token.TokenAssociateTransitionLogic;
import com.hedera.services.txns.token.TokenBurnTransitionLogic;
import com.hedera.services.txns.token.TokenCreateTransitionLogic;
import com.hedera.services.txns.token.TokenDeleteTransitionLogic;
import com.hedera.services.txns.token.TokenDissociateTransitionLogic;
import com.hedera.services.txns.token.TokenFreezeTransitionLogic;
import com.hedera.services.txns.token.TokenGrantKycTransitionLogic;
import com.hedera.services.txns.token.TokenMintTransitionLogic;
import com.hedera.services.txns.token.TokenRevokeKycTransitionLogic;
import com.hedera.services.txns.token.TokenUnfreezeTransitionLogic;
import com.hedera.services.txns.token.TokenUpdateTransitionLogic;
import com.hedera.services.txns.token.TokenWipeTransitionLogic;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.BasicPrecheck;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.queries.AnswerFlow;
import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.crypto.CryptoAnswers;
import com.hedera.services.queries.crypto.GetAccountBalanceAnswer;
import com.hedera.services.queries.crypto.GetAccountInfoAnswer;
import com.hedera.services.queries.crypto.GetAccountRecordsAnswer;
import com.hedera.services.queries.crypto.GetLiveHashAnswer;
import com.hedera.services.queries.crypto.GetStakersAnswer;
import com.hedera.services.queries.meta.GetFastTxnRecordAnswer;
import com.hedera.services.queries.meta.GetTxnReceiptAnswer;
import com.hedera.services.queries.meta.GetTxnRecordAnswer;
import com.hedera.services.queries.meta.MetaAnswers;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.records.TxnAwareRecordsHistorian;
import com.hedera.services.records.RecordCache;
import com.hedera.services.records.RecordCacheFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.sourcing.DefaultSigBytesProvider;
import com.hedera.services.sigs.verification.PrecheckKeyReqs;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.exports.BalancesExporter;
import com.hedera.services.state.initialization.SystemAccountsCreator;
import com.hedera.services.state.validation.LedgerValidator;
import com.hedera.services.state.exports.AccountsExporter;

import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.bytecodeMapFrom;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.storageMapFrom;
import static com.hedera.services.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;
import static com.hedera.services.records.NoopRecordsHistorian.NOOP_RECORDS_HISTORIAN;
import static com.hedera.services.tokens.ExceptionalTokenStore.NOOP_TOKEN_STORE;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromString;
import static com.hedera.services.utils.MiscUtils.lookupInCustomStore;

import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.Pause;
import com.hederahashgraph.api.proto.java.HederaFunctionality;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;

import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.legacy.handler.FreezeHandler;
import com.hedera.services.legacy.handler.SmartContractRequestHandler;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.legacy.netty.NettyServerManager;
import com.hedera.services.contracts.sources.LedgerAccountsSource;
import com.hedera.services.contracts.sources.BlobStorageSource;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.state.migration.StdStateMigrations;
import com.hedera.services.legacy.services.state.AwareProcessLogic;
import com.hedera.services.state.migration.StateMigrations;
import com.hedera.services.utils.SleepingPause;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.legacy.services.utils.DefaultAccountsExporter;
import com.hedera.services.legacy.stream.RecordStream;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.Console;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.fcmap.FCMap;
import org.ethereum.core.AccountState;
import org.ethereum.datasource.Source;
import org.ethereum.datasource.StoragePersistence;
import org.ethereum.db.ServicesRepositoryRoot;
import com.hedera.services.context.properties.PropertySource;

import java.io.PrintStream;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.files.interceptors.ConfigListUtils.uncheckedParse;
import static com.hedera.services.files.interceptors.PureRatesValidation.isNormalIntradayChange;
import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.defaultAccountRetryingLookupsFor;
import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.defaultLookupsFor;
import static com.hedera.services.sigs.utils.PrecheckUtils.queryPaymentTestFor;
import static com.hedera.services.legacy.config.PropertiesLoader.populateAPIPropertiesWithProto;
import static com.hedera.services.legacy.config.PropertiesLoader.populateApplicationPropertiesWithProto;
import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;

/**
 * Provide a trivial implementation of the inversion-of-control pattern,
 * isolating secondary responsibilities of dependency creation and
 * injection in a single component.
 *
 * @author Michael Tinker
 */
public class ServicesContext {
	/* Injected dependencies. */
	ServicesState state;

	private final NodeId id;
	private final Platform platform;
	private final PropertySources propertySources;

	/* Context-sensitive singletons. */
	private Thread recordStreamThread;
	private Address address;
	private Console console;
	private HederaFs hfs;
	private StateView currentView;
	private AccountID accountId;
	private AnswerFlow answerFlow;
	private HcsAnswers hcsAnswers;
	private FileNumbers fileNums;
	private FileAnswers fileAnswers;
	private MetaAnswers metaAnswers;
	private RecordCache recordCache;
	private TokenStore tokenStore;
	private ScheduleStore scheduleStore;
	private TokenAnswers tokenAnswers;
	private ScheduleAnswers scheduleAnswers;
	private HederaLedger ledger;
	private SyncVerifier syncVerifier;
	private IssEventInfo issEventInfo;
	private ProcessLogic logic;
	private RecordStream recordStream;
	private QueryFeeCheck queryFeeCheck;
	private HederaNumbers hederaNums;
	private ExpiryManager expiries;
	private FeeCalculator fees;
	private FeeExemptions exemptions;
	private EntityNumbers entityNums;
	private FreezeHandler freeze;
	private CryptoAnswers cryptoAnswers;
	private AccountNumbers accountNums;
	private SubmissionFlow submissionFlow;
	private PropertySource properties;
	private EntityIdSource ids;
	private FileController fileGrpc;
	private HapiOpCounters opCounters;
	private AnswerFunctions answerFunctions;
	private ContractAnswers contractAnswers;
	private OptionValidator validator;
	private LedgerValidator ledgerValidator;
	private TokenController tokenGrpc;
	private ScheduleController scheduleGrpc;
	private MiscRunningAvgs runningAvgs;
	private MiscSpeedometers speedometers;
	private ServicesNodeType nodeType;
	private SystemOpPolicies systemOpPolicies;
	private CryptoController cryptoGrpc;
	private BucketThrottling bucketThrottling;
	private HbarCentExchange exchange;
	private SemanticVersions semVers;
	private PrecheckVerifier precheckVerifier;
	private BackingTokenRels backingTokenRels;
	private FreezeController freezeGrpc;
	private BalancesExporter balancesExporter;
	private SolidityLifecycle solidityLifecycle;
	private ExpiringCreations creator;
	private NetworkController networkGrpc;
	private GrpcServerManager grpc;
	private TxnResponseHelper txnResponseHelper;
	private TransactionContext txnCtx;
	private BlobStorageSource bytecodeDb;
	private TransactionHandler txns;
	private ContractController contractsGrpc;
	private HederaSigningOrder keyOrder;
	private HederaSigningOrder backedKeyOrder;
	private HederaSigningOrder lookupRetryingKeyOrder;
	private StoragePersistence storagePersistence;
	private ConsensusController consensusGrpc;
	private QueryResponseHelper queryResponseHelper;
	private UsagePricesProvider usagePrices;
	private Supplier<StateView> stateViews;
	private FeeSchedulesManager feeSchedulesManager;
	private Map<String, byte[]> blobStore;
	private Map<EntityId, Long> entityExpiries;
	private NodeLocalProperties nodeLocalProperties;
	private TxnFeeChargingPolicy txnChargingPolicy;
	private TxnAwareRatesManager exchangeRatesManager;
	private ServicesStatsManager statsManager;
	private LedgerAccountsSource accountSource;
	private FCMapBackingAccounts backingAccounts;
	private TransitionLogicLookup transitionLogic;
	private TransactionThrottling txnThrottling;
	private ConsensusStatusCounts statusCounts;
	private HfsSystemFilesManager systemFilesManager;
	private CurrentPlatformStatus platformStatus;
	private SystemAccountsCreator systemAccountsCreator;
	private ItemizableFeeCharging itemizableFeeCharging;
	private ServicesRepositoryRoot repository;
	private AccountRecordsHistorian recordsHistorian;
	private GlobalDynamicProperties globalDynamicProperties;
	private PlatformSubmissionManager submissionManager;
	private SmartContractRequestHandler contracts;
	private TxnAwareSoliditySigsVerifier soliditySigsVerifier;
	private ValidatingCallbackInterceptor apiPermissionsReloading;
	private ValidatingCallbackInterceptor applicationPropertiesReloading;
	private Supplier<ServicesRepositoryRoot> newPureRepo;
	private Map<TransactionID, TxnIdRecentHistory> txnHistories;
	private AtomicReference<FCMap<MerkleEntityId, MerkleTopic>> queryableTopics;
	private AtomicReference<FCMap<MerkleEntityId, MerkleToken>> queryableTokens;
	private AtomicReference<FCMap<MerkleEntityId, MerkleAccount>> queryableAccounts;
	private AtomicReference<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> queryableStorage;
	private AtomicReference<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> queryableTokenAssociations;

	/* Context-free infrastructure. */
	private static Pause pause;
	private static StateMigrations stateMigrations;
	private static AccountsExporter accountsExporter;
	private static LegacyEd25519KeyReader b64KeyReader;

	static {
		pause = SleepingPause.SLEEPING_PAUSE;
		b64KeyReader = new LegacyEd25519KeyReader();
		stateMigrations = new StdStateMigrations(SleepingPause.SLEEPING_PAUSE);
		accountsExporter = new DefaultAccountsExporter();
	}

	public ServicesContext(
			NodeId id,
			Platform platform,
			ServicesState state,
			PropertySources propertySources
	) {
		this.id = id;
		this.platform = platform;
		this.state = state;
		this.propertySources = propertySources;
	}

	public void update(ServicesState state) {
		this.state = state;

		queryableAccounts().set(accounts());
		queryableTopics().set(topics());
		queryableStorage().set(storage());
		queryableTokens().set(tokens());
		queryableTokenAssociations().set(tokenAssociations());
	}

	public void rebuildBackingStoresIfPresent() {
		if (backingTokenRels != null) {
			backingTokenRels.rebuildFromSources();
		}
		if (backingAccounts != null) {
			backingAccounts.rebuildFromSources();
		}
	}

	public HapiOpCounters opCounters() {
		if (opCounters == null) {
			opCounters = new HapiOpCounters(new CounterFactory() {}, runningAvgs(), txnCtx(), MiscUtils::baseStatNameOf);
		}
		return opCounters;
	}

	public MiscRunningAvgs runningAvgs() {
		if (runningAvgs == null) {
			runningAvgs = new MiscRunningAvgs(new RunningAvgFactory() {}, nodeLocalProperties());
		}
		return runningAvgs;
	}

	public MiscSpeedometers speedometers() {
		if (speedometers == null) {
			speedometers = new MiscSpeedometers(new SpeedometerFactory() {}, nodeLocalProperties());
		}
		return speedometers;
	}

	public SemanticVersions semVers() {
		if (semVers == null) {
			semVers = new SemanticVersions();
		}
		return semVers;
	}

	public ServicesStatsManager statsManager() {
		if (statsManager == null) {
			var opSpeedometers = new HapiOpSpeedometers(
					opCounters(),
					new SpeedometerFactory() {},
					nodeLocalProperties(),
					MiscUtils::baseStatNameOf);
			statsManager = new ServicesStatsManager(
					opCounters(),
					runningAvgs(),
					speedometers(),
					opSpeedometers,
					nodeLocalProperties());
		}
		return statsManager;
	}

	public CurrentPlatformStatus platformStatus() {
		if (platformStatus == null) {
			platformStatus = new ContextPlatformStatus();
		}
		return platformStatus;
	}

	public LedgerValidator ledgerValidator() {
		if (ledgerValidator == null) {
			ledgerValidator = new BasedLedgerValidator(hederaNums(), properties(), globalDynamicProperties());
		}
		return ledgerValidator;
	}

	public IssEventInfo issEventInfo() {
		if (issEventInfo == null) {
			issEventInfo = new IssEventInfo(properties());
		}
		return issEventInfo;
	}

	public Map<String, byte[]> blobStore() {
		if (blobStore == null) {
			blobStore = new FcBlobsBytesStore(MerkleOptionalBlob::new, this::storage);
		}
		return blobStore;
	}

	public Supplier<StateView> stateViews() {
		if (stateViews == null) {
			stateViews = () -> new StateView(
					tokenStore(),
					() -> queryableTopics().get(),
					() -> queryableAccounts().get(),
					() -> queryableStorage().get(),
					() -> queryableTokenAssociations().get(),
					this::diskFs,
					properties());
		}
		return stateViews;
	}

	public StateView currentView() {
		if (currentView == null) {
			currentView = new StateView(
					tokenStore(),
					this::topics,
					this::accounts,
					this::storage,
					this::tokenAssociations,
					this::diskFs,
					properties());
		}
		return currentView;
	}

	public HederaNumbers hederaNums() {
		if (hederaNums == null) {
			hederaNums = new HederaNumbers(properties());
		}
		return hederaNums;
	}

	public FileNumbers fileNums() {
		if (fileNums == null) {
			fileNums = new FileNumbers(hederaNums(), properties());
		}
		return fileNums;
	}

	public AccountNumbers accountNums() {
		if (accountNums == null) {
			accountNums = new AccountNumbers(properties());
		}
		return accountNums;
	}

	public TxnResponseHelper txnResponseHelper() {
		if (txnResponseHelper == null) {
			txnResponseHelper = new TxnResponseHelper(submissionFlow(), opCounters());
		}
		return txnResponseHelper;
	}

	public TransactionThrottling txnThrottling() {
		if (txnThrottling == null) {
			txnThrottling = new TransactionThrottling(bucketThrottling());
		}
		return txnThrottling;
	}

	public BucketThrottling bucketThrottling() {
		if (bucketThrottling == null) {
			bucketThrottling = new BucketThrottling(
					this::addressBook,
					properties(),
					props -> bucketsIn(props).stream().collect(toMap(Function.identity(), b -> namedIn(props, b))),
					ThrottlingPropsBuilder::withPrioritySource);
		}
		return bucketThrottling;
	}

	public ItemizableFeeCharging charging() {
		if (itemizableFeeCharging == null) {
			itemizableFeeCharging = new ItemizableFeeCharging(
					ledger(),
					exemptions(),
					globalDynamicProperties());
		}
		return itemizableFeeCharging;
	}

	public SubmissionFlow submissionFlow() {
		if (submissionFlow == null) {
			submissionFlow = new TxnHandlerSubmissionFlow(
					nodeType(),
					txns(),
					transitionLogic(),
					submissionManager());
		}
		return submissionFlow;
	}

	public QueryResponseHelper queryResponseHelper() {
		if (queryResponseHelper == null) {
			queryResponseHelper = new QueryResponseHelper(answerFlow(), opCounters());
		}
		return queryResponseHelper;
	}

	public FileAnswers fileAnswers() {
		if (fileAnswers == null) {
			fileAnswers = new FileAnswers(
					new GetFileInfoAnswer(validator()),
					new GetFileContentsAnswer(validator())
			);
		}
		return fileAnswers;
	}

	public ContractAnswers contractAnswers() {
		if (contractAnswers == null) {
			contractAnswers = new ContractAnswers(
					new GetBytecodeAnswer(validator()),
					new GetContractInfoAnswer(validator()),
					new GetBySolidityIdAnswer(),
					new GetContractRecordsAnswer(validator()),
					new ContractCallLocalAnswer(contracts()::contractCallLocal, validator())
			);
		}
		return contractAnswers;
	}

	public HcsAnswers hcsAnswers() {
		if (hcsAnswers == null) {
			hcsAnswers = new HcsAnswers(
					new GetTopicInfoAnswer(validator())
			);
		}
		return hcsAnswers;
	}

	public MetaAnswers metaAnswers() {
		if (metaAnswers == null) {
			metaAnswers = new MetaAnswers(
					new GetTxnRecordAnswer(recordCache(), validator(), answerFunctions()),
					new GetTxnReceiptAnswer(recordCache()),
					new GetVersionInfoAnswer(semVers()),
					new GetFastTxnRecordAnswer()
			);
		}
		return metaAnswers;
	}

	public EntityNumbers entityNums() {
		if (entityNums == null) {
			entityNums = new EntityNumbers(fileNums(), hederaNums(), accountNums());
		}
		return entityNums;
	}

	public TokenAnswers tokenAnswers() {
		if (tokenAnswers == null) {
			tokenAnswers = new TokenAnswers(
					new GetTokenInfoAnswer()
			);
		}
		return tokenAnswers;
	}

	public ScheduleAnswers scheduleAnswers() {
		if (scheduleAnswers == null) {
			scheduleAnswers = new ScheduleAnswers(
					new GetScheduleInfoAnswer()
			);
		}
		return scheduleAnswers;
	}

	public CryptoAnswers cryptoAnswers() {
		if (cryptoAnswers == null) {
			cryptoAnswers = new CryptoAnswers(
					new GetLiveHashAnswer(),
					new GetStakersAnswer(),
					new GetAccountInfoAnswer(validator()),
					new GetAccountBalanceAnswer(validator()),
					new GetAccountRecordsAnswer(answerFunctions(), validator())
			);
		}
		return cryptoAnswers;
	}

	public AnswerFunctions answerFunctions() {
		if (answerFunctions == null) {
			answerFunctions = new AnswerFunctions();
		}
		return answerFunctions;
	}

	public QueryFeeCheck queryFeeCheck() {
		if (queryFeeCheck == null) {
			queryFeeCheck = new QueryFeeCheck(this::accounts);
		}
		return queryFeeCheck;
	}

	public FeeCalculator fees() {
		if (fees == null) {
			FileFeeBuilder fileFees = new FileFeeBuilder();
			CryptoFeeBuilder cryptoFees = new CryptoFeeBuilder();
			SmartContractFeeBuilder contractFees = new SmartContractFeeBuilder();

			fees = new UsageBasedFeeCalculator(
					exchange(),
					usagePrices(),
					List.of(
							/* Meta */
							new GetVersionInfoResourceUsage(),
							new GetTxnRecordResourceUsage(recordCache(), answerFunctions(), cryptoFees),
							/* Crypto */
							new GetAccountInfoResourceUsage(),
							new GetAccountRecordsResourceUsage(answerFunctions(), cryptoFees),
							/* File */
							new GetFileInfoResourceUsage(fileFees),
							new GetFileContentsResourceUsage(fileFees),
							/* Consensus */
							new GetTopicInfoResourceUsage(),
							/* Smart Contract */
							new GetBytecodeResourceUsage(contractFees),
							new GetContractInfoResourceUsage(),
							new GetContractRecordsResourceUsage(contractFees),
							new ContractCallLocalResourceUsage(
									contracts()::contractCallLocal, contractFees, globalDynamicProperties()),
							/* Token */
							new GetTokenInfoResourceUsage(),
							/* Schedule */
							new GetScheduleInfoResourceUsage()
					),
					txnUsageEstimators(fileFees, cryptoFees, contractFees)
			);
		}
		return fees;
	}

	private Function<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators(
			FileFeeBuilder fileFees,
			CryptoFeeBuilder cryptoFees,
			SmartContractFeeBuilder contractFees
	) {
		Map<HederaFunctionality, List<TxnResourceUsageEstimator>> estimatorsMap = Map.ofEntries(
				/* Crypto */
				entry(CryptoCreate, List.of(new CryptoCreateResourceUsage(cryptoFees))),
				entry(CryptoDelete, List.of(new CryptoDeleteResourceUsage(cryptoFees))),
				entry(CryptoUpdate, List.of(new CryptoUpdateResourceUsage(cryptoFees))),
				entry(CryptoTransfer, List.of(new CryptoTransferResourceUsage(globalDynamicProperties()))),
				/* Contract */
				entry(ContractCall, List.of(new ContractCallResourceUsage(contractFees))),
				entry(ContractCreate, List.of(new ContractCreateResourceUsage(contractFees))),
				entry(ContractDelete, List.of(new ContractDeleteResourceUsage(contractFees))),
				entry(ContractUpdate, List.of(new ContractUpdateResourceUsage(contractFees))),
				/* File */
				entry(FileCreate, List.of(new FileCreateResourceUsage(fileFees))),
				entry(FileDelete, List.of(new FileDeleteResourceUsage(fileFees))),
				entry(FileUpdate, List.of(new FileUpdateResourceUsage())),
				entry(FileAppend, List.of(new FileAppendResourceUsage(fileFees))),
				/* Consensus */
				entry(ConsensusCreateTopic, List.of(new CreateTopicResourceUsage())),
				entry(ConsensusUpdateTopic, List.of(new UpdateTopicResourceUsage())),
				entry(ConsensusDeleteTopic, List.of(new DeleteTopicResourceUsage())),
				entry(ConsensusSubmitMessage, List.of(new SubmitMessageResourceUsage())),
				/* Token */
				entry(TokenCreate, List.of(new TokenCreateResourceUsage())),
				entry(TokenUpdate, List.of(new TokenUpdateResourceUsage())),
				entry(TokenFreezeAccount, List.of(new TokenFreezeResourceUsage())),
				entry(TokenUnfreezeAccount, List.of(new TokenUnfreezeResourceUsage())),
				entry(TokenGrantKycToAccount, List.of(new TokenGrantKycResourceUsage())),
				entry(TokenRevokeKycFromAccount, List.of(new TokenRevokeKycResourceUsage())),
				entry(TokenDelete, List.of(new TokenDeleteResourceUsage())),
				entry(TokenMint, List.of(new TokenMintResourceUsage())),
				entry(TokenBurn, List.of(new TokenBurnResourceUsage())),
				entry(TokenAccountWipe, List.of(new TokenWipeResourceUsage())),
				entry(TokenAssociateToAccount, List.of(new TokenAssociateResourceUsage())),
				entry(TokenDissociateFromAccount, List.of(new TokenDissociateResourceUsage())),
				/* Schedule */
				entry(ScheduleCreate, List.of(new ScheduleCreateResourceUsage())),
				entry(ScheduleDelete, List.of(new ScheduleDeleteResourceUsage())),
				entry(ScheduleSign, List.of(new ScheduleSignResourceUsage())),
				/* System */
				entry(Freeze, List.of(new FreezeResourceUsage())),
				entry(SystemDelete, List.of(new SystemDeleteFileResourceUsage(fileFees))),
				entry(SystemUndelete, List.of(new SystemUndeleteFileResourceUsage(fileFees)))
		);
		return estimatorsMap::get;
	}

	public AnswerFlow answerFlow() {
		if (answerFlow == null) {
			if (nodeType() == STAKED_NODE) {
				answerFlow = new StakedAnswerFlow(
						fees(),
						txns(),
						stateViews(),
						usagePrices(),
						bucketThrottling(),
						submissionManager());
			} else {
				answerFlow = new ZeroStakeAnswerFlow(txns(), stateViews(), bucketThrottling());
			}
		}
		return answerFlow;
	}

	public HederaSigningOrder keyOrder() {
		if (keyOrder == null) {
			var lookups = defaultLookupsFor(hfs(), this::accounts, this::topics,
					REF_LOOKUP_FACTORY.apply(tokenStore()));
			keyOrder = keyOrderWith(lookups);
		}
		return keyOrder;
	}

	public HederaSigningOrder backedKeyOrder() {
		if (backedKeyOrder == null) {
			var lookups = backedLookupsFor(
					hfs(),
					backingAccounts(),
					this::topics,
					this::accounts,
					REF_LOOKUP_FACTORY.apply(tokenStore()));
			backedKeyOrder = keyOrderWith(lookups);
		}
		return backedKeyOrder;
	}

	public HederaSigningOrder lookupRetryingKeyOrder() {
		if (lookupRetryingKeyOrder == null) {
			var lookups = defaultAccountRetryingLookupsFor(
					hfs(),
					nodeLocalProperties(),
					this::accounts,
					this::topics,
					REF_LOOKUP_FACTORY.apply(tokenStore()),
					runningAvgs(),
					speedometers());
			lookupRetryingKeyOrder = keyOrderWith(lookups);
		}
		return lookupRetryingKeyOrder;
	}

	public ServicesNodeType nodeType() {
		if (nodeType == null) {
			nodeType = (address().getStake() > 0) ? STAKED_NODE : ZERO_STAKE_NODE;
		}
		return nodeType;
	}

	private HederaSigningOrder keyOrderWith(DelegatingSigMetadataLookup lookups) {
		var policies = systemOpPolicies();
		return new HederaSigningOrder(
				entityNums(),
				lookups,
				txn -> policies.check(txn, CryptoUpdate) != AUTHORIZED,
				(txn, function) -> policies.check(txn, function) != AUTHORIZED);
	}

	public StoragePersistence storagePersistence() {
		if (storagePersistence == null) {
			storagePersistence = new BlobStoragePersistence(storageMapFrom(blobStore()));
		}
		return storagePersistence;
	}

	public SyncVerifier syncVerifier() {
		if (syncVerifier == null) {
			syncVerifier = platform().getCryptography()::verifySync;
		}
		return syncVerifier;
	}

	public PrecheckVerifier precheckVerifier() {
		if (precheckVerifier == null) {
			Predicate<TransactionBody> isQueryPayment = queryPaymentTestFor(nodeAccount());
			PrecheckKeyReqs reqs = new PrecheckKeyReqs(keyOrder(), lookupRetryingKeyOrder(), isQueryPayment);
			precheckVerifier = new PrecheckVerifier(syncVerifier(), reqs, DefaultSigBytesProvider.DEFAULT_SIG_BYTES);
		}
		return precheckVerifier;
	}

	public PrintStream consoleOut() {
		return Optional.ofNullable(console()).map(c -> c.out).orElse(null);
	}

	public BalancesExporter balancesExporter() {
		if (balancesExporter == null) {
			balancesExporter = new SignedStateBalancesExporter(
					properties(),
					platform()::sign,
					globalDynamicProperties());
		}
		return balancesExporter;
	}

	public Map<EntityId, Long> entityExpiries() {
		if (entityExpiries == null) {
			entityExpiries = EntityExpiryMapFactory.entityExpiryMapFrom(blobStore());
		}
		return entityExpiries;
	}

	public HederaFs hfs() {
		if (hfs == null) {
			hfs = new TieredHederaFs(
					ids(),
					globalDynamicProperties(),
					txnCtx()::consensusTime,
					DataMapFactory.dataMapFrom(blobStore()),
					MetadataMapFactory.metaMapFrom(blobStore()),
					this::getCurrentSpecialFileSystem);
			hfs.register(feeSchedulesManager());
			hfs.register(exchangeRatesManager());
			hfs.register(apiPermissionsReloading());
			hfs.register(applicationPropertiesReloading());
		}
		return hfs;
	}

	MerkleDiskFs getCurrentSpecialFileSystem() {
		return this.state.diskFs();
	}
	
	public SoliditySigsVerifier soliditySigsVerifier() {
		if (soliditySigsVerifier == null) {
			soliditySigsVerifier = new TxnAwareSoliditySigsVerifier(
					syncVerifier(),
					txnCtx(),
					StandardSyncActivationCheck::allKeysAreActive,
					this::accounts);
		}
		return soliditySigsVerifier;
	}

	public FileUpdateInterceptor applicationPropertiesReloading() {
		if (applicationPropertiesReloading == null) {
			applicationPropertiesReloading = new ValidatingCallbackInterceptor(
					0,
					"files.networkProperties",
					properties(),
					contents -> {
						var config = uncheckedParse(contents);
						((StandardizedPropertySources) propertySources()).reloadFrom(config);
						globalDynamicProperties().reload();
						populateApplicationPropertiesWithProto(config);
					},
					ConfigListUtils::isConfigList
			);
		}
		return applicationPropertiesReloading;
	}

	public FileUpdateInterceptor apiPermissionsReloading() {
		if (apiPermissionsReloading == null) {
			apiPermissionsReloading = new ValidatingCallbackInterceptor(
					0,
					"files.hapiPermissions",
					properties(),
					contents -> populateAPIPropertiesWithProto(uncheckedParse(contents)),
					ConfigListUtils::isConfigList
			);
		}
		return apiPermissionsReloading;
	}

	public TransitionLogicLookup transitionLogic() {
		if (transitionLogic == null) {
			transitionLogic = new TransitionLogicLookup(transitions());
		}
		return transitionLogic;
	}

	private Function<HederaFunctionality, List<TransitionLogic>> transitions() {
		Map<HederaFunctionality, List<TransitionLogic>> transitionsMap = Map.ofEntries(
				/* Crypto */
				entry(CryptoCreate,
						List.of(new CryptoCreateTransitionLogic(ledger(), validator(), txnCtx()))),
				entry(CryptoUpdate,
						List.of(new CryptoUpdateTransitionLogic(ledger(), validator(), txnCtx()))),
				entry(CryptoDelete,
						List.of(new CryptoDeleteTransitionLogic(ledger(), txnCtx()))),
				entry(CryptoTransfer,
						List.of(new CryptoTransferTransitionLogic(ledger(), validator(), txnCtx()))),
				/* File */
				entry(FileUpdate,
						List.of(new FileUpdateTransitionLogic(hfs(), entityNums(), validator(), txnCtx()))),
				entry(FileCreate,
						List.of(new FileCreateTransitionLogic(hfs(), validator(), txnCtx()))),
				entry(FileDelete,
						List.of(new FileDeleteTransitionLogic(hfs(), txnCtx()))),
				entry(FileAppend,
						List.of(new FileAppendTransitionLogic(hfs(), txnCtx()))),
				/* Contract */
				entry(ContractCreate,
						List.of(new ContractCreateTransitionLogic(
								hfs(), contracts()::createContract, this::seqNo, validator(), txnCtx()))),
				entry(ContractUpdate,
						List.of(new ContractUpdateTransitionLogic(
								contracts()::updateContract, validator(), txnCtx(), this::accounts))),
				entry(ContractDelete,
						List.of(new ContractDeleteTransitionLogic(
								contracts()::deleteContract, validator(), txnCtx(), this::accounts))),
				entry(ContractCall,
						List.of(new ContractCallTransitionLogic(
								contracts()::contractCall, validator(), txnCtx(), this::seqNo, this::accounts))),
				/* Consensus */
				entry(ConsensusCreateTopic,
						List.of(new TopicCreateTransitionLogic(
								this::accounts, this::topics, ids(), validator(), txnCtx()))),
				entry(ConsensusUpdateTopic,
						List.of(new TopicUpdateTransitionLogic(
								this::accounts, this::topics, validator(), txnCtx()))),
				entry(ConsensusDeleteTopic,
						List.of(new TopicDeleteTransitionLogic(
								this::topics, validator(), txnCtx()))),
				entry(ConsensusSubmitMessage,
						List.of(new SubmitMessageTransitionLogic(
								this::topics, validator(), txnCtx()))),
				/* Token */
				entry(TokenCreate,
						List.of(new TokenCreateTransitionLogic(validator(), tokenStore(), ledger(), txnCtx()))),
				entry(TokenUpdate,
						List.of(new TokenUpdateTransitionLogic(
								validator(),
								tokenStore(),
								ledger(),
								txnCtx(),
								HederaTokenStore::affectsExpiryAtMost))),
				entry(TokenFreezeAccount,
						List.of(new TokenFreezeTransitionLogic(tokenStore(), ledger(), txnCtx()))),
				entry(TokenUnfreezeAccount,
						List.of(new TokenUnfreezeTransitionLogic(tokenStore(), ledger(), txnCtx()))),
				entry(TokenGrantKycToAccount,
						List.of(new TokenGrantKycTransitionLogic(tokenStore(), ledger(), txnCtx()))),
				entry(TokenRevokeKycFromAccount,
						List.of(new TokenRevokeKycTransitionLogic(tokenStore(), ledger(), txnCtx()))),
				entry(TokenDelete,
						List.of(new TokenDeleteTransitionLogic(tokenStore(), txnCtx()))),
				entry(TokenMint,
						List.of(new TokenMintTransitionLogic(tokenStore(), txnCtx()))),
				entry(TokenBurn,
						List.of(new TokenBurnTransitionLogic(tokenStore(), txnCtx()))),
				entry(TokenAccountWipe,
						List.of(new TokenWipeTransitionLogic(tokenStore(), txnCtx()))),
				entry(TokenAssociateToAccount,
						List.of(new TokenAssociateTransitionLogic(tokenStore(), txnCtx()))),
				entry(TokenDissociateFromAccount,
						List.of(new TokenDissociateTransitionLogic(tokenStore(), txnCtx()))),
				/* Schedule */
				entry(ScheduleCreate,
						List.of(new ScheduleCreateTransitionLogic(validator(), scheduleStore(), ledger(), txnCtx()))),
				entry(ScheduleSign,
						List.of(new ScheduleSignTransitionLogic(validator(), scheduleStore(), ledger(), txnCtx()))),
				entry(ScheduleDelete,
						List.of(new ScheduleDeleteTransitionLogic(validator(), scheduleStore(), ledger(), txnCtx()))),
				/* System */
				entry(SystemDelete,
						List.of(
								new FileSysDelTransitionLogic(hfs(), entityExpiries(), txnCtx()),
								new ContractSysDelTransitionLogic(
										validator(), txnCtx(), contracts()::systemDelete, this::accounts))),
				entry(SystemUndelete,
						List.of(
								new FileSysUndelTransitionLogic(hfs(), entityExpiries(), txnCtx()),
								new ContractSysUndelTransitionLogic(
										validator(), txnCtx(), contracts()::systemUndelete, this::accounts))),
				/* Network */
				entry(Freeze,
						List.of(new FreezeTransitionLogic(fileNums(), freeze()::freeze, txnCtx()))),
				entry(UncheckedSubmit,
						List.of(new UncheckedSubmitTransitionLogic()))
		);
		return transitionsMap::get;
	}

	public EntityIdSource ids() {
		if (ids == null) {
			ids = new SeqNoEntityIdSource(this::seqNo);
		}
		return ids;
	}

	public TransactionContext txnCtx() {
		if (txnCtx == null) {
			txnCtx = new AwareTransactionContext(this);
		}
		return txnCtx;
	}

	public Map<TransactionID, TxnIdRecentHistory> txnHistories() {
		if (txnHistories == null) {
			txnHistories = new ConcurrentHashMap<>();
		}
		return txnHistories;
	}

	public RecordCache recordCache() {
		if (recordCache == null) {
			recordCache = new RecordCache(
					this,
					new RecordCacheFactory(properties()).getRecordCache(),
					txnHistories());
		}
		return recordCache;
	}

	public AccountRecordsHistorian recordsHistorian() {
		if (recordsHistorian == null) {
			recordsHistorian = new TxnAwareRecordsHistorian(
					recordCache(),
					txnCtx(),
					this::accounts,
					expiries());
		}
		return recordsHistorian;
	}

	public FeeExemptions exemptions() {
		if (exemptions == null) {
			exemptions = new StandardExemptions(accountNums(), systemOpPolicies());
		}
		return exemptions;
	}

	public HbarCentExchange exchange() {
		if (exchange == null) {
			exchange = new AwareHbarCentExchange(txnCtx());
		}
		return exchange;
	}

	public BackingStore<Map.Entry<AccountID, TokenID>, MerkleTokenRelStatus> backingTokenRels() {
		if (backingTokenRels == null) {
			backingTokenRels = new BackingTokenRels(this::tokenAssociations);
		}
		return backingTokenRels;
	}

	public BackingStore<AccountID, MerkleAccount> backingAccounts() {
		if (backingAccounts == null) {
			backingAccounts = new FCMapBackingAccounts(this::accounts);
		}
		return backingAccounts;
	}

	public NodeLocalProperties nodeLocalProperties() {
		if (nodeLocalProperties == null) {
			nodeLocalProperties = new NodeLocalProperties(properties());
		}
		return nodeLocalProperties;
	}

	public GlobalDynamicProperties globalDynamicProperties() {
		if (globalDynamicProperties == null) {
			globalDynamicProperties = new GlobalDynamicProperties(hederaNums(), properties());
		}
		return globalDynamicProperties;
	}

	public TokenStore tokenStore() {
		if (tokenStore == null) {
			TransactionalLedger<Map.Entry<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger =
					new TransactionalLedger<>(
							TokenRelProperty.class,
							MerkleTokenRelStatus::new,
							backingTokenRels(),
							new ChangeSummaryManager<>());
			tokenRelsLedger.setKeyComparator(RELATIONSHIP_COMPARATOR);
			tokenRelsLedger.setKeyToString(BackingTokenRels::readableTokenRel);
			tokenStore = new HederaTokenStore(
					ids(),
					validator(),
					globalDynamicProperties(),
					this::tokens,
					tokenRelsLedger);
		}
		return tokenStore;
	}

	public ScheduleStore scheduleStore() {
		if (scheduleStore == null) {
			scheduleStore = new HederaScheduleStore();
		}
		return scheduleStore;
	}

	public HederaLedger ledger() {
		if (ledger == null) {
			TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger =
					new TransactionalLedger<>(
							AccountProperty.class,
							MerkleAccount::new,
							backingAccounts(),
							new ChangeSummaryManager<>());
			accountsLedger.setKeyComparator(ACCOUNT_ID_COMPARATOR);
			ledger = new HederaLedger(
					tokenStore(),
					ids(),
					creator(),
					recordsHistorian(),
					accountsLedger);
		}
		return ledger;
	}

	public ExpiryManager expiries() {
		if (expiries == null) {
			var histories = txnHistories();
			expiries = new ExpiryManager(recordCache(), histories);
		}
		return expiries;
	}

	public ExpiringCreations creator() {
		if (creator == null) {
			creator = new ExpiringCreations(expiries(), globalDynamicProperties());
			creator.setRecordCache(recordCache());
		}
		return creator;
	}

	public OptionValidator validator() {
		if (validator == null) {
			validator = new ContextOptionValidator(txnCtx(), globalDynamicProperties());
		}
		return validator;
	}

	public ProcessLogic logic() {
		if (logic == null) {
			logic = new AwareProcessLogic(this);
		}
		return logic;
	}

	public FreezeHandler freeze() {
		if (freeze == null) {
			freeze = new FreezeHandler(hfs(), platform(), exchange());
		}
		return freeze;
	}

	public Thread recordStreamThread() {
		if (recordStreamThread == null) {
			recordStreamThread = new Thread(recordStream());
			recordStreamThread.setName("record_stream_" + address().getMemo());
		}
		return recordStreamThread;
	}

	public void updateFeature() {
		if (freeze != null) {
			String os = System.getProperty("os.name").toLowerCase();
			if (os.indexOf("mac") >= 0) {
				if (platform.getSelfId().getId() == 0) {
					freeze.handleUpdateFeature();
				}
			} else {
				freeze.handleUpdateFeature();
			}
		}
	}

	public RecordStream recordStream() {
		if (recordStream == null) {
			recordStream = new RecordStream(
					platform,
					runningAvgs(),
					nodeAccount(),
					properties().getStringProperty("hedera.recordStream.logDir"),
					properties());
		}
		return recordStream;
	}

	public FileUpdateInterceptor exchangeRatesManager() {
		if (exchangeRatesManager == null) {
			exchangeRatesManager = new TxnAwareRatesManager(
					fileNums(),
					accountNums(),
					globalDynamicProperties(),
					txnCtx(),
					this::midnightRates,
					exchange()::updateRates,
					limitPercent -> (base, proposed) -> isNormalIntradayChange(base, proposed, limitPercent));
		}
		return exchangeRatesManager;
	}

	public FileUpdateInterceptor feeSchedulesManager() {
		if (feeSchedulesManager == null) {
			feeSchedulesManager = new FeeSchedulesManager(fileNums(), fees());
		}
		return feeSchedulesManager;
	}

	public FreezeController freezeGrpc() {
		if (freezeGrpc == null) {
			freezeGrpc = new FreezeController(txnResponseHelper());
		}
		return freezeGrpc;
	}

	public NetworkController networkGrpc() {
		if (networkGrpc == null) {
			networkGrpc = new NetworkController(metaAnswers(), txnResponseHelper(), queryResponseHelper());
		}
		return networkGrpc;
	}

	public FileController filesGrpc() {
		if (fileGrpc == null) {
			fileGrpc = new FileController(fileAnswers(), txnResponseHelper(), queryResponseHelper());
		}
		return fileGrpc;
	}

	public SystemOpPolicies systemOpPolicies() {
		if (systemOpPolicies == null) {
			systemOpPolicies = new SystemOpPolicies(entityNums());
		}
		return systemOpPolicies;
	}

	public TokenController tokenGrpc() {
		if (tokenGrpc == null) {
			tokenGrpc = new TokenController(tokenAnswers(), txnResponseHelper(), queryResponseHelper());
		}
		return tokenGrpc;
	}

	public ScheduleController scheduleGrpc() {
		if (scheduleGrpc == null) {
			scheduleGrpc = new ScheduleController(scheduleAnswers(), txnResponseHelper(), queryResponseHelper()); // TODO: Create controller functionality
		}
		return scheduleGrpc;
	}

	public CryptoController cryptoGrpc() {
		if (cryptoGrpc == null) {
			cryptoGrpc = new CryptoController(
					metaAnswers(),
					cryptoAnswers(),
					txnResponseHelper(),
					queryResponseHelper());
		}
		return cryptoGrpc;
	}

	public ContractController contractsGrpc() {
		if (contractsGrpc == null) {
			contractsGrpc = new ContractController(contractAnswers(), txnResponseHelper(), queryResponseHelper());
		}
		return contractsGrpc;
	}

	public PlatformSubmissionManager submissionManager() {
		if (submissionManager == null) {
			submissionManager = new PlatformSubmissionManager(platform(), recordCache(), speedometers());
		}
		return submissionManager;
	}

	public ConsensusController consensusGrpc() {
		if (null == consensusGrpc) {
			consensusGrpc = new ConsensusController(hcsAnswers(), txnResponseHelper(), queryResponseHelper());
		}
		return consensusGrpc;
	}

	public GrpcServerManager grpc() {
		if (grpc == null) {
			grpc = new NettyGrpcServerManager(
					Runtime.getRuntime()::addShutdownHook,
					new NettyServerManager(),
					List.of(
							cryptoGrpc(),
							filesGrpc(),
							freezeGrpc(),
							contractsGrpc(),
							consensusGrpc(),
							networkGrpc(),
							tokenGrpc()),
					Collections.emptyList());
		}
		return grpc;
	}

	public SmartContractRequestHandler contracts() {
		if (contracts == null) {
			contracts = new SmartContractRequestHandler(
					repository(),
					ledger(),
					this::accounts,
					txnCtx(),
					exchange(),
					usagePrices(),
					newPureRepo(),
					solidityLifecycle(),
					soliditySigsVerifier(),
					entityExpiries(),
					globalDynamicProperties());
		}
		return contracts;
	}

	public SolidityLifecycle solidityLifecycle() {
		if (solidityLifecycle == null) {
			solidityLifecycle = new SolidityLifecycle(globalDynamicProperties());
		}
		return solidityLifecycle;
	}

	public PropertySource properties() {
		if (properties == null) {
			properties = propertySources().asResolvingSource();
		}
		return properties;
	}

	public SystemFilesManager systemFilesManager() {
		if (systemFilesManager == null) {
			systemFilesManager = new HfsSystemFilesManager(
					addressBook(),
					fileNums(),
					properties(),
					(TieredHederaFs) hfs(),
					() -> lookupInCustomStore(
							b64KeyReader(),
							properties.getStringProperty("bootstrap.genesisB64Keystore.path"),
							properties.getStringProperty("bootstrap.genesisB64Keystore.keyName")),
					rates -> {
						exchange().updateRates(rates);
						if (!midnightRates().isInitialized()) {
							midnightRates().replaceWith(rates);
						}
					},
					schedules -> fees().init(),
					config -> {
						((StandardizedPropertySources) propertySources()).reloadFrom(config);
						globalDynamicProperties().reload();
						PropertiesLoader.populateApplicationPropertiesWithProto(config);
					},
					PropertiesLoader::populateAPIPropertiesWithProto);
			/* We must force eager evaluation of the throttle construction here,
			as in DEV environment with the per-classloader singleton pattern used by
			PropertiesLoader, it is otherwise possible to create weird race conditions
			between the initializing threads. */
			var throttles = bucketThrottling();
			PropertiesLoader.registerUpdateCallback(throttles::rebuild);
		}
		return systemFilesManager;
	}

	public ServicesRepositoryRoot repository() {
		if (repository == null) {
			repository = new ServicesRepositoryRoot(accountSource(), bytecodeDb());
			repository.setStoragePersistence(storagePersistence());
		}
		return repository;
	}

	public Supplier<ServicesRepositoryRoot> newPureRepo() {
		if (newPureRepo == null) {
			TransactionalLedger<AccountID, AccountProperty, MerkleAccount> pureDelegate = new TransactionalLedger<>(
					AccountProperty.class,
					MerkleAccount::new,
					new PureFCMapBackingAccounts(this::accounts),
					new ChangeSummaryManager<>());
			HederaLedger pureLedger = new HederaLedger(
					NOOP_TOKEN_STORE,
					NOOP_ID_SOURCE,
					NOOP_EXPIRING_CREATIONS,
					NOOP_RECORDS_HISTORIAN,
					pureDelegate);
			Source<byte[], AccountState> pureAccountSource = new LedgerAccountsSource(
					pureLedger,
					globalDynamicProperties());
			newPureRepo = () -> {
				var pureRepository = new ServicesRepositoryRoot(pureAccountSource, bytecodeDb());
				pureRepository.setStoragePersistence(storagePersistence());
				return pureRepository;
			};
		}
		return newPureRepo;
	}

	public ConsensusStatusCounts statusCounts() {
		if (statusCounts == null) {
			statusCounts = new ConsensusStatusCounts(new ObjectMapper());
		}
		return statusCounts;
	}

	public LedgerAccountsSource accountSource() {
		if (accountSource == null) {
			accountSource = new LedgerAccountsSource(ledger(), globalDynamicProperties());
		}
		return accountSource;
	}

	public BlobStorageSource bytecodeDb() {
		if (bytecodeDb == null) {
			bytecodeDb = new BlobStorageSource(bytecodeMapFrom(blobStore()));
		}
		return bytecodeDb;
	}

	public TransactionHandler txns() {
		if (txns == null) {
			txns = new TransactionHandler(
					recordCache(),
					precheckVerifier(),
					this::accounts,
					nodeAccount(),
					txnThrottling(),
					fees(),
					stateViews(),
					new BasicPrecheck(validator(), globalDynamicProperties()),
					queryFeeCheck(),
					bucketThrottling(),
					accountNums(),
					systemOpPolicies(),
					exemptions(),
					platformStatus());
		}
		return txns;
	}

	public Console console() {
		if (console == null) {
			console = platform().createConsole(true);
		}
		return console;
	}

	public AccountID nodeAccount() {
		if (accountId == null) {
			try {
				String memoOfAccountId = address().getMemo();
				accountId = accountParsedFromString(memoOfAccountId);
			} catch (Exception ignore) {
			}
		}
		return accountId;
	}

	public Address address() {
		if (address == null) {
			address = addressBook().getAddress(id.getId());
		}
		return address;
	}

	public AtomicReference<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> queryableStorage() {
		if (queryableStorage == null) {
			queryableStorage = new AtomicReference<>(storage());
		}
		return queryableStorage;
	}

	public AtomicReference<FCMap<MerkleEntityId, MerkleAccount>> queryableAccounts() {
		if (queryableAccounts == null) {
			queryableAccounts = new AtomicReference<>(accounts());
		}
		return queryableAccounts;
	}

	public AtomicReference<FCMap<MerkleEntityId, MerkleTopic>> queryableTopics() {
		if (queryableTopics == null) {
			queryableTopics = new AtomicReference<>(topics());
		}
		return queryableTopics;
	}

	public AtomicReference<FCMap<MerkleEntityId, MerkleToken>> queryableTokens() {
		if (queryableTokens == null) {
			queryableTokens = new AtomicReference<>(tokens());
		}
		return queryableTokens;
	}

	public AtomicReference<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> queryableTokenAssociations() {
		if (queryableTokenAssociations == null) {
			queryableTokenAssociations = new AtomicReference<>(tokenAssociations());
		}
		return queryableTokenAssociations;
	}

	public UsagePricesProvider usagePrices() {
		if (usagePrices == null) {
			usagePrices = new AwareFcfsUsagePrices(hfs(), fileNums(), txnCtx());
		}
		return usagePrices;
	}

	public TxnFeeChargingPolicy txnChargingPolicy() {
		if (txnChargingPolicy == null) {
			txnChargingPolicy = new TxnFeeChargingPolicy();
		}
		return txnChargingPolicy;
	}

	public SystemAccountsCreator systemAccountsCreator() {
		if (systemAccountsCreator == null) {
			systemAccountsCreator = new BackedSystemAccountsCreator(
					hederaNums(),
					accountNums(),
					properties(),
					b64KeyReader());
		}
		return systemAccountsCreator;
	}

	/* Context-free infrastructure. */
	public LegacyEd25519KeyReader b64KeyReader() {
		return b64KeyReader;
	}

	public Pause pause() {
		return pause;
	}

	public StateMigrations stateMigrations() {
		return stateMigrations;
	}

	public AccountsExporter accountsExporter() {
		return accountsExporter;
	}

	/* Injected dependencies. */
	public NodeId id() {
		return id;
	}

	public Platform platform() {
		return platform;
	}

	public PropertySources propertySources() {
		return propertySources;
	}

	public Instant consensusTimeOfLastHandledTxn() {
		return state.networkCtx().consensusTimeOfLastHandledTxn();
	}

	public void updateConsensusTimeOfLastHandledTxn(Instant dataDrivenNow) {
		state.networkCtx().setConsensusTimeOfLastHandledTxn(dataDrivenNow);
	}

	public AddressBook addressBook() {
		return state.addressBook();
	}

	public SequenceNumber seqNo() {
		return state.networkCtx().seqNo();
	}

	public ExchangeRates midnightRates() {
		return state.networkCtx().midnightRates();
	}

	public FCMap<MerkleEntityId, MerkleAccount> accounts() {
		return state.accounts();
	}

	public FCMap<MerkleEntityId, MerkleTopic> topics() {
		return state.topics();
	}

	public FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage() {
		return state.storage();
	}

	public FCMap<MerkleEntityId, MerkleToken> tokens() {
		return state.tokens();
	}

	public FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations() {
		return state.tokenAssociations();
	}

	public MerkleDiskFs diskFs() {
		return state.diskFs();
	}

	void setBackingTokenRels(BackingTokenRels backingTokenRels) {
		this.backingTokenRels = backingTokenRels;
	}

	void setBackingAccounts(FCMapBackingAccounts backingAccounts) {
		this.backingAccounts = backingAccounts;
	}
}
