Drishyam Android project — v2.4.2

This ZIP contains the Android Studio / GitHub Actions source project.

Build through GitHub Actions:
1. Upload the CONTENTS of this ZIP to your repository (including the hidden .github folder).
2. In repository Settings > Secrets and variables > Actions, configure UNSPLASH_ACCESS_KEY with your Unsplash Access Key.
3. Keep GOOGLE_SERVICES_JSON and the existing Android signing secrets configured if your workflow requires them.
4. Open Actions and run the Build APK workflow. Download the Drishyam-APK artifact after a successful run.

Design refresh:
- Midnight black base with amber, coral, pink and violet accents.
- Dark translucent glass surfaces instead of flat grey panels.
- Rounded cards and spring/bounce press feedback.
- BuildConfig injects UNSPLASH_ACCESS_KEY at build time; do not paste the key into source files.

Version: 2.4.2 (versionCode 13)
Application ID remains com.savan.drishyam so the app identity/signing can be preserved.
