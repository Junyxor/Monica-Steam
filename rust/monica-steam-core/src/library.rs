use crate::proto::{parse_all_ref, ProtoError, ProtoFieldRef};

const MAX_LIBRARY_RESPONSE_BYTES: usize = 64 * 1024 * 1024;
const MAX_OWNED_GAMES: usize = 100_000;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OwnedGame {
    pub app_id: i32,
    pub name: String,
    pub playtime_recent_minutes: i32,
    pub playtime_forever_minutes: i32,
    pub icon_hash: String,
    pub last_played_at: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LibraryParseError {
    Proto(ProtoError),
    PayloadTooLarge,
    InvalidResponse,
    TooManyGames,
}

impl From<ProtoError> for LibraryParseError {
    fn from(value: ProtoError) -> Self {
        Self::Proto(value)
    }
}

pub fn parse_owned_games(response: &[u8]) -> Result<Vec<OwnedGame>, LibraryParseError> {
    if response.len() > MAX_LIBRARY_RESPONSE_BYTES {
        return Err(LibraryParseError::PayloadTooLarge);
    }
    let fields = parse_all_ref(response)?;
    if fields.is_empty() {
        return Err(LibraryParseError::InvalidResponse);
    }

    // Kotlin uses firstOrNull { field == 1 && wireType == 0 } for the declared
    // count, while game entries are every length-delimited field 2.
    let declared_count = fields
        .iter()
        .find(|field| field.number == 1 && field.wire_type() == 0)
        .and_then(ProtoFieldRef::as_i64)
        .map(|value| value as i32);
    let game_field_count = fields
        .iter()
        .filter(|field| field.number == 2 && field.as_bytes().is_some())
        .count();
    if game_field_count > MAX_OWNED_GAMES {
        return Err(LibraryParseError::TooManyGames);
    }
    if declared_count.is_some_and(|count| count != game_field_count as i32) {
        return Err(LibraryParseError::InvalidResponse);
    }

    let mut games = Vec::with_capacity(game_field_count);
    for field in fields
        .iter()
        .filter(|field| field.number == 2)
        .filter_map(ProtoFieldRef::as_bytes)
    {
        let Ok(game_fields) = parse_all_ref(field) else {
            // Matches Kotlin's runCatching(...).getOrNull() per game: one corrupt
            // nested entry must not discard the rest of a valid library page.
            continue;
        };
        let Some(app_id_field) = last_field(&game_fields, 1) else {
            continue;
        };
        // SteamProtoField.asLong returns 0 for a present non-varint field. Keep
        // that slightly unusual behavior for compatibility with the fallback.
        let app_id = app_id_field.as_i64().unwrap_or(0) as i32;
        let raw_name = last_string(&game_fields, 2);
        let name = if raw_name.chars().all(char::is_whitespace) {
            format!("App {app_id}")
        } else {
            raw_name
        };

        games.push(OwnedGame {
            app_id,
            name,
            playtime_recent_minutes: last_i64_or_zero(&game_fields, 3).max(0) as i32,
            playtime_forever_minutes: last_i64_or_zero(&game_fields, 4).max(0) as i32,
            icon_hash: last_string(&game_fields, 5),
            last_played_at: last_i64_or_zero(&game_fields, 11).max(0),
        });
    }
    Ok(games)
}

fn last_field<'a>(fields: &'a [ProtoFieldRef<'a>], number: u32) -> Option<&'a ProtoFieldRef<'a>> {
    fields.iter().rev().find(|field| field.number == number)
}

fn last_i64_or_zero(fields: &[ProtoFieldRef<'_>], number: u32) -> i64 {
    last_field(fields, number)
        .and_then(ProtoFieldRef::as_i64)
        .unwrap_or(0)
}

fn last_string(fields: &[ProtoFieldRef<'_>], number: u32) -> String {
    last_field(fields, number)
        .and_then(ProtoFieldRef::as_bytes)
        .map(|bytes| String::from_utf8_lossy(bytes).into_owned())
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::proto::ProtoWriter;

    fn game(
        app_id: i64,
        name: &str,
        recent: i64,
        forever: i64,
        icon: &str,
        last_played: i64,
    ) -> ProtoWriter {
        let mut writer = ProtoWriter::new();
        writer.write_varint(1, app_id).unwrap();
        writer.write_string(2, name).unwrap();
        writer.write_varint(3, recent).unwrap();
        writer.write_varint(4, forever).unwrap();
        writer.write_string(5, icon).unwrap();
        writer.write_varint(11, last_played).unwrap();
        writer
    }

    #[test]
    fn owned_games_match_kotlin_layout_and_order() {
        let first = game(570, "Dota 2", 15, 1200, "abc", 1_700_000_000);
        let second = game(730, "Counter-Strike 2", 30, 2400, "def", 1_710_000_000);
        let mut response = ProtoWriter::new();
        response.write_varint(1, 2).unwrap();
        response.write_message(2, &first).unwrap();
        response.write_message(2, &second).unwrap();

        let games = parse_owned_games(response.as_bytes()).unwrap();
        assert_eq!(games.len(), 2);
        assert_eq!(games[0].app_id, 570);
        assert_eq!(games[0].name, "Dota 2");
        assert_eq!(games[0].playtime_forever_minutes, 1200);
        assert_eq!(games[0].icon_hash, "abc");
        assert_eq!(games[1].app_id, 730);
        assert_eq!(games[1].last_played_at, 1_710_000_000);
    }

    #[test]
    fn declared_count_mismatch_is_invalid() {
        let first = game(570, "Dota 2", 0, 0, "", 0);
        let mut response = ProtoWriter::new();
        response.write_varint(1, 2).unwrap();
        response.write_message(2, &first).unwrap();
        assert_eq!(
            parse_owned_games(response.as_bytes()),
            Err(LibraryParseError::InvalidResponse)
        );
    }

    #[test]
    fn malformed_nested_game_is_skipped_after_count_validation() {
        let valid = game(570, "Dota 2", 0, 1, "icon", 2);
        let mut response = ProtoWriter::new();
        response.write_varint(1, 2).unwrap();
        response.write_bytes(2, &[0x08, 0x80]).unwrap();
        response.write_message(2, &valid).unwrap();

        let games = parse_owned_games(response.as_bytes()).unwrap();
        assert_eq!(games.len(), 1);
        assert_eq!(games[0].app_id, 570);
    }

    #[test]
    fn nested_parse_uses_last_duplicate_field_like_kotlin_associate_by() {
        let mut duplicate = ProtoWriter::new();
        duplicate.write_varint(1, 1).unwrap();
        duplicate.write_varint(1, 570).unwrap();
        duplicate.write_string(2, "old").unwrap();
        duplicate.write_string(2, "new").unwrap();
        duplicate.write_varint(4, 1).unwrap();
        duplicate.write_varint(4, 99).unwrap();

        let mut response = ProtoWriter::new();
        response.write_varint(1, 1).unwrap();
        response.write_message(2, &duplicate).unwrap();
        let games = parse_owned_games(response.as_bytes()).unwrap();
        assert_eq!(games[0].app_id, 570);
        assert_eq!(games[0].name, "new");
        assert_eq!(games[0].playtime_forever_minutes, 99);
    }

    #[test]
    fn blank_name_and_negative_times_match_kotlin_fallback() {
        let blank = game(570, "   ", -1, -2, "", -3);
        let mut response = ProtoWriter::new();
        response.write_varint(1, 1).unwrap();
        response.write_message(2, &blank).unwrap();
        let game = &parse_owned_games(response.as_bytes()).unwrap()[0];
        assert_eq!(game.name, "App 570");
        assert_eq!(game.playtime_recent_minutes, 0);
        assert_eq!(game.playtime_forever_minutes, 0);
        assert_eq!(game.last_played_at, 0);
    }

    #[test]
    fn empty_response_is_invalid() {
        assert_eq!(
            parse_owned_games(&[]),
            Err(LibraryParseError::InvalidResponse)
        );
    }
}
