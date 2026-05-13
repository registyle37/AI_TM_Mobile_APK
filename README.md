# AI TM Trainer Mobile v2.4

## v2.4 수정 내용

- "계속 듣기만 하고 답변하지 않음" 문제 수정
- SpeechRecognizer 부분 인식값 저장
- 최종 결과가 없어도 부분 인식값이 있으면 서버로 전송
- 최대 8초 듣고 자동 발화 확정
- onEndOfSpeech 이후 0.9초 안에 결과가 없으면 부분 인식값으로 전송
- 중복 전송 방지 플래그 추가
- 관리자 서버 `/api/chat` 우선 사용

## 적용 방법

기존 GitHub Repository에 아래 파일/폴더를 덮어쓰기 업로드하세요.

```text
.github
app
build.gradle
settings.gradle
README.md
```

업로드 후:

```text
Actions → Build Android APK → Run workflow
```

빌드 성공 후 새 APK를 설치하세요.
