#! /bin/sh
cp config-mount/*.properties data/config 
cp config-mount/* . 
sed -i "s/NODE_ID/${NODE_ID}/" settings.txt
cat settings.txt
java -cp 'data/lib/*' -Dflag=1 -Dfile.encoding='utf-8' \
  com.swirlds.platform.Browser
