package com.hedera.services.bdd.spec.utilops.inventory;


import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromMnemonic.createAndLinkFromMnemonic;
import static com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromPem.incorporatePem;
import static com.hedera.services.yahcli.config.ConfigManager.isValid;
import static com.hedera.services.yahcli.config.ConfigUtils.keyFileAt;
import static com.hedera.services.yahcli.config.ConfigUtils.passFileFor;
import static com.hedera.services.yahcli.config.ConfigUtils.promptForPassphrase;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpecKeyFromFile extends UtilOp {
	private static final Logger log = LogManager.getLogger(SpecKeyFromFile.class);

	private final String name;
	private final String loc;
	private Optional<String> linkedId = Optional.empty();

	public SpecKeyFromFile(String name, String loc) {
		this.loc = loc;
		this.name = name;
	}

	public SpecKeyFromFile linkedTo(String id) {
		linkedId = Optional.of(id);
		return this;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		final var flexLoc = loc.substring(0, loc.lastIndexOf('.'));
		final var keyFile = keyFileAt(flexLoc);
		assertTrue(keyFile.isPresent(), "No key can be sourced from '" + loc + "'");
		final var f = keyFile.get();
		if (f.getName().endsWith(".pem")) {
			Optional<String> finalPassphrase = Optional.empty();
			var optPassFile = passFileFor(f);
			if (optPassFile.isPresent()) {
				final var pf = optPassFile.get();
				try {
					finalPassphrase = Optional.of(Files.readString(pf.toPath()).trim());
				} catch (IOException e) {
					log.warn("Password file {} inaccessible for PEM {}", pf.getAbsolutePath(), name, e);
				}
			}
			if (!isValid(f, finalPassphrase)) {
				var prompt = "Please enter the passphrase for key file " + keyFile;
				finalPassphrase = promptForPassphrase(loc, prompt, 3);
			}
			if (finalPassphrase.isEmpty() || !isValid(f, finalPassphrase)) {
				Assertions.fail(String.format("No valid passphrase could be obtained for PEM %s", loc));
			}
			incorporatePem(spec, SIMPLE, loc, finalPassphrase.get(), name, linkedId, Optional.empty());
		} else {
			var mnemonic = Files.readString(f.toPath());
			createAndLinkFromMnemonic(spec, mnemonic, name, linkedId, log);
		}
		return false;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		var helper = super.toStringHelper();
		helper.add("name", name);
		helper.add("loc", loc);
		linkedId.ifPresent(s -> helper.add("linkedId", s));
		return helper;
	}
}
