# Ghi chú port bookstore-android-biometric → bookstore-expo-biometric

Theo dõi tiến độ port app Android (Kotlin/Compose) sang React Native + Expo. Kế hoạch gốc:
`/Users/yamakun/.claude/plans/generic-squishing-scroll.md`.

## Trạng thái tổng quan

- [x] Phase 1 — Scaffold + login/logout OIDC
- [x] Phase 2 — Biometric vault (lưu + mở khóa)
- [x] Phase 3 — QR scanner + luồng LEGACY
- [x] Phase 4 — Luồng KEYCLOAK_SPI
- [x] Phase 5 — Danh sách sách + hoàn thiện

**Toàn bộ 5 phase trong kế hoạch đã code xong.** Còn lại: test thủ công trên thiết bị thật (xem mục "Việc còn lại trước khi coi là xong" ở cuối file).

## Việc đã làm ngoài code

- **Đăng ký redirect URI mới cho Keycloak client `biometric-demo`:**
  - Thêm `bookstorebioexpo://oauth2redirect` song song với `com.snp.bookstorebio:/oauth2redirect` (app Android cũ vẫn dùng được).
  - Áp dụng trực tiếp vào DB thật đang chạy qua Keycloak Admin REST API (`PUT /admin/realms/test/clients/{id}`) — vì volume `keycloak_keycloak-db` đã tồn tại nên sửa file JSON không tự áp dụng lại.
  - Đồng thời cập nhật `keycloak-config/test-realm.json` (dòng ~554) để nhất quán nếu sau này re-import từ đầu.
  - `post.logout.redirect.uris` của client là `"+"` (dùng chung danh sách `redirectUris`) nên không cần sửa thêm gì cho logout.

## Cấu trúc project

```
bookstore-expo-biometric/
  app.config.ts          # Keycloak/API config, scheme, cleartext HTTP, ATS exception, plugins
  App.tsx                 # Root: AppProvider + switch theo UiState
  src/
    config.ts             # đọc Constants.expoConfig.extra
    auth/
      authManager.ts       # login/logout/refresh OIDC (expo-auth-session)
      jwt.ts                # decode preferred_username (jwt-decode)
      biometricPrompts.ts   # promptSaveToVault / promptUnlockVault
      biometricEnrollment.ts # check hasHardware/isEnrolled trước khi bật vân tay
    vault/
      biometricVault.ts     # react-native-keychain wrapper (BiometricVault.kt tương đương)
    state/
      types.ts, reducer.ts, AppContext.tsx   # Context + useReducer (thay StateFlow)
    screens/
      LoginScreen.tsx        # login + nút "unlock bằng vân tay"
      LoadingScreen.tsx
      LoggedInScreen.tsx      # placeholder, sẽ thành BooksScreen ở Phase 5
```

## Phase 1 — Scaffold + login/logout OIDC

**Đã cài:** `expo-auth-session`, `expo-web-browser`, `expo-constants`, `expo-crypto`, `jwt-decode`.

**Quyết định kỹ thuật:**
- Không dùng Expo Router — dùng switch thủ công theo `UiState` trong `App.tsx`, vì đồ thị màn hình đơn giản và không cần deep-link-first routing (tránh xung đột với OAuth redirect deep link).
- Discovery endpoints tự dựng thủ công (`{baseUrl}/realms/{realm}/protocol/openid-connect/{auth,token,logout}`) thay vì fetch discovery document — giống `AuthorizationServiceConfiguration` trong `AuthManager.kt`.
- `app.config.ts` thay `app.json` để đọc `EXPO_PUBLIC_*` env override giống Gradle `project.findProperty(...) ?: default`.
- `usesCleartextTraffic` (Android) **không phải** field trực tiếp của `ExpoConfig.android` — phải qua plugin `expo-build-properties` (`android.usesCleartextTraffic`). iOS cần khai `NSAppTransportSecurity.NSAllowsArbitraryLoads` vì không có cờ tương đương.
- `redirectUri`: scheme riêng `bookstorebioexpo://oauth2redirect` (khác app Android `com.snp.bookstorebio:/`) để 2 app coexist trên cùng Keycloak client.

