#!/usr/bin/env bash

pcli diagram \
    -l 'applicationTransactionPrehandler:futures:consensusRoundHandler' \
    -l 'eventDurabilityNexus:wait for durability:consensusRoundHandler' \
    -s 'eventWindowManager:non-ancient event window:ʘ' \
    -s 'heartbeat:heartbeat:♡' \
    -s 'eventCreationManager:non-validated events:†' \
    -s 'applicationTransactionPrehandler:futures:★' \
    -s 'eventDurabilityNexus:wait for durability:🕑' \
    -s 'pcesReplayer:done streaming pces:@' \
    -s 'inOrderLinker:events to gossip:g' \
    -s 'runningHashUpdate:running hash update:§' \
    -s 'getKeystoneEventSequenceNumber:flush request:Ξ' \
    -g 'Event Validation:internalEventValidator,eventDeduplicator,eventSignatureValidator' \
    -g 'Event Hashing:eventHasher,postHashCollector' \
    -g 'Orphan Buffer:orphanBuffer,orphanBufferSplitter' \
    -g 'Consensus Engine:consensusEngine,consensusEngineSplitter,eventWindowManager,getKeystoneEventSequenceNumber' \
    -g 'State File Management:saveToDiskFilter,signedStateFileManager,extractOldestMinimumGenerationOnDisk,toStateWrittenToDiskAction' \
    -g 'State Signature Collection:stateSignatureCollector,reservedStateSplitter,allStatesReserver,completeStateFilter,completeStatesReserver,extractConsensusSignatureTransactions,extractPreconsensusSignatureTransactions' \
    -g 'Intake Pipeline:Event Validation,Orphan Buffer,Event Hashing' \
    -g 'Preconsensus Event Stream:pcesSequencer,pcesWriter,eventDurabilityNexus' \
    -g 'Consensus Event Stream:getEvents,eventStreamManager' \
    -g 'Consensus Pipeline:inOrderLinker,Consensus Engine,g,ʘ,Ξ' \
    -g 'Event Creation:futureEventBuffer,futureEventBufferSplitter,eventCreationManager' \
    -g 'Gossip:gossip,shadowgraph' \
    -c 'Consensus Event Stream' \
    -c 'Orphan Buffer' \
    -c 'Consensus Engine'
