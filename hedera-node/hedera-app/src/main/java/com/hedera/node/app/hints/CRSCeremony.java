package com.hedera.node.app.hints;

import com.hedera.hapi.services.auxiliary.hints.CrsPublicationTransactionBody;
import com.hedera.node.app.hints.impl.HintsLibraryCodec;
import com.hedera.node.app.hints.impl.HintsSubmissions;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.app.spi.AppContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import java.math.BigInteger;
import java.security.SecureRandom;

public class CRSCeremony {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final HintsLibrary hintsLibrary;
    private final ActiveRosters activeRosters;
    private final HintsSubmissions hintsSubmissions;
    private final AppContext appContext;
    private final HintsLibraryCodec hintsLibraryCodec;

    public CRSCeremony(final HintsLibrary hintsLibrary,
                       final ActiveRosters activeRosters,
                       final HintsSubmissions hintsSubmissions,
                       final AppContext appContext,
                       HintsLibraryCodec hintsLibraryCodec) {
        this.hintsLibrary = hintsLibrary;
        this.activeRosters = activeRosters;
        this.hintsSubmissions = hintsSubmissions;
        this.appContext = appContext;
        this.hintsLibraryCodec = hintsLibraryCodec;
    }

    public void generateCRS() {
        final var initialCrs = generateInitialCRS();
        final var entropy = generateEntropy();

        final var updatedCRS = hintsLibrary.updateCrs(initialCrs, entropy);
        // decode the CRS
        final var decodedCRS = hintsLibraryCodec.decodeCrsUpdate(updatedCRS);
        final var isValidCrs = hintsLibrary.verifyCrsUpdate(initialCrs, decodedCRS.crs(), decodedCRS.proof());
        if (isValidCrs) {
            // gossip the updated CRS
//            hintsSubmissions.submitInitialCRS((int) appContext.selfNodeInfoSupplier().get().nodeId(), decodedCRS.crs());
        }
    }

    /**
     * Generates an initial CRS using secure randomness.
     */
    public Bytes generateInitialCRS() {
        return hintsLibrary.newCrs(activeRosters.currentRoster().rosterEntries().size());
    }

    /**
     * Generates secure 128-bit entropy.
     */
    public Bytes generateEntropy() {
        byte[] entropyBytes = new byte[16];
        SECURE_RANDOM.nextBytes(entropyBytes);
        return Bytes.wrap(entropyBytes);
    }
}
