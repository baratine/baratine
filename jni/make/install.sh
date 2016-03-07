build_dir=/Users/ferg/ws5/baratine/jni

abs_home_dir=/usr/local/share/baratine-0.10-SNAPSHOT
home_dir=${DESTDIR}${abs_home_dir}
root_dir=${DESTDIR}/var/lib/baratine
log_dir=${DESTDIR}/var/log/baratine
conf_dir=${DESTDIR}/etc/baratine

abs_usr_share=/usr/local/share
usr_share=${DESTDIR}${abs_usr_share}

abs_usr_bin=/usr/bin
usr_bin=${DESTDIR}${abs_usr_bin}

abs_initd=
initd=${DESTDIR}${abs_initd}
initd_dir=${DESTDIR}

libexec=libexec64

home_subdirs="$libexec bin lib"
root_subdirs_preserve=""
root_subdirs_overwrite="doc"

conf_files=""
conf_subdirs="keys licenses"

setuid_user=baratine
setuid_group=baratine
setuid_set=
user_add=
group_add=

program=baratine
name=Baratine

die()
{
  echo "ABORT: $1";
  exit 42
}

mkdir_safe()
{
  if test ! -e "$1"; then
  
    mkdir -p "$1" || die "$2 failed. Ensure $1 can be created by ${USER}, or run 'make install' as root.";

  else
    if test ! -w "$1"; then
      die "${name} $2 failed. Ensure $1 is writable by ${USER}, or run 'make install' as root."
    fi;
  fi;
}

