# 용사님 퀘스트 — 처음부터 재생성 프롬프트

> 빈 git 저장소에서 Claude Code(Opus 4.8)에게 그대로 붙여넣어 이 앱을 동일하게 재현하기 위한 단일 프롬프트입니다.
> 영문 식별자/패키지/클래스명은 그대로 사용하고, 사용자에게 보이는 모든 문자열과 AI 출력은 **한국어**로 만듭니다.

---

## 역할 & 목표

당신은 시니어 Android 시스템 아키텍트이자 AI 엔지니어입니다. **Kotlin + Jetpack Compose**로 네이티브 Android 앱 **"용사님 퀘스트"**(`com.chroniclequest`)를 만듭니다.

이 앱은 맥락 인식형 게임화 라이프 어시스턴트입니다. 최적화된 포그라운드 서비스가 주변 음성을 캡처하고, 로컬 VAD로 음성 구간만 걸러, 멀티모달 LLM(Gemini)에 전송해 함수 호출로 RPG 퀘스트를 동적으로 생성합니다. 사용자 반응은 로컬 피드백 루프(Room)에 기록되어 오프라인 프롬프트 최적화에 쓰입니다.

**진행 방식**: 먼저 plan mode로 아키텍처를 설계하고, 아래 단계 순서대로 구현하되 **각 단계마다 `./gradlew assembleDebug`로 빌드가 통과하는지 확인**하면서 진행하세요(수직 슬라이스 우선 — 코어 파이프라인이 끝까지 동작하는 빌드 가능 앱부터).

---

## 1. 기술 스택 & 아키텍처

- **언어/프레임워크**: Kotlin, Jetpack Compose (선언형 UI), Material 3
- **아키텍처**: Clean Architecture (`data` / `domain` / `presentation` / `service` 패키지) + MVI
- **DI**: Hilt
- **동시성**: Coroutines & Flow (StateFlow, SharedFlow)
- **로컬 DB**: Room (KSP)
- **네트워킹**: OkHttp (WebSocket + REST), kotlinx.serialization
- **단일 모듈** `app`, 패키지 루트 `com.chroniclequest`

### 패키지 구조
```
data/      audio(AudioRecord+VAD+WAV), remote(Gemini), local(Room), analytics, repository
domain/    model, repository(interface), usecase, engine(cooldown/silence), AmbientEventBus
service/   AmbientAudioService(FGS), AmbientQuestPipeline, QuestVerificationManager, verification/
presentation/  theme, home, components, debug, narration
di/        DatabaseModule, RepositoryModule, NetworkModule, ServiceModule
```

### 빌드 환경 제약 (중요 — 그대로 따를 것)
- **Android Gradle Plugin은 JDK 25를 지원하지 않습니다.** 시스템에 JDK 21/24/25가 있다면 `gradle.properties`에 `org.gradle.java.home=<Corretto 21 경로>`를 지정해 데몬을 JDK 21로 고정하세요. (toolchain은 별도 다운로드 없이 Java 17 바이트코드로 컴파일.)
- 버전: **AGP 8.7.3, Kotlin 2.0.21**(+ compose compiler plugin), **KSP 2.0.21-1.0.28**, Gradle wrapper **8.11.1**, Hilt 2.52, Room 2.6.1, OkHttp 4.12.0, Compose BOM 2024.10.01.
- `compileSdk=35, targetSdk=35, minSdk=29`. `compileOptions`/`kotlinOptions` 모두 Java 17 타깃.
- 버전 카탈로그(`gradle/libs.versions.toml`) 사용.
- `GEMINI_API_KEY`는 git-ignore된 `local.properties`에서 읽어 `BuildConfig`로 주입. 키 없이도 빌드/실행되며 에이전트는 안내만 띄우고 대기.
- `local.properties`, `build/`, `.gradle/`를 `.gitignore`에 추가.

---

## 2. 컴포넌트 스펙

