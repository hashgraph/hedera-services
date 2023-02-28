#!/usr/bin/env bash

#
# Copyright 2016-2022 Hedera Hashgraph, LLC
#
# This software is the confidential and proprietary information of
# Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
# disclose such Confidential Information and shall use it only in
# accordance with the terms of the license agreement you entered into
# with Hedera Hashgraph.
#
# HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
# THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
# TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
# PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
# ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
# DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
#

#logFiles=$(ls $1)
#for i in $logFiles;
oldifs="$IFS"
IFS=$'\n'

for i in $1;
do 
awkOutput=$(egrep "Exception|ERROR" $i | awk 'match($0,/(ERROR)|([ \ta-zA-Z.$]+[eE]xception)/){a[substr($0, RSTART, RLENGTH)]++} END {for (i in a) print i,": ",a[i]}' | sort)
#    awkOutput=$(awk '{a[$1]++} END {for (i in a) print i,a[i]}' $i | egrep 'Exception|Error')
    if [[ ! -z "$awkOutput" ]]
    then
        echo '********************************************************************************************'
        echo $i 
        echo '********************************************************************************************'
        echo "$awkOutput"
    fi
    #awk '{a[$1]++} END {for (i in a) print i,a[i]}' $i | egrep 'Exception|Error' 
done
IFS="$oldifs"
