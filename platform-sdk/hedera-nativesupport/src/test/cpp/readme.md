Go to the dir containing the java file Greeter.java and execute

`javac -h . Greeter.java`
It will generate a `.h` file. Move it to `src/main/cpp` and set the current directory there.
If in darwin execute:
`g++ -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" -shared -o greeter.dylib cppGreeter.cpp`

for cross compiling, you will need to download the Adoption-JDK corresponding to the os/arch version crosscompiling to:
and unzip the `jdk.../include/` dir into `$DOWNLOAD_DIR`

* For Linux
1. Install
`brew tap messense/macos-cross-toolchains`
`brew install x86_64-unknown-linux-gnu`
2. Execute
`x86_64-unknown-linux-gnu-g++ -I"$DOWNLOAD_DIR/include/" -I"$DOWNLOAD_DIR/include/linux/" -shared -o greeter.so cppGreeter.cpp -fPIC`

* For Windows
1. Install
`brew install mingw-w64`
2. Execute
`x86_64-w64-mingw32-g++ -I"$$DOWNLOAD_DIR/include/" -I"$DOWNLOAD_DIR/include/win32/" -shared -o greeter.dll cppGreeter.cpp`

To compile natively on Linux:
`g++ -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -o greeter.so -shared cppGreeter.cpp -static-libgcc -static-libstdc++`