### A. 주변 오디오 & VAD 엔진 (data/service)
1. `AudioCaptureManager`: `AudioRecord`로 PCM 16-bit / 16kHz / 모노 캡처, `Flow<ShortArray>`(~100ms 청크)로 방출. 수집 시작 시 녹음 시작, 취소/중단 시 마이크 완전 해제.
2. `VadProcessor`: RMS 진폭을 `20·log10(rms)`(~0–90 스케일)로 근사해 임계값(기본 **45**) 미만 청크는 버려 대역폭/토큰 절약.
3. `AmbientAudioService`: `LifecycleService`, `foregroundServiceType="microphone"`, 영구 알림 + `PendingIntent`. `RECORD_AUDIO` 런타임 권한 처리. **오디오 포커스(`AudioFocusRequest`)로 통화 등 인터럽트 시 마이크 자동 해제/복귀**. 권한: `RECORD_AUDIO`, `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`. 서비스 실행 상태는 `companion`의 `StateFlow<Boolean> isRunning`으로 노출.

### B. 저지연 AI 에이전트 & 함수 호출 (domain/data)
LLM이 보이지 않는 배경 게임 마스터로 동작하도록 강제합니다.

**시스템 프롬프트 규칙(영문 지시 + 한국어 출력)**:
- "You are an invisible ambient RPG Game Master. The user speaks KOREAN. Do NOT speak or output audio/text directly. Only respond by calling the `giveUserQuest` or `sendInsightTip` tool when a clear lifestyle problem, emotional distress, or target behavior is detected. Prefer silence."
- **언어 규칙**: 모든 사용자 노출 문자열(quest `title`/`description`, insight `message`)을 **자연스러운 한국어**로 작성.
- **targetValue 단위 규칙(엄격)**: `SCREEN_OFF`=분(5~120), `STEP_COUNT`=걸음 수, `MEDIA_PLAY`/`USER_MANUAL`=1.

**타이밍/필터링**:
- `SilenceDetector`: 음성 후 **3초** 무음이면 턴 종료로 판단(말 중간 끊김 방지). `now`를 주입받아 테스트 가능하게.
- `CooldownEngine`: LLM이 아무리 자주 호출해도 퀘스트 생성 간 **20분** 최소 간격 강제(인메모리 상태머신).

**도구 스키마**:
```json
{
  "name": "giveUserQuest",
  "description": "Triggered when the user encounters a real-world situation solvable via a trackable micro-action.",
  "parameters": {
    "title": "String (긴급하고 서사적인 RPG 퀘스트 제목)",
    "description": "String (맥락적 근거 + 실행 가이드)",
    "verificationMethod": "ENUM [SCREEN_OFF, STEP_COUNT, MEDIA_PLAY, USER_MANUAL]",
    "targetValue": "Int",
    "rewardExp": "Int (10-100)",
    "rewardGold": "Int (5-50)"
  }
}
```
추가로 `sendInsightTip(message: String)` — 추적 불가한 가벼운 격려/통찰.

**AI 전송 방식 — 두 가지 트랜스포트(반드시 둘 다 구현)**:
- `GeminiLiveClient` (WebSocket): `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=...` 로 연결, 첫 프레임 `setup`(model, systemInstruction, tools, responseModalities=TEXT), 이후 `realtimeInput.mediaChunks`로 base64 PCM 스트리밍, 서버 `toolCall.functionCalls` 파싱.
- `GeminiRestClient` (폴백, **기본 사용**): **많은 키가 Live(bidiGenerateContent) 권한이 없어 `1008`로 거부됩니다.** 따라서 폴백을 구현하고 파이프라인은 이 경로를 사용하세요. 3초 침묵으로 턴이 닫히면 누적 음성을 **WAV(audio/wav)로 묶어** `POST .../v1beta/models/gemini-2.5-flash:generateContent?key=...` 에 `inlineData`로 전송(systemInstruction + tools + `toolConfig.functionCallingConfig.mode="AUTO"`), 응답의 `functionCall`을 파싱. `WavEncoder`로 PCM16→WAV 헤더 부착. 오디오 버퍼는 **30초 캡**, 최소 ~0.6초 미만 턴은 스킵.
- 두 경로는 동일한 `GeminiAgentConfig`(systemInstruction + tool 선언)와 동일한 쿨다운/퀘스트 생성 로직을 공유. 공통 이벤트 모델 `GeminiEvent`(Connected/SetupComplete/ToolCallReceived/...).
- `AmbientQuestPipeline`이 캡처(A)와 두뇌(B)를 잇는 seam(`AmbientPipeline` 인터페이스). voiced 청크 누적 + 침묵 시 평가 + 쿨다운 게이트 후 퀘스트 영속화.
- 모델: REST는 `gemini-2.5-flash`, Live는 `models/gemini-2.0-flash-live-001`(둘 다 `BuildConfig` 필드).

