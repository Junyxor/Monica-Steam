use jni::{
    objects::{JByteArray, JClass},
    sys::jbyteArray,
    JNIEnv,
};
use monica_steam_core::{
    auth_session::{
        parse_auth_session_info, parse_mobile_confirmation_success,
        parse_pending_login_client_ids, AuthSessionInfo,
    },
    family::{parse_shared_library_apps, FamilySharedGame},
    friend_nickname::{parse_friend_nicknames, FriendNickname},
};
use std::ptr;

const FAMILY_SHARED_APPS_BRIDGE_MAGIC: &[u8; 4] = b"MSF1";
const FRIEND_NICKNAMES_BRIDGE_MAGIC: &[u8; 4] = b"MSN1";
const AUTH_CLIENT_IDS_BRIDGE_MAGIC: &[u8; 4] = b"MAC1";
const AUTH_SESSION_INFO_BRIDGE_MAGIC: &[u8; 4] = b"MAI1";
const AUTH_CONFIRMATION_BRIDGE_MAGIC: &[u8; 4] = b"MAR1";

fn write_required_string(out: &mut Vec<u8>, value: &str) -> Option<()> {
    let bytes = value.as_bytes();
    let length = u32::try_from(bytes.len()).ok()?;
    out.extend_from_slice(&length.to_le_bytes());
    out.extend_from_slice(bytes);
    Some(())
}

fn serialize_family_shared_apps(games: &[FamilySharedGame]) -> Option<Vec<u8>> {
    let count = u32::try_from(games.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(FAMILY_SHARED_APPS_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for game in games {
        out.extend_from_slice(&game.app_id.to_le_bytes());
        out.extend_from_slice(&game.playtime_forever_minutes.to_le_bytes());
        write_required_string(&mut out, &game.name)?;
        write_required_string(&mut out, &game.icon_hash)?;
        let owner_count = u32::try_from(game.owner_steam_ids.len()).ok()?;
        out.extend_from_slice(&owner_count.to_le_bytes());
        for owner in &game.owner_steam_ids {
            write_required_string(&mut out, owner)?;
        }
    }
    Some(out)
}

fn serialize_friend_nicknames(items: &[FriendNickname]) -> Option<Vec<u8>> {
    let count = u32::try_from(items.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(FRIEND_NICKNAMES_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for item in items {
        out.extend_from_slice(&item.steam_id.to_le_bytes());
        write_required_string(&mut out, &item.nickname)?;
    }
    Some(out)
}

fn serialize_auth_client_ids(ids: &[i64]) -> Option<Vec<u8>> {
    let count = u32::try_from(ids.len()).ok()?;
    let mut out = Vec::with_capacity(8 + ids.len().saturating_mul(8));
    out.extend_from_slice(AUTH_CLIENT_IDS_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for id in ids {
        out.extend_from_slice(&id.to_le_bytes());
    }
    Some(out)
}

fn serialize_auth_session_info(info: &AuthSessionInfo) -> Option<Vec<u8>> {
    let mut out = Vec::new();
    out.extend_from_slice(AUTH_SESSION_INFO_BRIDGE_MAGIC);
    out.extend_from_slice(&info.version.to_le_bytes());
    write_required_string(&mut out, &info.ip)?;
    write_required_string(&mut out, &info.city)?;
    write_required_string(&mut out, &info.country)?;
    write_required_string(&mut out, &info.device_name)?;
    Some(out)
}

fn serialize_auth_confirmation(success: bool) -> Vec<u8> {
    let mut out = Vec::with_capacity(8);
    out.extend_from_slice(AUTH_CONFIRMATION_BRIDGE_MAGIC);
    out.extend_from_slice(&(if success { 1i32 } else { 0i32 }).to_le_bytes());
    out
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseFamilySharedApps(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(games) = parse_shared_library_apps(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_family_shared_apps(&games) else {
        return ptr::null_mut();
    };
    env.byte_array_from_slice(&encoded)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseFriendNicknames(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(items) = parse_friend_nicknames(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_friend_nicknames(&items) else {
        return ptr::null_mut();
    };
    env.byte_array_from_slice(&encoded)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParsePendingLoginClientIds(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(ids) = parse_pending_login_client_ids(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_auth_client_ids(&ids) else {
        return ptr::null_mut();
    };
    env.byte_array_from_slice(&encoded)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseAuthSessionInfo(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(info) = parse_auth_session_info(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_auth_session_info(&info) else {
        return ptr::null_mut();
    };
    env.byte_array_from_slice(&encoded)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseAuthConfirmation(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(success) = parse_mobile_confirmation_success(&response) else {
        return ptr::null_mut();
    };
    let encoded = serialize_auth_confirmation(success);
    env.byte_array_from_slice(&encoded)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn family_bridge_layout_is_stable() {
        let encoded = serialize_family_shared_apps(&[FamilySharedGame {
            app_id: 20,
            name: "Shared game".to_string(),
            playtime_forever_minutes: 7_200,
            icon_hash: "icon-20".to_string(),
            owner_steam_ids: vec!["76561198000000002".to_string()],
        }])
        .unwrap();

        assert_eq!(&encoded[0..4], b"MSF1");
        assert_eq!(u32::from_le_bytes(encoded[4..8].try_into().unwrap()), 1);
        assert_eq!(i32::from_le_bytes(encoded[8..12].try_into().unwrap()), 20);
        assert_eq!(i32::from_le_bytes(encoded[12..16].try_into().unwrap()), 7_200);
    }

    #[test]
    fn nickname_bridge_layout_is_stable() {
        let encoded = serialize_friend_nicknames(&[FriendNickname {
            steam_id: 76_561_198_000_000_002,
            nickname: "Alice".to_string(),
        }])
        .unwrap();

        assert_eq!(&encoded[0..4], b"MSN1");
        assert_eq!(u32::from_le_bytes(encoded[4..8].try_into().unwrap()), 1);
        assert_eq!(
            i64::from_le_bytes(encoded[8..16].try_into().unwrap()),
            76_561_198_000_000_002
        );
    }

    #[test]
    fn auth_session_bridges_have_stable_versions() {
        let ids = serialize_auth_client_ids(&[7, 42]).unwrap();
        assert_eq!(&ids[0..4], b"MAC1");
        assert_eq!(u32::from_le_bytes(ids[4..8].try_into().unwrap()), 2);

        let info = serialize_auth_session_info(&AuthSessionInfo {
            version: 3,
            ip: "127.0.0.1".to_string(),
            city: "City".to_string(),
            country: "Country".to_string(),
            device_name: "Phone".to_string(),
        })
        .unwrap();
        assert_eq!(&info[0..4], b"MAI1");
        assert_eq!(i32::from_le_bytes(info[4..8].try_into().unwrap()), 3);

        let confirmed = serialize_auth_confirmation(true);
        assert_eq!(&confirmed[0..4], b"MAR1");
        assert_eq!(i32::from_le_bytes(confirmed[4..8].try_into().unwrap()), 1);
    }
}