copy_dir_preserve()
{
  root=$1
  dir_list=$2
  desc=$3
  
  for subdir in ${dir_list}; do
    if test -d "${subdir}"; then
      if test -e "${root}/${subdir}"; then
        echo "Preserving existing ${name} $desc subdir ${root}/${subdir}.";
      else
        echo "Installing ${name} ${name} subdir ${root}/${subdir}."
      
        mkdir -p "${root}/${subdir}" ||
          die "${name} ${name} subdir creation failed. Ensure ${root}/${subdir} can be created by ${USER}, or run 'make install' as root."

        if cp -r ${subdir}/* ${root}/${subdir}; then
          echo "updated ${root}/${subdir}"
        else
          echo "skipped ${subdir}"
        fi
      fi
    fi
  done
}  
  
copy_dir_replace()
{
  root=$1
  dir_list=$2
  desc=$3
  
  for subdir in ${dir_list}; do
    if test -d "${subdir}"; then
      if test -e "${root}/${subdir}"; then
        echo "Updating existing ${name} ${desc} subdir ${root}/${subdir}."
      else
        echo "Installing ${name} ${desc} subdir ${root}/${subdir}."
      
        mkdir -p ${root}/${subdir} ||
          die "${name} ${desc} subdir creation failed. Ensure ${root}/${subdir} can be created by ${USER}, or run 'make install' as root."

      fi
    
      cp -r ${subdir}/* ${root}/${subdir}
    fi
  done
}  
  
chmod_exe()
{
  file=$1

  if test -e "${file}"; then
    echo "Setting ${file} executable."
    
    chmod +x ${file} ||
      echo "WARNING: failed to set ${file} executable. Ensure ${file} is writable by ${USER}, or run 'make install' as root."
  else
    a=b
    # die "ABORT: ${home_dir}/bin/${file} does not exist. ${name} home install may have failed.";
  fi
}  

symlink_safe()
{
  name=$1
  source_dir=$2
  target_dir=$3

 echo "Installing ${name} symlink ${target_dir}/${name}"
 mkdir -p "${target_dir}" ||
  echo "WARNING: ${target_dir} directory creation failed. Ensure ${target_dir} is writable by ${USER}, or run 'make install' as root."
  
 ln -sf "${source_dir}/${name}" "${target_dir}/${name}" ||
  echo "WARNING: ${name} symlink creation failed. Ensure ${target_dir} is writable by ${USER}, or run 'make install' as root."
}

if test "$setuid_set" = "true"; then
  if test -r conf/baratine.cf; then
    cp conf/baratine.cf conf/baratine.cf.orig;
    awk '/setuid_user/ { print "setuid_user : ${setuid_user}"; next; }
         /setuid_group/ { print "setuid_group : ${setuid_group}"; next; }
        { print $0; }'
      conf/baratine.cf.orig > conf/baratine.cf
  fi
fi

if test "$root_dir" != "$build_dir"; then
  echo "Installing ${name} root $root_dir."
  
  mkdir_safe "$root_dir" "${name} root"

  copy_dir_preserve "${root_dir}" "${root_subdirs_preserve}" "root"
  
  copy_dir_replace "${root_dir}" "${root_subdirs_overwrite}" "root"
fi

if test "$home_dir" != "$build_dir"; then
  echo "Installing ${name} home ${home_dir}.";
  
  mkdir_safe "${home_dir}" "${name} home"

  copy_dir_replace "${home_dir}" "${home_subdirs}" "home" 
fi

if test -e "${log_dir}"; then
  echo "Preserving existing ${name} log ${log_dir}."
else
  echo "Installing ${name} log ${log_dir}."

  mkdir_safe "${log_dir}" "${name} log"
fi

if test -n "${abs_usr_share}"; then
  echo "Installing ${name} home symlink ${abs_usr_share}/${program}.";
  if test -w "${usr_share}"; then
    ln -sf "${abs_home_dir}" "${usr_share}/${program}" ||
      echo "WARNING: ${name} home symlink creation failed. Ensure ${usr_share} is writable by ${USER}, or run 'make install' as root."
  else
    echo "WARNING: ${name} home symlink install failed. Ensure ${usr_share} is writable by ${USER}, or run 'make install' as root.";
  fi

  if test -r ${home_dir}/bin/baratine-dist; then
    cp ${home_dir}/bin/baratine-dist ${home_dir}/bin/baratine
    
    symlink_safe baratine ${abs_usr_share}/${program}/bin ${usr_bin}
  fi
fi

chmod_exe "${home_dir}/bin/baratine"

if test "${conf_dir}" != "${build_dir}/conf"; then
  echo "Installing ${name} conf ${conf_dir}.";

  mkdir_safe "${conf_dir}" "${name} conf"
  
  copy_dir_preserve "${conf_dir}" "${conf_subdirs}" "conf"
  
  for file in ${conf_files}; do
    if test -f "conf/${file}"; then
      if test -f "${conf_dir}/${file}"; then
        echo "Preserving existing ${name} conf file ${conf_dir}/${file}.";
      else
        echo "Installing ${name} conf file ${conf_dir}/${file}.";
        cp conf/${file} ${conf_dir}/${file};
      fi
    fi
  done
fi

if test -n "${abs_initd}"; then
  if test -f "${initd}"; then
    echo "Preserving existing ${name} init.d script ${initd} in ${initd_dir}.";
  else
    echo "Installing ${name} init.d script ${initd}.";

    mkdir_safe "${initd_dir}" "${name} init.d script"
    
    cp init.d/${program} "${initd}";
    chmod +x "${initd}" ||
        echo "WARNING: failed to set ${name} init.d executable. Ensure ${initd} is writable by ${USER}, or run 'make install' as root.";

  fi
fi

if test "${setuid_set}" = "true"; then
  if test -n "${group_add}"; then
    echo "Creating setuid group ${setuid_group}.";
    egrep '${setuid_group}:' /etc/group 1>/dev/null 2>/dev/null
    if test $? = "0"; then
      echo "setuid group ${setuid_group} already exists.";
    else
      groupadd ${setuid_group};
      if test $? != "0"; then
        echo "WARNING: failed to create the ${setuid_group} group. The group may already exist, or run 'make install' as root.";
      fi;
    fi;
  fi;
  if test -n "${user_add}"; then
    echo "Creating setuid user ${setuid_user}.";
    egrep '${setuid_user}:' /etc/passwd 1>/dev/null 2>/dev/null;
    if test $? = "0"; then
      echo "setuid user ${setuid_user} already exists.";
    else
      useradd -d /nonexistent -s /bin/false -g "$setuid_group" "$setuid_user" ||
        echo "WARNING: failed to create the ${setuid_user} user. The user may already exist, or run 'make install' as root."

    fi;
  fi;
  if test -n "${user_add}"; then
    echo "Changing the owner of ${name} root ${root_dir} to ${setuid_user}:${setuid_group}.";
    chown -R ${setuid_user}:${setuid_group} ${root_dir}
    if test $? != "0"; then
      echo "WARNING: failed change owner of ${name} root to ${setuid_user}. Ensure ${root_dir} is writable by $$USER, or run 'make install' as root."
    fi;
  fi;
  if test -n "${group_add}"; then
    echo "Changing the owner of ${name} log ${log_dir} to ${setuid_user}:${setuid_group}."
    chown -R ${setuid_user}:${setuid_group} "${log_dir}"
    if test $? != "0"; then
      echo "WARNING: failed change owner of ${name} log to ${setuid_user}. Ensure ${log_dir} is writable by ${USER}, or run 'make install' as root."
    fi;
  fi;
fi
