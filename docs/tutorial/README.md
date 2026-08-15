# toMeta JPA Entity 구조

현재 확정한 물리 ERD 22개 테이블을 기준으로 작성한 Java/JPA Entity 구조입니다.

## 패키지

- `domain.common`
  - `BaseCreatedEntity`
  - `BaseTimeEntity`

- `domain.user`
  - `User`
  - `AnonymousSession`
  - `UserConsent`
  - `PushToken`
  - `UserNotificationSetting`

- `domain.health`
  - `HealthConnection`
  - `HealthRawRecord`
  - `DailyHealthSummary`

- `domain.cosmetic`
  - `CosmeticProduct`
  - `UserCosmetic`
  - `Ingredient`
  - `CosmeticIngredient`
  - `CosmeticSet`
  - `CosmeticSetItem`

- `domain.record`
  - `DailyRecord`
  - `DailyRecordCosmetic`
  - `DailyRecordImage`

- `domain.report`
  - `DailyReport`
  - `WeeklyReport`
  - `WeeklyReportAnalysis`

- `domain.tip`
  - `SkinCareTip`
  - `UserDailySkinCareTip`

## 설계 원칙

1. 모든 연관관계는 현재 ERD대로 비식별 관계입니다.

2. FK를 가진 자식 Entity에서 부모 Entity로 단방향 매핑합니다.
  - 불필요한 양방향 컬렉션을 만들지 않아 연관관계 관리 복잡도와 N+1 위험을 줄입니다.
  - `UserNotificationSetting` 역시 FK를 소유하므로 `UserNotificationSetting → User` 단방향 관계로 구성합니다.

3. 모든 `@ManyToOne`, `@OneToOne`은 `LAZY`를 기본으로 사용합니다.

4. Entity에는 `@Data` 및 공개 setter를 사용하지 않습니다.
  - 상태 변경이 필요한 경우 의미가 드러나는 도메인 메서드를 사용합니다.

5. enum 성격의 컬럼은 물리 ERD와 동일하게 `VARCHAR` + Java `String`으로 관리합니다.
  - API/DTO 계층에서 허용값을 검증합니다.

6. `payload`, `ingredients_snapshot`은 MySQL JSON 컬럼을 유지하면서 Java에서는 JSON 문자열로 관리합니다.
  - 필요 시 추후 `JsonNode + @JdbcTypeCode(SqlTypes.JSON)` 형태로 변경할 수 있습니다.

7. `UserCosmetic`은 `deletedAt` 기반 Soft Delete 방식으로 관리합니다.
  - 과거 일일 기록에서 사용된 화장품 정보는 `DailyRecordCosmetic`의 스냅샷 데이터로 유지합니다.

8. 개별 화장품과 화장품 세트의 사용 시간 정책을 분리합니다.
  - `UserCosmetic`은 모닝/나이트 구분 없이 공통으로 관리합니다.
  - `CosmeticSet`에서만 `morning`, `night`, `both` 사용 시간대를 관리합니다.
  - `DailyRecordCosmetic.usagePeriod`는 해당 날짜에 실제 사용한 시간대를 의미하므로 유지합니다.

9. 사용자 알림 설정은 `User`와 분리하여 `UserNotificationSetting`에서 관리합니다.
  - `User`와 `UserNotificationSetting`은 1:1 관계입니다.
  - 일간 리포트 알림 여부를 관리합니다.
  - 기록 작성 알림 여부 및 시간을 관리합니다.
  - 주간 리포트 알림 여부 및 시간을 관리합니다.
  - 일간 리포트 발행 시각은 오전 7시 고정이므로 별도의 시간 컬럼을 두지 않습니다.
  - 알림 비활성화 시에도 기존 설정 시간은 유지할 수 있도록 시간 컬럼은 nullable로 관리합니다.

10. 날씨 및 위치 데이터는 저장하지 않습니다.
  - `DailyRecord`에서 기온, 습도, 날씨 상태 관련 필드를 관리하지 않습니다.
  - 날씨 및 위치 정보는 일간 리포트 AI 분석 대상에서 제외합니다.

11. DB의 `ON DELETE CASCADE / SET NULL / RESTRICT`는 Flyway DDL이 최종 책임을 지는 것으로 둡니다.
  - JPA `cascade = CascadeType.*`를 불필요하게 사용하지 않습니다.

## 주요 도메인 정책

### 사용자

- 별도의 로그인 없이 익명 세션을 통해 사용자를 식별합니다.
- 익명 세션 토큰 원문은 Cookie에 저장하고 DB에는 해시값만 저장합니다.
- 사용자 기본 정보와 알림 설정은 서로 다른 엔티티에서 관리합니다.

### Health Connect

- `HealthConnection`을 통해 사용자와 Android Health Connect 연결 상태를 관리합니다.
- `HealthRawRecord`에 Health Connect 원본 데이터를 저장합니다.
- `DailyHealthSummary`에서 일 단위 건강 데이터를 집계합니다.

### 화장품

- 개별 화장품은 `UserCosmetic`에서 관리하며 사용 시간대를 저장하지 않습니다.
- 화장품 세트는 `CosmeticSet`에서 `morning`, `night`, `both`로 구분합니다.
- 화장품 주요 성분은 `Ingredient`, `CosmeticIngredient`를 통해 관리합니다.

### 일일 기록

- `DailyRecord`는 사용자가 직접 입력한 일일 피부 및 생활 기록을 관리합니다.
- 날씨 및 위치 데이터는 저장하지 않습니다.
- `DailyRecordCosmetic`에는 기록 당시 화장품 정보를 스냅샷으로 저장합니다.
- 개별 화장품이 이후 수정되거나 삭제되더라도 과거 기록의 화장품 정보는 유지합니다.

### 알림

- 실제 FCM 발송 대상 기기는 `PushToken`에서 관리합니다.
- 사용자가 어떤 알림을 받을지는 `UserNotificationSetting`에서 관리합니다.
- FCM Token과 사용자 알림 설정은 서로 독립적으로 관리합니다.

## 확인할 부분

- `skin_temperature_celsius`는 서버 집계 정책에서 어떤 대표값을 사용할지 확정 필요
  - 예: 수면 중 평균 / 일 평균 등

- `menstruation_status`의 정확한 계산 결과 포맷 확정 필요

- `stress_level`의 서버 추정 알고리즘 및 값 범위 확정 필요

- enum 성격의 String 컬럼은 DTO Validation 또는 도메인 Enum/Converter로 강화 가능

- Health Connect 원본 JSON을 자주 질의하게 되는 경우 JSON 문자열 대신 `JsonNode` 매핑 검토
