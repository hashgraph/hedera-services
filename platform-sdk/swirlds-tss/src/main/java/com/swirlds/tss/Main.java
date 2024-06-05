package com.swirlds.tss;

import com.swirlds.pairings.api.Curve;
import com.swirlds.signaturescheme.api.GroupAssignment;
import com.swirlds.signaturescheme.api.PairingKeyPair;
import com.swirlds.signaturescheme.api.PairingPrivateKey;
import com.swirlds.signaturescheme.api.PairingPublicKey;
import com.swirlds.signaturescheme.api.PairingSignature;
import com.swirlds.signaturescheme.api.SignatureSchema;
import com.swirlds.tss.api.ShareClaims;
import com.swirlds.tss.api.Tss;
import com.swirlds.tss.api.TssMessage;
import com.swirlds.tss.api.TssPrivateShare;
import com.swirlds.tss.api.TssPublicShare;
import com.swirlds.tss.api.TssShareClaim;
import com.swirlds.tss.api.TssShareId;
import com.swirlds.tss.api.TssShareSignature;
import com.swirlds.tss.api.TssUtils;
import com.swirlds.tss.impl.groth21.ElGamalCache;
import com.swirlds.tss.impl.groth21.Groth21Tss;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static final SecureRandom RANDOM = new SecureRandom();
    static SignatureSchema signatureSchema = SignatureSchema.create(Curve.ALT_BN128, GroupAssignment.GROUP1_FOR_PUBLIC_KEY);
    //this needs to be from the nodeid and not 0
    //we need to solve the problem of node 0, and node LONG.MAX_VALUE.
    static int currentNodeIndex = 0;
    static Tss tssImpl = new Groth21Tss(signatureSchema);

    public static void main(String[] args) {

        generateNodePrivateEcKey();

        PairingPrivateKey ecKey = Platform.loadECKeyFromDisk();
        ShareClaims shareClaims =  new ShareClaims(Platform.getShareIdsFromAddressBook());

        TssShareId shareId = new TssShareId( signatureSchema.getField().elementFromLong(currentNodeIndex +1) );

        TssKeyPair result = generateNodePrivatePublicKeyShares(shareId, ecKey, shareClaims);

        List<TssPrivateShare> privateShares = result.privateShares();
        Map<TssShareId, TssPublicShare> tssShareIdTssPublicShareMap = result.publicShares();

        //block signing
        byte[] blockHash = new byte[]{};
        PairingSignature value = blockSigning(privateShares, blockHash, tssShareIdTssPublicShareMap);

        //block verification
        PairingPublicKey pairingPublicKey = TssUtils.aggregatePublicShares(List.copyOf(result.publicShares().values()));
        value.verifySignature(pairingPublicKey, blockHash);

        //****************************************************
        //BLOCK NODE side
        //Create ledgerID
        TssPublicShare ledgerId = BlockNode.createLedgerId(pairingPublicKey/*How the blockNode gets this value??*/);

        value.verifySignature(ledgerId.publicKey(), blockHash);


    }

    private static PairingSignature blockSigning(List<TssPrivateShare> privateShares, byte[] blockHash,
            Map<TssShareId, TssPublicShare> tssShareIdTssPublicShareMap) {
        for (TssPrivateShare share : privateShares){
            Platform.sentSignatureTransaction(share.sign(blockHash));
        }

        //collect signatures
        List<TssShareSignature> collectedSignatures = Platform.collectTssSignatures();
        List<TssShareSignature> validSignatures = new ArrayList<>();
        for (TssShareSignature signature : collectedSignatures){

            if(signature.signature().verifySignature(tssShareIdTssPublicShareMap.get(signature.shareId()).publicKey(),
                    blockHash)){
                validSignatures.add(signature);
            }
        }

        PairingSignature value = TssUtils.aggregateSignatures(validSignatures);
        Platform.addToBlock(value);
        return value;
    }

    private static TssKeyPair generateNodePrivatePublicKeyShares(TssShareId shareId, PairingPrivateKey ecKey, ShareClaims shareClaims) {
        TssPrivateShare privateShare = new TssPrivateShare(shareId, ecKey);

        //each node will do this 1 time
        TssMessage message = tssImpl.generateTssMessage(RANDOM, signatureSchema , shareClaims, privateShare,  Platform.geThresholdFromAddressBook());

        Platform.broadcastTssMessageWithSysTransaction(message);

        List<TssMessage> messages = Platform.receiveTssMessagesFromAllNodes();
        for (TssMessage m : messages){
            Platform.checkTssMessageAndBroadcastVote(m, ecKey, shareClaims); ;
        }

        // Platform starts collecting votes and when
        //Platform.electionCompletes();
        List<TssMessage> validMessages = Platform.filtervalidMessagesFromElection();

        //compute keys
        List<TssShareClaim> shareClaimsOwnedByCurrentNode = Platform.filterShareClaims(shareClaims);

        final int cacheSize = 256; //How should this config be stored, where should this table load.
        ElGamalCache cache = ElGamalCache.create(signatureSchema.getPublicKeyGroup(), cacheSize);
        List<TssPrivateShare> privateShares = shareClaimsOwnedByCurrentNode.stream().map(s ->TssUtils.decryptPrivateShare(signatureSchema, s.shareId(),
                        ecKey,validMessages,cache, shareClaims, Platform.geThresholdFromAddressBook()) )
                .toList();

        Map<TssShareId, TssPublicShare> publicShares = new HashMap<>();
        for (TssShareClaim claim : shareClaims.getClaims()){
            publicShares.put(claim.shareId(),
            TssUtils.computePublicShare(signatureSchema,claim.shareId(),validMessages,
                    Platform.geThresholdFromAddressBook()));
        }
        return new TssKeyPair(privateShares, publicShares);
    }

    private record TssKeyPair(List<TssPrivateShare> privateShares, Map<TssShareId, TssPublicShare> publicShares) {
    }

    private static void generateNodePrivateEcKey() {
        //Key generation process per each node. Happens outside the node
        PairingKeyPair keyPair = PairingKeyPair.create(signatureSchema, new SecureRandom());

        PairingPrivateKey ecKey =  keyPair.privateKey();
        PlatformTool.storeToDisk(ecKey);
        // saves the key to a pemFile in the node's filesystem
    }


    public static class Platform{
        private static List<TssMessage> receiveTssMessagesFromAllNodes() {
            return List.of();
        }

        private static void broadcastTssMessageWithSysTransaction(TssMessage message) {

        }

        public static  List<TssShareClaim> getShareIdsFromAddressBook(){
            return new ArrayList<>();
        }

        public static int geThresholdFromAddressBook(){
            return -1;
        }

        private static void addToBlock(PairingSignature pairingSignature) {

        }

        private static List<TssShareSignature> collectTssSignatures() {
            return List.of();
        }

        private static void sentSignatureTransaction(TssShareSignature sign) {

        }

        private static List<TssShareClaim> filterShareClaims(ShareClaims shareClaims) {

            return List.of();
        }

        private static List<TssMessage> filtervalidMessagesFromElection() {
            return null;
        }

        private static void checkTssMessageAndBroadcastVote(TssMessage m, PairingPrivateKey ecKey, ShareClaims shareClaims) {
            if(m.verify(PairingPublicKey.create(ecKey),  shareClaims)){
                //voteOk
            }
            else{
                //voteNOK
            }
        }

        public static void electionCompletes() {}

        @NonNull
        public static PairingPrivateKey loadECKeyFromDisk() {
            //Loads the node's echkey from the disk
            return null;
        }
    }

    private static class PlatformTool {
        public static void storeToDisk(PairingPrivateKey ecKey) {


        }
    }

    private static class BlockNode {

        public static TssPublicShare createLedgerId(PairingPublicKey publicKey) {
            TssShareId shareId0 = new TssShareId( signatureSchema.getField().elementFromLong(0) );
           return new TssPublicShare(shareId0,publicKey );
        }
    }
}
