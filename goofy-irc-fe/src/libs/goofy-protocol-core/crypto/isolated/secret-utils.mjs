// TODO: CHECK
export const ENC_DELIMITER = ".";

export const DEFAULT_ITERATIONS = 100_000;
export const DEFAULT_DETERMINISTIC_SALT = "Goofy Protocol Default Salt";

export const DEFAULT_HANDLE_ROOT_ITERATIONS = 300_000;
export const DEFAULT_HANDLE_WORD_ITERATIONS = 50_000;
export const DEFAULT_HANDLE_ROOT_SALT = "Goofy Protocol Root Salt";
export const DEFAULT_HANDLE_WORD_SALT = "Goofy Protocol Derived Word Salt";

export async function symmSecretFromSecret(secret, salt, size, iterations = DEFAULT_ITERATIONS) {
    // WebCrypto PBKDF2
    const enc = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey(
        "raw",
        enc.encode(secret),
        { name: "PBKDF2" },
        false,
        ["deriveBits"]
    );

    const bits = await crypto.subtle.deriveBits(
        {
            name: "PBKDF2",
            hash: "SHA-256",
            salt: enc.encode(salt),
            iterations,
        },
        keyMaterial,
        size
    );

    return new Uint8Array(bits);
}

export async function sha256(inputBytesOrString) {
    const bytes = typeof inputBytesOrString === "string"
        ? new TextEncoder().encode(inputBytesOrString)
        : inputBytesOrString;

    const digest = await crypto.subtle.digest("SHA-256", bytes);
    return new Uint8Array(digest);
}