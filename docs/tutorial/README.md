# toMeta 도메인 및 JPA Entity 구조

toMeta 백엔드의 현재 도메인 및 Java/JPA Entity 구조를 정리한 문서입니다.

현재 MVP 기획과 구현 상태를 기준으로 작성하며,
변경된 기획이나 사용하지 않는 구조는 문서에서 제외합니다.

## 패키지 구조

### `domain.common`

공통 Entity의 생성 및 수정 시간을 관리합니다.

- `BaseCreatedEntity`
- `BaseTimeEntity`

### `domain.user`

사용자 식별, 약관 동의, 알림 설정 및 푸시 발송 대상을 관리합니다.

- `User`
- `AnonymousSession`
- `UserConsent`
- `UserNotificationSetting`
- `PushToken`

### `domain.health`

Android Health Connect 연결과 수집된 건강 데이터를 관리합니다.

- `HealthConnection`
- `HealthRawRecord`
- `DailyHealthSummary`

### `domain.cosmetic`

화장품 정보와 사용자가 등록한 화장품 및 화장품 세트를 관리합니다.

- `CosmeticProduct`
- `UserCosmetic`
- `Ingredient`
- `CosmeticIngredient`
- `CosmeticSet`
- `CosmeticSetItem`

### `domain.record`

사용자가 직접 작성한 일일 피부 기록을 관리합니다.

- `DailyRecord`
- `DailyRecordCosmetic`
- `DailyRecordImage`

### `domain.report`

일간 및 주간 피부 분석 리포트를 관리합니다.

- `DailyReport`
- `WeeklyReport`
- `WeeklyReportAnalysis`

### `domain.tip`

사용자에게 제공하는 피부 관리 팁을 관리합니다.

- `SkinCareTip`
- `UserDailySkinCareTip`

---

## Entity 설계 원칙

### 1. 연관관계

FK를 가진 Entity에서 참조 대상 Entity로 단방향 매핑하는 것을 기본으로 합니다.

불필요한 양방향 연관관계는 사용하지 않으며,
`@ManyToOne`, `@OneToOne` 연관관계는 `LAZY` 로딩을 기본으로 사용합니다.

### 2. Entity 상태 변경

Entity에는 공개 setter 및 `@Data`를 사용하지 않습니다.

상태 변경이 필요한 경우 다음과 같이 의미가 드러나는 도메인 메서드를 사용합니다.

```java
user.updateProfile(...);
session.touch();
pushToken.updateFirebaseInstallationId(...);
```

### 3. 사용자 식별

별도의 로그인 기능 없이 익명 세션을 사용해 사용자를 식별합니다.

```text
최초 약관 동의
→ User 생성
→ 익명 세션 토큰 발급
→ anonymous_session Cookie 저장
→ 이후 Cookie를 통해 사용자 식별
```

익명 세션 토큰 원문은 클라이언트의 Cookie에 저장하며,
DB에는 토큰의 해시값만 저장합니다.

### 4. 사용자 정보와 알림 설정 분리

사용자의 기본 프로필과 알림 설정은 별도의 Entity에서 관리합니다.

```text
User
└── 기본 사용자 정보

UserNotificationSetting
└── 사용자 알림 설정
```

`UserNotificationSetting`에서는 다음 설정을 관리합니다.

- 일간 리포트 알림 여부
- 기록 작성 리마인드 여부
- 기록 작성 리마인드 시간
- 주간 리포트 알림 여부
- 주간 리포트 알림 시간

일간 리포트 발행 시각은 오전 7시로 고정하며 별도의 시간 컬럼을 두지 않습니다.

---

## 주요 도메인 정책

### 사용자 및 익명 세션

사용자는 최초 필수 약관 동의 시 생성됩니다.

이후 API 요청에서는 `anonymous_session` Cookie를 이용해 현재 사용자를 식별합니다.

익명 세션은 사용자 인증 및 WebView의 사용자 상태 유지에 사용합니다.

### Health Connect

서비스는 Android 앱 내부에서 React WebView와 Android Native를 함께 사용합니다.

Health Connect 데이터 접근은 Android Native가 담당하며,
Spring Boot 서버가 Health Connect에 직접 접근하지 않습니다.

```text
Galaxy Watch / 스마트폰
→ Samsung Health
→ Health Connect
→ Android Native
→ Spring Boot
```

`HealthConnection`은 사용자와 Android 앱 설치 환경 간의 Health Connect 연결을 관리합니다.

주요 정보는 다음과 같습니다.

- 사용자
- Android 앱 설치 식별자 `deviceId`
- `healthDeviceToken` 해시
- 연결 시각
- 마지막 동기화 시각
- 연결 해제 시각

Health Connect 연결 시 서버는 별도의 `healthDeviceToken`을 발급합니다.

