# SMS2Email

An Android application that forwards incoming SMS messages from selected SIM slots to configurable email senders and Matrix rooms through the official Matrix Android SDK.

Each SIM input and sender can be enabled or disabled independently. This app is useful for users who need to access their SMS messages remotely.

<p align="center"><a href="https://f-droid.org/packages/io.github.sms2email.sms2email"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid"  width="207" /></a>
<a href="https://apt.izzysoft.de/fdroid/index/apk/io.github.sms2email.sms2email"><img src="https://raw.githubusercontent.com/InfinityLoop1308/PipePipe/refs/tags/v5.5.0/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" width="207" /></a></p>

| 🌙                                                                        | ☀️                                                                         |
| ------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| ![Screenshot](metadata/en-US/images/phoneScreenshots/screenshot_dark.png) | ![Screenshot](metadata/en-US/images/phoneScreenshots/screenshot_light.png) |

## Configuration

Grant SMS and SIM-identification permissions, then select the SIM slots that should trigger forwarding. Add any number of enabled email or Matrix senders. Each email sender has its own SMTP host, port, OAuth token, encryption mode, sender address, and recipient. Matrix uses the official `matrix-android-sdk2` client and requires a Matrix user ID, access token, homeserver URL, and joined room ID.

Release builds are split into `arm64-v8a` and `armeabi-v7a` APKs to keep each download small.

![IzzyOnDroid Badge](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/io.github.sms2email.sms2email&label=IzzyOnDroid&style=flat-square)
[![IzzyOnDroid Yearly Downloads](https://img.shields.io/badge/dynamic/json?url=https://dlstats.izzyondroid.org/iod-stats-collector/stats/basic/yearly/rolling.json&query=$.['io.github.sms2email.sms2email']&label=IzzyOnDroid%20yearly%20downloads&style=flat-square)](https://apt.izzysoft.de/packages/io.github.sms2email.sms2email)
[![RB Status](https://shields.rbtlog.dev/simple/io.github.sms2email.sms2email?style=flat-square)](https://shields.rbtlog.dev/io.github.sms2email.sms2email)
