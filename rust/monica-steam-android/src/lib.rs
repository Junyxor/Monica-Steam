use jni::{
    objects::{JByteArray, JClass, JString},
    sys::{jbyteArray, jint, jlong, jstring},
    JNIEnv,
};
use monica_steam_core::{
    achievement::{parse_achievement_details, AchievementDetail},
    chat::{
        parse_chat_messages, parse_chat_sessions, ChatPage, ChatReactionKind, ChatSession,
    },
    cm::{decode_messages, encode_message, web_logon_body, CmEnvelope},
    library::{
        parse_achievement_progress, parse_owned_games, parse_store_items, AchievementProgress,
        OwnedGame, StoreMetadata,
    },
    generate_auth_code, generate_confirmation_hash, generate_login_approval_signature,
    generate_login_token_signature,
};
use std::ptr;

const CM_BRIDGE_MAGIC: &[u8; 4] = b"MSC1";
const CHAT_SESSIONS_BRIDGE_MAGIC: &[u8; 4] = b"MSS1";
const CHAT_MESSAGES_BRIDGE_MAGIC: &[u8; 4] = b"MSM1";
const OWNED_GAMES_BRIDGE_MAGIC: &[u8; 4] = b"MSL1";
const ACHIEVEMENT_PROGRESS_BRIDGE_MAGIC: &[u8; 4] = b"MSP1";
const STORE_ITEMS_BRIDGE_MAGIC: &[u8; 4] = b"MST1";
const ACHIEVEMENT_DETAILS_BRIDGE_MAGIC: &[u8; 4] = b"MSA1";
const OPTIONAL_I32_NONE: i32 = i32::MIN;

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

fn write_optional_string(out: &mut Vec<u8>, value: Option<&str>) -> Option<()> {
    match value {
        Some(value) => {
            let bytes = value.as_bytes();
            let length = i32::try_from(bytes.len()).ok()?;
            out.extend_from_slice(&length.to_le_bytes());
            out.extend_from_slice(bytes);
        }
        None => out.extend_from_slice(&(-1i32).to_le_bytes()),
    }
    Some(())
}

fn write_required_string(out: &mut Vec<u8>, value: &str) -> Option<()> {
    let bytes = value.as_bytes();
    let length = u32::try_from(bytes.len()).ok()?;
    out.extend_from_slice(&length.to_le_bytes());
    out.extend_from_slice(bytes);
    Some(())
}

