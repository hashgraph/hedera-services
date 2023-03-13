To do list
==========
Command line parameter change to json config ?


How it works
============

	If Settings.enableEventStreaming is enabled, platform would launch an extra thread running as streamClient.

	The last two elements of address line of config.txt defines the stream server host name and port number.
	For example, with the following address line

		 address,  A, Alice,    1, 127.0.0.1, 50204, 127.0.0.1, 50204, 127.0.0.1, 50051

	The stream server is running on host 127.0.0.1 and listening port 50051.

	When stream client send each event over socket and separate each event by marker
	Settings.commEventNext.


	When stream server writes to file, event is separated by marker Settings.commEventLast.

	Following fields of an event is written to file

		Creator ID
		Creator Sequence
		Other ID
		Other Sequence

		Transactions if Settings.stripTransactions is enabled

		Timestamp created
		Event Signature
		Settings.commEventLast as marker





Major Changes
=============

1) A new section in Settings.java to configure stream related parameters

2) New StreamSSLContext.java

   Loading private keystore and trust keystore to check whether 4th key pair exist, if so
   then initialize SSLContext to be used later

3) New StreamingClient.java

   Check if SSLContext is available for streaming, if so create a thread for connecting to server
   and retrieve event from queue and serialize it and send over socket

4) New StreamingServer.java

   Check if SSLContext is available for streaming, if so create a thread for listening connection,
   once receives a marker for event, deserializes the following bytes, strips off transaction bytes
   and writes other bytes to file

5) New StreamingFIleStorege.jva

   Accept bytes submitted by StreamingServer, saving to files, creating new files if necessary

6) Browser.java

   When parsing address lines from config.txt, checking whether there are extra fields for
   stream server IP address and port number.
   Also accordingly save private keystore and trust keystore in a map data structure and
   using small case node name as key.
   Then save those property into platform

7) EventFlow.java

    Added a new blocking queue forStream, any consensus event inserted to queue forCons,
    also inserted to queue forStream.


8) Platform.java

 	Inside run() function, check if streaming property has been set.
 	If so, start a client thread, which queries the forStream queue continuously.



Local PC Test with TLS
======================

	1) Goto directory /sdk/data/keys and run script generate.sh, make sure gen_stream_key is set to 1

	2) Launch stream server from /sdk/ directory

		java -Dkey=data/keys/private-stream.pfx -Dcert=data/keys/public.pfx -Dport=50052 -classpath swirlds.jar:data/apps/PlatformTestingDemo.jar com.swirlds.demo.platform.streamevent.StreamServerDemo

    which will generate *.evts file under directory stream_50052/

	3) Launch PlatformTestingDemo App from /sdk/ directory

		java -jar swirlds.jar

	4) Test and varify event file

	    java -Dfile=../sdk_mirror/stream_50052/2018-11--04_20-36-57_min.evts  -classpath swirlds.jar:data/apps/PlatformTestingDemo.jar com.swirlds.demo.platform.streamevent.LoadEventFile

AWS Testing
============


	1) Goto directory /sdk/data/keys and CHECK script generate.sh, make sure gen_stream_key is set to 1

	2) build all project from directory platform-swirlds

	3) Apply unreleased/PlatformTestingDemo/stream.diff to update script, these changes are not checked in because
	   it may impact nightly automated tests

	4) Launch test script startAwsInstancesAndRunTests.sh _FCFSTest.sh

	   After few second, you will lines like

	       Uploading to node 0, ip xx.xxx.xxx.xx

	  Using your AWS console to find machine DNS name of the node 0 and use a new terminal tab and SSH to login remotely

	  For example

	  	ssh -x -i my-key.pem ec2-user@ec2-34-239-152-163.compute-1.amazonaws.com

	  Once login, changed to directory ~/remoteExperiment and run Stream Server

	  	java -Dkey=data/keys/private-stream.pfx -Dcert=data/keys/public.pfx -classpath swirlds.jar:data/apps/PlatformTestingDemo.jar com.swirlds.platform.streamevent.StreamServerDemo

	  Wait for auto test script to finish to show some log like this,

		Woke up at: Tue Sep  4 19:43:43 CDT 2018
		Waiting for process 2363
		..................................................................................................

	  but it won't be able to retrieve the result, it's waiting all java process to be killed.
	  You MUST manually kill/stop StreamServerDemo, then the auto test script can retrieve its results

