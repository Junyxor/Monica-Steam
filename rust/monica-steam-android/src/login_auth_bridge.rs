use jni::{
    objects::{JByteArray, JClass, JString},
    sys::{jbyteArray, jint, jlong},
    JNIEnv,
};
use monica_steam_core::login_auth::{
    build_begin_credentials_request, build_begin_qr_request, build_generate_access_token_request,
    build_poll_request, build_update_guard_request, parse_begin_credentials_response,
    parse_begin_qr_response, parse_generate_access_token_response, parse_poll_response,
    AccessTokenResponse, BeginCredentialsResponse, BeginQrResponse, LoginAuthChallenge,
    PollAuthResponse,
};
use std::ptr;

const BEGIN_CREDENTIALS_MAGIC: &[u8; 4] = b"LAC1";
const BEGIN_QR_MAGIC: &[u8; 4] = b"LAQ1";
const POLL_MAGIC: &[u8; 4] = b"LAP1";
const ACCESS_TOKEN_MAGIC: &[u8; 4] = b"LAT1";

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

fn write_challenges(out: &mut Vec<u8>, challenges: &[LoginAuthChallenge]) -> Option<()> {
    let count = u32::try_from(challenges.len()).ok()?;
    out.extend_from_slice(&count.to_le_bytes());
    for challenge in challenges {
        out.extend_from_slice(&challenge.confirmation_type.to_le_bytes());
        write_required_string(out, &challenge.associated_message)?;
    }
    Some(())
}

fn serialize_begin_credentials(value: &BeginCredentialsResponse) -> Option<Vec<u8>> {
    let mut out = Vec::new();
    out.extend_from_slice(BEGIN_CREDENTIALS_MAGIC);
    write_required_string(&mut out, &value.client_id)?;
    write_required_string(&mut out, &value.request_id)?;
    write_required_string(&mut out, &value.steam_id)?;
    write_optional_string(&mut out, value.message.as_deref())?;
    write_challenges(&mut out, &value.challenges)?;
    Some(out)
}

fn serialize_begin_qr(value: &BeginQrResponse) -> Option<Vec<u8>> {
    let mut out = Vec::new();
    out.extend_from_slice(BEGIN_QR_MAGIC);
    write_required_string(&mut out, &value.client_id)?;
    write_required_string(&mut out, &value.request_id)?;
    write_required_string(&mut out, &value.challenge_url)?;
    write_challenges(&mut out, &value.challenges)?;
    Some(out)
}

fn serialize_poll(value: &PollAuthResponse) -> Option<Vec<u8>> {
    let mut out = Vec::new();
    out.extend_from_slice(POLL_MAGIC);
    write_optional_string(&mut out, value.client_id.as_deref())?;
    write_optional_string(&mut out, value.challenge_url.as_deref())?;
    write_optional_string(&mut out, value.refresh_token.as_deref())?;
    write_optional_string(&mut out, value.access_token.as_deref())?;
    write_optional_string(&mut out, value.account_name.as_deref())?;
    Some(out)
}

