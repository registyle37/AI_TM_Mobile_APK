# AI TM Trainer Mobile v2.1 - Build Fix

- 빌드 오류 수정: `import android.content.Intent;` 추가
- v2 음성인식/디자인 개선 유지

# AI TM Trainer Mobile v2 - GitHub Actions APK 자동 빌드용

## v2 수정 내용

- Android 기본 음성인식 팝업 방식 제거
- SpeechRecognizer 내부 인식 방식으로 변경
- "인식하지 못했습니다" 팝업 최소화
- 말이 없거나 짧게 인식되면 조용히 다시 듣기
- AI가 말하는 동안 마이크 중지
- AI 음성 종료 후 약 0.9초 뒤 자동 듣기 시작
- 전체 UI 디자인 재적용
  - 카드형 설정 화면
  - 그라데이션 버튼
  - 통화 타이머
  - 통화 상태 표시
  - 마이크 상태 표시
- Node.js 24 경고 대응 환경변수 추가

## 앱 기능

- 서버 주소 입력/저장
- 관리자페이지 서버 연결
- 포지션 불러오기
- 고객유형 불러오기
- 난이도 선택
- 시작하기
- 자동 음성 대화
- 종료/평가

## GitHub 적용 방법

기존 Repository에 아래 파일/폴더를 덮어쓰기 업로드하세요.

```text
.github
app
build.gradle
settings.gradle
README.md
```

특히 아래 파일이 중요합니다.

```text
app/src/main/java/com/place1/aitmtrainer/MainActivity.java
.github/workflows/build-apk.yml
```

업로드 후 Actions에서 `Build Android APK`를 실행하면 새 APK가 생성됩니다.

## 현재 한계

아직 관리자 서버에 실제 대화 API가 없으면 앱은 임시 응답으로 대화 흐름만 테스트합니다.
다음 단계에서 관리자 서버에 아래 API를 추가하면 실제 저장/분석까지 연결됩니다.

```text
POST /api/sessions/start
POST /api/chat
POST /api/sessions/{id}/finish
```
