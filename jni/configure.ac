AC_INIT([baratine],0.10-SNAPSHOT)
AC_CONFIG_AUX_DIR(make)

program_name=baratine
program_display=Baratine

AC_SUBST(program_name)
AC_SUBST(program_display)

dnl Get os info
AC_CANONICAL_SYSTEM

AC_PREFIX_DEFAULT(/usr/local/share)

FULL_VERSION="$program_display $PACKAGE_VERSION -- `date`"
AC_SUBST(FULL_VERSION)

dnl Checks for programs.
AC_PROG_CC
#AC_PROG_LD
AC_PROG_EGREP

#
# install locations and variables (/var/lib, /var/log, /etc)
#

m4_include([make/install.ac])
m4_include([make/setuid.ac])

AC_SUBST(EGREP)

INCLUDES=""
plugins=common
LIBEXEC=libexec

AC_SUBST(plugins)
AC_SUBST(LIBEXEC)
dnl should be unnecessary
AC_SUBST(LD)

m4_include([make/b64.ac])
m4_include([make/jni.ac])
m4_include([make/openssl.ac])

#
# final cleanup
#

plugins="$plugins $baratine_ssl_plugin"

AC_CONFIG_FILES([
Makefile
make/install.sh
libbaratine/Makefile
])

if test -r "bin/baratine-dist.in"; then
  AC_CONFIG_FILES([
    bin/baratine-dist
    init.d/baratine
  ])
fi

AC_OUTPUT

echo ""
echo ""
echo "$program_display Configuration Summary:"
echo ""
echo "  $program_display    : $PACKAGE_VERSION"
echo "    home      : ${home_dir}"
echo "    root      : ${root_dir}"
echo "    conf      : ${conf_dir}"
echo "    log       : ${log_dir}"
echo "    plugins   : ${plugins}"

if test -n "${initd}"; then
  echo "    init      : ${initd}"
fi

echo ""

echo "  JAVA_HOME   : ${JAVA_HOME}"
echo ""

if test "$JNI" = "false" ; then
  echo "  JNI         : Disabled by --disable-jni"
elif test -z "$JNI"; then
  echo "  JNI         : No JNI has been found in JAVA_HOME"
  echo "    *** JNI libraries cannot be compiled ***"
else
  if test -n "${b64_jni}"; then
    echo "  JNI         : 64-bit"
  else
    echo "  JNI         : 32-bit"
  fi

  echo "    include       : ${JNI_INCLUDE}"
  echo "    CFLAGS        : ${JNI_CFLAGS}"
  echo "    cflags_shlib  : ${jni_cflags_shlib}"
  echo "    ld_shlib      : ${jni_ld_shlib}"
  echo "    ldflags_shlib : ${jni_ldflags_shlib}"
  echo "    libs_shlib    : ${jni_libs_shlib}"
  echo "    glibc_version : ${glibc_version}"

  if test -n "$epoll"; then
    echo "    epoll() for keepalives"
  elif test -n "$poll"; then
    echo "    poll() for keepalives"
  else
    echo "    select() for keepalives"
  fi

  echo ""

  if test "${ENABLE_SSL}" = "false"; then
    echo "  OPENSSL     : Disable by --disable-ssl"
    echo ""
  elif test -z "${OPENSSL_INCLUDE}"; then
    echo "  OPENSSL     : No OpenSSL has been found"
    echo "    *** OpenSSL libraries cannot be compiled ***"
    echo ""
  elif test -n "${baratine_ssl_plugin}"; then
    echo "  OPENSSL     : ${openssl_version}"
    echo "    include   : ${OPENSSL_INCLUDE}"
    echo "    lib       : ${OPENSSL_LIB}"
    echo "    libraries : ${SSL_LIBS}"
    echo ""
  fi
fi

if test -n "${apache}"; then
  echo "  APACHE          : ${apache_dir}"
  echo "    conf          : ${apache_conf}"
  echo "    libexec       : ${apache_libexec}"
  echo "    include       : ${apache_include}"
  echo "    CFLAGS        : ${apache_cflags}"
  echo "    cflags_shlib  : ${apache_cflags_shlib}"
  echo "    ld_shlib      : ${apache_ld_shlib}"
  echo "    ldflags_shlib : ${apache_ldflags_shlib}"
  echo "    libs_shlib    : ${apache_libs_shlib}"
  echo "    apache_shm    : ${apache_shm}"
  echo "    apache_24     : ${apache_24}"

  echo ""
fi

if test -n "${is_dev}"; then
  echo "  DEVELOPMENT BUILD"
  echo ""
fi

echo ""
