use jni::{
    objects::{JByteArray, JClass, JString},
    sys::{jbyteArray, jint, jlong, jstring},
    JNIEnv,
};
use monica_steam_core::{
    cm::{encode_message, web_logon_body},
    generate_auth_code, generate_confirmation_hash, generate_login_approval_signature,
    generate_login_token_signature,
};
use std::ptr;

fn read_jstring(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    env.get_string(value).ok().map(Into::into)
}

fn write_jstring(env: &mut JNIEnv<'_>, value: String) -> jstring {
    env.new_string(value)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

fn write_jbytes(env: &mut JNIEnv<'_>, value: &[u8]) -> jbyteArray {
    env.byte_array_from_slice(value)
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
    let code = generate_auth_code(&shared_secret, unix_time_seconds).unwrap_or_default();
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
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeGenerateLoginApprovalSignature(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    shared_secret: JString<'_>,
    version: jint,
    client_id: jlong,
    steam_id: jlong,
) -> jbyteArray {
    let Some(shared_secret) = read_jstring(&mut env, &shared_secret) else {
        return ptr::null_mut();
    };
    let Ok(signature) = generate_login_approval_signature(
        &shared_secret,
        version,
        client_id,
        steam_id,
    ) else {
        return ptr::null_mut();
    };
    write_jbytes(&mut env, &signature)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeGenerateLoginTokenSignature(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    shared_secret: JString<'_>,
    token_id: jlong,
) -> jbyteArray {
    let Some(shared_secret) = read_jstring(&mut env, &shared_secret) else {
        return ptr::null_mut();
    };
    let Ok(signature) = generate_login_token_signature(&shared_secret, token_id) else {
        return ptr::null_mut();
    };
    write_jbytes(&mut env, &signature)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeEncodeCmMessage(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    e_msg: jint,
    steam_id: jlong,
    session_id: jint,
    body: JByteArray<'_>,
    job_id_source: jlong,
    job_id_target: jlong,
    target_job_name: JString<'_>,
) -> jbyteArray {
    let Ok(body) = env.convert_byte_array(&body) else {
        return ptr::null_mut();
    };
    let Some(target_job_name) = read_jstring(&mut env, &target_job_name) else {
        return ptr::null_mut();
    };
    let target_job_name = if target_job_name.is_empty() {
        None
    } else {
        Some(target_job_name.as_str())
    };
    let Ok(encoded) = encode_message(
        e_msg,
        steam_id,
        session_id,
        &body,
        job_id_source,
        job_id_target,
        target_job_name,
    ) else {
        return ptr::null_mut();
    };
    write_jbytes(&mut env, &encoded)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeWebLogonBody(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    web_logon_token: JString<'_>,
) -> jbyteArray {
    let Some(web_logon_token) = read_jstring(&mut env, &web_logon_token) else {
        return ptr::null_mut();
    };
    let Ok(encoded) = web_logon_body(&web_logon_token) else {
        return ptr::null_mut();
    };
    write_jbytes(&mut env, &encoded)
}
