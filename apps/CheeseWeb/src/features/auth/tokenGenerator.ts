import { getTokenConfig } from './tokenConfig';

interface JwtHeader {
  alg: 'HS256';
  typ: 'JWT';
}

interface JwtPayload {
  sub: string;
  platformID: number;
  iat: number;
  exp: number;
}

export async function generateAuthToken(userID: string, platformID: number): Promise<string> {
  const config = getTokenConfig();
  const issuedAtMs = Date.now();
  const payload: JwtPayload = {
    sub: userID,
    platformID,
    iat: Math.floor(issuedAtMs / 1000),
    exp: Math.floor((issuedAtMs + config.expirationMs) / 1000),
  };
  const header: JwtHeader = {
    alg: 'HS256',
    typ: 'JWT',
  };
  const encodedHeader = encodeBase64Url(JSON.stringify(header));
  const encodedPayload = encodeBase64Url(JSON.stringify(payload));
  const signingInput = `${encodedHeader}.${encodedPayload}`;
  const signature = await signHs256(signingInput, config.secretKey);

  return `${signingInput}.${signature}`;
}

async function signHs256(value: string, secretKey: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secretKey),
    {
      name: 'HMAC',
      hash: 'SHA-256',
    },
    false,
    ['sign'],
  );
  const signatureBuffer = await crypto.subtle.sign(
    'HMAC',
    key,
    new TextEncoder().encode(value),
  );

  return encodeBase64Url(signatureBuffer);
}

function encodeBase64Url(value: string | ArrayBuffer): string {
  const bytes =
    typeof value === 'string'
      ? new TextEncoder().encode(value)
      : new Uint8Array(value);
  let binary = '';

  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });

  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}
