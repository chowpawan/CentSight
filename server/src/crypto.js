import crypto from 'node:crypto';

const key = Buffer.from(process.env.ENCRYPTION_KEY || '', 'hex');
if (key.length !== 32) {
  throw new Error('ENCRYPTION_KEY must be 64 hex characters. Generate one with: openssl rand -hex 32');
}

// AES-256-GCM. Stored as iv.tag.ciphertext (base64 parts).
export function encrypt(text) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const enc = Buffer.concat([cipher.update(text, 'utf8'), cipher.final()]);
  return [iv, cipher.getAuthTag(), enc].map((b) => b.toString('base64')).join('.');
}

export function decrypt(payload) {
  const [iv, tag, enc] = payload.split('.').map((s) => Buffer.from(s, 'base64'));
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(enc), decipher.final()]).toString('utf8');
}
