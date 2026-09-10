use jni::{
    objects::{JByteArray, JClass},
    sys::jbyteArray,
    JNIEnv,
};
use monica_steam_core::group_chat::{
    parse_group_chat_history, GroupChatHistoryPage, GroupChatReaction,
};
use std::ptr;

const GROUP_CHAT_HISTORY_BRIDGE_MAGIC: &[u8; 4] = b"MSG1";

fn write_required_string(out: &mut Vec<u8>, value: &str) -> Option<()> {
    let bytes = value.as_bytes();
    let length = u32::try_from(bytes.len()).ok()?;
    out.extend_from_slice(&length.to_le_bytes());
    out.extend_from_slice(bytes);
    Some(())
}

fn serialize_reaction(out: &mut Vec<u8>, reaction: &GroupChatReaction) -> Option<()> {
    out.extend_from_slice(&reaction.kind.to_le_bytes());
    out.extend_from_slice(&reaction.count.to_le_bytes());
    out.extend_from_slice(&(if reaction.has_user_reacted { 1i32 } else { 0i32 }).to_le_bytes());
    write_required_string(out, &reaction.name)
}

fn serialize_group_chat_history(page: &GroupChatHistoryPage) -> Option<Vec<u8>> {
    let count = u32::try_from(page.messages.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(GROUP_CHAT_HISTORY_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    out.extend_from_slice(&(if page.more_available { 1i32 } else { 0i32 }).to_le_bytes());

    for message in &page.messages {
        out.extend_from_slice(&message.sender_account_id.to_le_bytes());
        out.extend_from_slice(&message.timestamp.to_le_bytes());
        out.extend_from_slice(&message.ordinal.to_le_bytes());
        out.extend_from_slice(&(if message.deleted { 1i32 } else { 0i32 }).to_le_bytes());
        out.extend_from_slice(&message.event_type.to_le_bytes());
        write_required_string(&mut out, &message.raw_body)?;
        write_required_string(&mut out, &message.event_text)?;
        let reaction_count = u32::try_from(message.reactions.len()).ok()?;
        out.extend_from_slice(&reaction_count.to_le_bytes());
        for reaction in &message.reactions {
            serialize_reaction(&mut out, reaction)?;
        }
    }
    Some(out)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseGroupChatHistory(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(page) = parse_group_chat_history(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_group_chat_history(&page) else {
        return ptr::null_mut();
    };
    env.byte_array_from_slice(&encoded)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;
    use monica_steam_core::group_chat::GroupChatHistoryMessage;

    #[test]
    fn group_chat_history_bridge_layout_is_stable() {
        let encoded = serialize_group_chat_history(&GroupChatHistoryPage {
            more_available: true,
            messages: vec![GroupChatHistoryMessage {
                sender_account_id: 39_734_274,
                timestamp: 300,
                ordinal: 2,
                raw_body: "Hello".to_string(),
                deleted: false,
                event_type: 0,
                event_text: String::new(),
                reactions: vec![GroupChatReaction {
                    kind: 2,
                    name: "party".to_string(),
                    count: 3,
                    has_user_reacted: true,
                }],
            }],
        })
        .unwrap();

        assert_eq!(&encoded[0..4], b"MSG1");
        assert_eq!(u32::from_le_bytes(encoded[4..8].try_into().unwrap()), 1);
        assert_eq!(i32::from_le_bytes(encoded[8..12].try_into().unwrap()), 1);
        assert_eq!(
            i64::from_le_bytes(encoded[12..20].try_into().unwrap()),
            39_734_274
        );
        assert_eq!(i64::from_le_bytes(encoded[20..28].try_into().unwrap()), 300);
        assert_eq!(i32::from_le_bytes(encoded[28..32].try_into().unwrap()), 2);
    }
}
