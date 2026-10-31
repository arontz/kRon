# kRon for Android

Early work on an Android version of kRon: a floating lyrics pill over any app. Right now it is only a
starting screen; the plan is in the kRon design canvas.

## Building

You need JDK 17 and the Android SDK (Android Studio installs both). Then, from this folder:

```sh
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. GitHub Actions builds the same debug APK on every
push that touches `android/`; download it from the run's **Artifacts** section.

## Releasing

Pushing a tag like `android-v0.1.0` builds a signed APK and publishes it as a GitHub Release. It is
never marked "latest", so the Windows app and the website keep pointing at the Windows installer.

### One-time: the signing key

Every update must be signed with the same key, or Android refuses to install it over the old one.
**Back up the keystore file and its passwords somewhere safe. If they are lost, nobody can ever
update installed copies of the app.** Never commit the keystore.

1. Create the key (JDK's `keytool`; it asks for a password):

   ```sh
   keytool -genkeypair -v -keystore kron-release.jks -alias kron \
     -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Ronzo"
   ```

2. Turn it into text for GitHub:

   ```sh
   base64 -w0 kron-release.jks > kron-release.jks.txt   # macOS: base64 -i kron-release.jks
   ```

3. In the repository, open **Settings › Secrets and variables › Actions** and add four repository
   secrets:

   | Secret | Value |
   |---|---|
   | `ANDROID_KEYSTORE_BASE64` | the contents of `kron-release.jks.txt` |
   | `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
   | `ANDROID_KEY_ALIAS` | `kron` |
   | `ANDROID_KEY_PASSWORD` | the key password (the same as the keystore password unless you set another) |

### Developer verification

Google requires apps installed on certified phones to come from a verified developer, including
APKs downloaded from GitHub. It applies in Brazil, Indonesia, Singapore and Thailand since
30 September 2026 and worldwide from 2027. Register before the first public release, and register
the package name `io.github.arontz.kron` with the same signing key.
