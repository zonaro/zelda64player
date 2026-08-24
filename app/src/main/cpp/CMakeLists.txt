# Zelda 64 Player - native build.
#
# Builds the rcheevos library (RetroAchievements runtime, MIT licensed, see
# rcheevos/LICENSE) together with the app JNI bridge into libra_jni.so.
# rcheevos sources are vendored unmodified from upstream tag v12.4.0
# (https://github.com/RetroAchievements/rcheevos). Do not edit them locally;
# upgrade by replacing the vendored snapshot with a newer release tag.
#
# Not compiled (unused here): rc_libretro.c (libretro-frontend helpers; the app
# maps N64 memory itself through the vendored LibretroDroid module) and
# rc_client_raintegration.c (RetroAchievements toolkit, Windows-only).

cmake_minimum_required(VERSION 3.22.1)
project(zelda64player_native LANGUAGES C)

set(RCHEEVOS_DIR rcheevos)

set(RCHEEVOS_SOURCES
    ${RCHEEVOS_DIR}/src/rapi/rc_api_common.c
    ${RCHEEVOS_DIR}/src/rapi/rc_api_editor.c
    ${RCHEEVOS_DIR}/src/rapi/rc_api_info.c
    ${RCHEEVOS_DIR}/src/rapi/rc_api_runtime.c
    ${RCHEEVOS_DIR}/src/rapi/rc_api_user.c
    ${RCHEEVOS_DIR}/src/rhash/aes.c
    ${RCHEEVOS_DIR}/src/rhash/cdreader.c
    ${RCHEEVOS_DIR}/src/rhash/hash.c
    ${RCHEEVOS_DIR}/src/rhash/hash_disc.c
    ${RCHEEVOS_DIR}/src/rhash/hash_encrypted.c
    ${RCHEEVOS_DIR}/src/rhash/hash_rom.c
    ${RCHEEVOS_DIR}/src/rhash/hash_zip.c
    ${RCHEEVOS_DIR}/src/rhash/md5.c
    ${RCHEEVOS_DIR}/src/rcheevos/alloc.c
    ${RCHEEVOS_DIR}/src/rcheevos/condition.c
    ${RCHEEVOS_DIR}/src/rcheevos/condset.c
    ${RCHEEVOS_DIR}/src/rcheevos/consoleinfo.c
    ${RCHEEVOS_DIR}/src/rcheevos/format.c
    ${RCHEEVOS_DIR}/src/rcheevos/lboard.c
    ${RCHEEVOS_DIR}/src/rcheevos/memref.c
    ${RCHEEVOS_DIR}/src/rcheevos/operand.c
    ${RCHEEVOS_DIR}/src/rcheevos/rc_validate.c
    ${RCHEEVOS_DIR}/src/rcheevos/richpresence.c
    ${RCHEEVOS_DIR}/src/rcheevos/runtime.c
    ${RCHEEVOS_DIR}/src/rcheevos/runtime_progress.c
    ${RCHEEVOS_DIR}/src/rcheevos/trigger.c
    ${RCHEEVOS_DIR}/src/rcheevos/value.c
    ${RCHEEVOS_DIR}/src/rc_client.c
    ${RCHEEVOS_DIR}/src/rc_client_external.c
    ${RCHEEVOS_DIR}/src/rc_compat.c
    ${RCHEEVOS_DIR}/src/rc_util.c
    ${RCHEEVOS_DIR}/src/rc_version.c
)

add_library(ra_jni SHARED
    ra_jni.c
    ${RCHEEVOS_SOURCES}
)

target_include_directories(ra_jni PRIVATE
    ${RCHEEVOS_DIR}/include
    # rc_version.h / rc_compat.h live alongside the sources upstream.
    ${RCHEEVOS_DIR}/src
)

# Enables the rhash-backed identify/load-game paths in rc_client.c and the
# matching block in ra_jni.c (nativeIdentifyAndLoadGame). Without this the
# client can never load a game and achievements stay unavailable in-session.
target_compile_definitions(ra_jni PRIVATE RC_CLIENT_SUPPORTS_HASH)

target_compile_options(ra_jni PRIVATE -Wall)

# Android log for later phases (rc_client logging callback).
find_library(log-lib log)
target_link_libraries(ra_jni ${log-lib})
