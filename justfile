sdk := env_var_or_default("ANDROID_SDK_ROOT", env_var_or_default("ANDROID_HOME", "$HOME/Android/Sdk"))
avd := env_var_or_default("SMS2EMAIL_AVD", "Medium_Phone_API_36.1")

emulator:
    "{{sdk}}/emulator/emulator" -avd "{{avd}}" -no-snapshot -no-audio >/tmp/sms2email-emulator.log 2>&1 &
