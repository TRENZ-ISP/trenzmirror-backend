# Deploying TrenzMirror Backend Online (Railway)

This gets your backend running 24/7 at a stable public URL, so your phone's IP
and your PC's IP never matter again.

## 1. Push this folder to GitHub

Open PowerShell in this `backend` folder and run:

```powershell
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/trenzmirror-backend.git
git push -u origin main
```

Replace `YOUR-USERNAME` with your actual GitHub username, and make sure the
repo name matches whatever you created on github.com (e.g.
`trenzmirror-backend`).

## 2. Deploy on Railway

1. Go to https://railway.app and sign in with GitHub.
2. Click **New Project** -> **Deploy from GitHub repo**.
3. Select your `trenzmirror-backend` repo.
4. Railway will detect the `Dockerfile` automatically and build it.
5. Once deployed, go to the service's **Settings** tab -> **Networking** ->
   **Generate Domain**. This gives you a public URL like
   `https://trenzmirror-backend-production.up.railway.app`.

## 3. Add a persistent volume (important - do this before real users sign up)

Without this step, your SQLite database (all registered users/devices) will
be wiped every time Railway redeploys or restarts the service.

1. In your Railway project, click **+ New** -> **Volume**.
2. Mount path: `/data`
3. Attach it to your backend service.

## 4. Set environment variables

In your service's **Variables** tab, add:

| Variable    | Value                              |
|-------------|-------------------------------------|
| `DB_PATH`   | `jdbc:sqlite:/data/trenz_mirror.db` |
| `JWT_SECRET`| any long random string you generate |

(`JWT_SECRET` replaces the placeholder value committed in
`application.conf` - don't rely on that default for anything real.)

Railway automatically provides its own `PORT` variable - you don't need to
set that yourself, the app already reads it.

## 5. Update the Android app

Once you have your Railway URL, update `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "BASE_URL", "\"https://your-actual-railway-url.up.railway.app/\"")
```

Since this is now a real `https://` URL, you can remove the debug-only
cleartext exception entirely (delete
`app/src/debug/res/xml/network_security_config.xml` and the
`networkSecurityConfig`/`usesCleartextTraffic` lines from
`app/src/debug/AndroidManifest.xml`) - it was only ever needed for the
unencrypted local `http://192.168.x.x` testing setup.

Rebuild, reinstall, and the app will now reach your backend from anywhere
with internet access - no more matching IP addresses between your PC and
phone.
