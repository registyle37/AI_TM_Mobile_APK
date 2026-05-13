# AI TM Trainer Mobile v3.1 Fast Natural

- 침묵 감지 기준 1.7초 → 약 1.0초
- AI 응답 후 녹음 재시작 대기 0.7초 → 0.35초
- 최대 녹음 시간 12초 → 9초
- 상태 문구를 "고객 응답 준비 중"으로 변경
- Android SpeechRecognizer 미사용 유지
- 시스템 음성인식 효과음 없음 유지

GitHub에 아래를 덮어쓰기 업로드:
.github
app
build.gradle
settings.gradle
README.md

빌드:
Actions → Build Android APK → Run workflow

확인 문구:
v3.1 빠른 응답 모드 · 실제 통화처럼 자연스럽게 진행
