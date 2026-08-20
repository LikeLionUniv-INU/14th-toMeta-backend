# 화장품 검색 API 응답 명세

화장품 표시명은 응답을 생성할 때 아래 정책으로 만든다.

- `brandName`이 있으면 앞뒤 공백을 제거하고, `productName`은 `브랜드명 + 공백 + DB 화장품명`으로 만든다.
- `brandName`이 `null`, 빈 문자열 또는 공백으로만 구성된 문자열이면 브랜드 접두어 없이 DB 화장품명만 반환한다.
- DB의 `product_name`과 `brand_name`은 각각 분리해서 저장한다.
- 기존 응답의 `brandName` 필드는 유지한다. 검색 결과에서 브랜드를 확인할 수 없을 때의 값은 기존과 동일하게 `"-"`이다.

## 검색 결과 조회

`GET /api/cosmetics/search?keyword=진정`

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "searchId": "search-id",
    "items": [
      {
        "itemId": 1,
        "productName": "아누아 어성초 토너",
        "brandName": "아누아",
        "productType": "skin_toner",
        "imageUrl": "https://example.com/toner.jpg"
      },
      {
        "itemId": 2,
        "productName": "진정 크림",
        "brandName": "-",
        "productType": "soothing_cream",
        "imageUrl": null
      }
    ]
  }
}
```

## 검색 결과 저장

`POST /api/user-cosmetics/search-result`

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "userCosmeticId": 11,
    "productName": "토리든 다이브인 세럼",
    "productType": "serum",
    "tags": ["세럼", "보습", "히알루론산"]
  }
}
```

저장된 화장품을 반환하는 화장품 옵션 조회와 화장품 세트 상세 조회의
`productName`에도 같은 표시 정책을 적용한다.
