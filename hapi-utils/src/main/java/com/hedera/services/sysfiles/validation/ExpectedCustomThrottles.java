package com.hedera.services.sysfiles.validation;

/*-
 * ‌
 * Hedera Services API Utilities
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.HederaFunctionality;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;

import java.util.EnumSet;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

public class ExpectedCustomThrottles {
	public static final EnumSet<HederaFunctionality> OPS_FOR_RELEASE_0130 = EnumSet.of(
			CryptoCreate,
			CryptoTransfer,
			CryptoUpdate,
			CryptoDelete,
			FileCreate,
			FileUpdate,
			FileDelete,
			FileAppend,
			ContractCreate,
			ContractUpdate,
			ContractCall,
			ContractDelete,
			ConsensusCreateTopic,
			ConsensusUpdateTopic,
			ConsensusDeleteTopic,
			ConsensusSubmitMessage,
			TokenCreate,
			TokenFreezeAccount,
			TokenUnfreezeAccount,
			TokenGrantKycToAccount,
			TokenRevokeKycFromAccount,
			TokenDelete,
			TokenMint,
			TokenBurn,
			TokenAccountWipe,
			TokenUpdate,
			TokenAssociateToAccount,
			TokenDissociateFromAccount,
			ScheduleCreate,
			ScheduleSign,
			ScheduleDelete,
			ConsensusGetTopicInfo,
			ContractCallLocal,
			ContractGetInfo,
			ContractGetBytecode,
			ContractGetRecords,
			CryptoGetAccountBalance,
			CryptoGetAccountRecords,
			CryptoGetInfo,
			FileGetContents,
			FileGetInfo,
			TransactionGetReceipt,
			TransactionGetRecord,
			GetVersionInfo,
			TokenGetInfo,
			ScheduleGetInfo
	);
}
