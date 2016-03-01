CC=gcc
CFLAGS_SHLIB=-DSHARED_MODULE
CFLAGS_OPENSSL= -DSSL_ENGINE
LD=gcc
OS=
SO=dylib
CPU=i386
LDFLAGS=@jni_ldflags@
LDFLAGS_SHLIB=-arch x86_64 -dynamiclib -undefined suppress -flat_namespace
PROXY_LIBS=  -lssl -lcrypto
LIBEXEC=libexec64
build_dir=/Users/ferg/ws5/baratine
native_dir=native/macosx-x86_64


JNI_INCLUDE=-I/Library/Java/JavaVirtualMachines/jdk1.8.0_20.jdk/Contents/Home/include -I/Library/Java/JavaVirtualMachines/jdk1.8.0_20.jdk/Contents/Home/include/darwin
INCLUDES =  -I/usr/local/opt/openssl/include $(JNI_INCLUDE) -I../common -I../baratine
CFLAGS = -g -O2 -DB64 -DHAS_SOCK_TIMEOUT -DHAS_JVMTI -D_POSIX_PTHREAD_SEMANTICS $(INCLUDES) -DCPU=\"$(CPU)\" -DOS=$(OS)  -DSHARED_MODULE  -DSSL_ENGINE
OBJ=ssl.o ../baratine/memory.o ../baratine/std.o ../baratine/java.o

all	: libbaratinessl.$(SO) libbaratinessl_npn

libbaratinessl.so	: $(OBJ) ssl_npn_stub.o
	$(CC) $(LDFLAGS_SHLIB) -o $@ $(OBJ) ssl_npn_stub.o $(PROXY_LIBS)

libbaratinessl.dylib	: $(OBJ) ssl_npn_stub.o
	$(CC) $(LDFLAGS_SHLIB) -framework JavaVM -o $@ $(OBJ) ssl_npn_stub.o $(PROXY_LIBS)

libbaratinessl_npn	: libbaratinessl_npn.$(SO)

libbaratinessl_npn.so	: $(OBJ) ssl_npn.o
	$(CC) $(LDFLAGS_SHLIB) -o $@ $(OBJ) ssl_npn.o $(PROXY_LIBS) -L/usr/local/opt/openssl/lib 

libbaratinessl_npn.dylib	: $(OBJ) ssl_npn.o
	$(CC) $(LDFLAGS_SHLIB) -framework JavaVM -o $@ $(OBJ) ssl_npn.o $(PROXY_LIBS) -L/usr/local/opt/openssl/lib 

$(OBJ)		: ../baratine/baratine_os.h Makefile
ssl_npn.o	: ../baratine/baratine_os.h Makefile
ssl_npn_stub.o	: ../baratine/baratine_os.h Makefile

clean	:
	- rm -f *.o *.$(SO)

install	: libbaratinessl.$(SO) libbaratinessl_npn
	@ echo "Installing baratinessl"
	@ - mkdir -p $(build_dir)/$(native_dir)
	cp libbaratinessl.$(SO) $(build_dir)/$(native_dir)
	- cp libbaratinessl_npn.$(SO) $(build_dir)/$(native_dir)