**Verify đã chạy:** `tsc --noEmit` sạch, `expo config --type public` resolve đúng `extra`, `expo export --platform android` bundle OK (622 modules).

**Việc user cần làm:** đăng ký redirect URI trong Keycloak — **đã làm xong** (xem mục trên).

## Phase 2 — Biometric vault

**Đã cài:** `react-native-keychain`, `expo-local-authentication`, `expo-dev-client`, `@react-native-async-storage/async-storage`.

**Quyết định kỹ thuật:**
- Dùng `react-native-keychain` (không dùng `expo-secure-store`) — người dùng đã chọn để giữ đúng cơ chế "biometric mở khóa cipher" thay vì "check pass/fail rồi đọc secret".
- Cấu hình vault: `storage: STORAGE_TYPE.AES_GCM` (theo doc của lib: "requires user authentication for both encryption and decryption operations") + `accessControl: ACCESS_CONTROL.BIOMETRY_CURRENT_SET` + `securityLevel: SECURITY_LEVEL.SECURE_HARDWARE` + `accessible: WHEN_UNLOCKED_THIS_DEVICE_ONLY`. Đây là map gần nhất với `KeyGenParameterSpec.setUserAuthenticationRequired(true)` trong `BiometricVault.kt` trên Android.
- Namespace theo username qua Keychain `service` key (`bookstore_biometric_vault_{username}`) — giữ đúng khả năng nhiều tài khoản cùng bật vân tay song song, logout không xóa vault.
- `lastUsername`/`biometricEnabled` (flag không nhạy cảm) lưu ở `AsyncStorage`, không lưu trong Keychain (Keychain chỉ giữ chính refresh token).
- **Khác biệt platform đã ghi chú trong code** (`biometricVault.ts`, `biometricPrompts.ts`): trên Android, `AES_GCM` storage có thể yêu cầu xác thực ở CẢ HAI chiều write/read (khác Kotlin gốc chỉ yêu cầu xác thực khi decrypt, không phải encrypt) — cần test thực tế xem `saveToken` có tự bật prompt sinh trắc học hay không trên thiết bị thật.
- Không có `KeyPermanentlyInvalidatedException` chuẩn trên iOS/cross-platform → xử lý: **mọi lỗi đọc vault đều coi là "vault invalidated"**, tự động `clear()` + bắt đăng nhập lại bằng mật khẩu (xem `biometricPrompts.ts:promptUnlockVault`).
- Thêm `NSFaceIDUsageDescription` vào `app.config.ts` (bắt buộc trên iOS khi dùng Face ID, thiếu sẽ tự fallback về passcode).

**Verify đã chạy:** `tsc --noEmit` sạch, `expo export --platform android` bundle OK (638 modules), `expo prebuild --platform android` sinh đúng `AndroidManifest.xml` (permission `USE_BIOMETRIC`/`USE_FINGERPRINT`, `usesCleartextTraffic="true"`, intent-filter scheme `bookstorebioexpo`). Thư mục `android/` sinh ra bởi prebuild đã xóa sau khi kiểm tra (không commit, đã gitignore).

**Quan trọng — thay đổi cách chạy app:** từ Phase 2 trở đi **không dùng được Expo Go** từ store nữa (native module `react-native-keychain`). `npm run android`/`npm run ios` giờ chạy `expo run:android`/`expo run:ios` (build Dev Client thật, cần Android Studio/Xcode).

**Chưa test trên thiết bị thật** (cần user tự chạy `npm run android`/`ios` vì môi trường này không có máy ảo/thiết bị kết nối) — đặc biệt cần xác nhận:
1. `saveToken` có bật prompt sinh trắc học hay không trên Android.
2. Luồng "vault invalidated" hoạt động đúng khi đổi vân tay đăng ký trên máy.

## Phase 3 — QR scanner + luồng LEGACY

**Đã cài:** `expo-camera`.

