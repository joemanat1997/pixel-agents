# App Locker (Android)

แอป Android สำหรับ **ล็อกแอปบางแอปในเครื่อง** ด้วยรหัส PIN ของแอปเอง
(ตั้งรหัสแยกต่างหากจากรหัสปลดล็อกเครื่องได้) เขียนด้วย Kotlin แบบ Native

> หมายเหตุ: โปรเจกต์นี้อยู่ในโฟลเดอร์ `android-app-locker/` แยกอิสระจากส่วน
> pixel-agents (VS Code extension) ในรีโปเดียวกัน

## ฟีเจอร์

- เลือกได้ว่าจะล็อกแอปไหนบ้าง (รายการแอปที่ติดตั้งพร้อมไอคอน)
- ตั้งรหัส PIN ของแอปเอง — **เปลี่ยนรหัสใหม่ได้** และตั้งให้ไม่ซ้ำกับรหัส
  ปลดล็อกเครื่องได้ (เป็นรหัสคนละตัวกันโดยสมบูรณ์) ผ่านขั้นตอนแบบ keypad
- เมื่อเปิดแอปที่ถูกล็อก จะมีหน้าจอใส่ PIN เด้งทับทันที (แป้นตัวเลขสไตล์ iOS
  มี dots บอกจำนวนหลัก) ธีมสีส้ม-ดำ
- **ล็อกตัวแอป App Locker เองด้วย PIN** — ต้องใส่รหัสก่อนเข้าหน้าตั้งค่า
  (เปิด/ปิดได้จากสวิตช์ "ล็อกการเข้าแอปนี้ด้วย PIN") กันคนอื่นมาแก้รายการแอป
  หรือปิดการป้องกัน
- **ปลดล็อกด้วยลายนิ้วมือ** — ใช้ระบบ biometric ของเครื่อง (เด้งถามอัตโนมัติ
  เมื่อหน้าล็อกขึ้น, กดปุ่มลายนิ้วมือเพื่อถามซ้ำได้, ใช้ PIN เป็น fallback)
- **สุ่มตำแหน่งแป้นตัวเลข** — สลับตำแหน่งเลข 0-9 ทุกครั้งที่หน้า PIN ขึ้น
  กันคนแอบดู/กันรอยนิ้วบนจอ (เปิด/ปิดได้)
- **เปลี่ยนภาษาได้ 6 ภาษา** (แท็บ Settings → Language แบบสไลด์ลง/ย่อได้):
  อังกฤษ, ไทย, จีน, ญี่ปุ่น, สเปน, นอร์เวย์ — ใช้ AppCompat per-app locales
- **เปลี่ยนธีมสีได้** (แท็บ Settings → Theme): teal, ส้ม, น้ำเงิน, ม่วง, ชมพู, เขียว
- **UI มินิมอล + bottom navigation 3 แท็บ** (Dashboard / App List / Settings)
- **ความปลอดภัยสูง**: หน้าใส่ PIN ทุกหน้าตั้ง `FLAG_SECURE` (กันแคปจอ/อัดจอ/
  ภาพใน recents), ใส่ PIN ผิดเกิน 5 ครั้งจะล็อกชั่วคราว 30 วินาที (กัน brute-force),
  รหัสเก็บแบบแฮช PBKDF2 ใน EncryptedSharedPreferences
- รหัส PIN ถูกเก็บแบบ **แฮช (PBKDF2-HMAC-SHA256 + salt)** ใน
  EncryptedSharedPreferences — ไม่เก็บรหัสจริง
- บริการทำงานเบื้องหลัง (foreground service) และ **เริ่มทำงานเองหลังรีบูต**
- แอปที่ปลดล็อกแล้วจะถูกล็อกอีกครั้งเมื่อออกจากแอปหรือปิดหน้าจอ

## โครงสร้างโค้ด

