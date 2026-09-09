use crate::proto::{parse_all, ProtoError, ProtoWriter};

const PROTOBUF_HEADER_FLAG: u32 = 0x8000_0000;
pub const WEB_PROTOCOL_VERSION: i64 = 65_580;
pub const WEB_CLIENT_OS_TYPE: i64 = 4_294_966_596;

pub fn encode_message(
    e_msg: i32,
    steam_id: i64,
    session_id: i32,
    body: &[u8],
    job_id_source: i64,
    job_id_target: i64,
    target_job_name: Option<&str>,
) -> Result<Vec<u8>, ProtoError> {
    let mut header = ProtoWriter::new();
    header.write_fixed64(1, steam_id)?;
    header.write_varint(2, session_id as i64)?;
    header.write_fixed64(10, job_id_source)?;
    header.write_fixed64(11, job_id_target)?;
    if let Some(name) = target_job_name.filter(|name| !name.is_empty()) {
        header.write_string(12, name)?;
    }
    let header = header.into_bytes();

    let mut message = Vec::with_capacity(8 + header.len() + body.len());
    let raw_e_msg = (e_msg as u32) | PROTOBUF_HEADER_FLAG;
    message.extend_from_slice(&raw_e_msg.to_le_bytes());
    message.extend_from_slice(&(header.len() as u32).to_le_bytes());
    message.extend_from_slice(&header);
    message.extend_from_slice(body);
    Ok(message)
}

pub fn web_logon_body(web_logon_token: &str) -> Result<Vec<u8>, ProtoError> {
    let mut writer = ProtoWriter::new();
    writer.write_varint(1, WEB_PROTOCOL_VERSION)?;
    writer.write_varint(7, WEB_CLIENT_OS_TYPE)?;
    writer.write_varint(32, 4)?;
    writer.write_varint(33, 2)?;
    writer.write_string(80, "anonymous")?;
    writer.write_string(103, web_logon_token)?;
    Ok(writer.into_bytes())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cm_envelope_matches_kotlin_layout() {
        let body = [0xde, 0xad, 0xbe, 0xef];
        let encoded = encode_message(146, 76561198000000000, 42, &body, -1, 77, Some("Test.Method"))
            .unwrap();

        assert_eq!(u32::from_le_bytes(encoded[0..4].try_into().unwrap()), 146 | PROTOBUF_HEADER_FLAG);
        let header_len = u32::from_le_bytes(encoded[4..8].try_into().unwrap()) as usize;
        let header = &encoded[8..8 + header_len];
        assert_eq!(&encoded[8 + header_len..], body);

        let fields = parse_all(header).unwrap();
        assert_eq!(fields.iter().find(|field| field.number == 1).unwrap().as_i64(), Some(76561198000000000));
        assert_eq!(fields.iter().find(|field| field.number == 2).unwrap().as_i64(), Some(42));
        assert_eq!(fields.iter().find(|field| field.number == 10).unwrap().as_i64(), Some(-1));
        assert_eq!(fields.iter().find(|field| field.number == 11).unwrap().as_i64(), Some(77));
        assert_eq!(
            fields.iter().find(|field| field.number == 12).unwrap().as_utf8_lossy().as_deref(),
            Some("Test.Method")
        );
    }

    #[test]
    fn empty_target_job_name_is_omitted() {
        let encoded = encode_message(151, 0, 0, &[], -1, -1, Some("")).unwrap();
        let header_len = u32::from_le_bytes(encoded[4..8].try_into().unwrap()) as usize;
        let fields = parse_all(&encoded[8..8 + header_len]).unwrap();
        assert!(fields.iter().all(|field| field.number != 12));
    }

    #[test]
    fn web_logon_body_has_expected_fields() {
        let encoded = web_logon_body("token-value").unwrap();
        let fields = parse_all(&encoded).unwrap();
        let field = |number| fields.iter().find(|field| field.number == number).unwrap();
        assert_eq!(field(1).as_i64(), Some(WEB_PROTOCOL_VERSION));
        assert_eq!(field(7).as_i64(), Some(WEB_CLIENT_OS_TYPE));
        assert_eq!(field(32).as_i64(), Some(4));
        assert_eq!(field(33).as_i64(), Some(2));
        assert_eq!(field(80).as_utf8_lossy().as_deref(), Some("anonymous"));
        assert_eq!(field(103).as_utf8_lossy().as_deref(), Some("token-value"));
    }
}