**File mới:**
- `src/qr/QrScannerScreen.tsx` — `CameraView` + `barcodeScannerSettings={{ barcodeTypes: ['qr'] }}`, single-shot detection qua `useRef` (giống `booleanArrayOf(false)` trong Kotlin — chặn xử lý > 1 lần khi `onBarcodeScanned` bắn nhiều lần trước khi camera unmount). Dùng `useCameraPermissions` để xin quyền camera.

**Cập nhật state:**
- `state/types.ts`: thêm `QrLoginMode`, field `scanningQrMode`/`urlToOpen` trong `loggedIn` state, các action `scanQrClicked`/`qrScanDismissed`/`legacyQrScanned`/`legacyQrInvalid`/`urlOpened`.
- `state/reducer.ts`: xử lý các action trên.
- Cố ý **chưa thêm** `qrApproveResult`/`pendingQrConfirmation` (dành cho luồng KEYCLOAK_SPI) — để Phase 4 thêm riêng, tránh phình reducer sớm.

**Luồng LEGACY trong `LoggedInScreen.tsx`:**
- Bấm "Quét QR trên trang web thường" → mở `QrScannerScreen`.
- Quét được: validate `http://`/`https://` prefix (giống Kotlin) → set `urlToOpen`.
- `useEffect` theo dõi `urlToOpen` → gọi `expo-web-browser.openBrowserAsync(url)` (không phải auth session, vì không cần bắt redirect — giống Custom Tabs "chỉ mở URL" trong bản gốc) → dispatch `urlOpened` khi đóng.
- Nút "Quét QR trên trang đăng nhập Keycloak" (KEYCLOAK_SPI) đã có UI nhưng khi quét chỉ đóng màn hình quét (`qrScanDismissed`) — xử lý thật để Phase 4.

**Cấu hình `app.config.ts`:**
- Thêm plugin `expo-camera` với `cameraPermission` (mô tả tiếng Việt cho `NSCameraUsageDescription`), và **tắt** `microphonePermission`/`recordAudioAndroid` vì app chỉ quét QR tĩnh, không quay video — plugin mặc định xin cả quyền micro/`RECORD_AUDIO` cho tính năng quay video của `expo-camera` mà app này không dùng.
- Đã verify qua `expo prebuild --platform android`: `AndroidManifest.xml` cuối cùng chỉ có `CAMERA`, `USE_BIOMETRIC`, `USE_FINGERPRINT` — không có `RECORD_AUDIO` thừa.

**Khác biệt platform cần lưu ý khi test (đã note trong plan gốc, nhắc lại ở đây):**
- Android: `openBrowserAsync` dùng Custom Tabs, chia sẻ cookie Chrome — nếu đã đăng nhập Keycloak trên Chrome, trang xác nhận sẽ tự nhận diện, không cần đăng nhập lại.
- iOS: dùng `SFSafariViewController`/tương tự qua `expo-web-browser`, **không đảm bảo** dùng chung session Safari — nhiều khả năng phải đăng nhập lại thủ công trên trang xác nhận. Đây là khác biệt UX cần test riêng, không phải bug.

**Verify đã chạy:** `tsc --noEmit` sạch, `expo export --platform android` bundle OK (646 modules), `expo prebuild --platform android` xác nhận Manifest permission đúng như mong đợi.

**Chưa test trên thiết bị thật** — cần user tự quét QR thật từ `bookstore-fe-qr` để xác nhận: (1) camera scan hoạt động, (2) browser mở đúng URL, (3) khác biệt cookie SSO Android vs iOS như ghi chú trên.

## Phase 4 — Luồng KEYCLOAK_SPI

**File mới:**
- `src/qr/qrLoginApi.ts` — `scan`/`approve`/`cancel` (POST tới `{apiUrl}/qr-login/{scan,approve,cancel}` với `{session_id, access_token}`, header `Authorization: Bearer`), `parseQrPayload` validate JSON `{apiUrl, sessionId}`. Trả `{ok: true} | {ok: false, error}` thay vì throw — tương đương `Result<Unit>` trong Kotlin.
- `src/screens/QrConfirmDialog.tsx` — modal "Cho phép đăng nhập?" (Xác nhận/Từ chối).
- `src/screens/QrApproveResultDialog.tsx` — modal 3 trạng thái: `approving` (spinner, không cho đóng), `success`, `failed`.
- `auth/biometricPrompts.ts`: thêm `promptConfirmQrApprove()` — gọi `expo-local-authentication.authenticateAsync` **presence-only** (không đụng vault/Keychain, khác hẳn `promptSaveToVault`/`promptUnlockVault`), trả `boolean` thành công/thất bại.

