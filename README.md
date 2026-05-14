# AI TM Trainer Mobile v3.3.2 Big Answer Button

## 수정 내용

- “답변 받기” 버튼을 통화 화면 중앙에 큰 단독 버튼으로 배치
- 하단 버튼은 일시정지 / 종료·평가만 유지
- 상담원이 말한 뒤 큰 “답변 받기” 버튼 클릭 시 즉시 서버 전송
- 침묵만으로 자동 전송하지 않음
- 45초 이상 길게 말하면 안전상 자동 전송
- 시스템 음성인식 효과음 없음 유지

## 화면 확인

앱 상단:
v3.3.2 큰 답변 버튼 모드 · 말한 뒤 바로 누르기

통화 화면 중앙:
큰 파란색 “답변 받기” 버튼

## GitHub 적용

기존 AI_TM_Mobile_APK Repository에 아래를 덮어쓰기 업로드:

.github
app
build.gradle
settings.gradle
README.md

그 후:

Actions → Build Android APK → Run workflow

기존 앱이 남아 있으면 핸드폰에서 AI TM Trainer 앱 삭제 후 새 APK를 설치하세요.
