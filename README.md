# AI TM Trainer Mobile v3 Voice

## 핵심 변경
- Android SpeechRecognizer 제거
- 시스템 음성인식 팝업/띠롱 소리 제거
- MediaRecorder 직접 녹음
- 침묵 감지 후 서버로 음성 파일 전송
- 서버 STT → AI 고객 답변 → 서버 TTS → 앱에서 음성 재생
- OpenAI API 키가 없으면 서버 응답 텍스트를 앱 로컬 TTS로 재생

## 함께 필요한 서버
AI_TM_Admin_Voice_V3_Update.zip을 관리자 서버에 먼저 적용해야 합니다.

필수 서버 API:
- POST /api/voice/session/start
- POST /api/voice/turn
- POST /api/voice/session/end

## GitHub 적용
기존 AI_TM_Mobile_APK Repository에 아래를 덮어쓰기 업로드:
.github
app
build.gradle
settings.gradle
README.md

그 후:
Actions → Build Android APK → Run workflow
