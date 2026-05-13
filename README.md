# AI TM Trainer Mobile v3.3 Push To Respond

## v3.3 핵심 변경

이번 버전은 자동 말끝 감지 대신, 상담원이 직접 발화 종료를 확정하는 구조입니다.

- 상담원이 말하는 중 자동으로 끊기지 않음
- 상담원이 말한 뒤 "답변 받기" 버튼을 누르면 즉시 서버 전송
- 무음/짧은 발화는 서버로 보내지 않음
- 통화 흐름은 조금 수동이지만, 테스트 안정성은 가장 높음
- 45초 이상 길게 말하면 안전상 자동 전송
- Android SpeechRecognizer 미사용 유지
- 시스템 음성인식 효과음 없음 유지

## 사용 방식

AI 고객이 말함
→ 앱이 녹음 시작
→ 상담원이 말함
→ 상담원이 말을 마치면 "답변 받기" 클릭
→ 고객 응답 준비
→ AI 고객 답변

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

앱 상단에 아래 문구가 보여야 최신 v3.3입니다.

v3.3 버튼 응답 모드 · 말한 뒤 누르면 즉시 답변
