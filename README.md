# SMS2Email

An Android application that forwards incoming SMS messages to a specified email address.

This app is useful for users who need to access their SMS messages remotely.

## OpenPGP encryption

Each email account can sign and encrypt forwarded SMS end to end with [OpenKeychain](https://f-droid.org/packages/org.sufficientlysecure.keychain/), so mail servers on the way can neither read nor fake them.

1. Install OpenKeychain on the phone. Import the recipient's public key, and create or import a key for the phone to sign with.
2. In SMS2Email, turn on **Sign and encrypt with OpenKeychain** for the email account.
3. Choose the recipient key and the signing key once; both are saved with the account.
4. Tap **Check encryption**. Allow SMS2Email in OpenKeychain, and enter the signing key's passphrase if it has one.

Emails use PGP/MIME (RFC 3156), which Thunderbird and Thunderbird for Android with OpenKeychain decrypt and verify automatically. The subject is always `SMS`; the sender, time, SIM and text are only inside the encrypted part. If OpenKeychain cannot sign and encrypt, the email is not sent. OpenKeychain remembers a passphrase only for a while, so a signing key made just for the phone, without a passphrase, keeps forwarding working unattended.

<p align="center"><a href="https://f-droid.org/packages/io.github.sms2email.sms2email"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid"  width="207" /></a>
<a href="https://apt.izzysoft.de/fdroid/index/apk/io.github.sms2email.sms2email"><img src="https://raw.githubusercontent.com/InfinityLoop1308/PipePipe/refs/tags/v5.5.0/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" width="207" /></a></p>

| 🌙                                                                        | ☀️                                                                         |
| ------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| ![Screenshot](metadata/en-US/images/phoneScreenshots/screenshot_dark.png) | ![Screenshot](metadata/en-US/images/phoneScreenshots/screenshot_light.png) |

![IzzyOnDroid Badge](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/io.github.sms2email.sms2email&label=IzzyOnDroid&style=flat-square)
[![IzzyOnDroid Yearly Downloads](https://img.shields.io/badge/dynamic/json?url=https://dlstats.izzyondroid.org/iod-stats-collector/stats/basic/yearly/rolling.json&query=$.['io.github.sms2email.sms2email']&label=IzzyOnDroid%20yearly%20downloads&style=flat-square)](https://apt.izzysoft.de/packages/io.github.sms2email.sms2email)
[![RB Status](https://shields.rbtlog.dev/simple/io.github.sms2email.sms2email?style=flat-square)](https://shields.rbtlog.dev/io.github.sms2email.sms2email)
