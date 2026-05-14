# AI TM Trainer Mobile v3.3.3 Replace Pause With Answer

## 수정 내용

- 기존에 화면에 보이던 하단 '일시정지' 위치를 '답변 받기' 버튼으로 교체
- 화면 중앙 버튼이 안 보이는 기기에서도 하단 버튼은 반드시 노출되도록 수정
- 하단 버튼 구성:
  - 답변 받기
  - 종료/평가
- 상담원이 말한 뒤 '답변 받기'를 누르면 즉시 서버로 음성 전송
- 침묵만으로 자동 전송하지 않음
- 45초 이상 길게 말하면 안전상 자동 전송

## 설치 후 확인

앱 상단:
v3.3.3 고정 답변 버튼 모드 · 하단 버튼으로 즉시 응답

통화 화면 하단:
답변 받기 / 종료·평가

## GitHub 적용

기존 AI_TM_Mobile_APK Repository에 아래를 덮어쓰기 업로드:

.github
app
build.gradle
settings.gradle
README.md

그 후:

Actions → Build Android APK → Run workflow

기존 앱 삭제 후 새 APK 설치를 권장합니다.
