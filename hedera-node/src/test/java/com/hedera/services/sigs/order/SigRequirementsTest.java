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
package com.hedera.services.sigs.order;

import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.PRETEND_SIGNING_TIME;
import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.defaultLookupsFor;
import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static com.hedera.test.factories.scenarios.BadPayerScenarios.INVALID_PAYER_ID_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusCreateTopicScenarios.CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.CONSENSUS_SUBMIT_MESSAGE_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.CONSENSUS_SUBMIT_MESSAGE_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.DILIGENT_SIGNING_PAYER_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.EXISTING_TOPIC_ID;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.FIRST_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_ACCOUNT_ID;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_FILE_WACL_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_TOPIC_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.MISC_TOPIC_SUBMIT_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.NEW_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.SCHEDULE_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.SECOND_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.SIMPLE_NEW_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_KYC_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_PAUSE_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_REPLACE_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_SUPPLY_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_TREASURY_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.TOKEN_WIPE_KT;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.UPDATE_TOPIC_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_EXPIRY_ONLY_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusUpdateTopicScenarios.CONSENSUS_UPDATE_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.CONTRACT_CREATE_DEPRECATED_CID_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.CONTRACT_CREATE_NO_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.CONTRACT_CREATE_WITH_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.CONTRACT_CREATE_WITH_AUTO_RENEW_ACCOUNT;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_IMMUTABLE_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_MISSING_ACCOUNT_BENEFICIARY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_MISSING_CONTRACT_BENEFICIARY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_XFER_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.CONTRACT_DELETE_XFER_CONTRACT_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_ONLY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_AUTORENEW_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_DEPRECATED_CID_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_FILE_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_PROXY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_INVALID_AUTO_RENEW_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_NEW_AUTO_RENEW_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_WITH_NEW_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.CryptoAllowanceScenarios.CRYPTO_APPROVE_ALLOWANCE_NO_OWNER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoAllowanceScenarios.CRYPTO_APPROVE_ALLOWANCE_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoAllowanceScenarios.CRYPTO_APPROVE_ALLOWANCE_SELF_OWNER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoAllowanceScenarios.CRYPTO_APPROVE_ALLOWANCE_USING_DELEGATING_SPENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoAllowanceScenarios.CRYPTO_APPROVE_CRYPTO_ALLOWANCE_MISSING_OWNER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoAllowanceScenarios.CRYPTO_APPROVE_NFT_ALLOWANCE_MISSING_DELEGATING_SPENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoAllowanceScenarios.CRYPTO_APPROVE_NFT_ALLOWANCE_MISSING_OWNER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoAllowanceScenarios.CRYPTO_APPROVE_TOKEN_ALLOWANCE_MISSING_OWNER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoAllowanceScenarios.CRYPTO_DELETE_ALLOWANCE_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoAllowanceScenarios.CRYPTO_DELETE_ALLOWANCE_SELF_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoAllowanceScenarios.CRYPTO_DELETE_NFT_ALLOWANCE_MISSING_OWNER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.CRYPTO_CREATE_NO_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoCreateScenarios.CRYPTO_CREATE_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.CRYPTO_DELETE_MISSING_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.CRYPTO_DELETE_MISSING_TARGET;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_CUSTOM_PAYER_PAID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_SELF_PAID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.CRYPTO_DELETE_TARGET_RECEIVER_SIG_CUSTOM_PAYER_PAID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.CRYPTO_DELETE_TARGET_RECEIVER_SIG_RECEIVER_PAID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.CRYPTO_DELETE_TARGET_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoDeleteScenarios.CRYPTO_DELETE_TARGET_RECEIVER_SIG_SELF_PAID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_ALLOWANCE_SPENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_CUSTOM_PAYER_SENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_FROM_IMMUTABLE_SENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_FROM_IMMUTABLE_SENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_FROM_MISSING_SENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_TO_IMMUTABLE_RECEIVER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NFT_TO_MISSING_RECEIVER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_NO_RECEIVER_SIG_USING_ALIAS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_IS_MISSING_ALIAS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_SIG_USING_ALIAS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_SENDER_IS_MISSING_ALIAS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_TOKEN_TO_IMMUTABLE_RECEIVER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_TO_IMMUTABLE_RECEIVER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.NFT_TRNASFER_ALLOWANCE_SPENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_MOVING_HBARS_WITH_EXTANT_SENDER;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_MOVING_HBARS_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDER;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_EXTANT_SENDERS;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_MISSING_SENDERS;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_RECEIVER;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_SENDER;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_FT;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_HBAR;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_BUT_ROYALTY_FEE_WITH_FALLBACK_TRIGGERED;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_TRIGGERED_BUT_SENDER_IS_TREASURY;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_RECEIVER_SIG_REQ;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_USING_ALIAS;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.TOKEN_TRNASFER_ALLOWANCE_SPENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_IMMUTABLE_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_NO_NEW_KEY_CUSTOM_PAYER_PAID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_NO_NEW_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_NO_NEW_KEY_SELF_PAID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NEW_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NO_NEW_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_SYS_ACCOUNT_WITH_PRIVILEGED_PAYER;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NEW_KEY;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NO_NEW_KEY;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_WITH_NEW_KEY_CUSTOM_PAYER_PAID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_WITH_NEW_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoUpdateScenarios.CRYPTO_UPDATE_WITH_NEW_KEY_SELF_PAID_SCENARIO;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.FILE_APPEND_MISSING_TARGET_SCENARIO;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.IMMUTABLE_FILE_APPEND_SCENARIO;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.MASTER_SYS_FILE_APPEND_SCENARIO;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.SYSTEM_FILE_APPEND_WITH_PRIVILEGD_PAYER;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.TREASURY_SYS_FILE_APPEND_SCENARIO;
import static com.hedera.test.factories.scenarios.FileAppendScenarios.VANILLA_FILE_APPEND_SCENARIO;
import static com.hedera.test.factories.scenarios.FileCreateScenarios.VANILLA_FILE_CREATE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileDeleteScenarios.IMMUTABLE_FILE_DELETE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileDeleteScenarios.MISSING_FILE_DELETE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileDeleteScenarios.VANILLA_FILE_DELETE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.FILE_UPDATE_MISSING_SCENARIO;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.FILE_UPDATE_NEW_WACL_SCENARIO;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.IMMUTABLE_FILE_UPDATE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.MASTER_SYS_FILE_UPDATE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.TREASURY_SYS_FILE_UPDATE_SCENARIO;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.TREASURY_SYS_FILE_UPDATE_SCENARIO_NO_NEW_KEY;
import static com.hedera.test.factories.scenarios.FileUpdateScenarios.VANILLA_FILE_UPDATE_SCENARIO;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_INVALID_XFER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_NONSENSE;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER_AND_REGULAR_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_TREASURY_UPDATE_WITH_TREASURY_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_NO_ADMIN;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER_AS_SELF;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN_CUSTOM_PAYER_SENDER_AND_PAYER_AS_SELF;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN_SELF_SENDER_AND_PAYER_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AND_PAYER_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AND_PAYER_AS_SELF;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AS_CUSTOM_PAYER_AND_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AS_SELF_AND_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleCreateScenarios.SCHEDULE_CREATE_XFER_WITH_MISSING_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_KNOWN_SCHEDULE;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_MISSING_SCHEDULE;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_MISSING_SCHEDULE_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.ScheduleSignScenarios.SCHEDULE_SIGN_KNOWN_SCHEDULE;
import static com.hedera.test.factories.scenarios.ScheduleSignScenarios.SCHEDULE_SIGN_KNOWN_SCHEDULE_WITH_NOW_INVALID_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleSignScenarios.SCHEDULE_SIGN_KNOWN_SCHEDULE_WITH_PAYER;
import static com.hedera.test.factories.scenarios.ScheduleSignScenarios.SCHEDULE_SIGN_KNOWN_SCHEDULE_WITH_PAYER_SELF;
import static com.hedera.test.factories.scenarios.ScheduleSignScenarios.SCHEDULE_SIGN_MISSING_SCHEDULE;
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.SYSTEM_DELETE_FILE_SCENARIO;
import static com.hedera.test.factories.scenarios.SystemUndeleteScenarios.SYSTEM_UNDELETE_FILE_SCENARIO;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_IMMUTABLE_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_MISSING_TARGET;
import static com.hedera.test.factories.scenarios.TokenAssociateScenarios.TOKEN_ASSOCIATE_WITH_SELF_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenBurnScenarios.BURN_WITH_SUPPLY_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_MISSING_ADMIN;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ADMIN_AND_FREEZE;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ADMIN_ONLY;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_AUTO_RENEW;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_AUTO_RENEW_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_MISSING_AUTO_RENEW;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_MISSING_COLLECTOR;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_MISSING_TREASURY;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_TREASURY_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.TOKEN_CREATE_WITH_TREASURY_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenDeleteScenarios.DELETE_WITH_KNOWN_TOKEN;
import static com.hedera.test.factories.scenarios.TokenDeleteScenarios.DELETE_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenDeleteScenarios.DELETE_WITH_MISSING_TOKEN_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_MISSING_TARGET;
import static com.hedera.test.factories.scenarios.TokenDissociateScenarios.TOKEN_DISSOCIATE_WITH_SELF_PAID_KNOWN_TARGET;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_FEE_SCHEDULE_BUT_TOKEN_DOESNT_EXIST;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_NO_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_NO_SIG_REQ_AND_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_MISSING_FEE_COLLECTOR;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.UPDATE_TOKEN_WITH_NO_FEE_SCHEDULE_KEY;
import static com.hedera.test.factories.scenarios.TokenFreezeScenarios.VALID_FREEZE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenKycGrantScenarios.VALID_GRANT_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenKycRevokeScenarios.REVOKE_FOR_TOKEN_WITHOUT_KYC;
import static com.hedera.test.factories.scenarios.TokenKycRevokeScenarios.REVOKE_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenKycRevokeScenarios.VALID_REVOKE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenMintScenarios.MINT_WITH_SUPPLY_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenPauseScenarios.VALID_PAUSE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.VALID_UNFREEZE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUnpauseScenarios.VALID_UNPAUSE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.TOKEN_UPDATE_WITH_MISSING_AUTO_RENEW_ACCOUNT;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_TREASURY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_TREASURY_AS_CUSTOM_PAYER;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_TREASURY_AS_PAYER;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_REPLACING_WITH_MISSING_TREASURY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_FREEZE_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_KYC_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_MISSING_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_MISSING_TOKEN_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_NO_KEYS_AFFECTED;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_SUPPLY_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUpdateScenarios.UPDATE_WITH_WIPE_KEYED_TOKEN;
import static com.hedera.test.factories.scenarios.TokenWipeScenarios.VALID_WIPE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CURRENTLY_UNUSED_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DELEGATING_SPENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.OWNER_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.OWNER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SIMPLE_NEW_WACL_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SYS_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SYS_FILE_WACL_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FEE_SCHEDULE_KT;
import static com.hedera.test.factories.txns.ConsensusCreateTopicFactory.SIMPLE_TOPIC_ADMIN_KEY;
import static com.hedera.test.factories.txns.ContractCreateFactory.DEFAULT_ADMIN_KT;
import static com.hedera.test.factories.txns.CryptoCreateFactory.DEFAULT_ACCOUNT_KT;
import static com.hedera.test.factories.txns.FileCreateFactory.DEFAULT_WACL_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.MASTER_PAYER_ID;
import static com.hedera.test.factories.txns.SignedTxnFactory.TREASURY_PAYER_ID;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.config.EntityNumbers;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.config.MockFileNumbers;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.sigs.metadata.ContractSigningMetadata;
import com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup;
import com.hedera.services.sigs.metadata.SafeLookupResult;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.metadata.TopicSigningMetadata;
import com.hedera.services.sigs.metadata.lookups.AccountSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.ContractSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.FileSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.HfsSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.TopicSigMetaLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.auth.SystemOpPolicies;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SigRequirementsTest {
    private static class TopicAdapter {
        public static TopicSigMetaLookup throwingUoe() {
            return id -> {
                throw new UnsupportedOperationException();
            };
        }

        public static TopicSigMetaLookup withSafe(
                Function<TopicID, SafeLookupResult<TopicSigningMetadata>> fn) {
            return fn::apply;
        }
    }

    private static class FileAdapter {
        public static FileSigMetaLookup throwingUoe() {
            return id -> {
                throw new UnsupportedOperationException();
            };
        }
    }

    private static class AccountAdapter {
        public static AccountSigMetaLookup withSafe(
                Function<AccountID, SafeLookupResult<AccountSigningMetadata>> fn) {
            return new AccountSigMetaLookup() {
                @Override
                public SafeLookupResult<AccountSigningMetadata> safeLookup(AccountID id) {
                    return fn.apply(id);
                }

                @Override
                public SafeLookupResult<AccountSigningMetadata> aliasableSafeLookup(
                        AccountID idOrAlias) {
                    return fn.apply(idOrAlias);
                }
            };
        }
    }

    private static class ContractAdapter {
        public static ContractSigMetaLookup withSafe(
                Function<ContractID, SafeLookupResult<ContractSigningMetadata>> fn) {
            return fn::apply;
        }
    }

    private static final Function<ContractSigMetaLookup, SigMetadataLookup> EXC_LOOKUP_FN =
            contractSigMetaLookup ->
                    new DelegatingSigMetadataLookup(
                            FileAdapter.throwingUoe(),
                            AccountAdapter.withSafe(
                                    id ->
                                            SafeLookupResult.failure(
                                                    KeyOrderingFailure.MISSING_FILE)),
                            contractSigMetaLookup,
                            TopicAdapter.withSafe(
                                    id ->
                                            SafeLookupResult.failure(
                                                    KeyOrderingFailure.MISSING_FILE)),
                            id -> null,
                            id -> null);
    private static final SigMetadataLookup EXCEPTION_THROWING_LOOKUP =
            EXC_LOOKUP_FN.apply(
                    ContractAdapter.withSafe(
                            id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT)));
    private static final SigMetadataLookup INVALID_CONTRACT_THROWING_LOOKUP =
            EXC_LOOKUP_FN.apply(
                    ContractAdapter.withSafe(
                            id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT)));
    private static final SigMetadataLookup IMMUTABLE_CONTRACT_THROWING_LOOKUP =
            EXC_LOOKUP_FN.apply(
                    ContractAdapter.withSafe(
                            id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT)));
    private static final SigMetadataLookup NONSENSE_CONTRACT_DELETE_THROWING_LOOKUP =
            EXC_LOOKUP_FN.apply(
                    ContractAdapter.withSafe(
                            id -> SafeLookupResult.failure(KeyOrderingFailure.MISSING_FILE)));
    private static final SigMetadataLookup INVALID_AUTO_RENEW_ACCOUNT_EXC =
            EXC_LOOKUP_FN.apply(
                    ContractAdapter.withSafe(
                            id ->
                                    SafeLookupResult.failure(
                                            KeyOrderingFailure.INVALID_AUTORENEW_ACCOUNT)));

    private HederaFs hfs;
    private TokenStore tokenStore;
    private AliasManager aliasManager;
    private FileNumbers fileNumbers = new MockFileNumbers();
    private ScheduleStore scheduleStore;
    private TransactionBody txn;
    private SigRequirements subject;
    private MerkleMap<EntityNum, MerkleAccount> accounts;
    private MerkleMap<EntityNum, MerkleTopic> topics;
    private CodeOrderResultFactory summaryFactory = CODE_ORDER_RESULT_FACTORY;
    private SigningOrderResultFactory<ResponseCodeEnum> mockSummaryFactory;
    private EntityNumbers mockEntityNumbers = new MockEntityNumbers();
    private SystemOpPolicies mockSystemOpPolicies = new SystemOpPolicies(mockEntityNumbers);
    private SignatureWaivers mockSignatureWaivers =
            new PolicyBasedSigWaivers(mockEntityNumbers, mockSystemOpPolicies);

    @Test
    void forwardsCallsWithoutLinkedRefs() {
        final var mockTxn = TransactionBody.getDefaultInstance();
        mockSummaryFactory();
        final var mockSubject = mock(SigRequirements.class);

        doCallRealMethod().when(mockSubject).keysForPayer(mockTxn, mockSummaryFactory);
        doCallRealMethod().when(mockSubject).keysForOtherParties(mockTxn, mockSummaryFactory);

        mockSubject.keysForPayer(mockTxn, mockSummaryFactory);
        mockSubject.keysForOtherParties(mockTxn, mockSummaryFactory);

        verify(mockSubject).keysForPayer(mockTxn, mockSummaryFactory, null);
        verify(mockSubject).keysForOtherParties(mockTxn, mockSummaryFactory, null);
    }

    @Test
    void forwardsCallsWithoutPayer() {
        final var mockLinkedRefs = new LinkedRefs();
        final var mockTxn = TransactionBody.getDefaultInstance();
        mockSummaryFactory();
        final var mockSubject = mock(SigRequirements.class);

        doCallRealMethod()
                .when(mockSubject)
                .keysForPayer(mockTxn, mockSummaryFactory, mockLinkedRefs);
        doCallRealMethod()
                .when(mockSubject)
                .keysForOtherParties(mockTxn, mockSummaryFactory, mockLinkedRefs);

        mockSubject.keysForPayer(mockTxn, mockSummaryFactory, mockLinkedRefs);
        mockSubject.keysForOtherParties(mockTxn, mockSummaryFactory, mockLinkedRefs);

        verify(mockSubject).keysForPayer(mockTxn, mockSummaryFactory, mockLinkedRefs, null);
        verify(mockSubject).keysForOtherParties(mockTxn, mockSummaryFactory, mockLinkedRefs, null);
    }

    @Test
    void reportsInvalidPayerId() throws Throwable {
        // given:
        setupFor(INVALID_PAYER_ID_SCENARIO);
        mockSummaryFactory();

        // when:
        subject.keysForPayer(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forInvalidAccount();
    }

    @Test
    void reportsGeneralPayerError() throws Throwable {
        // given:
        setupForNonStdLookup(CRYPTO_CREATE_NO_RECEIVER_SIG_SCENARIO, EXCEPTION_THROWING_LOOKUP);
        mockSummaryFactory();

        // when:
        subject.keysForPayer(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forGeneralPayerError();
    }

    @Test
    void getsCryptoCreateNoReceiverSigReq() throws Throwable {
        setupFor(CRYPTO_CREATE_NO_RECEIVER_SIG_SCENARIO);
        final var linkedRefs = new LinkedRefs();

        final var summary = subject.keysForPayer(txn, summaryFactory, linkedRefs);

        assertEquals(PRETEND_SIGNING_TIME, linkedRefs.getSourceSignedAt());
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoCreateNoReceiverSigReqWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_CREATE_NO_RECEIVER_SIG_SCENARIO);
        final var linkedRefs = new LinkedRefs();

        final var summary =
                subject.keysForPayer(txn, summaryFactory, linkedRefs, CUSTOM_PAYER_ACCOUNT);

        assertEquals(PRETEND_SIGNING_TIME, linkedRefs.getSourceSignedAt());
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoCreateReceiverSigReq() throws Throwable {
        // given:
        setupFor(CRYPTO_CREATE_RECEIVER_SIG_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoCreateReceiverSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(CRYPTO_CREATE_RECEIVER_SIG_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoTransferReceiverNoSigReqViaAlias() throws Throwable {
        setupFor(CRYPTO_TRANSFER_NO_RECEIVER_SIG_USING_ALIAS_SCENARIO);

        final var payerSummary = subject.keysForPayer(txn, summaryFactory);
        final var nonPayerSummary = subject.keysForOtherParties(txn, summaryFactory);

        assertThat(payerSummary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(payerSummary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
        assertFalse(nonPayerSummary.hasErrorReport());
        assertTrue(sanityRestored(nonPayerSummary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsCryptoTransferReceiverNoSigReqViaAliasWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_TRANSFER_NO_RECEIVER_SIG_USING_ALIAS_SCENARIO);

        final var payerSummary =
                subject.keysForPayer(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);
        final var nonPayerSummary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertThat(payerSummary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(payerSummary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
        assertFalse(nonPayerSummary.hasErrorReport());
        assertThat(nonPayerSummary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(nonPayerSummary.getOrderedKeys()),
                contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoTransferReceiverNoSigReq() throws Throwable {
        // given:
        setupFor(CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO);

        // when:
        final var payerSummary = subject.keysForPayer(txn, summaryFactory);
        final var nonPayerSummary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(
                sanityRestored(payerSummary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
        assertTrue(sanityRestored(nonPayerSummary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsCryptoTransferReceiverNoSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO);

        // when:
        final var payerSummary =
                subject.keysForPayer(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);
        final var nonPayerSummary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(payerSummary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(payerSummary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
        assertThat(nonPayerSummary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(nonPayerSummary.getOrderedKeys()),
                contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoTransferCustomPayerSender() throws Throwable {
        // given:
        setupFor(CRYPTO_TRANSFER_CUSTOM_PAYER_SENDER_SCENARIO);

        // when:
        final var payerSummary = subject.keysForPayer(txn, summaryFactory);
        final var nonPayerSummary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(
                sanityRestored(payerSummary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
        assertThat(
                sanityRestored(nonPayerSummary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoTransferCustomPayerSenderWithCustomPayer() throws Throwable {
        // given:
        setupFor(CRYPTO_TRANSFER_CUSTOM_PAYER_SENDER_SCENARIO);

        // when:
        final var payerSummary =
                subject.keysForPayer(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);
        final var nonPayerSummary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(payerSummary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(payerSummary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
        assertTrue(sanityRestored(nonPayerSummary.getOrderedKeys()).isEmpty());
    }

    @Test
    void doesntAddOwnerSigWhenAllowanceGrantedToPayerForHbarTransfer() throws Throwable {
        setupFor(CRYPTO_TRANSFER_ALLOWANCE_SPENDER_SCENARIO);

        final var nonPayerSummary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(sanityRestored(nonPayerSummary.getOrderedKeys()).isEmpty());
    }

    @Test
    void doesntAddOwnerSigWhenAllowanceGrantedToPayerForHbarTransferWithCustomPayer()
            throws Throwable {
        setupFor(CRYPTO_TRANSFER_ALLOWANCE_SPENDER_SCENARIO);

        final var nonPayerSummary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(sanityRestored(nonPayerSummary.getOrderedKeys()).isEmpty());
    }

    @Test
    void doesntAddOwnerSigWhenAllowanceGrantedToPayerForFungibleTokenTransfer() throws Throwable {
        setupFor(TOKEN_TRNASFER_ALLOWANCE_SPENDER_SCENARIO);

        final var nonPayerSummary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(sanityRestored(nonPayerSummary.getOrderedKeys()).isEmpty());
    }

    @Test
    void doesntAddOwnerSigWhenAllowanceGrantedToPayerForFungibleTokenTransferWithCustomPayer()
            throws Throwable {
        setupFor(TOKEN_TRNASFER_ALLOWANCE_SPENDER_SCENARIO);

        final var nonPayerSummary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(sanityRestored(nonPayerSummary.getOrderedKeys()).isEmpty());
    }

    @Test
    void doesntAddOwnerSigWhenAllowanceGrantedToPayerForNFTTransfer() throws Throwable {
        setupFor(NFT_TRNASFER_ALLOWANCE_SPENDER_SCENARIO);

        final var nonPayerSummary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(sanityRestored(nonPayerSummary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsCryptoTransferReceiverSigReq() throws Throwable {
        // given:
        setupFor(CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(RECEIVER_SIG_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoTransferReceiverSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(DEFAULT_PAYER_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsCryptoTransferReceiverSigReqWithAlias() throws Throwable {
        // given:
        setupFor(CRYPTO_TRANSFER_RECEIVER_SIG_USING_ALIAS_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(RECEIVER_SIG_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoTransferReceiverSigReqWithAliasWithCustomPayer() throws Throwable {
        // given:
        setupFor(CRYPTO_TRANSFER_RECEIVER_SIG_USING_ALIAS_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(DEFAULT_PAYER_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsNftOwnerChange() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeUsingAlias() throws Throwable {
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_USING_ALIAS);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeUsingAliasWithCustomPayer() throws Throwable {
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_USING_ALIAS);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeMissingSender() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_SENDER);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsNftOwnerChangeMissingSenderWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_SENDER);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsNftOwnerChangeMissingReceiver() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_RECEIVER);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsNftOwnerChangeMissingReceiverWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_MISSING_RECEIVER);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsNftOwnerChangeWithReceiverSigReq() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_RECEIVER_SIG_REQ);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertTrue(
                sanityRestored(summary.getOrderedKeys()).contains(SECOND_TOKEN_SENDER_KT.asKey()));
        assertTrue(sanityRestored(summary.getOrderedKeys()).contains(RECEIVER_SIG_KT.asKey()));
        assertTrue(
                sanityRestored(summary.getOrderedKeys()).contains(FIRST_TOKEN_SENDER_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithReceiverSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_RECEIVER_SIG_REQ);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(4));
        assertTrue(
                sanityRestored(summary.getOrderedKeys()).contains(SECOND_TOKEN_SENDER_KT.asKey()));
        assertTrue(sanityRestored(summary.getOrderedKeys()).contains(RECEIVER_SIG_KT.asKey()));
        assertTrue(
                sanityRestored(summary.getOrderedKeys()).contains(FIRST_TOKEN_SENDER_KT.asKey()));
        assertTrue(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithNoReceiverSigReq() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithNoReceiverSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithNoReceiverSigReqButFallbackFeeTriggered() throws Throwable {
        // given:
        setupFor(
                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_BUT_ROYALTY_FEE_WITH_FALLBACK_TRIGGERED);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(FIRST_TOKEN_SENDER_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithNoReceiverSigReqButFallbackFeeTriggeredWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(
                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_BUT_ROYALTY_FEE_WITH_FALLBACK_TRIGGERED);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(FIRST_TOKEN_SENDER_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithNoSigReqAndFallbackFeeTriggeredButSenderIsTreasury()
            throws Throwable {
        // given:
        setupFor(
                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_TRIGGERED_BUT_SENDER_IS_TREASURY);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithNoSigReqAndFallbackFeeTriggeredButSenderIsTreasuryWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(
                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_SIG_REQ_WITH_FALLBACK_TRIGGERED_BUT_SENDER_IS_TREASURY);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithNoReceiverSigReqAndFallbackFeeNotTriggeredDueToHbar()
            throws Throwable {
        // given:
        setupFor(
                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_HBAR);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithNoReceiverSigReqAndFallbackFeeNotTriggeredDueToHbarWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(
                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_HBAR);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithNoReceiverSigReqAndFallbackFeeNotTriggeredDueToFt()
            throws Throwable {
        // given:
        setupFor(
                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_FT);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithNoReceiverSigReqAndFallbackFeeNotTriggeredDueToFtWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(
                TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_FALLBACK_NOT_TRIGGERED_DUE_TO_FT);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsNftOwnerChangeWithNoReceiverSigReqAndMissingToken() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_MISSING_TOKEN);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, summary.getErrorReport());
    }

    @Test
    void getsNftOwnerChangeWithNoReceiverSigReqAndMissingTokenWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_OWNERSHIP_CHANGE_NO_RECEIVER_SIG_REQ_AND_MISSING_TOKEN);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, summary.getErrorReport());
    }

    @Test
    void getsMissingAliasCannotBeSender() throws Throwable {
        setupFor(CRYPTO_TRANSFER_SENDER_IS_MISSING_ALIAS_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsMissingAliasCannotBeSenderWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_TRANSFER_SENDER_IS_MISSING_ALIAS_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsMissingAliasCanBeReceiver() throws Throwable {
        setupFor(CRYPTO_TRANSFER_RECEIVER_IS_MISSING_ALIAS_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsMissingAliasCanBeReceiverWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_TRANSFER_RECEIVER_IS_MISSING_ALIAS_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void reportsMissingCryptoTransferReceiver() throws Throwable {
        setupFor(CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO);
        mockSummaryFactory();
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forMissingAccount()).willReturn(result);

        subject.keysForOtherParties(txn, mockSummaryFactory);

        verify(mockSummaryFactory).forInvalidAccount();
    }

    @Test
    void reportsMissingCryptoTransferReceiverWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_TRANSFER_MISSING_ACCOUNT_SCENARIO);
        mockSummaryFactory();
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forMissingAccount()).willReturn(result);

        subject.keysForOtherParties(txn, mockSummaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        verify(mockSummaryFactory).forInvalidAccount();
    }

    @Test
    void allowsTransferToImmutableReceiver() throws Throwable {
        setupFor(CRYPTO_TRANSFER_TO_IMMUTABLE_RECEIVER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertFalse(summary.hasErrorReport());
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void allowsTransferToImmutableReceiverWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_TRANSFER_TO_IMMUTABLE_RECEIVER_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertFalse(summary.hasErrorReport());
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void rejectsTransferFromImmutableSender() throws Throwable {
        setupFor(CRYPTO_TRANSFER_FROM_IMMUTABLE_SENDER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void rejectsNftTransferFromMissingSender() throws Throwable {
        setupFor(CRYPTO_TRANSFER_NFT_FROM_MISSING_SENDER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void allowsNftTransferToMissingReceiver() throws Throwable {
        setupFor(CRYPTO_TRANSFER_NFT_TO_MISSING_RECEIVER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertFalse(summary.getOrderedKeys().isEmpty());
        assertFalse(summary.hasErrorReport());
    }

    @Test
    void rejectsNftTransferFromImmutableSender() throws Throwable {
        setupFor(CRYPTO_TRANSFER_NFT_FROM_IMMUTABLE_SENDER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void rejectsNftTransferToImmutableReceiver() throws Throwable {
        setupFor(CRYPTO_TRANSFER_NFT_TO_IMMUTABLE_RECEIVER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void rejectsTransferFromImmutableSenderWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_TRANSFER_FROM_IMMUTABLE_SENDER_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void rejectsFungibleTokenTransferToImmutableReceiver() throws Throwable {
        setupFor(CRYPTO_TRANSFER_TOKEN_TO_IMMUTABLE_RECEIVER_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void reportsGeneralErrorInCryptoTransfer() throws Throwable {
        // given:
        setupForNonStdLookup(
                CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO,
                new DelegatingSigMetadataLookup(
                        FileAdapter.throwingUoe(),
                        AccountAdapter.withSafe(
                                id -> SafeLookupResult.failure(KeyOrderingFailure.MISSING_FILE)),
                        ContractAdapter.withSafe(
                                id ->
                                        SafeLookupResult.failure(
                                                KeyOrderingFailure.INVALID_CONTRACT)),
                        TopicAdapter.throwingUoe(),
                        id -> null,
                        id -> null));
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forGeneralError()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forGeneralError();
    }

    @Test
    void reportsGeneralErrorInCryptoTransferWithCustomPayer() throws Throwable {
        // given:
        setupForNonStdLookup(
                CRYPTO_TRANSFER_NO_RECEIVER_SIG_SCENARIO,
                new DelegatingSigMetadataLookup(
                        FileAdapter.throwingUoe(),
                        AccountAdapter.withSafe(
                                id -> SafeLookupResult.failure(KeyOrderingFailure.MISSING_FILE)),
                        ContractAdapter.withSafe(
                                id ->
                                        SafeLookupResult.failure(
                                                KeyOrderingFailure.INVALID_CONTRACT)),
                        TopicAdapter.throwingUoe(),
                        id -> null,
                        id -> null));
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forGeneralError()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        verify(mockSummaryFactory).forGeneralError();
    }

    @Test
    void getsCryptoApproveAllowanceVanilla() throws Throwable {
        setupFor(CRYPTO_APPROVE_ALLOWANCE_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        OWNER_ACCOUNT_KT.asKey(),
                        OWNER_ACCOUNT_KT.asKey(),
                        OWNER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoApproveAllowanceVanillaWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_APPROVE_ALLOWANCE_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        OWNER_ACCOUNT_KT.asKey(),
                        OWNER_ACCOUNT_KT.asKey(),
                        OWNER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoApproveAllowanceSelfOwner() throws Throwable {
        setupFor(CRYPTO_APPROVE_ALLOWANCE_SELF_OWNER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsCryptoApproveAllowanceSelfOwnerWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_APPROVE_ALLOWANCE_SELF_OWNER_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        DEFAULT_PAYER_KT.asKey(),
                        DEFAULT_PAYER_KT.asKey(),
                        DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoApproveAllowanceCustomPayerOwnerWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_APPROVE_ALLOWANCE_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory, null, OWNER_ACCOUNT);

        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsCryptoApproveAllowanceUsingDelegatingSpender() throws Throwable {
        setupFor(CRYPTO_APPROVE_ALLOWANCE_USING_DELEGATING_SPENDER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        OWNER_ACCOUNT_KT.asKey(),
                        OWNER_ACCOUNT_KT.asKey(),
                        DELEGATING_SPENDER_KT.asKey()));
    }

    @Test
    void getsCryptoApproveAllowanceUsingDelegatingSpenderWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_APPROVE_ALLOWANCE_USING_DELEGATING_SPENDER_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        OWNER_ACCOUNT_KT.asKey(),
                        OWNER_ACCOUNT_KT.asKey(),
                        DELEGATING_SPENDER_KT.asKey()));
    }

    @Test
    void getsCryptoApproveAllowanceWithSomeSpecificOwners() throws Throwable {
        setupFor(CRYPTO_APPROVE_ALLOWANCE_NO_OWNER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(OWNER_ACCOUNT_KT.asKey(), OWNER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoApproveAllowanceWithSomeSpecificOwnersWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_APPROVE_ALLOWANCE_NO_OWNER_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(OWNER_ACCOUNT_KT.asKey(), OWNER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoApproveAllowanceMissingOwnerInFungibleTokenAllowance() throws Throwable {
        setupFor(CRYPTO_APPROVE_TOKEN_ALLOWANCE_MISSING_OWNER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, summary.getErrorReport());
    }

    @Test
    void getsCryptoApproveAllowanceMissingOwnerInFungibleTokenAllowanceWithCustomPayer()
            throws Throwable {
        setupFor(CRYPTO_APPROVE_TOKEN_ALLOWANCE_MISSING_OWNER_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, summary.getErrorReport());
    }

    @Test
    void getsCryptoApproveAllowanceMissingOwnerInCryptoAllowance() throws Throwable {
        setupFor(CRYPTO_APPROVE_CRYPTO_ALLOWANCE_MISSING_OWNER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, summary.getErrorReport());
    }

    @Test
    void getsCryptoApproveAllowanceMissingOwnerInCryptoAllowanceWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_APPROVE_CRYPTO_ALLOWANCE_MISSING_OWNER_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, summary.getErrorReport());
    }

    @Test
    void getsCryptoApproveAllowanceMissingOwnerInNftAllowance() throws Throwable {
        setupFor(CRYPTO_APPROVE_NFT_ALLOWANCE_MISSING_OWNER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, summary.getErrorReport());
    }

    @Test
    void getsCryptoApproveAllowanceMissingOwnerInNftAllowanceWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_APPROVE_NFT_ALLOWANCE_MISSING_OWNER_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, summary.getErrorReport());
    }

    @Test
    void getsCryptoApproveAllowanceMissingDelegatingSpenderInNftAllowance() throws Throwable {
        setupFor(CRYPTO_APPROVE_NFT_ALLOWANCE_MISSING_DELEGATING_SPENDER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_DELEGATING_SPENDER, summary.getErrorReport());
    }

    @Test
    void getsCryptoApproveAllowanceMissingDelegatingSpenderInNftAllowanceWithCustomPayer()
            throws Throwable {
        setupFor(CRYPTO_APPROVE_NFT_ALLOWANCE_MISSING_DELEGATING_SPENDER_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_DELEGATING_SPENDER, summary.getErrorReport());
    }

    @Test
    void getsCryptoDeleteAllowanceVanilla() throws Throwable {
        setupFor(CRYPTO_DELETE_ALLOWANCE_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(OWNER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteAllowanceVanillaWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_DELETE_ALLOWANCE_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(OWNER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteAllowanceSelf() throws Throwable {
        setupFor(CRYPTO_DELETE_ALLOWANCE_SELF_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsCryptoDeleteAllowanceSelfWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_DELETE_ALLOWANCE_SELF_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteAllowanceCustomPayerWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_DELETE_ALLOWANCE_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory, null, OWNER_ACCOUNT);

        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsCryptoDeleteAllowanceMissingOwnerInNftAllowance() throws Throwable {
        setupFor(CRYPTO_DELETE_NFT_ALLOWANCE_MISSING_OWNER_SCENARIO);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, summary.getErrorReport());
    }

    @Test
    void getsCryptoDeleteAllowanceMissingOwnerInNftAllowanceWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_DELETE_NFT_ALLOWANCE_MISSING_OWNER_SCENARIO);

        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, summary.getErrorReport());
    }

    @Test
    void getsCryptoUpdateVanillaNewKey() throws Throwable {
        // given:
        setupFor(CRYPTO_UPDATE_WITH_NEW_KEY_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_ACCOUNT_KT.asKey(), NEW_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateVanillaNewKeyWithCustomPayer() throws Throwable {
        // given:
        setupFor(CRYPTO_UPDATE_WITH_NEW_KEY_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_ACCOUNT_KT.asKey(), NEW_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateNewKeySelfPaidReturnsJustTheNewKey() throws Throwable {
        // given:
        setupFor(CRYPTO_UPDATE_WITH_NEW_KEY_SELF_PAID_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(NEW_ACCOUNT_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateNewKeySelfPaidReturnsJustTheNewKeyWithCustomPayer() throws Throwable {
        // given:
        setupFor(CRYPTO_UPDATE_WITH_NEW_KEY_SELF_PAID_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(DEFAULT_PAYER_KT.asKey(), NEW_ACCOUNT_KT.asKey()));
        assertFalse(
                sanityRestored(summary.getOrderedKeys()).contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateNewKeyCustomPayerPaidReturnsJustTheNewKey() throws Throwable {
        // given:
        setupFor(CRYPTO_UPDATE_WITH_NEW_KEY_CUSTOM_PAYER_PAID_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey(), NEW_ACCOUNT_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateNewKeyCustomPayerPaidReturnsJustTheNewKeyWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(CRYPTO_UPDATE_WITH_NEW_KEY_CUSTOM_PAYER_PAID_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(NEW_ACCOUNT_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
        assertFalse(
                sanityRestored(summary.getOrderedKeys()).contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateProtectedSysAccountNewKey() throws Throwable {
        setupFor(CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NEW_KEY_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SYS_ACCOUNT_KT.asKey(), NEW_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateProtectedSysAccountNewKeyWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NEW_KEY_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SYS_ACCOUNT_KT.asKey(), NEW_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateProtectedSysAccountNoNewKey() throws Throwable {
        setupFor(CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NO_NEW_KEY_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(SYS_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateProtectedSysAccountNoNewKeyWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_SYS_ACCOUNT_WITH_NO_NEW_KEY_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(SYS_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateSysAccountWithPrivilegedPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_SYS_ACCOUNT_WITH_PRIVILEGED_PAYER);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsCryptoUpdateSysAccountWithPrivilegedPayerWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_SYS_ACCOUNT_WITH_PRIVILEGED_PAYER);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SYS_ACCOUNT_KT.asKey(), NEW_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateTreasuryWithTreasury() throws Throwable {
        setupFor(CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NO_NEW_KEY);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsCryptoUpdateTreasuryWithTreasuryWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NO_NEW_KEY);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateTreasuryWithTreasuryWithTreasuryCustomPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NO_NEW_KEY);

        // when:
        final var summary =
                subject.keysForOtherParties(
                        txn, summaryFactory, null, asAccount(TREASURY_PAYER_ID));

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsCryptoUpdateTreasuryWithTreasuryAndNewKey() throws Throwable {
        setupFor(CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NEW_KEY);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(NEW_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateTreasuryWithTreasuryAndNewKeyWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NEW_KEY);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(DEFAULT_PAYER_KT.asKey(), NEW_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateTreasuryWithTreasuryAndNewKeyWithTreasuryAsCustomPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_TREASURY_ACCOUNT_WITH_TREASURY_AND_NEW_KEY);

        // when:
        final var summary =
                subject.keysForOtherParties(
                        txn, summaryFactory, null, asAccount(TREASURY_PAYER_ID));

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(NEW_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateVanillaNoNewKey() throws Throwable {
        setupFor(CRYPTO_UPDATE_NO_NEW_KEY_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateVanillaNoNewKeyWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_NO_NEW_KEY_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateNoNewKeySelfPaidReturnsEmptyKeyList() throws Throwable {
        setupFor(CRYPTO_UPDATE_NO_NEW_KEY_SELF_PAID_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsCryptoUpdateNoNewKeySelfPaidReturnsEmptyKeyListWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_NO_NEW_KEY_SELF_PAID_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
        assertFalse(
                sanityRestored(summary.getOrderedKeys()).contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateNoNewKeyCustomPayerPaidReturnsEmptyKeyList() throws Throwable {
        setupFor(CRYPTO_UPDATE_NO_NEW_KEY_CUSTOM_PAYER_PAID_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoUpdateNoNewKeyCustomPayerPaidReturnsEmptyKeyListWithCustomPayer()
            throws Throwable {
        setupFor(CRYPTO_UPDATE_NO_NEW_KEY_CUSTOM_PAYER_PAID_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void reportsCryptoUpdateMissingAccount() throws Throwable {
        setupFor(CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO);
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forMissingAccount()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forMissingAccount();
    }

    @Test
    void reportsCryptoUpdateMissingAccountWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_MISSING_ACCOUNT_SCENARIO);
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forMissingAccount()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        verify(mockSummaryFactory).forMissingAccount();
    }

    @Test
    void getsCryptoDeleteNoTransferSigRequired() throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteNoTransferSigRequiredWithCustomPayer() throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteNoTransferSigRequiredSelfPaidWillReturnEmptyKeyList() throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_SELF_PAID_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsCryptoDeleteNoTransferSigRequiredSelfPaidWillReturnEmptyKeyListWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_SELF_PAID_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
        assertFalse(
                sanityRestored(summary.getOrderedKeys()).contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteNoTransferSigRequiredCustomPayerPaidWillReturnEmptyKeyList()
            throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_CUSTOM_PAYER_PAID_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteNoTransferSigRequiredCustomPayerPaidWillReturnEmptyKeyListWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_NO_TARGET_RECEIVER_SIG_CUSTOM_PAYER_PAID_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsCryptoDeleteMissingReceiverAccount() throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_MISSING_RECEIVER_SIG_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsCryptoDeleteMissingReceiverAccountWithCustomPayer() throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_MISSING_RECEIVER_SIG_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsCryptoDeleteMissingTarget() throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_MISSING_TARGET);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsCryptoDeleteMissingTargetWithCustomPayer() throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_MISSING_TARGET);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsCryptoDeleteTransferSigRequired() throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_TARGET_RECEIVER_SIG_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_ACCOUNT_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteTransferSigRequiredWithCustomPayer() throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_TARGET_RECEIVER_SIG_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_ACCOUNT_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteTransferSigRequiredPaidByReceiverReturnsJustAccountKey() throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_TARGET_RECEIVER_SIG_RECEIVER_PAID_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteTransferSigRequiredPaidByReceiverReturnsJustAccountKeyWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_TARGET_RECEIVER_SIG_RECEIVER_PAID_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteTransferSigRequiredSelfPaidReturnsJustReceiverKey() throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_TARGET_RECEIVER_SIG_SELF_PAID_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(RECEIVER_SIG_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteTransferSigRequiredSelfPaidReturnsJustReceiverKeyWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_TARGET_RECEIVER_SIG_SELF_PAID_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(DEFAULT_PAYER_KT.asKey(), RECEIVER_SIG_KT.asKey()));
        assertFalse(
                sanityRestored(summary.getOrderedKeys()).contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteTransferSigRequiredCustomPayerPaidReturnsJustReceiverKey()
            throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_TARGET_RECEIVER_SIG_CUSTOM_PAYER_PAID_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey(), RECEIVER_SIG_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsCryptoDeleteTransferSigRequiredCustomPayerPaidReturnsJustReceiverKeyWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(CRYPTO_DELETE_TARGET_RECEIVER_SIG_CUSTOM_PAYER_PAID_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(RECEIVER_SIG_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
        assertFalse(
                sanityRestored(summary.getOrderedKeys()).contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsFileCreate() throws Throwable {
        // given:
        setupFor(VANILLA_FILE_CREATE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_WACL_KT.asKey()));
    }

    @Test
    void getsFileAppend() throws Throwable {
        // given:
        setupFor(VANILLA_FILE_APPEND_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsFileAppendWithCustomPayer() throws Throwable {
        // given:
        setupFor(VANILLA_FILE_APPEND_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsFileAppendProtected() throws Throwable {
        // given:
        setupFor(SYSTEM_FILE_APPEND_WITH_PRIVILEGD_PAYER);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsFileAppendProtectedWithCustomPayer() throws Throwable {
        // given:
        setupFor(SYSTEM_FILE_APPEND_WITH_PRIVILEGD_PAYER);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(SYS_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsFileAppendProtectedPrivilegedAsCustomPayer() throws Throwable {
        // given:
        setupFor(SYSTEM_FILE_APPEND_WITH_PRIVILEGD_PAYER);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, asAccount(MASTER_PAYER_ID));

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsFileAppendImmutable() throws Throwable {
        // given:
        setupFor(IMMUTABLE_FILE_APPEND_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsFileAppendImmutableWithCustomPayer() throws Throwable {
        // given:
        setupFor(IMMUTABLE_FILE_APPEND_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsSysFileAppendByTreasury() throws Throwable {
        // given:
        setupFor(TREASURY_SYS_FILE_APPEND_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsSysFileAppendByTreasuryWithCustomPayer() throws Throwable {
        // given:
        setupFor(TREASURY_SYS_FILE_APPEND_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(SYS_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsSysFileAppendByTreasuryWithTreasuryAsCustomPayer() throws Throwable {
        // given:
        setupFor(TREASURY_SYS_FILE_APPEND_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(
                        txn, summaryFactory, null, asAccount(TREASURY_PAYER_ID));

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsSysFileAppendByMaster() throws Throwable {
        // given:
        setupFor(MASTER_SYS_FILE_APPEND_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsSysFileAppendByMasterWithCustomPayer() throws Throwable {
        // given:
        setupFor(MASTER_SYS_FILE_APPEND_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(SYS_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsSysFileAppendWithMasterAsCustomPayer() throws Throwable {
        // given:
        setupFor(MASTER_SYS_FILE_APPEND_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, asAccount(MASTER_PAYER_ID));

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsSysFileUpdateByMaster() throws Throwable {
        // given:
        setupFor(MASTER_SYS_FILE_UPDATE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsSysFileUpdateByMasterWithCustomPayer() throws Throwable {
        // given:
        setupFor(MASTER_SYS_FILE_UPDATE_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SYS_FILE_WACL_KT.asKey(), SIMPLE_NEW_WACL_KT.asKey()));
    }

    @Test
    void getsSysFileUpdateByMasterWithMasterAsCustomPayer() throws Throwable {
        // given:
        setupFor(MASTER_SYS_FILE_UPDATE_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, asAccount(MASTER_PAYER_ID));

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsSysFileUpdateByTreasury() throws Throwable {
        // given:
        setupFor(TREASURY_SYS_FILE_UPDATE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsSysFileUpdateByTreasuryWithCustomPayer() throws Throwable {
        // given:
        setupFor(TREASURY_SYS_FILE_UPDATE_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SYS_FILE_WACL_KT.asKey(), SIMPLE_NEW_WACL_KT.asKey()));
    }

    @Test
    void getsSysFileUpdateByTreasuryWithTreasuryAsCustomPayer() throws Throwable {
        // given:
        setupFor(TREASURY_SYS_FILE_UPDATE_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(
                        txn, summaryFactory, null, asAccount(TREASURY_PAYER_ID));

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void reportsMissingFile() throws Throwable {
        // given:
        setupFor(FILE_APPEND_MISSING_TARGET_SCENARIO);
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forMissingFile()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forMissingFile();
    }

    @Test
    void reportsMissingFileWithCustomPayer() throws Throwable {
        // given:
        setupFor(FILE_APPEND_MISSING_TARGET_SCENARIO);
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forMissingFile()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        verify(mockSummaryFactory).forMissingFile();
    }

    @Test
    void getsFileUpdateNoNewWacl() throws Throwable {
        // given:
        setupFor(VANILLA_FILE_UPDATE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsFileUpdateNoNewWaclWithCustomPayer() throws Throwable {
        // given:
        setupFor(VANILLA_FILE_UPDATE_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsTreasuryUpdateNoNewWacl() throws Throwable {
        // given:
        setupFor(TREASURY_SYS_FILE_UPDATE_SCENARIO_NO_NEW_KEY);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsTreasuryUpdateNoNewWaclWithCustomPayer() throws Throwable {
        // given:
        setupFor(TREASURY_SYS_FILE_UPDATE_SCENARIO_NO_NEW_KEY);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(SYS_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsTreasuryUpdateNoNewWaclWithTreasuryAsCustomPayer() throws Throwable {
        // given:
        setupFor(TREASURY_SYS_FILE_UPDATE_SCENARIO_NO_NEW_KEY);

        // when:
        final var summary =
                subject.keysForOtherParties(
                        txn, summaryFactory, null, asAccount(TREASURY_PAYER_ID));

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsFileUpdateImmutable() throws Throwable {
        // given:
        setupFor(IMMUTABLE_FILE_UPDATE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsFileUpdateImmutableWithCustomPayer() throws Throwable {
        // given:
        setupFor(IMMUTABLE_FILE_UPDATE_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsNonSystemFileUpdateNoNewWacl() throws Throwable {
        // given:
        setupFor(VANILLA_FILE_UPDATE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsNonSystemFileUpdateNoNewWaclWithCustomPayer() throws Throwable {
        // given:
        setupFor(VANILLA_FILE_UPDATE_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsFileUpdateNewWACL() throws Throwable {
        // given:
        setupFor(FILE_UPDATE_NEW_WACL_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_FILE_WACL_KT.asKey(), SIMPLE_NEW_WACL_KT.asKey()));
    }

    @Test
    void getsFileUpdateNewWACLWithCustomPayer() throws Throwable {
        // given:
        setupFor(FILE_UPDATE_NEW_WACL_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_FILE_WACL_KT.asKey(), SIMPLE_NEW_WACL_KT.asKey()));
    }

    @Test
    void getsFileUpdateNewWACLWithTreasuryAsCustomPayer() throws Throwable {
        // given:
        setupFor(FILE_UPDATE_NEW_WACL_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(
                        txn, summaryFactory, null, asAccount(TREASURY_PAYER_ID));

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_FILE_WACL_KT.asKey(), SIMPLE_NEW_WACL_KT.asKey()));
    }

    @Test
    void getsFileUpdateMissing() throws Throwable {
        // given:
        setupFor(FILE_UPDATE_MISSING_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_FILE_ID, summary.getErrorReport());
    }

    @Test
    void getsFileUpdateMissingWithCustomPayer() throws Throwable {
        // given:
        setupFor(FILE_UPDATE_MISSING_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_FILE_ID, summary.getErrorReport());
    }

    @Test
    void getsFileUpdateMissingWithTreasuryAsCustomPayer() throws Throwable {
        // given:
        setupFor(FILE_UPDATE_MISSING_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(
                        txn, summaryFactory, null, asAccount(TREASURY_PAYER_ID));

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_FILE_ID, summary.getErrorReport());
    }

    @Test
    void getsFileDelete() throws Throwable {
        // given:
        setupFor(VANILLA_FILE_DELETE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsFileDeleteProtected() throws Throwable {
        // given:
        setupFor(VANILLA_FILE_DELETE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_FILE_WACL_KT.asKey()));
    }

    @Test
    void getsFileDeleteImmutable() throws Throwable {
        // given:
        setupFor(IMMUTABLE_FILE_DELETE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsFileDeleteMissing() throws Throwable {
        // given:
        setupFor(MISSING_FILE_DELETE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsContractCreateNoAdminKey() throws Throwable {
        // given:
        setupFor(CONTRACT_CREATE_NO_ADMIN_KEY);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsContractDeleteImmutable() throws Throwable {
        // given:
        setupFor(CONTRACT_DELETE_IMMUTABLE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, summary.getErrorReport());
    }

    @Test
    void getsContractDeleteNonsense() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONTRACT_DELETE_IMMUTABLE_SCENARIO, NONSENSE_CONTRACT_DELETE_THROWING_LOOKUP);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_SIGNATURE, summary.getErrorReport());
    }

    @Test
    void getInvalidAutoRenewAccountDuringUpdate() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONTRACT_UPDATE_INVALID_AUTO_RENEW_SCENARIO, INVALID_AUTO_RENEW_ACCOUNT_EXC);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_AUTORENEW_ACCOUNT, summary.getErrorReport());
    }

    @Test
    void getsContractCreateDeprecatedAdminKey() throws Throwable {
        // given:
        setupFor(CONTRACT_CREATE_DEPRECATED_CID_ADMIN_KEY);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsContractCreateWithAdminKey() throws Throwable {
        // given:
        setupFor(CONTRACT_CREATE_WITH_ADMIN_KEY);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_ADMIN_KT.asKey()));
    }

    @Test
    void getsContractCreateWithAutoRenew() throws Throwable {
        // given:
        setupFor(CONTRACT_CREATE_WITH_AUTO_RENEW_ACCOUNT);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsContractUpdateWithAdminKey() throws Throwable {
        // given:
        setupFor(CONTRACT_UPDATE_WITH_NEW_ADMIN_KEY);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_ADMIN_KT.asKey(), SIMPLE_NEW_ADMIN_KT.asKey()));
    }

    @Test
    void getsContractUpdateNewExpirationTimeOnly() throws Throwable {
        // given:
        setupFor(CONTRACT_UPDATE_EXPIRATION_ONLY_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsContractUpdateWithDeprecatedAdminKey() throws Throwable {
        // given:
        setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_DEPRECATED_CID_ADMIN_KEY_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsContractUpdateNewExpirationTimeAndAdminKey() throws Throwable {
        // given:
        setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_ADMIN_KEY_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_ADMIN_KT.asKey(), SIMPLE_NEW_ADMIN_KT.asKey()));
    }

    @Test
    void getsContractUpdateNewExpirationTimeAndProxy() throws Throwable {
        // given:
        setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_PROXY_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
    }

    @Test
    void getsContractUpdateNewExpirationTimeAndAutoRenew() throws Throwable {
        // given:
        setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_AUTORENEW_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
    }

    @Test
    void getsContractUpdateNewExpirationTimeAndFile() throws Throwable {
        // given:
        setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_FILE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
    }

    @Test
    void getsContractUpdateNewExpirationTimeAndMemo() throws Throwable {
        // given:
        setupFor(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ADMIN_KT.asKey()));
    }

    @Test
    void getsContractUpdateNewAutoRenewAccount() throws Throwable {
        // given:
        setupFor(CONTRACT_UPDATE_NEW_AUTO_RENEW_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void reportsInvalidContract() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO, INVALID_CONTRACT_THROWING_LOOKUP);
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forInvalidContract()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forInvalidContract();
    }

    @Test
    void reportsImmutableContract() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO, IMMUTABLE_CONTRACT_THROWING_LOOKUP);
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forInvalidContract()).willReturn(result);

        // when:
        var summary = subject.keysForOtherParties(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forInvalidContract();
    }

    @Test
    void getsContractDelete() throws Throwable {
        // given:
        setupFor(CONTRACT_DELETE_XFER_ACCOUNT_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_ADMIN_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsContractDeleteMissingAccountBeneficiary() throws Throwable {
        // given:
        setupFor(CONTRACT_DELETE_MISSING_ACCOUNT_BENEFICIARY_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsContractDeleteMissingContractBeneficiary() throws Throwable {
        // given:
        setupFor(CONTRACT_DELETE_MISSING_CONTRACT_BENEFICIARY_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_CONTRACT_ID, summary.getErrorReport());
    }

    @Test
    void getsContractDeleteContractXfer() throws Throwable {
        // given:
        setupFor(CONTRACT_DELETE_XFER_CONTRACT_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_ADMIN_KT.asKey(), DILIGENT_SIGNING_PAYER_KT.asKey()));
    }

    @Test
    void getsSystemDelete() throws Throwable {
        // given:
        setupFor(SYSTEM_DELETE_FILE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsSystemUndelete() throws Throwable {
        // given:
        setupFor(SYSTEM_UNDELETE_FILE_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsConsensusCreateTopicNoAdminKeyOrAutoRenewAccount() throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO);

        // when:
        final var summary = subject.keysForPayer(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsConsensusCreateTopicNoAdminKeyOrAutoRenewAccountWithCustomPayer() throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_NO_ADDITIONAL_KEYS_SCENARIO);

        // when:
        final var summary = subject.keysForPayer(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsConsensusCreateTopicAdminKey() throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(SIMPLE_TOPIC_ADMIN_KEY.asKey()));
    }

    @Test
    void getsConsensusCreateTopicAdminKeyWithCustomPayer() throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(SIMPLE_TOPIC_ADMIN_KEY.asKey()));
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccount() throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SIMPLE_TOPIC_ADMIN_KEY.asKey(), MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccountWithCustomPayer() throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SIMPLE_TOPIC_ADMIN_KEY.asKey(), MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccountAsPayer() throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(SIMPLE_TOPIC_ADMIN_KEY.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccountAsPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SIMPLE_TOPIC_ADMIN_KEY.asKey(), DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccountAsCustomPayer() throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO);

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SIMPLE_TOPIC_ADMIN_KEY.asKey(), CUSTOM_PAYER_ACCOUNT_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsConsensusCreateTopicAdminKeyAndAutoRenewAccountAsCustomPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO);

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(SIMPLE_TOPIC_ADMIN_KEY.asKey()));
        assertFalse(
                sanityRestored(summary.getOrderedKeys()).contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void invalidAutoRenewAccountOnConsensusCreateTopicThrows() throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO);
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forInvalidAutoRenewAccount()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forInvalidAutoRenewAccount();
    }

    @Test
    void invalidAutoRenewAccountOnConsensusCreateTopicThrowsWithCustomPayer() throws Throwable {
        // given:
        setupFor(CONSENSUS_CREATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO);
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forInvalidAutoRenewAccount()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        verify(mockSummaryFactory).forInvalidAutoRenewAccount();
    }

    @Test
    void getsConsensusSubmitMessageNoSubmitKey() throws Throwable {
        // given:
        setupForNonStdLookup(CONSENSUS_SUBMIT_MESSAGE_SCENARIO, hcsMetadataLookup(null, null));

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsConsensusSubmitMessageWithSubmitKey() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_SUBMIT_MESSAGE_SCENARIO,
                hcsMetadataLookup(null, MISC_TOPIC_SUBMIT_KT.asJKey()));

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_SUBMIT_KT.asKey()));
    }

    @Test
    void reportsConsensusSubmitMessageMissingTopic() throws Throwable {
        // given:
        setupFor(CONSENSUS_SUBMIT_MESSAGE_MISSING_TOPIC_SCENARIO);
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forMissingTopic()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forMissingTopic();
    }

    @Test
    void getsConsensusDeleteTopicNoAdminKey() throws Throwable {
        // given:
        setupForNonStdLookup(CONSENSUS_DELETE_TOPIC_SCENARIO, hcsMetadataLookup(null, null));

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsConsensusDeleteTopicWithAdminKey() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_DELETE_TOPIC_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KT.asKey()));
    }

    @Test
    void reportsConsensusDeleteTopicMissingTopic() throws Throwable {
        // given:
        setupFor(CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO);
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forMissingTopic()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forMissingTopic();
    }

    @Test
    void getsConsensusUpdateTopicNoAdminKey() throws Throwable {
        // given:
        setupForNonStdLookup(CONSENSUS_UPDATE_TOPIC_SCENARIO, hcsMetadataLookup(null, null));

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsConsensusUpdateTopicNoAdminKeyWithCustomPayer() throws Throwable {
        // given:
        setupForNonStdLookup(CONSENSUS_UPDATE_TOPIC_SCENARIO, hcsMetadataLookup(null, null));

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsConsensusUpdateTopicWithExistingAdminKey() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KT.asKey()));
    }

    @Test
    void getsConsensusUpdateTopicWithExistingAdminKeyWithCustomPayer() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_TOPIC_ADMIN_KT.asKey()));
    }

    @Test
    void getsConsensusUpdateTopicExpiryOnly() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_EXPIRY_ONLY_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsConsensusUpdateTopicExpiryOnlyWithCustomPayer() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_EXPIRY_ONLY_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void reportsConsensusUpdateTopicMissingTopic() throws Throwable {
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_MISSING_TOPIC_SCENARIO, hcsMetadataLookup(null, null));
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forMissingTopic()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forMissingTopic();
    }

    @Test
    void reportsConsensusUpdateTopicMissingTopicWithCustomPayer() throws Throwable {
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_MISSING_TOPIC_SCENARIO, hcsMetadataLookup(null, null));
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forMissingTopic()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        verify(mockSummaryFactory).forMissingTopic();
    }

    @Test
    void invalidAutoRenewAccountOnConsensusUpdateTopicThrows() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO,
                hcsMetadataLookup(null, null));
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forInvalidAutoRenewAccount()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory);

        // then:
        verify(mockSummaryFactory).forInvalidAutoRenewAccount();
    }

    @Test
    void invalidAutoRenewAccountOnConsensusUpdateTopicThrowsWithCustomPayer() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_MISSING_AUTORENEW_ACCOUNT_SCENARIO,
                hcsMetadataLookup(null, null));
        // and:
        mockSummaryFactory();
        // and:
        SigningOrderResult<ResponseCodeEnum> result = mock(SigningOrderResult.class);

        given(mockSummaryFactory.forInvalidAutoRenewAccount()).willReturn(result);

        // when:
        subject.keysForOtherParties(txn, mockSummaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        verify(mockSummaryFactory).forInvalidAutoRenewAccount();
    }

    @Test
    void getsConsensusUpdateTopicNewAdminKey() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_TOPIC_ADMIN_KT.asKey(), UPDATE_TOPIC_ADMIN_KT.asKey()));
    }

    @Test
    void getsConsensusUpdateTopicNewAdminKeyWithCustomPayer() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_TOPIC_ADMIN_KT.asKey(), UPDATE_TOPIC_ADMIN_KT.asKey()));
    }

    @Test
    void getsConsensusUpdateTopicNewAdminKeyAndAutoRenewAccount() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        MISC_TOPIC_ADMIN_KT.asKey(),
                        UPDATE_TOPIC_ADMIN_KT.asKey(),
                        MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsConsensusUpdateTopicNewAdminKeyAndAutoRenewAccountWithCustomPayer() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        MISC_TOPIC_ADMIN_KT.asKey(),
                        UPDATE_TOPIC_ADMIN_KT.asKey(),
                        MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsConsensusUpdateTopicNewAdminKeyAndAutoRenewAccountAsPayer() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_TOPIC_ADMIN_KT.asKey(), UPDATE_TOPIC_ADMIN_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsConsensusUpdateTopicNewAdminKeyAndAutoRenewAccountAsPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_PAYER_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_AUTORENEW_ACCOUNT, summary.getErrorReport());
        assertEquals(0, summary.getOrderedKeys().size());
    }

    @Test
    void getsConsensusUpdateTopicNewAdminKeyAndAutoRenewAccountAsCustomPayer() throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_AUTORENEW_ACCOUNT, summary.getErrorReport());
        assertEquals(0, summary.getOrderedKeys().size());
    }

    @Test
    void getsConsensusUpdateTopicNewAdminKeyAndAutoRenewAccountAsCustomPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupForNonStdLookup(
                CONSENSUS_UPDATE_TOPIC_NEW_ADMIN_KEY_AND_AUTORENEW_ACCOUNT_AS_CUSTOM_PAYER_SCENARIO,
                hcsMetadataLookup(MISC_TOPIC_ADMIN_KT.asJKey(), null));

        // when:
        final var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_TOPIC_ADMIN_KT.asKey(), UPDATE_TOPIC_ADMIN_KT.asKey()));
        assertFalse(
                sanityRestored(summary.getOrderedKeys()).contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsTokenCreateAdminKeyOnly() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ADMIN_ONLY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsTokenCreateAdminKeyOnlyWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ADMIN_ONLY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsTokenCreateMissingTreasury() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_MISSING_TREASURY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsTokenCreateMissingTreasuryWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_MISSING_TREASURY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsTokenCreateTreasuryAsPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_TREASURY_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsTokenCreateTreasuryAsPayerWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_TREASURY_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsTokenCreateTreasuryAsCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_TREASURY_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsTokenCreateTreasuryAsCustomPayerWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_TREASURY_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsTokenUpdateMissingAutoRenew() throws Throwable {
        // given:
        setupFor(TOKEN_UPDATE_WITH_MISSING_AUTO_RENEW_ACCOUNT);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_AUTORENEW_ACCOUNT, summary.getErrorReport());
    }

    @Test
    void getsTokenUpdateMissingAutoRenewWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_UPDATE_WITH_MISSING_AUTO_RENEW_ACCOUNT);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_AUTORENEW_ACCOUNT, summary.getErrorReport());
    }

    @Test
    void getsTokenCreateAdminAndFreeze() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ADMIN_AND_FREEZE);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsTokenCreateAdminAndFreezeWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ADMIN_AND_FREEZE);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomFixedFeeNoCollectorSigReq() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_TREASURY_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomFixedFeeNoCollectorSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_TREASURY_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomFixedFeeNoCollectorSigReqButDenomWildcard() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomFixedFeeNoCollectorSigReqButDenomWildcardWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomFixedFeeAndCollectorSigReq() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomFixedFeeAndCollectorSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomFixedFeeAndCollectorSigReqAndAsPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_TREASURY_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomFixedFeeAndCollectorSigReqAndAsPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_TREASURY_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomRoyaltyFeeNoFallbackAndNoCollectorSigReq() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_TREASURY_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomRoyaltyFeeNoFallbackAndNoCollectorSigReqWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_TREASURY_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomRoyaltyFeeNoFallbackButSigReq() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomRoyaltyFeeNoFallbackButSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomRoyaltyFeeFallbackNoWildcardButSigReq() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomRoyaltyFeeFallbackNoWildcardButSigReqWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomRoyaltyFeeFallbackWildcardNoSigReq() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomRoyaltyFeeFallbackWildcardNoSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomFractionalFeeNoCollectorSigReq() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomFractionalFeeNoCollectorSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), NO_RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenCreateCustomFeeAndCollectorMissing() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_MISSING_COLLECTOR);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_CUSTOM_FEE_COLLECTOR, summary.getErrorReport());
    }

    @Test
    void getsTokenCreateCustomFeeAndCollectorMissingWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_MISSING_COLLECTOR);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_CUSTOM_FEE_COLLECTOR, summary.getErrorReport());
    }

    @Test
    void getsTokenCreateMissingAdmin() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_MISSING_ADMIN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_TREASURY_KT.asKey()));
    }

    @Test
    void getsTokenCreateMissingAdminWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_MISSING_ADMIN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_TREASURY_KT.asKey()));
    }

    @Test
    void getsTokenTransactAllSenders() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_EXTANT_SENDERS);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(SECOND_TOKEN_SENDER_KT.asKey()));
        assertFalse(sanityRestored(summary.getOrderedKeys()).contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsTokenTransactAllSendersWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_EXTANT_SENDERS);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(DEFAULT_PAYER_KT.asKey(), SECOND_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsTokenTransactMovingHbarsReceiverSigReq() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_MOVING_HBARS_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(FIRST_TOKEN_SENDER_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenTransactMovingHbarsReceiverSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_MOVING_HBARS_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(FIRST_TOKEN_SENDER_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenTransactMovingHbars() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_MOVING_HBARS_WITH_EXTANT_SENDER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsTokenTransactMovingHbarsWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_MOVING_HBARS_WITH_EXTANT_SENDER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(FIRST_TOKEN_SENDER_KT.asKey()));
    }

    @Test
    void getsTokenTransactMissingSenders() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_MISSING_SENDERS);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsTokenTransactMissingSendersWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_MISSING_SENDERS);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsTokenTransactWithReceiverSigReq() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        FIRST_TOKEN_SENDER_KT.asKey(),
                        SECOND_TOKEN_SENDER_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenTransactWithReceiverSigReqWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_TRANSACT_WITH_RECEIVER_SIG_REQ_AND_EXTANT_SENDERS);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        FIRST_TOKEN_SENDER_KT.asKey(),
                        SECOND_TOKEN_SENDER_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsAssociateWithKnownTarget() throws Throwable {
        // given:
        setupFor(TOKEN_ASSOCIATE_WITH_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsAssociateWithKnownTargetWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_ASSOCIATE_WITH_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsAssociateWithImmutableTarget() throws Throwable {
        setupFor(TOKEN_ASSOCIATE_WITH_IMMUTABLE_TARGET);

        var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsAssociateWithImmutableTargetWithCustomPayer() throws Throwable {
        setupFor(TOKEN_ASSOCIATE_WITH_IMMUTABLE_TARGET);

        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsCryptoUpdateWithImmutableTarget() throws Throwable {
        setupFor(CRYPTO_UPDATE_IMMUTABLE_ACCOUNT_SCENARIO);

        var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsCryptoUpdateWithImmutableTargetWithCustomPayer() throws Throwable {
        setupFor(CRYPTO_UPDATE_IMMUTABLE_ACCOUNT_SCENARIO);

        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsAssociateWithSelfPaidKnownTargetGivesEmptyKeyList() throws Throwable {
        // given:
        setupFor(TOKEN_ASSOCIATE_WITH_SELF_PAID_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsAssociateWithSelfPaidKnownTargetGivesEmptyKeyListWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_ASSOCIATE_WITH_SELF_PAID_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsAssociateWithCustomPayerPaidKnownTargetGivesEmptyKeyList() throws Throwable {
        // given:
        setupFor(TOKEN_ASSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsAssociateWithCustomPayerPaidKnownTargetGivesEmptyKeyListWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(TOKEN_ASSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsAssociateWithMissingTarget() throws Throwable {
        // given:
        setupFor(TOKEN_ASSOCIATE_WITH_MISSING_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsAssociateWithMissingTargetWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_ASSOCIATE_WITH_MISSING_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsDissociateWithKnownTarget() throws Throwable {
        // given:
        setupFor(TOKEN_DISSOCIATE_WITH_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsDissociateWithKnownTargetWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_DISSOCIATE_WITH_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsDissociateWithSelfPaidKnownTargetGivesEmptyKeyList() throws Throwable {
        // given:
        setupFor(TOKEN_DISSOCIATE_WITH_SELF_PAID_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsDissociateWithSelfPaidKnownTargetGivesEmptyKeyListWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_DISSOCIATE_WITH_SELF_PAID_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsDissociateWithCustomPayerPaidKnownTargetGivesEmptyKeyList() throws Throwable {
        // given:
        setupFor(TOKEN_DISSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsDissociateWithCustomPayerPaidKnownTargetGivesEmptyKeyListWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(TOKEN_DISSOCIATE_WITH_CUSTOM_PAYER_PAID_KNOWN_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsDissociateWithMissingTarget() throws Throwable {
        // given:
        setupFor(TOKEN_DISSOCIATE_WITH_MISSING_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsDissociateWithMissingTargetWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_DISSOCIATE_WITH_MISSING_TARGET);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsTokenPauseWithExtantPausable() throws Throwable {
        setupFor(VALID_PAUSE_WITH_EXTANT_TOKEN);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_PAUSE_KT.asKey()));
    }

    @Test
    void getsTokenUnpauseWithExtantPausable() throws Throwable {
        setupFor(VALID_UNPAUSE_WITH_EXTANT_TOKEN);

        final var summary = subject.keysForOtherParties(txn, summaryFactory);

        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_PAUSE_KT.asKey()));
    }

    @Test
    void getsTokenFreezeWithExtantFreezable() throws Throwable {
        // given:
        setupFor(VALID_FREEZE_WITH_EXTANT_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_FREEZE_KT.asKey()));
    }

    @Test
    void getsTokenUnfreezeWithExtantFreezable() throws Throwable {
        // given:
        setupFor(VALID_UNFREEZE_WITH_EXTANT_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_FREEZE_KT.asKey()));
    }

    @Test
    void getsTokenGrantKycWithExtantFreezable() throws Throwable {
        // given:
        setupFor(VALID_GRANT_WITH_EXTANT_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_KYC_KT.asKey()));
    }

    @Test
    void getsTokenRevokeKycWithExtantFreezable() throws Throwable {
        // given:
        setupFor(VALID_REVOKE_WITH_EXTANT_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_KYC_KT.asKey()));
    }

    @Test
    void getsTokenRevokeKycWithMissingToken() throws Throwable {
        // given:
        setupFor(REVOKE_WITH_MISSING_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, summary.getErrorReport());
    }

    @Test
    void getsTokenRevokeKycWithoutKyc() throws Throwable {
        // given:
        setupFor(REVOKE_FOR_TOKEN_WITHOUT_KYC);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsTokenMintWithValidId() throws Throwable {
        // given:
        setupFor(MINT_WITH_SUPPLY_KEYED_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_SUPPLY_KT.asKey()));
    }

    @Test
    void getsTokenBurnWithValidId() throws Throwable {
        // given:
        setupFor(BURN_WITH_SUPPLY_KEYED_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_SUPPLY_KT.asKey()));
    }

    @Test
    void getsTokenDeletionWithValidId() throws Throwable {
        // given:
        setupFor(DELETE_WITH_KNOWN_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsTokenDeletionWithMissingToken() throws Throwable {
        // given:
        setupFor(DELETE_WITH_MISSING_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, summary.getErrorReport());
    }

    @Test
    void getsTokenDeletionWithNoAdminKey() throws Throwable {
        // given:
        setupFor(DELETE_WITH_MISSING_TOKEN_ADMIN_KEY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsTokenWipeWithRelevantKey() throws Throwable {
        // given:
        setupFor(VALID_WIPE_WITH_EXTANT_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_WIPE_KT.asKey()));
    }

    @Test
    void getsUpdateNoSpecialKeys() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_NO_KEYS_AFFECTED);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateNoSpecialKeysWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_NO_KEYS_AFFECTED);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateWithWipe() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_WIPE_KEYED_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateWithWipeWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_WIPE_KEYED_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateWithSupply() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_SUPPLY_KEYED_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateWithSupplyWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_SUPPLY_KEYED_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateWithKyc() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_KYC_KEYED_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateWithKycWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_KYC_KEYED_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateWithMissingTreasury() throws Throwable {
        // given:
        setupFor(UPDATE_REPLACING_WITH_MISSING_TREASURY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsUpdateWithMissingTreasuryWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_REPLACING_WITH_MISSING_TREASURY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsUpdateWithNewTreasury() throws Throwable {
        // given:
        setupFor(UPDATE_REPLACING_TREASURY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_ADMIN_KT.asKey(), TOKEN_TREASURY_KT.asKey()));
    }

    @Test
    void getsUpdateWithNewTreasuryWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_REPLACING_TREASURY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_ADMIN_KT.asKey(), TOKEN_TREASURY_KT.asKey()));
    }

    @Test
    void getsUpdateWithNewTreasuryAsPayer() throws Throwable {
        // given:
        setupFor(UPDATE_REPLACING_TREASURY_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateWithNewTreasuryAsPayerWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_REPLACING_TREASURY_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_ADMIN_KT.asKey(), DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsUpdateWithNewTreasuryAsCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_REPLACING_TREASURY_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_ADMIN_KT.asKey(), CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsUpdateWithNewTreasuryAsPayerWithCustomCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_REPLACING_TREASURY_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateWithFreeze() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_FREEZE_KEYED_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateWithFreezeWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_FREEZE_KEYED_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsUpdateReplacingAdmin() throws Throwable {
        // given:
        setupFor(UPDATE_REPLACING_ADMIN_KEY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_ADMIN_KT.asKey(), TOKEN_REPLACE_KT.asKey()));
    }

    @Test
    void getsUpdateReplacingAdminWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_REPLACING_ADMIN_KEY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_ADMIN_KT.asKey(), TOKEN_REPLACE_KT.asKey()));
    }

    @Test
    void getsTokenUpdateWithMissingToken() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_MISSING_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, summary.getErrorReport());
    }

    @Test
    void getsTokenUpdateWithMissingTokenWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_MISSING_TOKEN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, summary.getErrorReport());
    }

    @Test
    void getsTokenUpdateWithNoAdminKey() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_MISSING_TOKEN_ADMIN_KEY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsTokenUpdateWithNoAdminKeyWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_WITH_MISSING_TOKEN_ADMIN_KEY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsTokenCreateWithAutoRenew() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_AUTO_RENEW);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsTokenCreateWithAutoRenewWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_AUTO_RENEW);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsTokenCreateWithAutoRenewAsPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_TREASURY_KT.asKey()));
    }

    @Test
    void getsTokenCreateWithAutoRenewAsPayerWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsTokenCreateWithAutoRenewAsCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_AUTO_RENEW_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_TREASURY_KT.asKey(), CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsTokenCreateWithAutoRenewAsCustomPayerWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_AUTO_RENEW_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_TREASURY_KT.asKey()));
    }

    @Test
    void getsTokenCreateWithMissingAutoRenew() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        // and:
        assertEquals(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT, summary.getErrorReport());
    }

    @Test
    void getsTokenCreateWithMissingAutoRenewWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        // and:
        assertEquals(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT, summary.getErrorReport());
    }

    @Test
    void getsTokenUpdateWithAutoRenew() throws Throwable {
        // given:
        setupFor(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_ADMIN_KT.asKey(), MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsTokenUpdateWithAutoRenewWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_ADMIN_KT.asKey(), MISC_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsTokenUpdateWithAutoRenewAsPayer() throws Throwable {
        // given:
        setupFor(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsTokenUpdateWithAutoRenewAsPayerWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_ADMIN_KT.asKey(), DEFAULT_PAYER_KT.asKey()));
    }

    @Test
    void getsTokenUpdateWithAutoRenewAsCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_ADMIN_KT.asKey(), CUSTOM_PAYER_ACCOUNT_KT.asKey()));
    }

    @Test
    void getsTokenUpdateWithAutoRenewAsCustomPayerWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_UPDATE_WITH_NEW_AUTO_RENEW_ACCOUNT_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(TOKEN_ADMIN_KT.asKey()));
    }

    @Test
    void getsTokenFeeScheduleUpdateWithMissingFeeScheduleKey() throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_NO_FEE_SCHEDULE_KEY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsTokenFeeScheduleUpdateWithMissingFeeScheduleKeyWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_NO_FEE_SCHEDULE_KEY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(sanityRestored(summary.getOrderedKeys()).isEmpty());
    }

    @Test
    void getsTokenFeeScheduleUpdateWithMissingToken() throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_FEE_SCHEDULE_BUT_TOKEN_DOESNT_EXIST);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, summary.getErrorReport());
    }

    @Test
    void getsTokenFeeScheduleUpdateWithMissingTokenWithCustomPayer() throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_FEE_SCHEDULE_BUT_TOKEN_DOESNT_EXIST);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, summary.getErrorReport());
    }

    @Test
    void getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorSigReq() throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_MISSING_FEE_COLLECTOR);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR, summary.getErrorReport());
    }

    @Test
    void getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorSigReqWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_MISSING_FEE_COLLECTOR);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR, summary.getErrorReport());
    }

    @Test
    void getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorWithReceiverSigReqOn()
            throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_FEE_SCHEDULE_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void
            getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorWithReceiverSigReqOnWithCustomPayer()
                    throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_FEE_SCHEDULE_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void
            getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorWithReceiverSigReqOnWithCustomPayerAsReceiver()
                    throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, RECEIVER_SIG);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(TOKEN_FEE_SCHEDULE_KT.asKey()));
    }

    @Test
    void getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorWithReceiverSigReqOff()
            throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_NO_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(TOKEN_FEE_SCHEDULE_KT.asKey()));
    }

    @Test
    void
            getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorWithReceiverSigReqOffWithCustomPayer()
                    throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_NO_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(TOKEN_FEE_SCHEDULE_KT.asKey()));
    }

    @Test
    void
            getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorWithReceiverSigReqOffWithCustomPayerAsReceiver()
                    throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_NO_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, NO_RECEIVER_SIG);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(TOKEN_FEE_SCHEDULE_KT.asKey()));
    }

    @Test
    void getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorWithReceiverSigReqON()
            throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_FEE_SCHEDULE_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void
            getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorWithReceiverSigReqONWithCustomPayer()
                    throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_FEE_SCHEDULE_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorAsPayerSigReq()
            throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(TOKEN_FEE_SCHEDULE_KT.asKey()));
    }

    @Test
    void getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorAsPayerSigReqWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(TOKEN_FEE_SCHEDULE_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorAsPayer() throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_NO_SIG_REQ_AND_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(TOKEN_FEE_SCHEDULE_KT.asKey()));
    }

    @Test
    void getsTokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorAsPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_NO_SIG_REQ_AND_AS_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(
                sanityRestored(summary.getOrderedKeys()), contains(TOKEN_FEE_SCHEDULE_KT.asKey()));
    }

    @Test
    void getsTokenUpdateWithMissingAutoRenew() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        // and:
        assertEquals(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT, summary.getErrorReport());
    }

    @Test
    void getsTokenUpdateWithMissingAutoRenewWithCustomPayer() throws Throwable {
        // given:
        setupFor(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        // and:
        assertEquals(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateInvalidXfer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_INVALID_XFER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateInvalidXferWithCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_INVALID_XFER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateXferNoAdmin() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_NO_ADMIN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_ACCOUNT_KT.asKey(), RECEIVER_SIG_KT.asKey()));
        // and:
        assertTrue(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateXferNoAdminWithCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_NO_ADMIN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithAdmin() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        SCHEDULE_ADMIN_KT.asKey(),
                        MISC_ACCOUNT_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateWithAdminWithCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithMissingDesignatedPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_MISSING_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithMissingDesignatedPayerWithCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_MISSING_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithNonsense() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_NONSENSE);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.hasErrorReport());
        assertEquals(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithNonsenseWithCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_NONSENSE);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithAdminAndDesignatedPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(4));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        SCHEDULE_ADMIN_KT.asKey(),
                        DILIGENT_SIGNING_PAYER_KT.asKey(),
                        MISC_ACCOUNT_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(3).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateWithAdminAndDesignatedPayerWithCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithAdminAndDesignatedPayerAsSelf() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER_AS_SELF);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(4));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        SCHEDULE_ADMIN_KT.asKey(),
                        DEFAULT_PAYER_KT.asKey(),
                        MISC_ACCOUNT_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(3).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateWithAdminAndDesignatedPayerAsSelfWithCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER_AS_SELF);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithAdminSenderAndDesignatedPayerAsSelf() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AND_PAYER_AS_SELF);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        SCHEDULE_ADMIN_KT.asKey(),
                        DEFAULT_PAYER_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateWithAdminSenderAndDesignatedPayerAsSelfWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AND_PAYER_AS_SELF);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithAdminSenderAsSelfAndDesignatedPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AS_SELF_AND_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(4));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        SCHEDULE_ADMIN_KT.asKey(),
                        DILIGENT_SIGNING_PAYER_KT.asKey(),
                        DEFAULT_PAYER_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateWithAdminSenderAsSelfAndDesignatedPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AS_SELF_AND_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithAdminSenderAsCustomPayerAndDesignatedPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AS_CUSTOM_PAYER_AND_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(4));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        SCHEDULE_ADMIN_KT.asKey(),
                        DILIGENT_SIGNING_PAYER_KT.asKey(),
                        CUSTOM_PAYER_ACCOUNT_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateWithAdminSenderAsCustomPayerAndDesignatedPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AS_CUSTOM_PAYER_AND_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithAdminAndDesignatedPayerAsCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(4));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        SCHEDULE_ADMIN_KT.asKey(),
                        CUSTOM_PAYER_ACCOUNT_KT.asKey(),
                        MISC_ACCOUNT_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateWithAdminAndDesignatedPayerAsCustomPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_AND_PAYER_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithAdminSenderAndDesignatedPayerAsCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AND_PAYER_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        SCHEDULE_ADMIN_KT.asKey(),
                        CUSTOM_PAYER_ACCOUNT_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateWithAdminSenderAndDesignatedPayerAsCustomPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_SENDER_AND_PAYER_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithAdminSelfSenderAndDesignatedPayerAsCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_SELF_SENDER_AND_PAYER_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(4));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        SCHEDULE_ADMIN_KT.asKey(),
                        CUSTOM_PAYER_ACCOUNT_KT.asKey(),
                        DEFAULT_PAYER_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(3).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateWithAdminSelfSenderAndDesignatedPayerAsCustomPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_SELF_SENDER_AND_PAYER_AS_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateWithAdminCustomPayerSenderAndDesignatedPayerAsSelf() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_CUSTOM_PAYER_SENDER_AND_PAYER_AS_SELF);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(4));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        SCHEDULE_ADMIN_KT.asKey(),
                        DEFAULT_PAYER_KT.asKey(),
                        CUSTOM_PAYER_ACCOUNT_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(3).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateWithAdminCustomPayerSenderAndDesignatedPayerAsSelfWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_XFER_WITH_ADMIN_CUSTOM_PAYER_SENDER_AND_PAYER_AS_SELF);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateTreasuryUpdateWithTreasuryAsCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_TREASURY_UPDATE_WITH_TREASURY_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        SCHEDULE_ADMIN_KT.asKey(),
                        DEFAULT_PAYER_KT.asKey(), // treasury
                        NEW_ACCOUNT_KT.asKey()));
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateTreasuryUpdateWithTreasuryAsCustomPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_TREASURY_UPDATE_WITH_TREASURY_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateTreasuryUpdateWithTreasuryAsCustomPayerWithTreasuryAsCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_TREASURY_UPDATE_WITH_TREASURY_CUSTOM_PAYER);

        // when:
        var summary =
                subject.keysForOtherParties(
                        txn, summaryFactory, null, asAccount(TREASURY_PAYER_ID));

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateSysAccountUpdateWithPrivilegedCustomPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SCHEDULE_ADMIN_KT.asKey(), DEFAULT_PAYER_KT.asKey())); // master payer
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateSysAccountUpdateWithPrivilegedCustomPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateSysAccountUpdateWithPrivilegedCustomPayerWithMasterPayerAsCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER);

        // when:
        var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, asAccount(MASTER_PAYER_ID));

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleCreateSysAccountUpdateWithPrivilegedCustomPayerAndRegularPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER_AND_REGULAR_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SCHEDULE_ADMIN_KT.asKey(), DEFAULT_PAYER_KT.asKey())); // master payer key
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
    }

    @Test
    void getsScheduleCreateSysAccountUpdateWithPrivilegedCustomPayerAndRegularPayerWithCustomPayer()
            throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER_AND_REGULAR_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory, null, CUSTOM_PAYER_ACCOUNT);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertTrue(summary.hasErrorReport());
        assertEquals(INVALID_ACCOUNT_ID, summary.getErrorReport());
    }

    @Test
    void
            getsScheduleCreateSysAccountUpdateWithPrivilegedCustomPayerAndRegularPayerWithMasterPayerAsCustomPayer()
                    throws Throwable {
        // given:
        setupFor(SCHEDULE_CREATE_SYS_ACCOUNT_UPDATE_WITH_PRIVILEGED_CUSTOM_PAYER_AND_REGULAR_PAYER);

        // when:
        var summary =
                subject.keysForOtherParties(txn, summaryFactory, null, asAccount(MASTER_PAYER_ID));

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(SCHEDULE_ADMIN_KT.asKey(), DEFAULT_PAYER_KT.asKey())); // mater payer
        // and:
        assertFalse(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
    }

    @Test
    void getsScheduleSignKnownScheduleWithPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_SIGN_KNOWN_SCHEDULE_WITH_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        DILIGENT_SIGNING_PAYER_KT.asKey(),
                        MISC_ACCOUNT_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertTrue(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
    }

    @Test
    void getsScheduleSignKnownScheduleWithPayerSelf() throws Throwable {
        // given:
        setupFor(SCHEDULE_SIGN_KNOWN_SCHEDULE_WITH_PAYER_SELF);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(3));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(
                        DEFAULT_PAYER_KT.asKey(),
                        MISC_ACCOUNT_KT.asKey(),
                        RECEIVER_SIG_KT.asKey()));
        // and:
        assertTrue(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(2).isForScheduledTxn());
    }

    @Test
    void getsScheduleSignKnownScheduleWithNowInvalidPayer() throws Throwable {
        // given:
        setupFor(SCHEDULE_SIGN_KNOWN_SCHEDULE_WITH_NOW_INVALID_PAYER);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ACCOUNT_ID_DOES_NOT_EXIST, summary.getErrorReport());
    }

    @Test
    void getsScheduleSignKnownSchedule() throws Throwable {
        // given:
        setupFor(SCHEDULE_SIGN_KNOWN_SCHEDULE);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(2));
        assertThat(
                sanityRestored(summary.getOrderedKeys()),
                contains(MISC_ACCOUNT_KT.asKey(), RECEIVER_SIG_KT.asKey()));
        // and:
        assertTrue(summary.getOrderedKeys().get(0).isForScheduledTxn());
        assertTrue(summary.getOrderedKeys().get(1).isForScheduledTxn());
    }

    @Test
    void getsScheduleSignWithMissingSchedule() throws Throwable {
        // given:
        setupFor(SCHEDULE_SIGN_MISSING_SCHEDULE);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(ResponseCodeEnum.INVALID_SCHEDULE_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleDeleteWithMissingSchedule() throws Throwable {
        // given:
        setupFor(SCHEDULE_DELETE_WITH_MISSING_SCHEDULE);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
        assertEquals(INVALID_SCHEDULE_ID, summary.getErrorReport());
    }

    @Test
    void getsScheduleDeleteWithMissingAdminKey() throws Throwable {
        // given:
        setupFor(SCHEDULE_DELETE_WITH_MISSING_SCHEDULE_ADMIN_KEY);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);

        // then:
        assertTrue(summary.getOrderedKeys().isEmpty());
    }

    @Test
    void getsScheduleDeleteKnownSchedule() throws Throwable {
        // given:
        setupFor(SCHEDULE_DELETE_WITH_KNOWN_SCHEDULE);

        // when:
        var summary = subject.keysForOtherParties(txn, summaryFactory);
        // then:
        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), contains(SCHEDULE_ADMIN_KT.asKey()));
    }

    private void setupFor(TxnHandlingScenario scenario) throws Throwable {
        setupFor(scenario, Optional.empty(), mockSignatureWaivers);
    }

    private void setupForNonStdLookup(
            TxnHandlingScenario scenario, SigMetadataLookup sigMetadataLookup) throws Throwable {
        setupFor(scenario, Optional.of(sigMetadataLookup), mockSignatureWaivers);
    }

    private void setupFor(
            final TxnHandlingScenario scenario,
            final Optional<SigMetadataLookup> sigMetaLookup,
            final SignatureWaivers signatureWaivers)
            throws Throwable {
        txn = scenario.platformTxn().getTxn();
        hfs = scenario.hfs();
        accounts = scenario.accounts();
        topics = scenario.topics();
        tokenStore = scenario.tokenStore();
        scheduleStore = scenario.scheduleStore();
        final var hfsSigMetaLookup = new HfsSigMetaLookup(hfs, fileNumbers);

        aliasManager = mock(AliasManager.class);
        given(aliasManager.lookupIdBy(ByteString.copyFromUtf8(CURRENTLY_UNUSED_ALIAS)))
                .willReturn(EntityNum.MISSING_NUM);
        given(aliasManager.lookupIdBy(ByteString.copyFromUtf8(NO_RECEIVER_SIG_ALIAS)))
                .willReturn(EntityNum.fromAccountId(NO_RECEIVER_SIG));
        given(aliasManager.lookupIdBy(ByteString.copyFromUtf8(RECEIVER_SIG_ALIAS)))
                .willReturn(EntityNum.fromAccountId(RECEIVER_SIG));
        given(aliasManager.lookupIdBy(TxnHandlingScenario.FIRST_TOKEN_SENDER_LITERAL_ALIAS))
                .willReturn(EntityNum.fromAccountId(FIRST_TOKEN_SENDER));

        subject =
                new SigRequirements(
                        sigMetaLookup.orElse(
                                defaultLookupsFor(
                                        aliasManager,
                                        hfsSigMetaLookup,
                                        () -> accounts,
                                        () -> topics,
                                        DelegatingSigMetadataLookup.REF_LOOKUP_FACTORY.apply(
                                                tokenStore),
                                        DelegatingSigMetadataLookup.SCHEDULE_REF_LOOKUP_FACTORY
                                                .apply(scheduleStore))),
                        signatureWaivers);
    }

    @SuppressWarnings("unchecked")
    private void mockSummaryFactory() {
        mockSummaryFactory =
                (SigningOrderResultFactory<ResponseCodeEnum>) mock(SigningOrderResultFactory.class);
    }

    private SigMetadataLookup hcsMetadataLookup(JKey adminKey, JKey submitKey) {
        return new DelegatingSigMetadataLookup(
                FileAdapter.throwingUoe(),
                AccountAdapter.withSafe(
                        id -> {
                            if (id.equals(asAccount(MISC_ACCOUNT_ID))) {
                                try {
                                    return new SafeLookupResult<>(
                                            new AccountSigningMetadata(
                                                    MISC_ACCOUNT_KT.asJKey(), false));
                                } catch (Exception e) {
                                    throw new IllegalArgumentException(e);
                                }
                            } else {
                                return SafeLookupResult.failure(KeyOrderingFailure.MISSING_ACCOUNT);
                            }
                        }),
                ContractAdapter.withSafe(
                        id -> SafeLookupResult.failure(KeyOrderingFailure.INVALID_CONTRACT)),
                TopicAdapter.withSafe(
                        id -> {
                            if (id.equals(asTopic(EXISTING_TOPIC_ID))) {
                                return new SafeLookupResult<>(
                                        new TopicSigningMetadata(adminKey, submitKey));
                            } else {
                                return SafeLookupResult.failure(KeyOrderingFailure.INVALID_TOPIC);
                            }
                        }),
                id -> null,
                id -> null);
    }

    static List<Key> sanityRestored(List<JKey> jKeys) {
        return jKeys.stream()
                .map(
                        jKey -> {
                            try {
                                return JKey.mapJKey(jKey);
                            } catch (Exception ignore) {
                            }
                            throw new AssertionError("All keys should be mappable!");
                        })
                .collect(toList());
    }
}
