/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.support.validators.block;

import com.google.protobuf.TextFormat;
import com.hedera.hapi.block.stream.output.StateIdentifier;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.swirlds.common.utility.CommonUtils;

public class BlockStreamUtils {
    public static void main(String[] args) throws TextFormat.InvalidEscapeSequenceException {
        final var hexed =
                "f903da80a000000000000000000000000000000000000000000000000000000004a817c800830f42408080b9036a608060405234801561001057600080fd5b5061034a806100206000396000f300608060405260043610610057576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680632f19c04a1461005c57806338cc483114610087578063efc81a8c146100de575b600080fd5b34801561006857600080fd5b506100716100f5565b6040518082815260200191505060405180910390f35b34801561009357600080fd5b5061009c6101bc565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b3480156100ea57600080fd5b506100f36101e5565b005b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1663086949b76040518163ffffffff167c0100000000000000000000000000000000000000000000000000000000028152600401602060405180830381600087803b15801561017c57600080fd5b505af1158015610190573d6000803e3d6000fd5b505050506040513d60208110156101a657600080fd5b8101908080519060200190929190505050905090565b60008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff16905090565b6101ed61024b565b604051809103906000f080158015610209573d6000803e3d6000fd5b506000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550565b60405160c48061025b83390190560060806040526008600055348015601457600080fd5b5060a1806100236000396000f300608060405260043610603f576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff168063086949b7146044575b600080fd5b348015604f57600080fd5b506056606c565b6040518082815260200191505060405180910390f35b600060079050905600a165627a7a723058202e097bbe122ad5d86e840be60aab41d160ad5b86745aa7aa0099a6bbfc2652180029a165627a7a723058206cf7ea9d4e506886b602ff7a628401611437cbfd0dfcbd5beec37757070da5b30029820277a045a9f8087e387c7d65e9be7bf9e738aead7de59a6d569085ce0aafc8da1f1b85a075d8b19498cceca7b847bc7f176cec6f235eb1f98b17baf2bba281598dcf661f";
        //        final var encoded =
        // "\\371\\003\\332\\200\\240\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\004\\250\\027\\310\\000\\203\\017B@\\200\\200\\271\\003j`\\200`@R4\\200\\025a\\000\\020W`\\000\\200\\375[Pa\\003J\\200a\\000 `\\0009`\\000\\363\\000`\\200`@R`\\0046\\020a\\000WW`\\0005|\\001\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\220\\004c\\377\\377\\377\\377\\026\\200c/\\031\\300J\\024a\\000\\\\W\\200c8\\314H1\\024a\\000\\207W\\200c\\357\\310\\032\\214\\024a\\000\\336W[`\\000\\200\\375[4\\200\\025a\\000hW`\\000\\200\\375[Pa\\000qa\\000\\365V[`@Q\\200\\202\\201R` \\001\\221PP`@Q\\200\\221\\003\\220\\363[4\\200\\025a\\000\\223W`\\000\\200\\375[Pa\\000\\234a\\001\\274V[`@Q\\200\\202s\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\026s\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\026\\201R` \\001\\221PP`@Q\\200\\221\\003\\220\\363[4\\200\\025a\\000\\352W`\\000\\200\\375[Pa\\000\\363a\\001\\345V[\\000[`\\000\\200`\\000\\220T\\220a\\001\\000\\n\\220\\004s\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\026s\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\026c\\biI\\267`@Q\\201c\\377\\377\\377\\377\\026|\\001\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\002\\201R`\\004\\001` `@Q\\200\\203\\003\\201`\\000\\207\\200;\\025\\200\\025a\\001|W`\\000\\200\\375[PZ\\361\\025\\200\\025a\\001\\220W=`\\000\\200>=`\\000\\375[PPPP`@Q=` \\201\\020\\025a\\001\\246W`\\000\\200\\375[\\201\\001\\220\\200\\200Q\\220` \\001\\220\\222\\221\\220PPP\\220P\\220V[`\\000\\200`\\000\\220T\\220a\\001\\000\\n\\220\\004s\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\026\\220P\\220V[a\\001\\355a\\002KV[`@Q\\200\\221\\003\\220`\\000\\360\\200\\025\\200\\025a\\002\\tW=`\\000\\200>=`\\000\\375[P`\\000\\200a\\001\\000\\n\\201T\\201s\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\002\\031\\026\\220\\203s\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\377\\026\\002\\027\\220UPV[`@Q`\\304\\200a\\002[\\2039\\001\\220V\\000`\\200`@R`\\b`\\000U4\\200\\025`\\024W`\\000\\200\\375[P`\\241\\200a\\000#`\\0009`\\000\\363\\000`\\200`@R`\\0046\\020`?W`\\0005|\\001\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\220\\004c\\377\\377\\377\\377\\026\\200c\\biI\\267\\024`DW[`\\000\\200\\375[4\\200\\025`OW`\\000\\200\\375[P`V`lV[`@Q\\200\\202\\201R` \\001\\221PP`@Q\\200\\221\\003\\220\\363[`\\000`\\a\\220P\\220V\\000\\241ebzzr0X .\\t{\\276\\022*\\325\\330n\\204\\v\\346\\n\\253A\\321`\\255[\\206tZ\\247\\252\\000\\231\\246\\273\\374&R\\030\\000)\\241ebzzr0X l\\367\\352\\235NPh\\206\\266\\002\\377zb\\204\\001a\\0247\\313\\375\\r\\374\\275[\\356\\303wW\\a\\r\\245\\263\\000)\\202\\002w\\240E\\251\\370\\b~8|}e\\351\\276{\\371\\3478\\256\\255}\\345\\232mV\\220\\205\\316\\n\\257\\310\\332\\037\\033\\205\\240u\\330\\261\\224\\230\\314\\354\\247\\270G\\274\\177\\027l\\354o#^\\261\\371\\213\\027\\272\\362\\273\\242\\201Y\\215\\317f\\037";
        //        final var raw = TextFormat.unescapeBytes(encoded);
        final var raw = CommonUtils.unhex(hexed);
        final var ethTx = EthTxData.populateEthTxData(raw);
        System.out.println(ethTx);
    }

