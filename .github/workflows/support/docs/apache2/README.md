# Hashgraph Platform Source

### License

Copyright 2016-2022 Hedera Hashgraph, LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[https://www.apache.org/licenses/LICENSE-2.0](https://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


### Acknowledgments

Portions of this Hedera Hashgraph, LLC Software may utilize the following copyrighted material, the use of which is hereby 
acknowledged.

The full list of acknowledgements is available at 
[https://github.com/hashgraph/swirlds-open-review/raw/master/acknowledgments.html](/acknowledgments.html)


### Building the Source Code

#### Minimum Requirements

- **Oracle OpenJDK 17**
  - Available from [http://jdk.java.net/17/](http://jdk.java.net/17/)
- **Apache Maven 3**
  - Available from [http://maven.apache.org/download.cgi](http://maven.apache.org/download.cgi)
  
#### Standard Build Command

The entire project may be built by executing `mvn deploy` from the root of this repository. 

##### **NOTE:** 
The `deploy` phase is required to produce a complete build. 
The maven `compile` and `install` phases will not install all necessary dependencies. 

#### Contributing

Please send any comments or suggestions or suggested code changes to [software@hedera.com](mailto:software@hedera.com).<br />
By doing so, you agree to grant Hedera Hashgraph the right and license to use any such feedback as set out in the Hashgraph Open Review License.<br />
To report bugs, and possibly be rewarded through the Bug Bounty program, please see https://www.hedera.com/bounty
