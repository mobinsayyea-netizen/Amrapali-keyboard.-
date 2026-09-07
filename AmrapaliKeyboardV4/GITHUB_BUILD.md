# GitHub पर APK कैसे बनाएं (बिना Android Studio के)

1. GitHub पर एक नया **empty** repository बनाएं (कोई README auto-add मत करें)।
2. यह पूरा `AmrapaliKeyboardV4` फ़ोल्डर उस repo में push कर दें:
   ```bash
   cd AmrapaliKeyboardV4
   git init
   git add .
   git commit -m "V4: TalkBack fix + hover haptic + silent normal mode"
   git branch -M main
   git remote add origin https://github.com/<आपका-username>/<repo-name>.git
   git push -u origin main
   ```
3. Push होते ही repo के **Actions** tab में "Build APK" workflow अपने आप चलना शुरू हो जाएगा (`.github/workflows/build-apk.yml` में यह पहले से लगा हुआ है)।
4. वो workflow run खुलें, नीचे **Artifacts** में `AmrapaliKeyboard-debug-apk` दिखेगा — वहीं से `.apk` file download हो जाएगी।
5. वो APK फोन पर डालकर install करें (Unknown sources allow करना पड़ सकता है), फिर Settings → System → Languages & input में जाकर "Amrapali" keyboard enable करें और default बना दें।

नोट: यह debug APK है (testing के लिए) — Play Store पर डालने के लिए बाद में signed release build चाहिए होगी, वो अलग से बताऊंगा जब ज़रूरत हो।
