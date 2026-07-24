# TrenzMirror Backend

Kotlin + Ktor backend for TrenzMirror, using Exposed/SQLite for storage.
This repository root IS the deployable project - no subfolder, no nested
`backend/` directory. Railway (or any Dockerfile-based platform) can deploy
this repo as-is with zero configuration.

## Structure

```
Dockerfile
build.gradle.kts
settings.gradle.kts
gradlew
gradlew.bat
gradle/
src/
.gitignore
.dockerignore
README.md
```

Nothing else should be committed at the root. `build/`, `.gradle/`, and any
local `.db` files are excluded via `.gitignore`.

## Local development

```bash
./gradlew run
```

Reads `src/main/resources/application.conf` - binds to `0.0.0.0:8080` by
default.

## Deploying to Railway

1. Push this repo to GitHub (see commands below).
2. On railway.app: **New Project -> Deploy from GitHub repo** -> select this
   repo.
3. Railway detects the `Dockerfile` at the root automatically - no Root
   Directory override, no custom Docker path needed.
4. Once deployed: **Settings -> Networking -> Generate Domain** for a public
   HTTPS URL.

### Persistent storage (required before real users sign up)

Without this, the SQLite database is wiped on every redeploy/restart:

1. In the Railway project: **+ New -> Volume**, mount path `/data`, attach it
   to this service.
2. Add environment variable: `DB_PATH=jdbc:sqlite:/data/trenz_mirror.db`

### Environment variables

| Variable     | Required | Notes                                              |
|--------------|----------|-----------------------------------------------------|
| `PORT`       | No       | Railway sets this automatically                    |
| `DB_PATH`    | Yes*     | `jdbc:sqlite:/data/trenz_mirror.db` (with volume)  |
| `JWT_SECRET` | Yes      | Any long random string - don't use the code default |

\* Technically optional (falls back to a non-persistent local file), but
required for data to survive a redeploy.

## Pushing to GitHub

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/YOUR-REPO-NAME.git
git push -u origin main
```

## Updating the Android app afterward

Once you have your Railway URL, point the app at it in
`app/build.gradle.kts`:

```kotlin
buildConfigField("String", "BASE_URL", "\"https://your-app.up.railway.app/\"")
```

Since this is now HTTPS, remove the debug-only cleartext exception
(`app/src/debug/res/xml/network_security_config.xml` and its reference in
`app/src/debug/AndroidManifest.xml`) - it was only needed for the earlier
local `http://192.168.x.x` testing setup.
