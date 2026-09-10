#!/usr/bin/env python3
"""Compile the real RA JNI bridge on Linux and test with synthetic RAM/server (no network)."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
base = root / 'app/src/main/cpp'
java = Path(shutil.which('javac')).resolve().parents[1]
cmake = (base / 'CMakeLists.txt').read_text()
sources = re.search(r'set\(RCHEEVOS_SOURCES(.*?)\n\)', cmake, re.S).group(1).split()
with tempfile.TemporaryDirectory(prefix='zelda-ra-runtime-') as directory:
    output = Path(directory)
    library = output / 'libra_jni.so'
    # Android and desktop JNI headers differ in AttachCurrentThread's pointer type.
    command = [os.environ.get('CC', 'cc'), '-shared', '-fPIC', '-O1',
               '-Wno-error=incompatible-pointer-types', '-DRC_CLIENT_SUPPORTS_HASH',
               '-I' + str(java / 'include'), '-I' + str(java / 'include/linux'),
               '-I' + str(base / 'rcheevos/include'), '-I' + str(base / 'rcheevos/src'),
               str(base / 'ra_jni.c')]
    command += [str(base / source.replace('${RCHEEVOS_DIR}', 'rcheevos')) for source in sources]
    subprocess.run(command + ['-lpthread', '-lm', '-o', str(library)], check=True)
    subprocess.run(['javac', '-d', str(output),
                    str(root / 'scripts/tests/ra_runtime/RcheevosJni.java')], check=True)
    subprocess.run(['java', '-Dra.library=' + str(library), '-cp', str(output),
                    'br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni',
                    str(output / 'synthetic.z64')], check=True, timeout=20)