### C. 센서 검증 & 퀘스트 상태머신 (service/verification)
`QuestVerificationManager`가 `verificationMethod`에 따라 검증기를 디스패치:
1. `SCREEN_OFF`: `ACTION_SCREEN_OFF`/`ON` `BroadcastReceiver` 등록, 화면 꺼짐 후 목표 분만큼 유지 시 완료(중간에 켜지면 카운트다운 취소).
2. `STEP_COUNT`: `Sensor.TYPE_STEP_COUNTER` `SensorEventListener`, 무장 시점 기준 델타가 목표 도달 시 완료.
3. `MEDIA_PLAY`: `AudioManager.isMusicActive` 폴링(권한 불필요한 프록시; 더 풍부한 `MediaSessionManager`는 알림 접근 권한 필요 — 후속 과제로 주석 명시).
4. `USER_MANUAL`: 인앱 체크인 버튼으로 완료.

수락 시 deadline 설정(메서드별 윈도우), deadline 경과 시 `EXPIRED` 처리. 완료 시 보상 지급 + 이벤트 발행. 프로세스 재시작 시 `ACCEPTED` 퀘스트 재무장(`restoreArmed`).

### D. RPG 게임화 UI/UX (presentation) — **Google Material Design(머터리얼 3) 기반**
1. **테마**: Material 3 다크 컬러 스킴에 RPG 네온 액센트를 얹음. 팔레트 예: 배경 `#0B0E1A`, 표면 `#1A1F36`, primary 네온 시안 `#00E5FF`, secondary 아케인 퍼플 `#7C4DFF`, tertiary 퀘스트 골드 `#FFC542`, 경험치 에메랄드 `#22E0A1`. 재사용 가능한 `neonGlow`/`glowBorder` Modifier.
2. **HUD**: "레벨/방랑자" 배지, 애니메이션 **경험치 바**(`animateFloatAsState`), **골드** 주머니를 상단에 표시.
3. **퀘스트 트리거**: 도구 호출 수신 시 **햅틱** + 입·퇴장 애니메이션이 있는 정교한 모달(`QuestModal`) 표시. 버튼: "퀘스트 수락" / "나중에".
4. **성공 애니메이션**: 퀘스트가 `COMPLETED`되면 Jetpack Compose **Canvas 파티클 폭발(XP 컨페티)** + 경험치 바 채워지는 애니메이션. (이 환경엔 `Math.random`/`Date.now`가 없으니 인덱스 기반 결정적 분산 사용.)

### E. 피드백 플라이휠 & 최적화 (data/analytics)
1. **Room 스키마**: `QuestLogEntity`(id, timestamp, conversation_summary, generated_quest_json, title, description, verificationMethod, targetValue, rewardExp, rewardGold, **user_reaction_state** ENUM[TRIGGERED, ACCEPTED, DISMISSED, EXPIRED, COMPLETED], acceptedAt, deadlineAt) — 라이브 퀘스트 저장소이자 플라이휠 로그를 겸함. `UserStatsEntity`(level/totalExp/gold 단일 행).
2. **암묵적 피드백**: 모달에서 "나중에"→`DISMISSED`, 수락 후 시간 초과→`EXPIRED`, 완료→`COMPLETED`.
3. **최적화 패널(디버그)**: Room을 집계해 성공(COMPLETED) vs 실패(DISMISSED/EXPIRED) 매칭을 담은 구조화 JSON(`FlywheelExport`)을 만들어 복사/공유. 홈 상단 아이콘으로 진입.

---

## 3. 한국어화 요구사항 (전체 UI)