**Cập nhật state (`state/types.ts`, `state/reducer.ts`):**
- Thêm `QrApproveResult` (`approving | success | failed`), `PendingQrConfirmation { apiUrl, sessionId }`, field `qrApproveResult`/`pendingQrConfirmation` trong `loggedIn` state.
- Action mới: `spiQrInvalid`, `spiScanStarted`, `spiScanSucceeded`, `spiScanFailed`, `qrApproveResultDismissed`, `qrConfirmationDismissed`, `qrApproveStarted`, `qrApproveSucceeded`, `qrApproveFailed`.

**Luồng trong `LoggedInScreen.tsx` (`onQrDetected` nhánh KEYCLOAK_SPI):**
1. Quét được → parse JSON `{apiUrl, sessionId}`. Parse lỗi → `spiQrInvalid`.
2. Gọi `qrLoginApi.scan(...)` (dispatch `spiScanStarted` trước để hiện dialog "Đang xác nhận…") → thành công thì chuyển sang `pendingQrConfirmation` (ghi nhận danh tính, **chưa cấp quyền**), thất bại thì `spiScanFailed`.
3. `QrConfirmDialog` hiện khi có `pendingQrConfirmation` → bấm "Xác nhận" gọi `promptConfirmQrApprove()` (biometric presence-only) → nếu pass thì gọi `qrLoginApi.approve(...)` → `qrApproveSucceeded`/`qrApproveFailed`.
4. Bấm "Từ chối" hoặc biometric thất bại → `onQrConfirmDismiss` gọi `qrLoginApi.cancel(...)` để giải phóng session phía kia (không để treo mãi ở "đã quét, chờ xác nhận"), giống Kotlin gốc.

**Verify đã chạy:** `tsc --noEmit` sạch, `expo export --platform android` bundle OK (649 modules).

**Chưa test trên thiết bị thật** — cần user tự quét QR thật từ trang `qr-login.ftl` (Keycloak SPI "Try another way") để xác nhận scan→pending→approve chạy đúng và cancel giải phóng session phía Keycloak (không bị treo).

## Phase 5 — Danh sách sách + hoàn thiện

**File mới:**
- `src/api/bookstoreApi.ts` — `fetchBooks(accessToken)` gọi `GET {bookstoreApiBaseUrl}/api/books` với Bearer token. Gộp `BookstoreApi.kt` + `BookRepositoryImpl.kt` + `GetBooksUseCase.kt` + `BookRepository.kt` + `Book.kt`/`BookDto.kt` thành 1 file — không cần tách layer vì không có DI framework (Hilt) ép buộc tách trong bản gốc.
- `src/screens/BooksScreen.tsx` — thay thế `LoggedInScreen.tsx` (đã xóa), là bản đầy đủ với: header (username + toggle vân tay + error), `FlatList` danh sách sách, loading state, nút Làm mới/Quét QR (x2)/Đăng xuất, cùng 2 dialog QR ở Phase 4.

**Cập nhật state:**
- `state/types.ts`: thêm `books: Book[]`, `loadingBooks: boolean` vào `loggedIn` state; action `booksLoadStarted`/`booksLoadSucceeded`/`booksLoadFailed`.
- `state/reducer.ts`: khởi tạo `books: []`, `loadingBooks: false` khi login/unlock thành công; xử lý 3 action trên.

**Hành vi trong `BooksScreen.tsx`:**
- `useEffect` tự gọi `onRefresh()` một lần khi màn hình mount — tương đương `AppViewModel` gọi `loadBooks()` ngay sau `onFirstLoginResult`/`onBiometricUnlocked` trong bản gốc.
- Nút "Làm mới" gọi lại `fetchBooks`, cập nhật `books`/`error` qua reducer.

