
* selectors
    * hook up selectors to macros so that signatures get listed in proper places
    * have a second file to read that has selectors/signatures for our precompiles
    * back up selector file with online call (and write updates)
* other  
    * `jumpdest` to include all "come-from" locations
----

* in progress
    * basic blocks
    * get contract state for each contract from signed state store
    * simulated execution, with contract state, to determine call/delegatecall destination (address + method)