    public static String stateNameOf(final int stateId) {
        return switch (StateIdentifier.fromProtobufOrdinal(stateId)) {
            case STATE_ID_NODES -> "AddressBookService.NODES";
            case STATE_ID_BLOCK_INFO -> "BlockRecordService.BLOCKS";
            case STATE_ID_RUNNING_HASHES -> "BlockRecordService.RUNNING_HASHES";
            case STATE_ID_BLOCK_STREAM_INFO -> "BlockStreamService.BLOCK_STREAM_INFO";
            case STATE_ID_CONGESTION_STARTS -> "CongestionThrottleService.CONGESTION_LEVEL_STARTS";
            case STATE_ID_THROTTLE_USAGE -> "CongestionThrottleService.THROTTLE_USAGE_SNAPSHOTS";
            case STATE_ID_TOPICS -> "ConsensusService.TOPICS";
            case STATE_ID_CONTRACT_BYTECODE -> "ContractService.BYTECODE";
            case STATE_ID_CONTRACT_STORAGE -> "ContractService.STORAGE";
            case STATE_ID_ENTITY_ID -> "EntityIdService.ENTITY_ID";
            case STATE_ID_MIDNIGHT_RATES -> "FeeService.MIDNIGHT_RATES";
            case STATE_ID_FILES -> "FileService.FILES";
            case STATE_ID_UPGRADE_DATA_150 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=150]]";
            case STATE_ID_UPGRADE_DATA_151 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=151]]";
            case STATE_ID_UPGRADE_DATA_152 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=152]]";
            case STATE_ID_UPGRADE_DATA_153 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=153]]";
            case STATE_ID_UPGRADE_DATA_154 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=154]]";
            case STATE_ID_UPGRADE_DATA_155 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=155]]";
            case STATE_ID_UPGRADE_DATA_156 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=156]]";
            case STATE_ID_UPGRADE_DATA_157 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=157]]";
            case STATE_ID_UPGRADE_DATA_158 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=158]]";
            case STATE_ID_UPGRADE_DATA_159 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=159]]";
            case STATE_ID_UPGRADE_FILE -> "FileService.UPGRADE_FILE";
            case STATE_ID_FREEZE_TIME -> "FreezeService.FREEZE_TIME";
            case STATE_ID_UPGRADE_FILE_HASH -> "FreezeService.UPGRADE_FILE_HASH";
            case STATE_ID_PLATFORM_STATE -> "PlatformStateService.PLATFORM_STATE";
            case STATE_ID_ROSTER_STATE -> "RosterService.ROSTER_STATE";
            case STATE_ID_ROSTERS -> "RosterService.ROSTERS";
            case STATE_ID_TRANSACTION_RECEIPTS_QUEUE -> "RecordCache.TransactionReceiptQueue";
            case STATE_ID_SCHEDULES_BY_EQUALITY -> "ScheduleService.SCHEDULES_BY_EQUALITY";
            case STATE_ID_SCHEDULES_BY_EXPIRY -> "ScheduleService.SCHEDULES_BY_EXPIRY_SEC";
            case STATE_ID_SCHEDULES_BY_ID -> "ScheduleService.SCHEDULES_BY_ID";
            case STATE_ID_ACCOUNTS -> "TokenService.ACCOUNTS";
            case STATE_ID_ALIASES -> "TokenService.ALIASES";
            case STATE_ID_NFTS -> "TokenService.NFTS";
            case STATE_ID_PENDING_AIRDROPS -> "TokenService.PENDING_AIRDROPS";
            case STATE_ID_STAKING_INFO -> "TokenService.STAKING_INFOS";
            case STATE_ID_NETWORK_REWARDS -> "TokenService.STAKING_NETWORK_REWARDS";
            case STATE_ID_TOKEN_RELATIONS -> "TokenService.TOKEN_RELS";
            case STATE_ID_TOKENS -> "TokenService.TOKENS";
        };
    }
}
