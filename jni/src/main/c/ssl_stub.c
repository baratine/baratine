#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <memory.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <jni.h>

#include "resin_os.h"

int
ssl_create(server_socket_t *ss, ssl_config_t *config)
{
  return 0;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_vfs_QJniSocket_getClientCert(JNIEnv *env,
                                             jobject obj,
                                             jint fd,
                                             jbyteArray buf,
                                             jint offset,
                                             jint length)
{
  return 0;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_initConfig(JNIEnv *env,
					      jobject obj,
					      jstring jCertificateFile,
					      jstring jKeyFile,
					      jstring jPassword,
					      jstring jCertificateChainFile,
					      jstring jCaCertificatePath,
					      jstring jCaCertificateFile,
					      jstring jCaRevocationPath,
					      jstring jCaRevocationFile,
					      jstring jCipherSuite,
                                              jboolean jIsHonorCipherOrder,
					      jstring jCryptoDevice,
					      jint protocol,
					      jboolean uncleanShutdown)
{
  resin_printf_exception(env, "com/caucho/config/ConfigException",
			 "The resinssl library was not configured for OpenSSL.  It has been stubbed out instead.  Please check the ./configure; make logs.");
  
  return 0;
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_setVerify(JNIEnv *env,
					     jobject obj,
					     jint p_config,
					     jstring jVerifyClient,
					     jint jVerifyDepth)
{
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_setSessionCache(JNIEnv *env,
						   jobject obj,
						   jint p_config,
						   jboolean enable,
						   jint timeout)
{
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_nativeInit(JNIEnv *env,
					      jobject obj,
					      jint p_config)
{
  
}

/**
 * Needs to match ssl_stub.c
 */
JNIEXPORT int JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_open(JNIEnv *env,
					jobject obj,
					int fd,
					int p_config)
{
  resin_printf_exception(env, "java/io/IOException",
			 "The resinssl library was not configured for OpenSSL.  It has been stubbed out instead.  Please check the ./configure; make logs.");
  
  return 0;
}

/**
 * Needs to match ssl_stub.c
 */
JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_initSystemNative(JNIEnv *env)
{
  return false;
}

/**
 * Needs to match ssl_stub.c
 */
JNIEXPORT void JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_closeSystemNative(JNIEnv *env)
{
}
