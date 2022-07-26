package com.hedera.services.yahcli.commands.keys;

import static com.hedera.services.yahcli.config.ConfigUtils.setLogLevels;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.bdd.spec.keys.deterministic.Bip0032;
import com.hedera.services.keys.Ed25519Utils;
import com.swirlds.common.utility.CommonUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import picocli.CommandLine;

@CommandLine.Command(
        name = "print-keys",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Prints the public and private keys in a Ed25519 key pair")
public class ExtractDetailsCommand implements Callable<Integer> {
    public static final String DER_EDDSA_PREFIX = "302e020100300506032b657004220420";
    @CommandLine.ParentCommand private KeysCommand keysCommand;

    @CommandLine.Option(
            names = {"-p", "--path"},
            paramLabel = "path to PEM or mnemonic to print public/private keys",
            defaultValue = "keys/ed25519.pem")
    private String loc;

    @CommandLine.Option(
            names = {"-x", "--passphrase"},
            paramLabel = "passphrase for PEM (if needed and not present as matching .pass)")
    private String passphrase;

    @Override
    public Integer call() throws Exception {
        setLogLevels(keysCommand.getYahcli().getLogLevel());

        final EdDSAPrivateKey privateKey;
        if (loc.endsWith(".pem")) {
            if (passphrase == null) {
                final var passLoc = loc.replace(".pem", ".pass");
                passphrase = Files.readString(Paths.get(passLoc)).trim();
            }
            privateKey = Ed25519Utils.readKeyFrom(new File(loc), passphrase);
        } else {
            final var mnemonic = Files.readString(Paths.get(loc)).trim();
            final var seed = Bip0032.seedFrom(mnemonic);
            final var curvePoint = Bip0032.privateKeyFrom(seed);
            privateKey = Ed25519Utils.keyFrom(curvePoint);
        }
        final var pubKey = privateKey.getAbyte();
        final var hexedPubKey = CommonUtils.hex(pubKey);
        final var hexedPrivKey = CommonUtils.hex(privateKey.getSeed());
        COMMON_MESSAGES.info("The public key @ " + loc + " is : " + hexedPubKey);
        COMMON_MESSAGES.info("The private key @ " + loc + " is: " + hexedPrivKey);
        COMMON_MESSAGES.info(
                "  -> With DER prefix; " + DER_EDDSA_PREFIX + hexedPrivKey);

        return 0;
    }
}
