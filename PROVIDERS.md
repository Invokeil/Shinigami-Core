# Providers

Shinigami Core ships without a brain on purpose. You connect one — your key, your account, your rules. Add providers in **Settings → Providers → Add provider**; each profile stores its own base URL, model, auth scheme, and generation settings, and you can keep as many profiles as you like and switch the active one at any time.

Every profile is a template that pre-fills sensible values — you always see and can change every field, including the model string, which accepts **any** value.

---

## OpenAI

1. Create an API key at [platform.openai.com](https://platform.openai.com/api-keys) (a paid platform account; billing is separate from ChatGPT).
2. In Shinigami: type **OpenAI**. Template defaults: Base URL `https://api.openai.com/v1`, model `gpt-4o-mini`, auth **Bearer**.
3. Paste the key (`sk-…`) and run **Test Connection**.

> **Important:** an OpenAI *API* key is not your ChatGPT login. ChatGPT Plus does not include API credits, and your ChatGPT password will not work here.

## Google Gemini

1. Get a key from [Google AI Studio](https://aistudio.google.com/apikey) — a free tier is available.
2. Type **Google Gemini**. Defaults: Base URL `https://generativelanguage.googleapis.com/v1beta`, model `gemini-1.5-flash`, auth **API key header** (`x-goog-api-key`).
3. Paste the key (`AIza…`) and test.

Gemini uses Google's native REST shape; Shinigami speaks it natively (no compatibility shim).

## Mistral

1. Create a key at [console.mistral.ai](https://console.mistral.ai/api-keys/).
2. Type **Mistral**. Defaults: Base URL `https://api.mistral.ai/v1`, model `mistral-small-latest`, auth **Bearer**.
3. Paste the key and test.

Mistral exposes an OpenAI-compatible endpoint, so streaming, Fetch Models, and tool behaviour work as expected.

## GLM / Z.AI

1. Get credentials from [z.ai](https://z.ai) (international) or open.bigmodel.cn (Zhipu, China).
2. Type **GLM / Z.AI**. Defaults: Base URL `https://api.z.ai/api/paas/v4`, model `glm-4-flash`, auth **Bearer**.
3. Paste the key (`id.secret` form) and test.

## Ollama (local, fully offline)

1. Install [Ollama](https://ollama.com) on this device or another machine and pull a model, e.g. `ollama pull llama3.2`.
2. Type **Ollama (local)**. Defaults: Base URL `http://localhost:11434/v1`, model `llama3.2`, auth **None**.
3. Test. No key is needed; nothing leaves your hardware.

**LAN cleartext warning:** if Ollama runs on another machine (`http://192.168.x.x:11434/v1`), traffic over your local network is HTTP — unencrypted. Shinigami asks for an explicit per-profile "allow cleartext" confirmation before talking to non-HTTPS endpoints. Keep Ollama on a trusted network, or tunnel it (e.g. SSH/Tailscale) if you need confidentiality.

## OpenAI-compatible / custom endpoint

For LM Studio, vLLM, llama.cpp server, OpenRouter, your own VPS, a corporate gateway — anything exposing `POST {baseUrl}/chat/completions`.

- **Base URL:** everything up to (but not including) `/chat/completions`. Example: `https://api.example.com/v1`.
- **Auth options:**
  - **None** — no credentials sent.
  - **Bearer** — `Authorization: Bearer <key>` (the default).
  - **API key header** — your credentials in a custom header (name configurable, e.g. `x-api-key`).
  - **Basic** — username + password, HTTP Basic.
- **Extra headers:** add arbitrary key/value headers (JSON) for gateways that require them.
- **Model:** accepts any string the backend understands.
- If the endpoint is plain HTTP, expect the cleartext confirmation described above.

## Test Connection and Fetch Models

- **Test Connection** sends a minimal chat request using the profile's settings. Success means: reachable URL, valid credentials, a working model id, and (if enabled) a functioning stream. Failures are reported with the HTTP status or network error and a plain-language hint — this is usually the fastest way to debug a profile.
- **Fetch Models** queries the provider's models listing where one exists (OpenAI-compatible `/models`, Gemini's model list) and fills the model picker. Custom servers that do not implement a listing simply return an empty list; type the model name manually.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `401 / 403` | Wrong or revoked key; wrong auth type | Re-create the key; check the auth scheme matches the provider (Bearer vs API-key header vs Basic) |
| `404` | Base URL wrong — usually missing or extra `/v1`, or a gateway path | Compare with the provider docs; the URL must end just before `/chat/completions` |
| Timeout / connection refused | Server unreachable: wrong host/port, firewall, Ollama bound to localhost only | Check the URL; for LAN Ollama run it with `OLLAMA_HOST=0.0.0.0`; check the same URL works in a browser/curl |
| `429` | Rate limit or quota exhausted | Wait, lower usage, or check the provider's quota page; free tiers throttle aggressively |
| Cleartext blocked | Plain-HTTP endpoint without the opt-in | Enable "allow cleartext" on that profile, or switch the endpoint to HTTPS |
| Empty replies / stream errors | Model id typo, or a server that does not support streaming | Verify the model string; toggle streaming off on the profile |
| Key works elsewhere but not here | Stray whitespace/quotes pasted into the key field | Re-paste the key cleanly |

## Multiple profiles and fallback

- Keep any number of profiles — e.g. "Ollama at home", "GPT-4o mini (cheap)", "Gemini free tier" — and set one as **default** or switch per conversation.
- Per-profile controls: temperature, max tokens, custom instructions, streaming on/off, cleartext opt-in.
- Keys are encrypted (Android Keystore AES-GCM), excluded from backups, and redacted from logs. See [PRIVACY.md](PRIVACY.md).
- **Automatic failover / model routing is on the roadmap, not in v1.** If a provider fails, Shinigami reports the error; switching to another profile is a manual tap today.
