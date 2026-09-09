use jni::{
    objects::{JClass, JString},
    sys::{jint, jlong, jstring},
    JNIEnv,
};
use monica_steam_core::{generate_auth_code, generate_confirmation_hash, seconds_remaining};
use std::ptr;

fn read_jstring(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    env.get_string(value).ok().map(Into::into)
}

fn write_jstring(env: &mut JNIEnv<'_>, value: String) -> jstring {
    env.new_string(value)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeGenerateAuthCode(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    shared_secret: JString<'_>,
    unix_time_seconds: jlong,
) -> jstring {
    let Some(shared_secret) = read_jstring(&mut env, &shared_secret) else {
        return write_jstring(&mut env, String::new());
    };
    let code = generate_auth_code(&shared_secret, unix_time_seconds)
        .unwrap_or_default();
    write_jstring(&mut env, code)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeGenerateConfirmationHash(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    identity_secret: JString<'_>,
    unix_time_seconds: jlong,
    tag: JString<'_>,
) -> jstring {
    let Some(identity_secret) = read_jstring(&mut env, &identity_secret) else {
        return write_jstring(&mut env, String::new());
    };
    let Some(tag) = read_jstring(&mut env, &tag) else {
        return write_jstring(&mut env, String::new());
    };
    let hash = generate_confirmation_hash(&identity_secret, unix_time_seconds, &tag)
        .unwrap_or_default();
    write_jstring(&mut env, hash)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeSecondsRemaining(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    unix_time_seconds: jlong,
) -> jint {
    seconds_remaining(unix_time_seconds)
}
