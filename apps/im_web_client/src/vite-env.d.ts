/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_IM_TOKEN_SECRET_KEY?: string;
  readonly VITE_IM_TOKEN_EXPIRATION_MS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
