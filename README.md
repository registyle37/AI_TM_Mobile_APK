# AI TM Trainer Mobile v3.4.1 - No Cut Optimized

기준: v3.4.1 Confirmed UI

반영 내용:
- 메인 화면 하단 버튼 고정 영역 분리
- 훈련 화면 답변 받기 / 일시정지 / 종료·평가 버튼 고정 영역 분리
- 작은 화면에서 내용 영역은 ScrollView로 스크롤 가능하도록 안정화
- Android 시스템 상태바/내비게이션바 영역 침범 방지
- targetSdk 34로 조정하여 Android 15 edge-to-edge 강제 잘림 위험 완화
- MediaRecorder 유지
- SpeechRecognizer 재도입 없음
- 기존 /api/voice/* API 유지

GitHub 루트에 아래 항목을 업로드하세요.
- .github
- app
- build.gradle
- settings.gradle
- README.md
