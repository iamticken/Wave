import re

def replace_in_file(file_path, replacements):
    with open(file_path, 'r') as file:
        content = file.read()

    for old, new in replacements.items():
        content = re.sub(old, new, content)

    with open(file_path, 'w') as file:
        file.write(content)

replacements = {
    r'android:label="@string/app_name"': r'android:label="@string/app_name_wave"',
    r'android:backupAgent=".absbackup.SignalBackupAgent"': r'android:backupAgent=".absbackup.WaveBackupAgent"',
    r'android:theme="@style/TextSecure.LightTheme"': r'android:theme="@style/Wave.LightTheme"',
    r'android:host="signal.art"': r'android:host="wave.art"',
    r'ic_launcher_alt_signal_color': r'ic_launcher_alt_wave_color',
    r'ic_launcher_alt_signal_dark': r'ic_launcher_alt_wave_dark',
    r'ic_launcher_alt_signal_dark_variant': r'ic_launcher_alt_wave_dark_variant',
    r'ic_launcher_alt_signal_white': r'ic_launcher_alt_wave_white',
    r'android:host="signal.group"': r'android:host="wave.group"',
    r'android:host="signaldonations.org"': r'android:host="wavedonations.org"',
    r'android:host="signal.tube"': r'android:host="wave.tube"',
    r'android:host="signal.me"': r'android:host="wave.me"',
    r'android:host="signal.link"': r'android:host="wave.link"',
    r'android:theme="@style/Signal.Transparent"': r'android:theme="@style/Wave.Transparent"',
    r'android:theme="@style/Signal.DayNight"': r'android:theme="@style/Wave.DayNight"',
    r'android:theme="@style/TextSecure.LightTheme.Popup"': r'android:theme="@style/Wave.LightTheme.Popup"',
    r'android:theme="@style/Theme.Signal.DayNight.NoActionBar"': r'android:theme="@style/Theme.Wave.DayNight.NoActionBar"',
    r'android:theme="@style/Signal.DayNight.NoActionBar"': r'android:theme="@style/Wave.DayNight.NoActionBar"',
    r'android:theme="@style/TextSecure.DarkNoActionBar.StoryViewer"': r'android:theme="@style/Wave.DarkNoActionBar.StoryViewer"',
    r'android:theme="@style/Signal.DayNight.ConversationSettings"': r'android:theme="@style/Wave.DayNight.ConversationSettings"',
    r'android:theme="@style/TextSecure.LightRegistrationTheme"': r'android:theme="@style/Wave.LightRegistrationTheme"',
    r'android:theme="@style/Theme.Signal.WallpaperCropper"': r'android:theme="@style/Theme.Wave.WallpaperCropper"'
}

replace_in_file('Wave-Android/app/src/main/AndroidManifest.xml', replacements)

print("File updated successfully!")
