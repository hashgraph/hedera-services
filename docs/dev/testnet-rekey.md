# :old_key: Re-keying treasury on a testnet saved state

This document describes how you use your local environment to
change the `0.0.2` key on a saved state from stable testnet.
(We can then update our migration tests to use the new state
and key, without any security concerns.)

:bangbang:&nbsp; There is one important prerequisite to this guide:

1. Since only `0.0.2` can update the treasury key, you must
   have a local PEM/passphrase combination with the current
   `0.0.2` key on stable testnet.

:warning:&nbsp; Always follow these steps **on the tag** matching
the version of testnet state that DevOps provided. For example, if
DevOps provided a 0.14.0 state, do everything that follows on the
`v0.14.0` tag!

Also, note that at the time of writing, stable testnet has five nodes;
this number appears below in a couple places which should be easy to
adapt if needed.

## Preparing to start Services from the saved state

### Setting up data/saved/

Let's suppose you have the _SignedState.swh_ and _PostgresBackup.tar.gz_
from round 123456789 on testnet in a directory
_~/dev/states/stabletestnet/current_. We begin by running, from the
root of the Services project,

```
$ run/reset-data-saved-from.sh \
> ~/dev/states/stabletestnet/current \
> 123456789 \
> 5
```

This should copy the state files into five node-scoped subdirectories under
_hedera-node/data/saved/com.hedera.services.ServicesMain_.

### Restoring the PostgreSQL state

Open a new terminal at _hedera-node/data/backup_. Then run,

```
$ ./pg_restore_manual.sh \
> ~/dev/states/stabletestnet/current/PostgresBackup.tar.gz
```

:mantelpiece_clock:&nbsp;Don't worry if it appears to hang,
this will take several minutes in the best case!

### Setting Platform config

Ensure your _hedera-node/settings.txt_ contains the lines,

```
dbRestore.active, 0
dbBackup.active, 0
```

And that you have enough nodes in your _config.txt_ address book,

```
address,  A, Alice,    1, 127.0.0.1, 50204, 127.0.0.1, 50204, 0.0.3
address,  B, Bob,      1, 127.0.0.1, 50205, 127.0.0.1, 50205, 0.0.4
address,  C, Carol,    1, 127.0.0.1, 50206, 127.0.0.1, 50206, 0.0.5
address,  D, Dave,     1, 127.0.0.1, 50207, 127.0.0.1, 50207, 0.0.6
address,  E, Eric,     1, 127.0.0.1, 50208, 127.0.0.1, 50208, 0.0.7
```

## Starting Services

Whether you are starting Services from IntelliJ or command line,
make sure you give it sufficient heap (8GB to be safe). In IntelliJ
this looks like,

![](../assets/VM-options-for-local-testnet-rekey.png)

Then start Services and wait for a long time until you see Platform
is `ACTIVE`

### Rekeying and freezing

:old_key:&nbsp;Put your PEM with the current testnet treasury key
at _test-clients/stabletestnet-account2.pem_. Update the EET suite
`com.hedera.services.bdd.suites.misc.RekeySavedStateTreasury` with
the passphrase to your PEM file on L68, changing,

```
...
final var passphraseForOriginalPemLoc = "<SECRET>";
...
```

Now run the `RekeySavedStateTreasury` suite. The new treasury key
will be saved at,

1. _test-clients/DevStableTestnetStartUpAccount.txt_
2. _test-clients/dev-stabletestnet-account2.pem_

...where the first is a legacy Base64-encoded "startup account"; and
the PEM file has a passphrase of "swirlds".

Next, run the `FreezeRekeyedState` EET suite. It should end with logs like,

```
...
2021-05-04 09:47:26.187 INFO   311  HapiApiSpec - 'FreezeRekeyedState' finished initial execution of HapiFreeze{sigs=1, node=0.0.3, start=14:48, end=14:49}
2021-05-04 09:47:26.691 INFO   330  HapiApiSpec - 'FreezeRekeyedState' - final status: PASSED!
```

## Capturing the new state

:bangbang:&nbsp;Now we need a minute or two for Services logs to go
through the `MAINTENANCE -> ACTIVE` cycle.

After the `Freeze` has written a new signed state to disk, you can
stop Services and archive the state with the new treasury key,

```
$ tar -cvf rekeyed-testnet-round65464591.tar.gz \
> hedera-node/data/saved/com.hedera.node.app.service.mono.ServicesMain/0/123/65464591
```

(Here 65464591 was the round of state saved by the `Freeze`.)

You're done! Enjoy your shiny new treasury key.
