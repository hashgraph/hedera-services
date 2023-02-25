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

##############################################################################
# move results into results archive so graphing works only on the latest run #
##############################################################################

# make dir for all the dates in rusults right now
>&2 echo "copying results"
for s in `ls /mnt/efs/atf/results/ | egrep -o '[0-9]{4}-[0-9]+-[0-9]+' | sort --unique`; do 
	tempdir="/mnt/efs/atf/result-archives/results-$s"; 
	(mkdir $tempdir);
	(ls /mnt/efs/atf/results/ | egrep $s| sed -e 's/^/"\/mnt\/efs\/atf\/results\//g' -e 's/$/"/g' -e 's/ /\\ /g' | xargs -I % sh -c "cp -r -t $tempdir % && rm -rfv %");
done;

>&2 echo "copying graphs"
for s in `ls /mnt/efs/atf/graphs/ | egrep -o '[0-9]{4}-[0-9]+-[0-9]+' | sort --unique`; do 
	tempdir="/mnt/efs/atf/result-archives/results-$s"; 
	(mkdir $tempdir);
	(ls /mnt/efs/atf/graphs/ | egrep $s ) | while read f
	 do
		baseDir="/mnt/efs/atf/graphs"
		escapedF=$(echo $f |sed -e 's/ /\\ /g')
		currentFileName="$baseDir/$f"
		newFileName="$baseDir/graphing-$f"
		(cp -r "$currentFileName" "$newFileName")
	done
	(ls /mnt/efs/atf/graphs/ | egrep $s| sed -e 's/^/"\/mnt\/efs\/atf\/graphs\//g' -e 's/$/"/g' -e 's/ /\\ /g' | xargs -I % sh -c "cp -r -t $tempdir % && rm -rfv %"); 
	(ls /mnt/efs/atf/graphs | sed -e 's/^/"\/mnt\/efs\/atf\/graphs\//g' -e 's/$/"/g' -e 's/ /\\ /g' | xargs -I % sh -c "cp -v -t $tempdir % && rm -v %");
done;
