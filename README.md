# AI TM Trainer Mobile - GitHub Actions APK 자동 빌드용

이 프로젝트는 Android Studio 없이 GitHub Actions에서 APK를 자동 빌드하기 위한 Android 앱 프로젝트입니다.

## 앱 포함 기능

- 서버 주소 입력/저장
- 관리자페이지 서버 연결
- 포지션 불러오기
- 고객유형 불러오기
- 난이도 선택
- 시작하기
- 자동 음성 대화
- 종료/평가
- 서버 대화 API가 아직 없을 경우에도 임시 응답으로 앱 동작 테스트 가능

## 관리자 서버 연동

현재 앱은 아래 관리자 서버 API를 사용합니다.

```text
GET /api/health
GET /api/positions
GET /api/customer-types?position=newbie
```

다음 단계에서 아래 대화 API를 관리자 서버에 추가하면 실제 AI 대화 저장/평가까지 연결됩니다.

```text
POST /api/sessions/start
POST /api/chat
POST /api/sessions/{id}/finish
```

## GitHub에서 APK 만드는 방법

1. GitHub에서 새 Repository 생성
2. 이 프로젝트 안의 모든 파일 업로드
3. GitHub Repository 상단의 `Actions` 탭 클릭
4. `Build Android APK` 워크플로우 선택
5. `Run workflow` 클릭
6. 빌드가 끝나면 실행 결과 아래의 `Artifacts`에서 APK 다운로드

다운로드되는 파일명:

```text
AI_TM_Trainer_debug_apk
```

압축을 풀면:

```text
app-debug.apk
```

이 파일을 핸드폰에 설치하면 됩니다.

## 서버 주소

앱 기본 서버 주소는 아래로 되어 있습니다.

```text
http://172.30.0.53:8031
```

앱 첫 화면에서 직접 수정하고 저장할 수 있습니다.

## 주의

- 내부망 HTTP 접속 테스트를 위해 `usesCleartextTraffic=true`를 사용합니다.
- 정식 운영에서는 HTTPS 주소를 권장합니다.
- APK 설치 시 Android에서 '알 수 없는 앱 설치 허용'이 필요할 수 있습니다.
