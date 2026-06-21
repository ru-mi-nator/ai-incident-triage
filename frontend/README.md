# AI Incident Triage Portal frontend

Angular 19 standalone application using Angular Material and SCSS.

## Local development

Run the backend on `http://localhost:8080`, then:

```powershell
npm install
npm start
```

Open `http://localhost:4200`. The development server proxies `/api` to port `8080`.

## Verification

```powershell
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

This slice implements authentication and the protected application shell. Incident features are intentionally pending.