fn serialize_cm_envelopes(messages: &[CmEnvelope]) -> Option<Vec<u8>> {
    let count = u32::try_from(messages.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(CM_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());

    for message in messages {
        out.extend_from_slice(&message.e_msg.to_le_bytes());
        out.extend_from_slice(&message.header.steam_id.to_le_bytes());
        out.extend_from_slice(&message.header.session_id.to_le_bytes());
        out.extend_from_slice(&message.header.job_id_source.to_le_bytes());
        out.extend_from_slice(&message.header.job_id_target.to_le_bytes());
        out.extend_from_slice(&message.header.e_result.unwrap_or(OPTIONAL_I32_NONE).to_le_bytes());
        out.extend_from_slice(
            &message
                .header
                .transport_error
                .unwrap_or(OPTIONAL_I32_NONE)
                .to_le_bytes(),
        );
        write_optional_string(&mut out, message.header.target_job_name.as_deref())?;
        write_optional_string(&mut out, message.header.error_message.as_deref())?;
        let body_length = u32::try_from(message.body.len()).ok()?;
        out.extend_from_slice(&body_length.to_le_bytes());
        out.extend_from_slice(&message.body);
    }
    Some(out)
}

fn serialize_chat_sessions(sessions: &[ChatSession]) -> Option<Vec<u8>> {
    let count = u32::try_from(sessions.len()).ok()?;
    let mut out = Vec::with_capacity(8 + sessions.len().saturating_mul(28));
    out.extend_from_slice(CHAT_SESSIONS_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for session in sessions {
        out.extend_from_slice(&session.partner_steam_id.to_le_bytes());
        out.extend_from_slice(&session.last_message_timestamp.to_le_bytes());
        out.extend_from_slice(&session.last_view_timestamp.to_le_bytes());
        out.extend_from_slice(&session.unread_count.to_le_bytes());
    }
    Some(out)
}

fn serialize_chat_page(page: &ChatPage) -> Option<Vec<u8>> {
    let count = u32::try_from(page.messages.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(CHAT_MESSAGES_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    out.extend_from_slice(&(if page.more_available { 1i32 } else { 0i32 }).to_le_bytes());

    for message in &page.messages {
        out.extend_from_slice(&message.sender_steam_id.to_le_bytes());
        out.extend_from_slice(&message.timestamp.to_le_bytes());
        out.extend_from_slice(&message.ordinal.to_le_bytes());
        write_required_string(&mut out, &message.body)?;
        let reaction_count = u32::try_from(message.reactions.len()).ok()?;
        out.extend_from_slice(&reaction_count.to_le_bytes());
        for reaction in &message.reactions {
            let kind = match reaction.kind {
                ChatReactionKind::Emoticon => 1i32,
                ChatReactionKind::Sticker => 2i32,
            };
            out.extend_from_slice(&kind.to_le_bytes());
            write_required_string(&mut out, &reaction.name)?;
            let reactor_count = u32::try_from(reaction.reactor_steam_ids.len()).ok()?;
            out.extend_from_slice(&reactor_count.to_le_bytes());
            for steam_id in &reaction.reactor_steam_ids {
                out.extend_from_slice(&steam_id.to_le_bytes());
            }
        }
    }
    Some(out)
}

fn serialize_owned_games(games: &[OwnedGame]) -> Option<Vec<u8>> {
    let count = u32::try_from(games.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(OWNED_GAMES_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for game in games {
        out.extend_from_slice(&game.app_id.to_le_bytes());
        out.extend_from_slice(&game.playtime_recent_minutes.to_le_bytes());
        out.extend_from_slice(&game.playtime_forever_minutes.to_le_bytes());
        out.extend_from_slice(&game.last_played_at.to_le_bytes());
        write_required_string(&mut out, &game.name)?;
        write_required_string(&mut out, &game.icon_hash)?;
    }
    Some(out)
}

fn serialize_achievement_progress(progress: &[AchievementProgress]) -> Option<Vec<u8>> {
    let count = u32::try_from(progress.len()).ok()?;
    let mut out = Vec::with_capacity(8 + progress.len().saturating_mul(16));
    out.extend_from_slice(ACHIEVEMENT_PROGRESS_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for item in progress {
        out.extend_from_slice(&item.app_id.to_le_bytes());
        out.extend_from_slice(&item.unlocked.to_le_bytes());
        out.extend_from_slice(&item.total.to_le_bytes());
        out.extend_from_slice(&(if item.all_unlocked { 1i32 } else { 0i32 }).to_le_bytes());
    }
    Some(out)
}

fn serialize_store_items(items: &[StoreMetadata]) -> Option<Vec<u8>> {
    let count = u32::try_from(items.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(STORE_ITEMS_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for item in items {
        out.extend_from_slice(&item.app_id.to_le_bytes());
        let cloud_state = match item.supports_steam_cloud {
            Some(true) => 1i32,
            Some(false) => 0i32,
            None => -1i32,
        };
        out.extend_from_slice(&cloud_state.to_le_bytes());
        let has_price = item.final_price_minor.is_some() && item.original_price_minor.is_some();
        out.extend_from_slice(&(if has_price { 1i32 } else { 0i32 }).to_le_bytes());
        out.extend_from_slice(&item.final_price_minor.unwrap_or(0).to_le_bytes());
        out.extend_from_slice(&item.original_price_minor.unwrap_or(0).to_le_bytes());
        write_required_string(&mut out, &item.header_image_url)?;
    }
    Some(out)
}

fn serialize_achievement_details(items: &[AchievementDetail]) -> Option<Vec<u8>> {
    let count = u32::try_from(items.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(ACHIEVEMENT_DETAILS_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for item in items {
        out.extend_from_slice(&(if item.achieved { 1i32 } else { 0i32 }).to_le_bytes());
        let has_unlock_time = item.unlock_time_seconds.is_some();
        out.extend_from_slice(&(if has_unlock_time { 1i32 } else { 0i32 }).to_le_bytes());
        out.extend_from_slice(&item.unlock_time_seconds.unwrap_or(0).to_le_bytes());
        write_required_string(&mut out, &item.api_name)?;
        write_required_string(&mut out, &item.display_name)?;
        write_required_string(&mut out, &item.description)?;
        write_optional_string(&mut out, item.icon_url.as_deref())?;
        write_optional_string(&mut out, item.locked_icon_url.as_deref())?;
    }
    Some(out)
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
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeDecodeCmMessages(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    payload: JByteArray<'_>,
) -> jbyteArray {
    let Ok(payload) = env.convert_byte_array(&payload) else {
        return ptr::null_mut();
    };
    let Ok(messages) = decode_messages(&payload) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_cm_envelopes(&messages) else {
        return ptr::null_mut();
    };
    write_jbytes(&mut env, &encoded)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseChatSessions(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(sessions) = parse_chat_sessions(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_chat_sessions(&sessions) else {
        return ptr::null_mut();
    };
    write_jbytes(&mut env, &encoded)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseChatMessages(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(page) = parse_chat_messages(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_chat_page(&page) else {
        return ptr::null_mut();
    };
    write_jbytes(&mut env, &encoded)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseOwnedGames(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(games) = parse_owned_games(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_owned_games(&games) else {
        return ptr::null_mut();
    };
    write_jbytes(&mut env, &encoded)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseAchievementProgress(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(progress) = parse_achievement_progress(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_achievement_progress(&progress) else {
        return ptr::null_mut();
    };
    write_jbytes(&mut env, &encoded)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseStoreItems(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(items) = parse_store_items(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_store_items(&items) else {
        return ptr::null_mut();
    };
    write_jbytes(&mut env, &encoded)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseAchievementDetails(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    definitions_response: JByteArray<'_>,
    user_response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(definitions_response) = env.convert_byte_array(&definitions_response) else {
        return ptr::null_mut();
    };
    let Ok(user_response) = env.convert_byte_array(&user_response) else {
        return ptr::null_mut();
    };
    let Ok(items) = parse_achievement_details(&definitions_response, &user_response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_achievement_details(&items) else {
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn store_bridge_layout_is_stable() {
        let encoded = serialize_store_items(&[
            StoreMetadata {
                app_id: 730,
                header_image_url: "https://cdn.example/header.jpg".to_string(),
                final_price_minor: Some(1290),
                original_price_minor: Some(8600),
                supports_steam_cloud: Some(true),
            },
            StoreMetadata {
                app_id: 570,
                header_image_url: String::new(),
                final_price_minor: None,
                original_price_minor: None,
                supports_steam_cloud: None,
            },
        ])
        .unwrap();

        assert_eq!(&encoded[0..4], b"MST1");
        assert_eq!(u32::from_le_bytes(encoded[4..8].try_into().unwrap()), 2);
        assert_eq!(i32::from_le_bytes(encoded[8..12].try_into().unwrap()), 730);
        assert_eq!(i32::from_le_bytes(encoded[12..16].try_into().unwrap()), 1);
        assert_eq!(i32::from_le_bytes(encoded[16..20].try_into().unwrap()), 1);
        assert_eq!(i64::from_le_bytes(encoded[20..28].try_into().unwrap()), 1290);
        assert_eq!(i64::from_le_bytes(encoded[28..36].try_into().unwrap()), 8600);
        let header_len = u32::from_le_bytes(encoded[36..40].try_into().unwrap()) as usize;
        assert_eq!(&encoded[40..40 + header_len], b"https://cdn.example/header.jpg");
    }

    #[test]
    fn achievement_details_bridge_layout_is_stable() {
        let encoded = serialize_achievement_details(&[AchievementDetail {
            api_name: "ACH_WIN".to_string(),
            display_name: "Winner".to_string(),
            description: "Win once".to_string(),
            achieved: true,
            unlock_time_seconds: Some(1_700_000_000),
            icon_url: Some("https://cdn.example/icon.jpg".to_string()),
            locked_icon_url: None,
        }])
        .unwrap();

        assert_eq!(&encoded[0..4], b"MSA1");
        assert_eq!(u32::from_le_bytes(encoded[4..8].try_into().unwrap()), 1);
        assert_eq!(i32::from_le_bytes(encoded[8..12].try_into().unwrap()), 1);
        assert_eq!(i32::from_le_bytes(encoded[12..16].try_into().unwrap()), 1);
        assert_eq!(
            i64::from_le_bytes(encoded[16..24].try_into().unwrap()),
            1_700_000_000
        );
    }
}