사용자에게 보이는 **모든 문자열을 한국어로** 작성하세요(하드코딩 직접 한국어, 단일 언어 타깃). 예시 번역:
- 앱 이름(런처/타이틀/알림): **"용사님 퀘스트"**
- "진행 중인 퀘스트", "에이전트 시작" / "감지 중", 빈 상태 "에이전트를 시작해 당신의 모험을 시작하세요."
- HUD: "레벨", "방랑자", "경험치"
- 상태 칩: 신규/진행 중/완료/무시됨/만료됨
- 검증 칩: "📵 화면 끄기 · N분", "👟 걷기 · N걸음", "🎵 미디어 재생", "✍️ 직접 인증"
- 보상: "+N 경험치 · +N 골드", 모달 헤더 "⚔️ 새로운 퀘스트 등장", 버튼 "퀘스트 수락"/"나중에"
- 완료 스낵바: "퀘스트 완료! +N 경험치 · +N 골드"
- 최적화 패널: "최적화 패널", "성공률: N%", "발생/수락/완료/무시/만료", "복사"/"공유"
- 알림: "용사님 퀘스트가 듣고 있어요", "새로운 퀘스트를 찾는 중…"

---

## 4. TTS 음성 안내 (presentation/narration)

`QuestNarrator`(Android `TextToSpeech` 래퍼)를 구현:
- 퀘스트 모달이 뜨면(새 `pendingQuest` 등장) **"용사님. {title}. {description}"** 을 한국어로 읽어줍니다.
- 목소리는 **신뢰감 있는 한국어 여성** 톤: `Locale.KOREAN`, `setPitch(0.92f)`, `setSpeechRate(0.96f)`, 여성 음성 휴리스틱(이름에 female 힌트 우선, 없으면 오프라인 ko 음성). (Android `Voice`엔 성별 필드가 없어 기기 기본 ko 음성이 보통 여성.)
- TTS 엔진은 비동기 초기화 — 준비 전 요청은 큐잉했다가 발화.
- **수락/나중에 등 액션으로 모달이 닫히면(`pendingQuest`=null) 음성 즉시 중단**(`stop`). 화면 종료 시 `shutdown`.
- Compose 헬퍼 `rememberQuestNarrator()`(remember + DisposableEffect)로 생명주기 관리, `HomeScreen`에서 `LaunchedEffect(pendingQuest?.id)`로 발화/중단 연결.

---

## 5. 검증 방법

1. 각 단계 후 `JAVA_HOME=<corretto-21> ./gradlew assembleDebug` → BUILD SUCCESSFUL.
2. 에뮬레이터/실기기(API 29+)에 `installDebug` → "에이전트 시작" → 마이크·알림 권한 → 포그라운드 마이크 서비스(`types=0x80`)와 알림 확인, 크래시 없음.
3. **REST 경로 실제 검증**: 키를 넣고, 앱이 보내는 것과 동일한 요청(WAV `inlineData` + systemInstruction + tools, `mode:AUTO`)으로 한국어 음성을 보내 `giveUserQuest`가 **한국어 title/description**과 올바른 단위(SCREEN_OFF=분)로 반환되는지 확인. (macOS면 `say -v Yuna`로 한국어 WAV 생성 후 `afconvert`로 16kHz mono PCM WAV 변환해 테스트 가능.)
4. 실기기에서 음성→퀘스트 모달→TTS "용사님…" 발화→수락 시 음성 중단→걸음수/화면끄기 검증→완료 시 컨페티+경험치 애니메이션→최적화 패널 JSON 확인.

---

## 참고 (이전 구현에서 확정된 사실)
- 많은 Gemini API 키는 Live API가 비활성(ListModels에 bidiGenerateContent 모델 0개, `1008` 거부)이지만 `generateContent`+함수 호출은 정상 → **REST 폴백을 기본 경로로** 둘 것.
- `gemini-2.5-flash`는 한국어 오디오 이해 + 한국어 함수 호출 인자 생성이 우수.
- 단위 미지정 시 모델이 SCREEN_OFF에 초 단위(예: 1800)를 넣을 수 있으니 프롬프트에서 "분"임을 명시.
