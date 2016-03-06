/*
 * Copyright (c) 1998-2014 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#ifdef WIN32
#ifndef _WINSOCKAPI_ 
#define _WINSOCKAPI_
#endif 
#include <windows.h>
#else
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <sys/time.h>
#include <grp.h>
#include <pwd.h>
#include <sys/resource.h>
#include <limits.h>
#endif

#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <signal.h>
#include <errno.h>
/* probably system-dependent */
#include <jni.h>

static char *
get_utf8(JNIEnv *env, jstring jaddr, char *buf, int buflen)
{
  const char *temp_string = 0;

  temp_string = (*env)->GetStringUTFChars(env, jaddr, 0);
  
  if (temp_string) {
    strncpy(buf, temp_string, buflen);
    buf[buflen - 1] = 0;
  
    (*env)->ReleaseStringUTFChars(env, jaddr, temp_string);
  }

  return buf;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniProcess_isNativeBootAvailable(JNIEnv *env, jobject obj)
{
#ifdef WIN32
  return 0;
#else
  return 1;
#endif  
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniProcess_clearSaveOnExec(JNIEnv *env, jobject obj)
{
#ifdef WIN32
  return 0;
#else
  {
    int fd;
   
    for (fd = 3; fd < 4096; fd++) {
      int arg = fcntl(fd, F_GETFD, 0);
      if (arg >= 0) {
	arg |= FD_CLOEXEC;
	fcntl(fd, F_SETFD, arg);
      }
    }

    return 1;
  }
#endif
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniProcess_getFdMax(JNIEnv *env, jobject obj)
{
#ifdef WIN32
  return -1;
#else  
  struct rlimit rlimit;

  if (getrlimit(RLIMIT_NOFILE, &rlimit) != 0)
    return -1;

  return rlimit.rlim_cur;
#endif  
}

#ifdef WIN32
JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniProcess_setFdMax(JNIEnv *env, jobject obj)
{
  return -1;
}

#else  
JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniProcess_setFdMax(JNIEnv *env, jobject obj)
{

  struct rlimit orig_rlimit;
  struct rlimit rlimit;
  struct rlimit set_rlimit;

  set_rlimit.rlim_cur = set_rlimit.rlim_max = 128 * 1024;

  if (setrlimit(RLIMIT_NOFILE, &set_rlimit) != 0) {
    set_rlimit.rlim_cur = set_rlimit.rlim_max = RLIM_INFINITY;
    
    setrlimit(RLIMIT_NOFILE, &set_rlimit);
  }

  if (getrlimit(RLIMIT_NOFILE, &rlimit) == 0)
    return rlimit.rlim_cur;
  else
    return -1;
}
#endif

jboolean
Java_com_caucho_v5_jni_JniProcess_exec(JNIEnv *env,
				     jobject obj,
				     jobjectArray j_argv,
				     jobjectArray j_envp,
				     jstring j_chroot,
				     jstring j_pwd,
				     jstring j_user,
				     jstring j_group)
{
  char **argv;
  char **envp;
  char chroot_path[4096];
  char pwd[4096];
  char user[256];
  char group[256];
  int uid = -1;
  int gid = -1;
  int len;
  int i;
  int pipe_fds[2];
  int pid;
  jclass c_jni_process;
  jfieldID f_stdoutFd;
  jfieldID f_pid;

#ifdef WIN32
  if (1) return -1;
#endif /* WIN32 */
  
  user[0] = 0;
  group[0] = 0;
  chroot_path[0] = 0;
  
  if (! j_argv) {
    baratine_printf_exception(env, "java/lang/NullPointerException", "argv");
    return 0;
  }
  
  if (! j_envp) {
    baratine_printf_exception(env, "java/lang/NullPointerException", "argv");
    return 0;
  }
  
  if (! j_pwd) {
    baratine_printf_exception(env, "java/lang/NullPointerException", "pwd");
    return 0;
  }

  c_jni_process = (*env)->FindClass(env, "com/caucho/v5/jni/JniProcess");

  if (! c_jni_process) {
    baratine_printf_exception(env, "java/lang/NullPointerException", "can't load JniProcess");
    return 0;
  }

  f_stdoutFd = (*env)->GetFieldID(env, c_jni_process, "_stdoutFd", "I");

  if (! f_stdoutFd) {
    baratine_printf_exception(env, "java/lang/NullPointerException", "can't load field");
    return 0;
  }

  f_pid = (*env)->GetFieldID(env, c_jni_process, "_pid", "I");

  if (! f_pid) {
    baratine_printf_exception(env, "java/lang/NullPointerException", "can't load field");
    return 0;
  }

  len = (*env)->GetArrayLength(env, j_argv);
  argv = malloc((len + 1) * sizeof(char*));
  argv[len] = 0;
  
  for (i = 0; i < len; i++) {
    jstring j_string;

    j_string = (*env)->GetObjectArrayElement(env, j_argv, i);

    if (j_string) {
      int strlen = (*env)->GetStringUTFLength(env, j_string);
      
      argv[i] = (char *) malloc(strlen + 1);
    
      argv[i] = get_utf8(env, j_string, argv[i], strlen + 1);
    }
  }

  len = (*env)->GetArrayLength(env, j_envp);
  envp = malloc((len + 1) * sizeof(char*));
  envp[len] = 0;
  
  for (i = 0; i < len; i++) {
    jstring j_string;

    j_string = (*env)->GetObjectArrayElement(env, j_envp, i);

    if (j_string) {
      int strlen = (*env)->GetStringUTFLength(env, j_string);
      
      envp[i] = (char *) malloc(strlen + 1);
    
      envp[i] = get_utf8(env, j_string, envp[i], strlen + 1);
    }
  }

  if (j_chroot) {
    int strlen = (*env)->GetStringUTFLength(env, j_chroot);

    get_utf8(env, j_chroot, chroot_path, strlen + 1);
  }
  else
    chroot_path[0] = 0;

  if (j_pwd) {
    int strlen = (*env)->GetStringUTFLength(env, j_pwd);

    get_utf8(env, j_pwd, pwd, strlen + 1);
  }

#ifndef WIN32
  if (j_user) {
    struct passwd *passwd;
    int strlen = (*env)->GetStringUTFLength(env, j_user);

    get_utf8(env, j_user, user, strlen + 1);

    passwd = getpwnam(user);

    if (! passwd) {
      baratine_printf_exception(env, "java/lang/IllegalArgumentException",
			     "setuid '%s' is an unknown <user-name>",
                             user);
      return 0;
    }
    
    uid = passwd->pw_uid;
    gid = passwd->pw_gid;
  }
  
  if (j_group) {
    struct group *group_ent;
    int strlen = (*env)->GetStringUTFLength(env, j_group);

    get_utf8(env, j_group, group, strlen + 1);

    group_ent = getgrnam(group);

    if (! group_ent) {
      baratine_printf_exception(env, "java/lang/IllegalArgumentException",
			     "setuid '%s' is an unknown <group-name>", group);
      return 0;
    }
    
    gid = group_ent->gr_gid;
  }
  
  if (pipe(pipe_fds) < 0) {
    baratine_printf_exception(env, "java/lang/IllegalStateException",
                           "pipe failed");
    return 0;
  }

  pid = fork();

  if (pid > 0) {
    close(pipe_fds[1]);

    (*env)->SetIntField(env, obj, f_stdoutFd, pipe_fds[0]);
    (*env)->SetIntField(env, obj, f_pid, pid);
    
    return 1;
  }
  else if (pid < 0) {
    close(pipe_fds[0]);
    close(pipe_fds[1]);
    
    baratine_printf_exception(env, "java/lang/NullPointerException",
			   "can't fork");
    return 0;
  }

  close(pipe_fds[0]);

  /*
  if (fork())
    exit(0);
  
  setsid();
  */

#ifndef WIN32  
  if (chroot_path[0]) {
    if (chroot(chroot_path) < 0) {
      fprintf(stderr, "%s:%d JniProcess.exec chroot failed %s\n",
              __FILE__, __LINE__, chroot_path);
    }
  }
#endif

  if (gid >= 0)
    setregid(gid, gid);
  
  if (uid >= 0) {
    setreuid(uid, uid);

    if (getuid() != uid) {
      fprintf(stderr, "Can't setuid to %d, received %d\n", uid, getuid());
      exit(1);
    }
  }
  
  if (chdir(pwd) < 0) {
    fprintf(stderr, "%s:%d JniProcess.exec chdir failed %s\n",
            __FILE__, __LINE__, pwd);
  }
#endif

  dup2(pipe_fds[1], 1);
  dup2(pipe_fds[1], 2);

  for (i = 0; envp[i]; i++)
    putenv(envp[i]);

  execvp(argv[0], argv);

  fprintf(stderr, "exec failed %s -> %d\n", argv[0], errno);
  exit(1);
  
  return -1;
}

jint
Java_com_caucho_v5_jni_JniProcess_waitpid(JNIEnv *env,
					   jobject obj,
					   jint pid,
					   jboolean is_block)
{
  int status = 0;
  int result;

#ifdef WIN32
  return -1;
#else
  if (pid < 0) {
    baratine_printf_exception(env, "java/lang/IllegalArgumentException",
			   "invalid pid");
    return -1;
  }

  result = waitpid(pid, &status, is_block ? 0 : WNOHANG);

  if (result == 0)
    return -1;
  
  if (result < 0) {
    baratine_printf_exception(env, "java/lang/IllegalArgumentException",
			   "invalid result %d", result);
    return -1;
  }

  if (WIFSIGNALED(status))
    return 128 + WTERMSIG(status);
  else
    return WEXITSTATUS(status);
#endif
}
