# AI TM Trainer Mobile v2.2

## v2.2 수정 내용

- 시작하기 로딩 지연 수정
  - 관리자 서버에 아직 없는 `/api/sessions/start`를 기다리지 않고 즉시 통화 화면으로 전환합니다.
  - 현재는 앱 내부 임시 응답으로 빠르게 테스트합니다.

- 음성 듣기 시간 개선
  - AI 음성 종료 후 1.5초 뒤 듣기 시작
  - 음성 입력 최소 길이 3초 기준으로 완화
  - 침묵 감지 시간을 2.5~3.5초로 증가
  - 부분 인식 결과가 있으면 최종 결과가 없어도 상담원 발화로 사용

- 불필요한 안내 문구 제거
  - “말씀이 너무 짧아…” 문구를 줄이고 “계속 듣는 중”으로 표시
  - 인식 실패 시 팝업/토스트 없이 조용히 재청취

## GitHub 적용 방법

기존 Repository에 아래 파일/폴더를 덮어쓰기 업로드하세요.

```text
.github
app
build.gradle
settings.gradle
README.md
```

특히 아래 파일이 반드시 변경되어야 합니다.

```text
app/src/main/java/com/place1/aitmtrainer/MainActivity.java
app/build.gradle
.github/workflows/build-apk.yml
```

업로드 후 Actions에서 `Build Android APK`를 다시 실행하세요.

## 참고

아직 관리자 서버에 실제 대화 API가 없기 때문에 `USE_SERVER_CHAT_API=false`로 되어 있습니다.
관리자 서버에 실제 AI 대화 API를 붙이면 이 값을 `true`로 바꾸면 됩니다.
