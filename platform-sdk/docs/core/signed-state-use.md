# Signed State Use

When using a `SignedState`, it is critically important to do so in a way that is thread safe and that does not cause
improper use of the state's reference count. Failure to use a state object correctly is almost always fatal to a node.

## Rules

1. If an object of type `SignedState` is passed into a method, it is never thread safe to take a reference to the state
   and use it after the method is returned.
   a. A `SignedState` passed directly into a method is only guaranteed to be valid until the method returns.
   b. If a `SignedState` is passed into a method, and there is need for the signed state to be used after the method
      returns, then a new reservation on the signed state must be taken via
      `ReservedSignedState ss = SignedState.reserve("reason")`.
   c. Avoid using the merkle reference count API to force a state to remain in memory. Merkle reference counts should
      only be directly modified by the suite of utilities that were designed to operate directly on merkle trees.
2. If an object of type `ReservedSignedState` is passed into a method, it is the responsibility of the method to
   guarantee that `ReservedSignedState.close()` is eventually called.
   a. If possible, use a `ReservedSignedState` in a try-with-resources block.
   b. Avoid using `@Nullable ReservedSignedState` parameters. As a pattern, it is better to pass a non-null
      `ReservedSignedState` wrapped around `null`.
   c. In general, prefer passing a `SignedState` into a method instead of a `ReservedSignedState`. If an implementation
      is synchronous and does not need a copy of the state after the method returns, it won't need to take another
      reservation. And if it does need to keep the state after the method returns, it is simple to take a new
      reservation.
3. When creating a new `SignedState`, do not pass it to other methods or parts of the system with an implicit reference,
   always ensure that an explicit reservation is held before passing a `SignedState` to other parts of the code.
4. It is NEVER thread safe for threads to read from the same `ReservedSignedState` instance concurrently
   with another thread calling `ReservedSignedState.close()`.
   a. In multithreaded environments, prefer to create a new `ReservedSignedState` for each thread that needs to
      read from the state, or
   b. Use the `SignedStateReference` and/or `SignedStateMap` utility objects, which provide thread-safe access and
      management of `SignedState` objects.
5. When using any API that requires a reason to be provided when taking a new reservation on a signed state, ensure
   that the reason is sufficiently unique so that an engineer debugging a reference count exception can unambiguously
   find the code that is responsible for the reservation by searching for the reason string.
6. Setting configuration `state.debugStackTracesEnabled = true` provides extra information in the logs when a
   reference count exception occurs. This can be useful for debugging reference count issues, but it is critical
   that this setting is NEVER enabled in a production environment.