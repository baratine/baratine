.SUFFIXES: .o .lo

CC=gcc
CFLAGS=-g -O2 -DB64 -DHAS_SOCK_TIMEOUT -DHAS_JVMTI -D_POSIX_PTHREAD_SEMANTICS @apache_cflags@ @apache_cflags_shlib@ @apache_shm@
LD=gcc
LD_SHLIB=@apache_ld_shlib@
LDFLAGS=@apache_ldflags@
LDFLAGS_SHLIB=@apache_ldflags_shlib@
OBJ=stream.o config.o memory.o
OBJ_PICS=stream.lo config.lo memory.lo
INCLUDES= -I/usr/local/opt/openssl/include

all	: $(OBJ) $(OBJ_PICS)

.c.lo	:
	$(LIBTOOL) --mode=compile $(CC) -o $@ -c $(INCLUDES) $(CFLAGS) $<

.c.o	:
	$(CC) -c $(INCLUDES) $(CFLAGS) $<

install	:
	@ echo "Installing plugin common"

clean	:
	-rm *.o *.lo *.la *.so *.jnilib *.dylib
	-rm -rf .libs

$(OBJ)	: Makefile cse.h
$(OBJ_PICS)	: Makefile cse.h
