# AI TM Trainer Mobile v2.3

## 수정 내용
- 포지션/고객유형 드롭다운 로딩 안정화
- 서버 연결 실패 시 명확한 안내
- 시작하기 전에 포지션/유형이 없으면 시작 차단
- 관리자 서버의 실제 대화 API 사용
- 관리자 서버 API 실패 시만 임시 응답 사용
- 대화 응답 반복 감소
- 음성 듣기 시간 3초 기준 유지
- Android Studio 없이 GitHub Actions로 빌드

## 함께 적용해야 하는 서버 업데이트
관리자 서버 `AI_TM_Admin`에도 `AI_TM_Admin_Update_Mobile_Chat_API.zip`의 app.py를 덮어써야 실제 대화 API가 동작합니다.

추가되는 API:
- POST /api/sessions/start
- POST /api/chat
- POST /api/sessions/{id}/finish
