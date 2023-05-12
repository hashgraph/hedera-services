# Test Clients for Hedera Services

Provides clients to the Hedera API whose primary purpose is to validate the
behavior and performance of the network.

## Overview

At present, Hedera engineering is focusing only on the use and improvement of
clients constructed from sequences of
[`HapiSpecOperation`](src/main/java/com/hedera/services/bdd/spec/HapiSpecOperation.java)
implementations. All other components in this folder should be taken as deprecated.
