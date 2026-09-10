use jni::{
    objects::{JByteArray, JClass, JString},
    sys::{jboolean, jbyteArray, jlong},
    JNIEnv,
};
use monica_steam_core::two_factor::{
    build_add_authenticator_request, build_finalize_authenticator_request,
    build_replace_continue_request, parse_add_authenticator_response,
    parse_finalize_authenticator_response, parse_replace_continue_response, AuthenticatorData,
    FinalizeAuthenticatorData,
};
use std::ptr;

const AUTHENTICATOR_MAGIC: &[u8; 4] = b"TFA1";
const FINALIZE_MAGIC: &[u8; 4] = b"TFF1";

fn read_jstring(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    env.get_string(value).ok().map(Into::into)
}

fn write_jbytes(env: &JNIEnv<'_>, value: &[u8]) -> jbyteArray {
    env.byte_array_from_slice(value)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

fn write_required_string(out: &mut Vec<u8>, value: &str) -> Option<()> {
    let bytes = value.as_bytes();
    let length = u32::try_from(bytes.len()).ok()?;
    out.extend_from_slice(&length.to_le_bytes());
    out.extend_from_slice(bytes);
    Some(())
}

fn write_optional_string(out: &mut Vec<u8>, value: Option<&str>) -> Option<()> {
    match value {
        Some(value) => {
            out.extend_from_slice(&1i32.to_le_bytes());
            write_required_string(out, value)?;
        }
        None => out.extend_from_slice(&0i32.to_le_bytes()),
    }
    Some(())
}

fn write_optional_i64(out: &mut Vec<u8>, value: Option<i64>) {
    match value {
        Some(value) => {
            out.extend_from_slice(&1i32.to_le_bytes());
            out.extend_from_slice(&value.to_le_bytes());
        }
        None => out.extend_from_slice(&0i32.to_le_bytes()),
    }
}

fn write_optional_i32(out: &mut Vec<u8>, value: Option<i32>) {
    match value {
        Some(value) => {
            out.extend_from_slice(&1i32.to_le_bytes());
            out.extend_from_slice(&value.to_le_bytes());
        }
        None => out.extend_from_slice(&0i32.to_le_bytes()),
    }
}

fn serialize_authenticator(value: &AuthenticatorData) -> Option<Vec<u8>> {
    let mut out = Vec::new();
    out.extend_from_slice(AUTHENTICATOR_MAGIC);
    write_optional_string(&mut out, value.shared_secret.as_deref())?;
    write_optional_string(&mut out, value.serial_number.as_deref())?;
    write_optional_string(&mut out, value.revocation_code.as_deref())?;
    write_optional_string(&mut out, value.uri.as_deref())?;
    write_optional_i64(&mut out, value.server_time);
    write_optional_string(&mut out, value.account_name.as_deref())?;
    write_optional_string(&mut out, value.token_gid.as_deref())?;
    write_optional_string(&mut out, value.identity_secret.as_deref())?;
    write_optional_string(&mut out, value.secret_1.as_deref())?;
    out.extend_from_slice(&value.status.to_le_bytes());
    write_required_string(&mut out, &value.phone_hint)?;
    out.extend_from_slice(&value.confirm_type.to_le_bytes());
    write_optional_i32(&mut out, value.steamguard_scheme);
    write_optional_string(&mut out, value.steam_id.as_deref())?;
    Some(out)
}

fn serialize_finalize(value: FinalizeAuthenticatorData) -> Vec<u8> {
    let mut out = Vec::with_capacity(16);
    out.extend_from_slice(FINALIZE_MAGIC);
    out.extend_from_slice(&(if value.success { 1i32 } else { 0 }).to_le_bytes());
    out.extend_from_slice(&(if value.want_more { 1i32 } else { 0 }).to_le_bytes());
    out.extend_from_slice(&value.status.to_le_bytes());
    out
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeBuildAddAuthenticator(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    steam_id: JString<'_>,
    auth_time: jlong,
    device_id: JString<'_>,
) -> jbyteArray {
    let Some(steam_id) = read_jstring(&mut env, &steam_id) else {
        return ptr::null_mut();
    };
    let Some(device_id) = read_jstring(&mut env, &device_id) else {
        return ptr::null_mut();
    };
    let Ok(request) = build_add_authenticator_request(&steam_id, auth_time, &device_id) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &request)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseAddAuthenticator(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(parsed) = parse_add_authenticator_response(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_authenticator(&parsed) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &encoded)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeBuildFinalizeAuthenticator(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    steam_id: JString<'_>,
    authenticator_code: JString<'_>,
    auth_time: jlong,
    activation_code: JString<'_>,
    validate_sms_code: jboolean,
) -> jbyteArray {
    let Some(steam_id) = read_jstring(&mut env, &steam_id) else {
        return ptr::null_mut();
    };
    let Some(authenticator_code) = read_jstring(&mut env, &authenticator_code) else {
        return ptr::null_mut();
    };
    let Some(activation_code) = read_jstring(&mut env, &activation_code) else {
        return ptr::null_mut();
    };
    let Ok(request) = build_finalize_authenticator_request(
        &steam_id,
        &authenticator_code,
        auth_time,
        &activation_code,
        validate_sms_code != 0,
    ) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &request)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseFinalizeAuthenticator(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(parsed) = parse_finalize_authenticator_response(&response) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &serialize_finalize(parsed))
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeBuildReplaceAuthenticatorContinue(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    code: JString<'_>,
) -> jbyteArray {
    let Some(code) = read_jstring(&mut env, &code) else {
        return ptr::null_mut();
    };
    let Ok(request) = build_replace_continue_request(&code) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &request)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseReplaceAuthenticatorContinue(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(parsed) = parse_replace_continue_response(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_authenticator(&parsed) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &encoded)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn two_factor_bridge_formats_are_versioned() {
        let encoded = serialize_authenticator(&AuthenticatorData {
            shared_secret: Some("secret".to_string()),
            serial_number: Some("42".to_string()),
            status: 1,
            ..AuthenticatorData::default()
        })
        .unwrap();
        assert_eq!(&encoded[..4], b"TFA1");

        let finalize = serialize_finalize(FinalizeAuthenticatorData {
            success: true,
            want_more: false,
            status: 1,
        });
        assert_eq!(&finalize[..4], b"TFF1");
        assert_eq!(i32::from_le_bytes(finalize[4..8].try_into().unwrap()), 1);
    }
}
