use jni::{
    objects::{JClass, JString},
    sys::{jboolean, jbyteArray},
    JNIEnv,
};
use monica_steam_core::ma_file::{parse_ma_file_json, MaFilePayload};
use std::ptr;

const MA_FILE_BRIDGE_MAGIC: &[u8; 4] = b"MFI1";

fn read_jstring(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    env.get_string(value).ok().map(Into::into)
}

fn read_optional_jstring(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    read_jstring(env, value).filter(|value| !value.is_empty())
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

fn serialize_ma_file(payload: &MaFilePayload) -> Option<Vec<u8>> {
    let mut out = Vec::with_capacity(
        128usize
            .saturating_add(payload.raw_json.len())
            .saturating_add(payload.account_name.len())
            .saturating_add(payload.display_name.len()),
    );
    out.extend_from_slice(MA_FILE_BRIDGE_MAGIC);
    write_required_string(&mut out, &payload.steam_id)?;
    write_required_string(&mut out, &payload.account_name)?;
    write_required_string(&mut out, &payload.display_name)?;
    write_required_string(&mut out, &payload.device_id)?;
    write_required_string(&mut out, &payload.shared_secret)?;
    write_optional_string(&mut out, payload.identity_secret.as_deref())?;
    write_optional_string(&mut out, payload.revocation_code.as_deref())?;
    write_optional_string(&mut out, payload.token_gid.as_deref())?;
    write_optional_string(&mut out, payload.access_token.as_deref())?;
    write_optional_string(&mut out, payload.refresh_token.as_deref())?;
    write_optional_string(&mut out, payload.steam_login_secure.as_deref())?;
    write_required_string(&mut out, &payload.raw_json)?;
    Some(out)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseMaFileJson(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    plain_json: JString<'_>,
    file_name: JString<'_>,
    display_name_override: JString<'_>,
    steam_id_override: JString<'_>,
    allow_missing_steam_id: jboolean,
) -> jbyteArray {
    let Some(plain_json) = read_jstring(&mut env, &plain_json) else {
        return ptr::null_mut();
    };
    let file_name = read_optional_jstring(&mut env, &file_name);
    let display_name_override = read_optional_jstring(&mut env, &display_name_override);
    let steam_id_override = read_optional_jstring(&mut env, &steam_id_override);

    let Ok(payload) = parse_ma_file_json(
        &plain_json,
        file_name.as_deref(),
        display_name_override.as_deref(),
        steam_id_override.as_deref(),
        allow_missing_steam_id != 0,
    ) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_ma_file(&payload) else {
        return ptr::null_mut();
    };
    env.byte_array_from_slice(&encoded)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ma_file_bridge_layout_is_versioned_and_stable() {
        let payload = MaFilePayload {
            steam_id: "76561198000000000".to_string(),
            account_name: "alice".to_string(),
            display_name: "Alice".to_string(),
            device_id: "android:abc".to_string(),
            shared_secret: "AAECAwQFBgcICQoLDA0ODxAREhM=".to_string(),
            identity_secret: Some("identity".to_string()),
            revocation_code: None,
            token_gid: Some("gid".to_string()),
            access_token: Some("access".to_string()),
            refresh_token: None,
            steam_login_secure: Some("76561198000000000||access".to_string()),
            raw_json: "{}".to_string(),
        };
        let encoded = serialize_ma_file(&payload).unwrap();
        assert_eq!(&encoded[0..4], b"MFI1");
        assert_eq!(u32::from_le_bytes(encoded[4..8].try_into().unwrap()), 17);
        assert!(encoded.len() > payload.raw_json.len());
    }
}