| ไฟล์ | หน้าที่ |
|------|---------|
| `MainActivity.kt` | หน้าหลัก: สวิตช์เปิดป้องกัน, ขอสิทธิ์, เลือกแอปที่จะล็อก |
| `PinSetupActivity.kt` | ตั้ง/เปลี่ยนรหัส PIN (เปลี่ยนต้องใส่รหัสเดิมก่อน) |
| `LockScreenActivity.kt` | หน้าจอใส่ PIN ที่เด้งทับแอปที่ถูกล็อก |
| `AppAuthActivity.kt` | หน้าใส่ PIN ก่อนเข้าแอป App Locker เอง (self-lock) |
| `AppLockService.kt` | Foreground service: poll แอปที่อยู่หน้าจอด้วย UsageStats แล้วเด้งหน้า PIN |
| `BootReceiver.kt` | เริ่มบริการใหม่อัตโนมัติหลังรีบูต |
| `SecurePrefs.kt` | ที่เก็บข้อมูลแบบเข้ารหัส (รหัสที่แฮชแล้ว + รายชื่อแอปที่ล็อก) |
| `PinHasher.kt` | แฮชและตรวจสอบ PIN ด้วย PBKDF2 |
| `SessionState.kt` | สถานะในหน่วยความจำว่าแอปไหนปลดล็อกชั่วคราวแล้ว |
| `AppListAdapter.kt` | RecyclerView adapter แสดงรายการแอป |

## สิทธิ์ที่ต้องใช้ (ผู้ใช้ต้องเปิดเองในหน้าตั้งค่าครั้งแรก)

1. **Usage Access** (`PACKAGE_USAGE_STATS`) — ตรวจว่าแอปไหนกำลังเปิดอยู่
2. **Display over other apps** (`SYSTEM_ALERT_WINDOW`) — เด้งหน้าจอ PIN ทับแอปอื่น
3. (อัตโนมัติ) Foreground service + รับ event boot

หน้า `MainActivity` มีปุ่มพาไปหน้าตั้งค่าของระบบเพื่อเปิดสิทธิ์เหล่านี้ และจะ
ไม่ยอมเปิด "การป้องกัน" จนกว่าจะตั้ง PIN และให้สิทธิ์ครบ

## วิธีบิลด์

ต้องมี Android SDK (เปิดผ่าน Android Studio จะง่ายที่สุด)

```sh
cd android-app-locker
# สร้างไฟล์ local.properties ชี้ไปที่ Android SDK เช่น:
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug
# ได้ไฟล์ APK ที่ app/build/outputs/apk/debug/app-debug.apk
```

หรือเปิดโฟลเดอร์ `android-app-locker` ใน Android Studio แล้วกด Run

- `compileSdk` / `targetSdk`: 34
- `minSdk`: 26 (Android 8.0)

### APK ขนาดเล็ก (release)
debug APK จะใหญ่ ถ้าต้องการไฟล์เล็กที่สุดให้ build แบบ **release** (เปิด R8
minify + shrinkResources + จำกัดภาษา ทำให้เล็กลงมาก และเซ็นด้วย debug key
ให้แล้ว ติดตั้งได้เลย):

1. Android Studio → เมนู **Build ▸ Select Build Variant…** → เลือก **release**
2. **Build ▸ Build APK(s)** → ได้ `app/build/outputs/apk/release/app-release.apk`

## ข้อจำกัด / หมายเหตุ

- วิธี UsageStats จะ poll ทุก ~700ms (บน background thread) อาจมีดีเลย์
  เสี้ยววินาทีก่อนหน้า PIN เด้ง — การ poll จะ**หยุดเองเมื่อจอดับ**และเมื่อ
  ไม่มีแอปถูกล็อก เพื่อประหยัดแบต (ถ้าต้องการเร็ว/เนียนกว่านี้ใช้
  AccessibilityService ได้ แต่ขอสิทธิ์ยากกว่า)
- บน Android 13+ ระบบขอสิทธิ์แจ้งเตือน (POST_NOTIFICATIONS) — ถ้าไม่ให้
  ตัวบริการยังทำงานได้ แค่ไม่โชว์ notification
- iOS ทำฟีเจอร์แบบนี้ไม่ได้สำหรับนักพัฒนาทั่วไป (ต้องใช้ Screen Time API
  ที่ต้องขอสิทธิ์พิเศษจาก Apple)
