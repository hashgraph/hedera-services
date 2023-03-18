# `VTableEntryRecognizer` - macroize contract virtual tables

Multi-entry contracts (via Solidity AFAIK) use a multiway branch
at the beginning of the contract to branch on a ["function
selector"](https://solidity-by-example.org/function-selector/) to
get to the right method in the code.
- Selector is the first 4 bytes of the computed function signature
  (details of which are unimportant here)
- Selector is the first 4 bytes of the `calldata` passed in to the
  contract.

This is similar to the "virtual table" familiar to C++/C#/Java
programmers.

If there are just a few methods in the contract the vtable is
simply a sequence of branches.  But if there are more than a few
methods (not sure what the threshold is) then it is encoded as a
binary search tree.

The purpose of the `VTableEntryRecognizer` is to find the vtable
in a contract and "macroize" it so that it becomes easy for the
reader to grok (instead of being a long long list of tests and
branches).  And it also labels all the function entry points with
their selector so they can be easily found.  And the list of
selectors with their entry points can be used for other purposes
as well.

- Not implemented: Looking up the selectors in
  [4byte.directory](https://4byte.directory) so that actual method
  names can be supplied as labels, when available.
    - Which would work pretty well for some contracts (presumably)
      those which have been ported over rather than new ones
        - E.g., you can find the selectors for `968267` in that
          directory
