# AI TM Trainer Mobile v3.3.1 Button Fix

## 수정 내용

- v3.3에서 누락된 “답변 받기” 버튼을 실제 화면에 확실히 추가
- 버튼 영역을 3개 구성으로 변경:
  - 일시정지
  - 답변 받기
  - 종료/평가
- 상담원이 말한 뒤 “답변 받기”를 누르면 즉시 서버로 음성 전송
- 침묵만으로 자동 전송하지 않음
- 45초 이상 길게 말하면 안전상 자동 전송
- 시스템 음성인식 효과음 없음 유지

## 사용 방식

AI 고객이 말함
→ 앱이 자동 녹음 시작
→ 상담원이 말함
→ 상담원이 말을 마치면 “답변 받기” 클릭
→ AI 고객 응답

## GitHub 적용

기존 AI_TM_Mobile_APK Repository에 아래를 덮어쓰기 업로드:

.github
app
build.gradle
settings.gradle
README.md

그 후:

Actions → Build Android APK → Run workflow

## 설치 후 확인 문구

앱 상단:
v3.3 버튼 응답 모드 · 말한 뒤 누르면 즉시 답변

통화 화면 버튼:
일시정지 / 답변 받기 / 종료·평가
