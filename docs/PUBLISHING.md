# Publishing blobface to Maven Central

These steps are for publishing from **Windows** with the actual upload run by
GitHub Actions (`.github/workflows/publish.yml`, macOS runner, needed for the iOS
artifacts). You do steps 1–5 once. Step 6 is repeated for each release.

Coordinates:

| artifact | coordinates |
|---|---|
| core | `io.github.pradyumna-dp:blobface-core:0.1.0` |
| compose | `io.github.pradyumna-dp:blobface-compose:0.1.0` |

The version is `blobface.version` in `gradle.properties`.

> Never commit secrets. Credentials and the signing key come only from GitHub
> secrets (CI) or `%USERPROFILE%\.gradle\gradle.properties` (local), never from
> the repo. `.gitignore` already excludes `*.gpg`, `*.asc` and `secring.*`.

---

## 1. Create a Central Portal account and verify the namespace

1. Go to <https://central.sonatype.com> and click **Sign in**, then choose **Sign in
   with GitHub** using the `pradyumna-dp` account. Signing in with GitHub verifies
   the `io.github.pradyumna-dp` namespace automatically.
2. Open **View Namespaces** from your profile menu and confirm that
   `io.github.pradyumna-dp` shows **Verified**.
   - If it isn't verified, click **Add Namespace** and enter
     `io.github.pradyumna-dp`. The portal will show a verification key. Create a
     **public** GitHub repository named exactly that key, such as
     `github.com/pradyumna-dp/abcd1234`, then click **Verify Namespace**. You can
     delete the repo once the namespace is verified.

## 2. Generate a user token (the Maven Central username and password)

1. On central.sonatype.com, open the profile menu, then **View Account**, then
   **Generate User Token**.
2. Copy the **Username** and **Password** it shows. These are the token values,
   not your login. You'll need them in step 5:
   - `MAVEN_CENTRAL_USERNAME` = token username
   - `MAVEN_CENTRAL_PASSWORD` = token password

## 3. Create a GPG signing key (Gpg4win)

1. Install **Gpg4win** from <https://www.gpg4win.org/> using the default
   components. This gives you `gpg` on the PATH and the Kleopatra GUI.
2. Open **PowerShell** and run:
   ```powershell
   gpg --full-generate-key
   ```
   - Kind: `(9) ECC (sign and encrypt)` (or `(1) RSA and RSA` with 4096 bits).
   - Expiry: `2y` is a good default. You can extend it later.
   - Name: `Pradyumna DP`. Email: the address you want shown on the artifacts.
   - Choose a strong **passphrase**. This becomes `SIGNING_IN_MEMORY_KEY_PASSWORD`.
3. Find the key ID:
   ```powershell
   gpg --list-secret-keys --keyid-format=long
   ```
   The `sec` line looks like `ed25519/ABCDEF0123456789`. The part after the slash
   is the long key ID. The 40-character line below it is the fingerprint.

## 4. Publish the public key and export the private key

1. Upload the **public** key to keys.openpgp.org. Maven Central checks
   signatures against public keyservers.
   ```powershell
   gpg --keyserver hkps://keys.openpgp.org --send-keys ABCDEF0123456789
   ```
   keys.openpgp.org then emails you a verification link. **Click it.** Until you
   do, the key is published without its user ID and Central may reject it. If
   `--send-keys` fails behind a proxy, export the key with
   `gpg --armor --export ABCDEF0123456789 > public.asc` and upload `public.asc`
   at <https://keys.openpgp.org/upload>. Delete the file afterwards.
2. Export the **private** key as ASCII armor for the GitHub secret:
   ```powershell
   gpg --armor --export-secret-keys ABCDEF0123456789 | Out-File -Encoding ascii $env:TEMP\signing-key.asc
   notepad $env:TEMP\signing-key.asc
   ```
   Copy the **entire** contents, including the
   `-----BEGIN PGP PRIVATE KEY BLOCK-----` and `-----END ...-----` lines. This
   becomes `SIGNING_IN_MEMORY_KEY`.
3. Delete the exported file once the secret is saved:
   ```powershell
   Remove-Item $env:TEMP\signing-key.asc
   ```
4. Back up your key safely, for example with Kleopatra's **Backup Secret Keys** to
   an offline location. If you lose it, you won't be able to sign future releases
   with the same identity.

## 5. Add the GitHub secrets

In the repository go to **Settings → Secrets and variables → Actions → New
repository secret** and add:

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | token username from step 2 |
| `MAVEN_CENTRAL_PASSWORD` | token password from step 2 |
| `SIGNING_IN_MEMORY_KEY` | the full ASCII-armored private key from step 4.2 |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | the key's passphrase from step 3 |

`publish.yml` passes these to Gradle as `ORG_GRADLE_PROJECT_mavenCentralUsername`,
`…Password`, `…signingInMemoryKey` and `…signingInMemoryKeyPassword`. These are
the property names the `com.vanniktech.maven.publish` plugin reads.

## 6. Cut a release

1. Make sure CI is green on `main` (`ci.yml`: JVM, Android, JS, Wasm and iOS).
2. Set the version in `gradle.properties` (`blobface.version=0.1.0`), then commit
   and push.
3. On GitHub go to **Releases → Draft a new release**, create the tag `v0.1.0`,
   write the notes, and click **Publish release**.
4. The **Publish** workflow runs on macOS. It re-runs the parity tests, then runs
   `./gradlew publishAndReleaseToMavenCentral`, which uploads both artifacts and
   releases them automatically.
5. Follow the deployment under **Deployments** on central.sonatype.com. Artifacts
   usually appear on `repo1.maven.org` within about 30 minutes, and search results
   update later.

### Optional: dry run locally (no upload)

You can check that the signed artifacts build, without uploading anything. Keep
credentials in your **user** Gradle properties, never in the repo:

```properties
# %USERPROFILE%\.gradle\gradle.properties
signingInMemoryKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----
signingInMemoryKeyPassword=your-passphrase
```

```powershell
.\gradlew.bat publishToMavenLocal
```

Note: iOS artifacts can't be built on Windows (they're skipped because of
`kotlin.native.ignoreDisabledTargets=true`). This is why real publishing runs on
macOS.
