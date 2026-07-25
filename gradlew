#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a symlink
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls -ld "$PRG"
    PRG=`readlink "$PRG"`
done
SCRIPTDIR=`dirname "$PRG"`
APP_HOME=`cd "$SCRIPTDIR/.." >/dev/null && pwd -P` || exit

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "$*"
} >&2

die () {
    echo "$*" >&2
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MSYS* | MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/bin/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/bin/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="`command -v java`"
fi

if [ ! -x "$JAVACMD" ] ; then
    die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

if [ -z "$JAVA_HOME" ] ; then
    warn "WARNING: JAVA_HOME environment variable is not set."
fi

JAVA_OPTS="-XX:MaxMetaspaceSize=64m $JAVA_OPTS"

# Increase the default stack size.
JAVA_OPTS="$JAVA_OPTS -Xss2m"

# Collect all arguments for the java command, stacking in reverse order:
#   * Args from the command line
#   * The main class name
#   * -classpath
#   * -D...sysproperties
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if [ "$cygwin" = "true" -o "$msys" = "true" ] ; then
    APP_HOME=`cygpath --path --mixed "$APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
    JAVACMD=`cygpath --mixed "$JAVACMD"`
    for javadir in "$JAVA_HOME/jre/bin" "$JAVA_HOME/bin"
    do
      if [ -e "$javadir/java.exe" ]; then
        JAVACMD="`cygpath --mixed "$javadir/java.exe"`"
        break
      fi
    done
fi

# Split up the JVM_OPTS And GRADLE_OPTS values into an array, following the shell quoting and substitution rules
function splitJvmOpts() {
    local jvmOpts=("$@")
    local i
    for i in "${!jvmOpts[@]}"
    do
        case ${jvmOpts[i]} in
          -Xmx*|-Xms*|-XX*)
            DEFAULT_JVM_OPTS="$DEFAULT_JVM_OPTS "${jvmOpts[i]}"
            ;;
          *)
            if [ -n "${jvmOpts[i]}" ] ; then
              eval arg="$jvmOpts[i]"
              DEFAULT_JVM_OPTS="$DEFAULT_JVM_OPTS $arg"
            fi
            ;;
        esac
    done
}
eval splitJvmOpts $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS
JVM_OPTS="$DEFAULT_JVM_OPTS"

# by default we should be in the correct project dir, but let's be safe
if [ ! -d "$APP_HOME" ] ; then
    # try to locate the gradle folder
    cd "$( dirname "$0" )/.." >/dev/null
fi

exec "$JAVACMD" "${JVM_OPTS[@]}" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
