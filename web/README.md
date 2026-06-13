# EllasGame Web

This is a dependency-free browser skeleton for testing the web target.

Run locally:

```bash
cd web
npm run dev
```

Open:

```text
http://127.0.0.1:5173
```

For phone testing on the same network:

```bash
cd web
npm run dev:host
```

Then open the host machine IP address with port `5173`.

## Cloudflare Pages

Use these settings for GitHub/GitLab integration:

```text
Framework preset: None
Root directory: web
Build command: npm run build
Build output directory: public
```

The production branch should usually be:

```text
main
```

You can also deploy manually with Wrangler:

```bash
cd web
npm run deploy:cloudflare
```

Manual deployment requires a Cloudflare API token and account ID in your shell.
