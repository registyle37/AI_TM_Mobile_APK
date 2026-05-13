# AI TM Trainer Mobile v3.2 Stable Conversation

## v3.2 핵심 수정

이번 버전은 속도보다 실제 통화 흐름 안정성을 우선합니다.

- 상담원이 말하는 중 너무 빨리 끊기는 문제 수정
- 실제 음성이 감지된 뒤에만 침묵 종료 판단
- 말하지 않은 무음 구간은 서버로 전송하지 않음
- 무음 파일로 인해 이상한 STT 문장이 생기는 문제 방지
- 침묵 감지 기준 약 1초 → 약 2.1초
- 최소 녹음 시간 1초 → 2.5초
- 최대 발화 시간 18초까지 허용
- “음식서비스가 중단됩니다” 같은 무음 STT 오작동 방지 목적

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

앱 상단에 아래 문구가 보여야 최신 v3.2입니다.

v3.2 안정 대화 모드 · 말이 끝난 뒤 자연스럽게 응답
