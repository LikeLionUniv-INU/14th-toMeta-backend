# toMeta JPA Entity 초안

현재 확정한 물리 ERD 22개 테이블을 기준으로 작성한 Java/JPA Entity 초안입니다.

## 패키지

- `domain.common`
  - `BaseCreatedEntity`
  - `BaseTimeEntity`
- `domain.user`
  - `User`
  - `AnonymousSession`
  - `UserConsent`
  - `PushSubscription`
- `domain.health`
  - `HealthConnection`
  - `HealthLinkToken`
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
2. FK를 가진 자식 Entity에서 부모 Entity로 단방향 매핑했습니다.
   - 불필요한 양방향 컬렉션을 만들지 않아 연관관계 관리와 N+1 위험을 줄였습니다.
3. 모든 `@ManyToOne`, `@OneToOne`은 `LAZY`를 기본으로 사용했습니다.
4. Entity에는 `@Data`, 공개 setter를 사용하지 않았습니다.
5. enum 성격의 컬럼은 물리 ERD와 동일하게 `VARCHAR` + Java `String`으로 두었습니다.
   - API/DTO 계층에서 허용값을 검증하는 현재 설계와 맞춘 것입니다.
6. `payload`, `ingredients_snapshot`은 MySQL JSON 컬럼을 유지하면서 Java에서는 JSON 문자열로 받습니다.
   - 필요하면 추후 `JsonNode + @JdbcTypeCode(SqlTypes.JSON)`로 변경할 수 있습니다.
7. `UserCosmetic`은 `deletedAt` 기반 Soft Delete입니다.
8. DB의 `ON DELETE CASCADE / SET NULL / RESTRICT`는 Flyway DDL이 최종 책임을 지는 것으로 두었습니다.
   - JPA `cascade = CascadeType.*`를 남발하지 않았습니다.

## 확인할 부분

- `skin_temperature_celsius`는 서버 집계 정책에서 어떤 대표값(수면 중 평균/일 평균 등)을 사용할지 확정 필요
- `menstruation_status`의 정확한 계산 결과 포맷 확정 필요
- `stress_level`의 서버 추정 알고리즘 및 값 범위 확정 필요
- enum 성격의 String 컬럼은 DTO Validation 또는 도메인 Enum/Converter로 강화 가능
- Health Connect 원본 JSON을 자주 질의하게 되면 JSON 문자열 대신 `JsonNode` 매핑 권장
