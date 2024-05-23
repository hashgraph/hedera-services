import com.swirlds.pairings.bls12381.impl.spi.Bls12381Provider;
import com.swirlds.pairings.spi.BilinearPairingProvider;

module com.swirlds.pairings.bls12381.impl {
    requires transitive com.swirlds.pairings.api;
    requires com.swirlds.nativesupport;
    requires static com.github.spotbugs.annotations;

    provides BilinearPairingProvider with
            Bls12381Provider;
}
