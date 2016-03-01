CC=gcc
CFLAGS_SHLIB=@CFLAGS_SHLIB@
LD=gcc
OS=
SO=dylib
CPU=i386
LDFLAGS=@jni_ldflags@
LDFLAGS_SHLIB=-arch x86_64 -dynamiclib -undefined suppress -flat_namespace
PROXY_LIBS=
resin_os=@resin_os@
LIBEXEC=libexec64
native_dir=native/macosx-x86_64
resin_home=$(DESTDIR)@resin_home@
build_dir=/Users/ferg/ws5/baratine
JNI_INCLUDE=-I/Library/Java/JavaVirtualMachines/jdk1.8.0_20.jdk/Contents/Home/include -I/Library/Java/JavaVirtualMachines/jdk1.8.0_20.jdk/Contents/Home/include/darwin
INCLUDES =  -I/usr/local/opt/openssl/include $(JNI_INCLUDE) -I../common -I$(resin_os)
CFLAGS = -g -O2 -DB64 -DHAS_SOCK_TIMEOUT -DHAS_JVMTI -D_POSIX_PTHREAD_SEMANTICS -DRESIN_HOME=\"$(resin_home)\" $(INCLUDES) -DCPU=\"$(CPU)\" -DOS=$(OS)  -DSHARED_MODULE
OBJ= jni_vfs.o profile.o heap.o lru.o symbol.o crc64.o stat.o mmap_file.o ipc.o boot.o java.o jni_os.o jni_jvmti.o jni_socket.o memory.o std.o

all	: libbaratine.$(SO)

libbaratine.so	: $(OBJ)
	$(CC) $(LDFLAGS_SHLIB) -o $@ $(OBJ) $(PROXY_LIBS)

libbaratine.dylib	: $(OBJ)
	$(CC) $(LDFLAGS_SHLIB) -framework JavaVM -o $@ $(OBJ) $(PROXY_LIBS)

boot.o	: baratine.h Makefile
jni_vfs.o	: baratine.h Makefile
lru.o	: baratine.h Makefile
profile.o	: baratine.h Makefile
symbol.o	: baratine.h Makefile
crc64.o		: baratine.h Makefile
mmap_file.o	: baratine.h Makefile
ipc.o	: baratine.h Makefile
heap.o	: baratine.h Makefile
stat.o	: baratine.h Makefile

clean	:
	- rm *.o *.lo *.la *.so *.jnilib *.dylib *.$(SO)

install	: libbaratine.$(SO)
	@ echo "Installing baratine jni"
	@ - mkdir -p $(build_dir)/$(native_dir)
	cp libbaratine.$(SO) $(build_dir)/$(native_dir)
