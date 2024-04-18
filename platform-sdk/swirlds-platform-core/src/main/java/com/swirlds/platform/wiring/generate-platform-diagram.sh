#!/usr/bin/env bash

# The location were this script can be found.
SCRIPT_PATH="$(dirname "$(readlink -f "$0")")"

# You must install mermaid to use this script.
# npm install -g @mermaid-js/mermaid-cli

# Add the flag "--less-mystery" to add back labels for mystery input wires (noisy diagram warning)

pcli diagram \
    -l 'applicationTransactionPrehandler:futures:consensusRoundHandler' \
    -l 'eventDurabilityNexus:wait for durability:consensusRoundHandler' \
    -l 'EventCreationManager:get transactions:transactionPool' \
    -l 'RunningEventHasher:future hash:consensusRoundHandler' \
    -l 'ConsensusEventStream:future hash:consensusRoundHandler' \
    -s 'eventWindowManager:event window:🌀' \
    -s 'heartbeat:heartbeat:❤️' \
    -s 'applicationTransactionPrehandler:futures:🔮' \
    -s 'eventDurabilityNexus:wait for durability:🕑' \
    -s 'pcesReplayer:done streaming pces:✅' \
    -s 'OrphanBufferSplitter:events to gossip:📬' \
    -s 'getKeystoneEventSequenceNumber:flush request:🚽' \
    -s 'extractOldestMinimumGenerationOnDisk:minimum identifier to store:📀' \
    -s 'SelfEventSigner:non-validated events:🍎' \
    -s 'Mystery Input:mystery data:❔' \
    -s 'stateSigner:signature transactions:🖋️' \
    -s 'issNotificationSplitter:Iss Notification:💥' \
    -s 'toNotification:state written notification:📦' \
    -s 'latestCompleteStateNotifier:complete state notification:💢' \
    -s 'OrphanBufferSplitter:preconsensus signatures:🔰' \
    -s 'RunningEventHashOverride:hash override:💨' \
    -g 'Event Validation:InternalEventValidator,EventDeduplicator,EventSignatureValidator' \
    -g 'Event Hashing:eventHasher,postHashCollector' \
    -g 'Orphan Buffer:OrphanBuffer,OrphanBufferSplitter' \
    -g 'Consensus Engine:ConsensusEngine,ConsensusEngineSplitter,eventWindowManager,getKeystoneEventSequenceNumber,getConsensusEvents' \
    -g 'State File Manager:saveToDiskFilter,signedStateFileManager,extractOldestMinimumGenerationOnDisk,toStateWrittenToDiskAction,statusManager_submitStateWritten,toNotification' \
    -g 'State File Management:State File Manager,📦,📀' \
    -g 'State Signature Collector:stateSignatureCollector,reservedStateSplitter,allStatesReserver,completeStateFilter,completeStatesReserver,extractConsensusSignatureTransactions,extractPreconsensusSignatureTransactions,latestCompleteStateNotifier' \
    -g 'State Signature Collection:State Signature Collector,latestCompleteStateNexus,💢' \
    -g 'Preconsensus Event Stream:PcesSequencer,pcesWriter,eventDurabilityNexus,🕑' \
    -g 'Event Creation:EventCreationManager,transactionPool,SelfEventSigner,🍎' \
    -g 'ISS Detector:issDetector,issNotificationSplitter,issHandler,statusManager_submitCatastrophicFailure' \
    -g 'Heartbeat:heartbeat,❤️' \
    -g 'PCES Replay:pcesReplayer,✅' \
    -g 'Transaction Prehandling:applicationTransactionPrehandler,🔮' \
    -g 'Consensus Round Handler:consensusRoundHandler,postHandler_stateAndRoundReserver,getState,savedStateController' \
    -g 'State Hasher:stateHasher,postHasher_stateAndRoundReserver,postHasher_getConsensusRound,postHasher_stateReserver' \
    -g 'Consensus:Consensus Engine,🚽,🌀' \
    -g 'State Verification:stateSigner,hashLogger,ISS Detector,🖋️,💥' \
    -g 'Transaction Handling:Consensus Round Handler,latestImmutableStateNexus' \
    -c 'Orphan Buffer' \
    -c 'Consensus Engine' \
    -c 'State Signature Collector' \
    -c 'State File Manager' \
    -c 'Consensus Round Handler' \
    -c 'State Hasher' \
    -c 'ISS Detector' \
    -o "${SCRIPT_PATH}/../../../../../../../../docs/core/wiring-diagram.svg"
