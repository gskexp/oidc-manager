import { mkdirSync, writeFileSync } from "fs";
import { join } from "path";
import { generateKeyPairSync } from "crypto";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = join(__filename, "..");
const keysDir = join(__dirname, "keys");

mkdirSync(keysDir, { recursive: true });

const { privateKey, publicKey } = generateKeyPairSync("rsa", {
  modulusLength: 2048,
  publicKeyEncoding: { type: "pkcs1", format: "pem" },
  privateKeyEncoding: { type: "pkcs1", format: "pem" }
});

writeFileSync(join(keysDir, "id_rsa_priv.pem"), privateKey);
writeFileSync(join(keysDir, "id_rsa_pub.pem"), publicKey);

console.log("Generated RSA key pair in ./keys");