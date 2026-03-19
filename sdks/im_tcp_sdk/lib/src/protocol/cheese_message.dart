import 'dart:convert';
import 'dart:typed_data';

final class CheeseMessage {
  static const int magic = 0xCEEE;
  static const int version = 0x01;
  static const int headerLength = 32;
  static const int operationIdLength = 16;
  static const int maxDataLength = 1024 * 1024;

  const CheeseMessage({
    required this.msgType,
    required this.operationId,
    required this.data,
    int? timestamp,
    this.protocolVersion = version,
  }) : timestamp = timestamp ?? 0;

  final int protocolVersion;
  final int msgType;
  final String operationId;
  final int timestamp;
  final String? data;

  int get dataLength => utf8.encode(data ?? '').length;

  Uint8List encode() {
    final dataBytes = Uint8List.fromList(utf8.encode(data ?? ''));
    final normalizedOperationId = _normalizeOperationId(operationId);
    final buffer = ByteData(headerLength + dataBytes.length);

    buffer.setUint16(0, magic);
    buffer.setUint8(2, protocolVersion);
    buffer.setUint8(3, msgType);
    buffer.setUint32(4, dataBytes.length);

    for (var index = 0; index < operationIdLength; index++) {
      buffer.setUint8(8 + index, normalizedOperationId[index]);
    }

    buffer.setInt64(24, _resolvedTimestamp());

    final bytes = buffer.buffer.asUint8List();
    bytes.setRange(headerLength, headerLength + dataBytes.length, dataBytes);
    return bytes;
  }

  static CheeseMessage decode(List<int> bytes) {
    if (bytes.length < headerLength) {
      throw ArgumentError('Invalid message length: ${bytes.length}');
    }

    final byteData = ByteData.sublistView(Uint8List.fromList(bytes));
    final decodedMagic = byteData.getUint16(0);
    if (decodedMagic != magic) {
      throw ArgumentError('Invalid magic number: $decodedMagic');
    }

    final msgType = byteData.getUint8(3);
    final dataLength = byteData.getUint32(4);
    if (dataLength < 0 || dataLength > maxDataLength) {
      throw ArgumentError('Invalid data length: $dataLength');
    }

    if (bytes.length != headerLength + dataLength) {
      throw ArgumentError('Message length mismatch');
    }

    final operationIdBytes = bytes.sublist(8, 24);
    final timestamp = byteData.getInt64(24);
    final data = dataLength == 0
        ? null
        : utf8.decode(bytes.sublist(headerLength, headerLength + dataLength));

    return CheeseMessage(
      protocolVersion: byteData.getUint8(2),
      msgType: msgType,
      operationId: _decodeOperationId(operationIdBytes),
      timestamp: timestamp,
      data: data,
    );
  }

  static int peekFrameLength(List<int> bytes, {int offset = 0}) {
    if (bytes.length - offset < headerLength) {
      throw ArgumentError('Insufficient bytes to read frame header');
    }

    final byteData = ByteData.sublistView(
      Uint8List.fromList(bytes.sublist(offset, offset + headerLength)),
    );
    final dataLength = byteData.getUint32(4);
    if (dataLength > maxDataLength) {
      throw ArgumentError('Invalid data length: $dataLength');
    }
    return headerLength + dataLength;
  }

  int _resolvedTimestamp() =>
      timestamp == 0 ? DateTime.now().millisecondsSinceEpoch : timestamp;

  static Uint8List _normalizeOperationId(String value) {
    final encoded = utf8.encode(value);
    final normalized = Uint8List(operationIdLength);
    final copyLength =
        encoded.length < operationIdLength ? encoded.length : operationIdLength;
    normalized.setRange(0, copyLength, encoded);
    return normalized;
  }

  static String _decodeOperationId(List<int> bytes) {
    final end = bytes.indexOf(0);
    final slice = end == -1 ? bytes : bytes.sublist(0, end);
    return utf8.decode(slice).trim();
  }
}
