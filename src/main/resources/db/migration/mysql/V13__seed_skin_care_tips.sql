INSERT INTO skin_care_tips (content, is_active, created_at, updated_at)
SELECT seed.content, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT '세안할 때는 뜨거운 물보다 미지근한 물을 사용해 피부 자극을 줄여보세요.' AS content
    UNION ALL SELECT '세안 전 손을 깨끗이 씻으면 얼굴에 불필요한 오염물이 닿는 것을 줄일 수 있어요.'
    UNION ALL SELECT '아침에는 피부 상태에 따라 물 세안이나 순한 세안제로 가볍게 씻어보세요.'
    UNION ALL SELECT '세안 후에는 물기가 마르기 전에 보습제를 발라 수분 손실을 줄여보세요.'
    UNION ALL SELECT '보습제는 문지르기보다 피부 결을 따라 부드럽게 펴 발라주세요.'
    UNION ALL SELECT '외출 전에는 날씨와 계절에 관계없이 자외선 차단제를 챙겨 바르세요.'
    UNION ALL SELECT '야외 활동이 길다면 자외선 차단제를 2~3시간 간격으로 덧발라주세요.'
    UNION ALL SELECT '자외선 차단제는 얼굴뿐 아니라 목과 귀처럼 노출되는 부위에도 발라주세요.'
    UNION ALL SELECT '새 화장품은 얼굴 전체에 사용하기 전에 작은 부위에 먼저 테스트해보세요.'
    UNION ALL SELECT '새 제품은 한 번에 하나씩 추가하면 피부 반응의 원인을 파악하기 쉬워요.'
    UNION ALL SELECT '화장품 사용 후 따갑거나 붉어지면 사용을 멈추고 피부 상태를 살펴보세요.'
    UNION ALL SELECT '각질 제거는 피부 상태를 확인하며 주 1~2회 이내로 조절해보세요.'
    UNION ALL SELECT '스크럽을 사용할 때는 세게 문지르지 말고 짧고 부드럽게 사용하세요.'
    UNION ALL SELECT '레티놀 제품은 낮은 농도와 적은 횟수로 시작해 피부가 적응할 시간을 주세요.'
    UNION ALL SELECT '레티놀을 사용한 다음 날에는 자외선 차단에 더욱 신경 써주세요.'
    UNION ALL SELECT '비타민 C 제품은 빛과 열을 피해 보관하고 색이나 냄새가 변하면 사용을 확인하세요.'
    UNION ALL SELECT '트러블은 손으로 만지거나 짜지 말고 깨끗하게 관리해주세요.'
    UNION ALL SELECT '베개 커버와 얼굴 수건을 정기적으로 세탁해 피부에 닿는 환경을 청결하게 유지하세요.'
    UNION ALL SELECT '메이크업 브러시와 퍼프는 주기적으로 세척하고 완전히 말려 사용하세요.'
    UNION ALL SELECT '운동 후에는 땀을 오래 방치하지 말고 가능한 한 빨리 부드럽게 씻어내세요.'
    UNION ALL SELECT '긴 시간의 뜨거운 샤워는 피부를 건조하게 할 수 있으니 시간과 온도를 조절하세요.'
    UNION ALL SELECT '실내가 건조할 때는 적절한 습도를 유지해 피부의 건조함을 줄여보세요.'
    UNION ALL SELECT '충분한 수면과 규칙적인 생활 습관은 건강한 피부 관리의 기본이에요.'
    UNION ALL SELECT '갈증이 나지 않도록 물을 꾸준히 마시는 습관을 들여보세요.'
    UNION ALL SELECT '잠들기 전에는 메이크업과 자외선 차단제를 꼼꼼하게 지워주세요.'
    UNION ALL SELECT '트러블이 잦다면 논코메도제닉 표시가 있는 제품을 선택할 때 참고해보세요.'
    UNION ALL SELECT '입술도 쉽게 건조해지므로 립밤으로 보습하고 뜯거나 핥는 습관을 피하세요.'
    UNION ALL SELECT '계절과 습도 변화에 맞춰 보습제의 사용량과 제형을 조절해보세요.'
    UNION ALL SELECT '화장품의 사용 기한과 개봉 후 사용 기간을 확인하고 입구를 깨끗하게 관리하세요.'
    UNION ALL SELECT '피부 변화를 기록하며 자신에게 잘 맞는 관리 습관을 꾸준히 찾아보세요.'
) AS seed
WHERE NOT EXISTS (
    SELECT 1
    FROM skin_care_tips existing
    WHERE existing.content = seed.content
);