**Rà soát hoàn thiện cấu hình (`app.config.ts`):**
- Đã chạy `expo prebuild` (cả Android **và** iOS) lần cuối để xác nhận toàn bộ plugin/permission áp dụng đúng:
  - Android Manifest: `usesCleartextTraffic="true"`, permissions `USE_BIOMETRIC`, `USE_FINGERPRINT`, `CAMERA` (không có `RECORD_AUDIO` thừa), intent-filter scheme `bookstorebioexpo`.
  - iOS Info.plist: `NSCameraUsageDescription`, `NSFaceIDUsageDescription`, `NSAppTransportSecurity.NSAllowsArbitraryLoads` đều có mặt; không có `NSMicrophoneUsageDescription` thừa.
- `android.package` / `ios.bundleIdentifier` = `com.snp.bookstorebioexpo` (khác `com.snp.bookstorebio` của app Android gốc, coexist được trên cùng máy/Keycloak client).

**Verify đã chạy:** `tsc --noEmit` sạch, `expo export` bundle thành công **cả Android (650 modules) và iOS (652 modules)**, `expo prebuild` (2 platform) sinh cấu hình native đúng như kỳ vọng ở trên. Toàn bộ thư mục `android/`/`ios/` sinh ra đã xóa sau khi kiểm tra (không commit, đã gitignore).

## Cấu trúc source cuối cùng

```
src/
  api/bookstoreApi.ts
  auth/
    authManager.ts
    biometricEnrollment.ts
    biometricPrompts.ts
    jwt.ts
  config.ts
  qr/
    QrScannerScreen.tsx
    qrLoginApi.ts
  screens/
    BooksScreen.tsx
    LoadingScreen.tsx
    LoginScreen.tsx
    QrApproveResultDialog.tsx
    QrConfirmDialog.tsx
  state/
    AppContext.tsx
    reducer.ts
    types.ts
  vault/
    biometricVault.ts
App.tsx
app.config.ts
```

## Đã test trên thiết bị thật (iPhone)

- **Môi trường build:** máy Mac cần Xcode đầy đủ (không chỉ Command Line Tools) — macOS phải ≥26.6 để cài được Xcode từ App Store. Cài thêm `watchman` (khuyến nghị chính thức cho Metro). Java cho Android build: máy có sẵn Java 26 (không tương thích AGP hiện tại, lỗi `jlink`/`androidJdkImage`) — đã cài riêng Java 17 qua Homebrew (`brew install openjdk@17`) và set `JAVA_HOME`/`ANDROID_HOME` trong `~/.zshrc`, không đổi Java hệ thống mặc định.
- **Lần build đầu lên thiết bị thật cần ký app thủ công qua Xcode:** `npx expo run:ios --device` báo lỗi `No code signing certificates are available to use` — mở `ios/*.xcworkspace` bằng Xcode, vào Signing & Capabilities → chọn Team (Apple ID cá nhân, free provisioning đủ dùng) → Xcode tự tạo certificate. Sau lần đó CLI chạy bình thường.
- **`npx expo run:ios --device` tự khởi động Metro** — nếu build qua Xcode (nút Run ▶️) trực tiếp thay vì qua CLI, Metro KHÔNG tự chạy kèm, app cài xong sẽ báo lỗi đỏ "Could not connect to development server". Phải tự chạy `npx expo start --dev-client` riêng.

### Bug quan trọng đã tìm và sửa: dialog QR không hiện trên iOS sau khi quét

**Triệu chứng:** luồng KEYCLOAK_SPI — quét QR xong (Keycloak xác nhận đã quét), nhưng dialog "Cho phép đăng nhập?" trên app không hiện. Bấm lại nút "Quét QR" tưởng chưa quét được → mở lại camera đè lên, gây hiện tượng tưởng như "đứng màn hình".