fn serialize_access_token(value: &AccessTokenResponse) -> Option<Vec<u8>> {
    let mut out = Vec::new();
    out.extend_from_slice(ACCESS_TOKEN_MAGIC);
    write_optional_string(&mut out, value.access_token.as_deref())?;
    write_optional_string(&mut out, value.refresh_token.as_deref())?;
    Some(out)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeBuildLoginBeginCredentials(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    device_friendly_name: JString<'_>,
    user_name: JString<'_>,
    encrypted_password: JString<'_>,
    encryption_timestamp: JString<'_>,
    platform_type: jlong,
    os_type: jlong,
    gaming_device_type: jlong,
    website_id: JString<'_>,
) -> jbyteArray {
    let Some(device_friendly_name) = read_jstring(&mut env, &device_friendly_name) else {
        return ptr::null_mut();
    };
    let Some(user_name) = read_jstring(&mut env, &user_name) else {
        return ptr::null_mut();
    };
    let Some(encrypted_password) = read_jstring(&mut env, &encrypted_password) else {
        return ptr::null_mut();
    };
    let Some(encryption_timestamp) = read_jstring(&mut env, &encryption_timestamp) else {
        return ptr::null_mut();
    };
    let Some(website_id) = read_jstring(&mut env, &website_id) else {
        return ptr::null_mut();
    };
    let Ok(request) = build_begin_credentials_request(
        &device_friendly_name,
        &user_name,
        &encrypted_password,
        &encryption_timestamp,
        platform_type,
        os_type,
        gaming_device_type,
        &website_id,
    ) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &request)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseLoginBeginCredentials(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(parsed) = parse_begin_credentials_response(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_begin_credentials(&parsed) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &encoded)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeBuildLoginBeginQr(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    device_friendly_name: JString<'_>,
    platform_type: jlong,
    os_type: jlong,
    gaming_device_type: jlong,
    website_id: JString<'_>,
) -> jbyteArray {
    let Some(device_friendly_name) = read_jstring(&mut env, &device_friendly_name) else {
        return ptr::null_mut();
    };
    let Some(website_id) = read_jstring(&mut env, &website_id) else {
        return ptr::null_mut();
    };
    let Ok(request) = build_begin_qr_request(
        &device_friendly_name,
        platform_type,
        os_type,
        gaming_device_type,
        &website_id,
    ) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &request)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseLoginBeginQr(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(parsed) = parse_begin_qr_response(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_begin_qr(&parsed) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &encoded)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeBuildLoginUpdateGuard(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    client_id: JString<'_>,
    steam_id: JString<'_>,
    code: JString<'_>,
    confirmation_type: jint,
) -> jbyteArray {
    let Some(client_id) = read_jstring(&mut env, &client_id) else {
        return ptr::null_mut();
    };
    let Some(steam_id) = read_jstring(&mut env, &steam_id) else {
        return ptr::null_mut();
    };
    let Some(code) = read_jstring(&mut env, &code) else {
        return ptr::null_mut();
    };
    let Ok(request) = build_update_guard_request(&client_id, &steam_id, &code, confirmation_type) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &request)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeBuildLoginPoll(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    client_id: JString<'_>,
    request_id: JString<'_>,
    token_to_revoke: JString<'_>,
) -> jbyteArray {
    let Some(client_id) = read_jstring(&mut env, &client_id) else {
        return ptr::null_mut();
    };
    let Some(request_id) = read_jstring(&mut env, &request_id) else {
        return ptr::null_mut();
    };
    let token_to_revoke = read_jstring(&mut env, &token_to_revoke)
        .filter(|value| !value.trim().is_empty());
    let Ok(request) = build_poll_request(&client_id, &request_id, token_to_revoke.as_deref()) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &request)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseLoginPoll(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(parsed) = parse_poll_response(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_poll(&parsed) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &encoded)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeBuildLoginAccessToken(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    refresh_token: JString<'_>,
    steam_id: JString<'_>,
) -> jbyteArray {
    let Some(refresh_token) = read_jstring(&mut env, &refresh_token) else {
        return ptr::null_mut();
    };
    let Some(steam_id) = read_jstring(&mut env, &steam_id) else {
        return ptr::null_mut();
    };
    let Ok(request) = build_generate_access_token_request(&refresh_token, &steam_id) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &request)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseLoginAccessToken(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(parsed) = parse_generate_access_token_response(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_access_token(&parsed) else {
        return ptr::null_mut();
    };
    write_jbytes(&env, &encoded)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parsed_login_bridge_formats_have_distinct_versions() {
        let credentials = serialize_begin_credentials(&BeginCredentialsResponse {
            client_id: "7".to_string(),
            request_id: "cmVxdWVzdA==".to_string(),
            steam_id: "76561198000000000".to_string(),
            challenges: vec![LoginAuthChallenge {
                confirmation_type: 3,
                associated_message: "device".to_string(),
            }],
            message: Some("hello".to_string()),
        })
        .unwrap();
        assert_eq!(&credentials[..4], b"LAC1");

        let qr = serialize_begin_qr(&BeginQrResponse {
            client_id: "7".to_string(),
            request_id: "cmVxdWVzdA==".to_string(),
            challenge_url: "https://s.team/q/1".to_string(),
            challenges: Vec::new(),
        })
        .unwrap();
        assert_eq!(&qr[..4], b"LAQ1");

        let poll = serialize_poll(&PollAuthResponse::default()).unwrap();
        assert_eq!(&poll[..4], b"LAP1");

        let token = serialize_access_token(&AccessTokenResponse::default()).unwrap();
        assert_eq!(&token[..4], b"LAT1");
    }
}
