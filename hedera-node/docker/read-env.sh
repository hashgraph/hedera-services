// SPDX-License-Identifier: Apache-2.0
#!/usr/bin/env bash
file="./.env"
function prop {
    grep "${1}" ${file} | cut -d'=' -f2
}
echo $(prop 'TAG')