**Nguyên nhân gốc (xác nhận qua log `console.log` thêm tạm thời rồi xóa):**
1. State/reducer hoàn toàn đúng — `pendingQrConfirmation` được set đúng ngay sau khi `/qr-login/scan` thành công.
2. Nhưng trên **iOS**, `expo-camera`'s `CameraView` dùng native full-screen presentation (tương tự modal). Khi `scanningQrMode` chuyển về `null`, React unmount `QrScannerScreen` đúng, nhưng UIKit chưa hoàn tất animation dismiss camera. Nếu RN `Modal` (dùng cho `QrConfirmDialog`/`QrApproveResultDialog`) present ngay lập tức lúc đó, UIKit **âm thầm từ chối** present ("Attempt to present ... while a presentation is in progress") — lỗi này chỉ log ở tầng native (Xcode console), **không hề xuất hiện trong JS console/Metro log**, nên trông như dialog "không hiện" dù state đúng 100%.
3. Có bug phụ liên quan: gọi `dispatch`/side-effect trực tiếp trong `setState` updater hoặc trong native callback `onBarcodeScanned` khi nó bắn ngay trong render pass đầu tiên gây lỗi React thật: `Cannot update a component (AppProvider) while rendering a different component (QrScannerScreen)` — đã sửa bằng cách tách `onQrDetected` (side-effect gọi dispatch ở component cha) ra `useEffect` riêng, chỉ trigger sau khi `CameraView`'s local state đổi, không gọi trực tiếp trong callback native.

**Cách sửa (đã áp dụng trong `BooksScreen.tsx` và `QrScannerScreen.tsx`):**
- `QrScannerScreen.tsx`: thêm prop `active={detectedValue === null}` cho `CameraView` (dừng camera session ngay khi phát hiện QR, không chờ unmount — `active` là prop chính thức của `expo-camera` cho đúng mục đích này, chỉ có trên iOS). Gọi `onQrDetected` trong `useEffect` theo dõi state nội bộ, không gọi trực tiếp trong `onBarcodeScanned`.
- `BooksScreen.tsx`: thêm state `dialogsReady` với `setTimeout(400ms)` sau khi `scanningQrMode` về `null`, chỉ cho phép `QrApproveResultDialog`/`QrConfirmDialog` mount sau khoảng trễ đó — đảm bảo camera đã dismiss animation xong trước khi Modal cố present. Đồng thời thêm guard ở `onScanQrClick`/`onQrDetected`: chặn mở lại camera hoặc xử lý QR mới khi đã có `pendingQrConfirmation`/`qrApproveResult` đang chờ xử lý (tránh việc người dùng bấm lại nút quét khi tưởng chưa quét được, ghi đè state đang chờ).

**Đã verify:** quét QR SPI → dialog "Cho phép đăng nhập?" hiện đúng → bấm Xác nhận → Face ID → dialog "Đang xác nhận…" → "Thành công" — chạy trơn tru 2 lần liên tiếp trên iPhone thật.

**Lưu ý cho Android:** bug này là **đặc thù iOS** (do cách UIKit xử lý presentation chồng lấn). Trên Android, camera preview không dùng cơ chế modal-like tương tự nên nhiều khả năng không gặp vấn đề này — nhưng delay 400ms/guard đã thêm không gây hại gì nếu Android không cần, nên giữ nguyên logic chung cho cả 2 platform thay vì tách riêng.

## Việc còn lại trước khi coi là xong

1. **Luồng LEGACY (Device Grant) trên iOS** — chưa test riêng trên thiết bị thật. Cần verify hành vi cookie SSO Safari (`expo-web-browser.openBrowserAsync`) không đảm bảo dùng chung session như Android Custom Tabs (đã note ở Phase 3) — kỳ vọng có thể phải đăng nhập lại thủ công trên trang xác nhận, không phải bug.
2. **Biometric vault (save/unlock) trên iOS** — chưa xác nhận riêng hành vi Face ID gate cả write/read hay chỉ read (khác Android nơi `STORAGE_TYPE.AES_GCM` yêu cầu xác thực cả 2 chiều theo doc thư viện).
3. **Test trên Android thật** — toàn bộ smoke test ở trên mới làm trên iPhone, chưa có thiết bị Android thật để đối chiếu.
4. Redirect URI Keycloak cho app Expo (`bookstorebioexpo://oauth2redirect`) đã đăng ký sẵn, không cần làm lại trừ khi đổi `scheme` trong `app.config.ts`.
