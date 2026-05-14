# AI TM Trainer Mobile v3.4.1 Confirmed UI

첫 번째 확정 시안을 실제 앱 통화 화면에 반영한 확인용 정리본입니다.

## 확인 사항

압축 최상위 폴더명도 아래처럼 정리했습니다.

AI_TM_Mobile_APK_GitHub_v3_4_1_ConfirmedUI_FIXED_ROOT

## 이번 반영 내용

- 상단 여백/겹침 구조 정리
- 세션 정보 카드 추가
- 타이머/상태 카드 재배치
- 답변 받기 버튼을 큰 단독 버튼으로 명확하게 노출
- 일시정지 / 종료·평가 버튼 분리
- 상태 표시 영역 정리
- 이전 v3.3.x 흔적 문구 제거

## 설치 후 화면 확인

상단 부제:
v3.4.1 UI 확정 반영 · 첫 번째 시안 적용 완료

통화 화면 구성:
1. 세션 정보 카드
2. 경과 시간 / 현재 상태 카드
3. 안내 카드
4. 큰 답변 받기 버튼
5. 일시정지 / 종료·평가 버튼
6. 상태 표시 카드

## GitHub 적용

압축을 풀면 나오는 아래 항목을 GitHub Repository 루트에 덮어쓰기 업로드하세요.

.github
app
build.gradle
settings.gradle
README.md

그다음 Actions → Build Android APK → Run workflow