```text
anonymous_session + deviceId
→ Health Connect 연결 API
→ HealthConnection 생성 또는 갱신
→ healthDeviceToken 발급
```

이후 건강 데이터 동기화에서는 익명 세션 Cookie 대신 `healthDeviceToken`을 사용합니다.

```text
Authorization: Bearer {healthDeviceToken}
```

`HealthRawRecord`에는 Health Connect에서 수집한 원본 데이터를 저장하며,
`DailyHealthSummary`에서는 필요한 값을 일 단위로 집계합니다.

### 화장품

사용자가 등록한 개별 화장품은 `UserCosmetic`에서 관리합니다.

개별 화장품에는 모닝/나이트 사용 구분을 저장하지 않습니다.

화장품 세트는 `CosmeticSet`에서 관리하며 사용 시간대를 다음과 같이 구분합니다.

- `morning`
- `night`
- `both`

화장품과 주요 성분의 관계는 `CosmeticIngredient`를 통해 관리합니다.

사용자가 등록한 화장품을 삭제하더라도 과거 피부 기록의 화장품 정보가 영향을 받지 않도록,
일일 기록에서는 기록 시점의 화장품 정보를 별도로 보존합니다.

### 일일 기록

`DailyRecord`는 사용자가 직접 작성한 일일 피부 상태 및 생활 기록을 관리합니다.

하루 기록에는 여러 화장품과 이미지를 연결할 수 있습니다.

`DailyRecordCosmetic`에는 기록 당시 사용한 화장품 정보를 보존하여,
이후 원본 화장품 정보가 변경되거나 삭제되어도 과거 기록을 유지할 수 있도록 합니다.

날씨 및 위치 정보는 현재 MVP의 저장 및 분석 대상에서 제외합니다.

### 리포트

건강 데이터와 사용자의 일일 기록을 기반으로 피부 상태 분석 결과를 제공합니다.

리포트는 다음과 같이 구분합니다.

- `DailyReport`
  - 일 단위 피부 분석 결과
- `WeeklyReport`
  - 주 단위 피부 상태 및 생활 패턴 요약
- `WeeklyReportAnalysis`
  - 주간 리포트의 세부 분석 결과

### 알림

사용자가 어떤 종류의 알림을 받을지는 `UserNotificationSetting`에서 관리합니다.

실제 FCM 푸시 발송 대상 앱 인스턴스는 `PushToken`에서 관리합니다.

```text
UserNotificationSetting
→ 어떤 알림을 받을지 관리

PushToken
→ 어느 Android 앱 인스턴스로 알림을 보낼지 관리
```

`deviceId`는 toMeta에서 생성한 Android 앱 설치본 식별자입니다.

Firebase Cloud Messaging의 발송 대상 식별자는 Firebase Installation ID(FID)를 사용합니다.

현재 DB의 기존 물리 컬럼명은 `token`을 유지하며,
Java Entity에서는 해당 값을 `firebaseInstallationId`로 관리합니다.

---

## Android Health Connect 구조

Android 프로젝트는 백엔드 Repository 내부의 `/android` 디렉터리에서 별도로 관리합니다.

```text
Android 앱
├── React WebView
│   └── 사용자 화면 및 온보딩
│
└── Android Native
    ├── Health Connect 권한
    ├── Health Connect 데이터 조회
    ├── deviceId 관리
    ├── healthDeviceToken 관리
    └── 서버 건강 데이터 동기화
```

Android Native에서 조회한 건강 데이터는 서버의 Health Connect 동기화 API를 통해 전달합니다.

```text
Health Connect
→ HealthConnectReader
→ HealthSyncRequestFactory
→ HealthSyncRequestDto
→ HealthConnectRepository
→ Spring Boot
```

`healthDeviceToken` 원문은 Android Keystore의 암호화 키를 이용해 암호화하여 로컬에 저장합니다.

---

## DB 스키마 관리

DB 스키마와 기준 데이터는 Flyway 마이그레이션으로 관리합니다.
Hibernate는 모든 환경에서 `ddl-auto: validate`만 수행하며 스키마를 변경하지 않습니다.

- 신규 DB는 `db/migration/{vendor}`의 V1부터 순서대로 적용됩니다.
- 이미 Hibernate가 생성한 기존 DB에 처음 Flyway를 적용할 때만
  `FLYWAY_BASELINE_ON_MIGRATE=true`로 실행해 version 1을 baseline 처리합니다.
- 첫 Flyway 배포가 완료되면 `FLYWAY_BASELINE_ON_MIGRATE=false`로 되돌립니다.
- 적용된 마이그레이션 파일은 수정하지 않고 이후 변경은 다음 버전 파일로 추가합니다.

---
