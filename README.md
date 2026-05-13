# AI TM Trainer Mobile v2.5

## v2.5 수정 내용
- "어떤 말씀인지 구체적으로..." 류의 반복 답변 차단
- 서버가 generic 답변을 반환해도 앱에서 고객유형별 답변으로 교체
- session_id가 -1이어도 customer_type_name, position_code를 /api/chat에 함께 전송
- 앱 자체 fallback 답변을 고객유형/발화의도 기반으로 강화
- 자료/팀장/재통화/가격/신뢰/바쁨/거절 의도를 구분해 답변
- 답변 후보를 여러 개로 나누어 반복감 감소

## 적용 방법
기존 GitHub Repository에 아래 파일/폴더를 덮어쓰기 업로드하세요.

.github
app
build.gradle
settings.gradle
README.md

업로드 후:
Actions → Build Android APK → Run workflow
