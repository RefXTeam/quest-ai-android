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
data/      audio(AudioRecord+VAD+WAV), remote(Gemini), local(Room), analytics(Flywheel+FewShot), repository
domain/    model, repository(interface), usecase, engine(cooldown/silence), AmbientEventBus, PipelineMonitor
service/   AmbientAudioService(FGS), AmbientQuestPipeline, QuestVerificationManager, verification/,
           MonitorWebServer(NanoHTTPD), BatteryOptimization, NetworkUtils
presentation/  theme, home, components, debug, narration, monitor
di/        DatabaseModule, RepositoryModule, NetworkModule, ServiceModule
assets/    monitor.html   ·   docs/  index.html (GitHub Pages 사본)
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
3. `AmbientAudioService`: `LifecycleService`, `foregroundServiceType="microphone"`, 영구 알림 + `PendingIntent`. `RECORD_AUDIO` 런타임 권한 처리. 서비스 실행 상태는 `companion`의 `StateFlow<Boolean> isRunning`으로 노출. 권한: `RECORD_AUDIO`, `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
4. **백그라운드 상시 감지**: 앱을 백그라운드로 보내거나 화면을 꺼도 감지가 유지돼야 한다.
   - **오디오 포커스 정책**: 다른 앱의 미디어 재생(`AUDIOFOCUS_LOSS`)에는 캡처를 멈추지 말 것. 통화 등 일시 점유(`AUDIOFOCUS_LOSS_TRANSIENT`)에만 일시정지하고 `AUDIOFOCUS_GAIN` 시 재개(스펙의 "통화 시 마이크 해제" 유지). 마이크 입력은 미디어 출력 포커스와 무관하게 계속 읽힌다.
   - **배터리 최적화 제외**: `BatteryOptimization` 헬퍼 — `PowerManager.isIgnoringBatteryOptimizations()` 확인 후 아니면 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 인텐트(`FLAG_ACTIVITY_NEW_TASK`)로 사용자 승인 요청. 에이전트 시작(권한 부여) 시 호출해 삼성 등 OEM 배터리 킬러에서도 FGS 생존.
   - **런타임 권한 요청(`HomeScreen`)**: 마이크 + (API 33+) 알림 **+ `ACTIVITY_RECOGNITION`(걸음 센서 필수, API 29+ dangerous)** 를 accompanist `rememberMultiplePermissionsState`로 함께 요청. 매니페스트 선언만으론 부족 — `ACTIVITY_RECOGNITION`이 런타임 미부여면 STEP 센서 이벤트가 전혀 오지 않아 진행도가 0에 멈춘다.

### B. 저지연 AI 에이전트 & 함수 호출 (domain/data)
LLM이 보이지 않는 배경 게임 마스터로 동작하도록 강제합니다.

**시스템 프롬프트 규칙(영문 지시 + 한국어 출력)** — ⚠️ **적극적으로 작성할 것**(보수적 "prefer silence"
지시는 명확한 신호도 무시하게 만들어 발표/실사용에서 퀘스트가 안 뜬다):
- "You are an invisible ambient RPG Game Master. The user speaks KOREAN. Do NOT speak; only act via tools. PREFER `triggerDynamicQuest`(category 포함); `giveUserQuest` is a legacy alternative; `sendInsightTip` for lighter nudges."
- "Be **GENEROUS and proactive**. Whenever you hear ANY lifestyle signal — tiredness/힘들다/피곤, overeating or fullness/과식·배부름, lack of exercise or movement, stress, eye strain, poor sleep, sitting or staring at the phone too long, low energy — call `triggerDynamicQuest` with a fitting category and a small trackable action."
- 한국어 예시를 프롬프트에 포함: "배부르다→HEALTH 산책/STEP_COUNT", "운동을 안 해서 힘들다→HEALTH 걷기", "폰을 오래 봤다/눈이 아프다→REST 화면 끄기/SCREEN_OFF".
- "Err **STRONGLY** toward creating a quest; when in doubt, call the tool. Stay silent ONLY for greetings or clearly unrelated chit-chat."
- **언어 규칙**: 모든 사용자 노출 문자열(quest `title`/`description`, insight `message`, `contextSummary`)을 **자연스러운 한국어**로 작성.
- **targetValue 단위 규칙(엄격)**: `SCREEN_OFF`=분(5~120), `STEP_COUNT`=걸음 수, `USER_CHECK`/`MEDIA_PLAY`/`USER_MANUAL`=1.

**타이밍/필터링**:
- `SilenceDetector`: 음성 후 **3초** 무음이면 턴 종료로 판단(말 중간 끊김 방지). `now`를 주입받아 테스트 가능하게.
- `CooldownEngine`: LLM이 아무리 자주 호출해도 퀘스트 생성 간 최소 간격 강제(인메모리 상태머신). 프로덕션 **20분**, 발표 데모는 **1분**(`DEFAULT_COOLDOWN_MS`).

**도구 스키마** — 퀘스트 함수 2개(주력 `triggerDynamicQuest` + 호환 `giveUserQuest`) + `sendInsightTip`:
```json
{
  "name": "triggerDynamicQuest",
  "description": "유저의 상황에 맞춰 실시간으로 가변적인 RPG 미션을 동적으로 생성하고 하달한다.",
  "parameters": {
    "category": "ENUM [HEALTH, STUDY, REST, SOCIAL]",
    "title": "String (RPG 스타일 제목)",
    "description": "String (공감 + 행동 유도 가이드)",
    "targetSensor": "ENUM [SCREEN_OFF, STEP_COUNT, USER_CHECK]",
    "targetValue": "Int (분 또는 걸음 수)",
    "rewardExp": "Int (10-100)",
    "rewardGold": "Int (5-50)",
    "contextSummary": "String (당시 상황 한국어 한 줄 요약 — 피드백 학습용)"
  }
}
```
- `giveUserQuest`(호환): 위와 동일하나 `category` 없고 `verificationMethod` ENUM[SCREEN_OFF, STEP_COUNT, MEDIA_PLAY, USER_MANUAL] 사용 + `contextSummary`. 기존 구조 비파괴 유지.
- `sendInsightTip(message: String)` — 추적 불가한 가벼운 격려/통찰.
- **두 퀘스트 함수는 동일 경로로 수렴**: `GenerateQuestFromToolCallUseCase`가 `targetSensor`(우선)/`verificationMethod`를 `VerificationMethod.fromOrNull`로 파싱(**`USER_CHECK`→`USER_MANUAL` 별칭**), `category`는 `QuestCategory.fromOrNull`. `AmbientQuestPipeline.onToolCall`이 두 함수명을 같은 `handleQuestToolCall`로 라우팅.
- `contextSummary`는 `QuestLogEntity.conversation_summary`로 저장(없을 때만 fallback) — 자가 개선(§F) few-shot의 핵심 연료.
- **신규 enum** `domain/model/QuestCategory`(HEALTH/STUDY/REST/SOCIAL + 한국어 label/emoji). `Quest`/`QuestLogEntity`에 `category: QuestCategory? = null` 추가, Room **version 2**(`fallbackToDestructiveMigration`) + Converter.

**AI 전송 방식 — 두 가지 트랜스포트(반드시 둘 다 구현)**:
- `GeminiLiveClient` (WebSocket): `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=...` 로 연결, 첫 프레임 `setup`(model, systemInstruction, tools, responseModalities=TEXT), 이후 `realtimeInput.mediaChunks`로 base64 PCM 스트리밍, 서버 `toolCall.functionCalls` 파싱.
- `GeminiRestClient` (폴백, **기본 사용**): **많은 키가 Live(bidiGenerateContent) 권한이 없어 `1008`로 거부됩니다.** 따라서 폴백을 구현하고 파이프라인은 이 경로를 사용하세요. 3초 침묵으로 턴이 닫히면 누적 음성을 **WAV(audio/wav)로 묶어** `POST .../v1beta/models/<model>:generateContent` 에 `inlineData`로 전송(systemInstruction + tools + `toolConfig.functionCallingConfig.mode="AUTO"`), 응답의 `functionCall`을 파싱. `WavEncoder`로 PCM16→WAV 헤더 부착. 오디오 버퍼는 **30초 캡**, 최소 ~0.6초 미만 턴은 스킵.
- **응답이 non-success(특히 `429`)면 빈 리스트가 아니라 예외를 던질 것** — 빈 리스트로 처리하면 모니터에 "함수 호출 없음(침묵)"으로 잘못 표시된다. `429`는 "API 호출 한도 초과(429) — 잠시 후 다시 시도"로, 그 외는 "AI 요청 실패(HTTP n)"로 메시지를 만들어 파이프라인의 `onFailure`가 모니터 ERROR + 스낵바로 노출.
- 두 경로는 동일한 `GeminiAgentConfig`(systemInstruction + tool 선언)와 동일한 쿨다운/퀘스트 생성 로직을 공유. 공통 이벤트 모델 `GeminiEvent`(Connected/SetupComplete/ToolCallReceived/...).
- **API 키는 URL 쿼리(`?key=`)가 아닌 `x-goog-api-key` 헤더로 전달**(OkHttp 로그 노출 방지). REST 호출은 공유 클라이언트에 `callTimeout`/`readTimeout`(예: 40초)을 적용한 별도 빌더 사용(Live용 무한 readTimeout과 분리).
- `GeminiAgentConfig.systemInstruction(fewShot)` / `buildSetup(model, fewShot)`로 **few-shot 블록을 시스템 프롬프트 뒤에 주입**(§6). `GeminiRestClient.evaluateTurn(..., fewShot)`가 받는다.
- `AmbientQuestPipeline`이 캡처(A)와 두뇌(B)를 잇는 seam(`AmbientPipeline` 인터페이스). voiced 청크 누적 + 침묵 시 평가(평가 직전 `FewShotBuilder.build()` 주입) + 쿨다운 게이트 후 퀘스트 영속화.
- 모델: REST는 **`gemini-2.5-flash-lite`**, Live는 `models/gemini-2.0-flash-live-001`(둘 다 `BuildConfig` 필드). flash-lite는 오디오 입력 위주·잦은 호출인 이 앱에 최적 — 오디오 입력 3.3배·출력 6배 저렴, 무료 티어 RPM 2배(`429` 완화), 더 빠르며, 이 "신호→함수 호출" 태스크엔 품질 충분.

### B-2. 음성 감정 분석 → 공감 퀘스트 (data/remote)
매 턴, Gemini로 보내는 **동일한 WAV**를 외부 감정 인식 API에도 보내 사용자의 감정을 읽고, 그 결과를 Gemini 요청 맥락에 주입해 더 공감적인 퀘스트를 만든다.
- **신규** `data/remote/dto/EmotionDtos.kt`(@Serializable): `EmotionResponse(code, message, content)` → `content.result.emotion: Map<String,Int>`(HAPPINESS/NEUTRAL/ANGRY/SADNESS/SURPRISE 0–100) + `@SerialName("best_emotion") bestEmotion: String?`. (응답에 바이오마커 등 다른 필드가 많으니 `Json{ignoreUnknownKeys=true}` 필수.)
- **신규** `domain/model/EmotionResult(bestEmotion, scores)`: `fun toPromptHint()` → 한국어 한 줄("음성 감정 분석 — 우세 감정: 슬픔 (점수: 슬픔 73 · …)"), `bestKorean`(영문 enum→한국어: HAPPINESS=기쁨/NEUTRAL=중립/ANGRY=분노/SADNESS=슬픔/SURPRISE=놀람).
- **신규** `data/remote/EmotionClient`(`@Singleton`, OkHttp+Json 주입): `suspend fun analyze(wav: ByteArray): EmotionResult?` — `POST https://api.magovoice.com/emotion_recognition/v1/run?is_speech=false` 에 multipart(`file`=wav `audio/wav`, `content_id`=""·`out_dir`="exp/emotion_recognition"). **인증 키 불필요**, 우리가 만든 WAV를 그대로 수락. **best-effort**: 실패/타임아웃(15초)이면 `null` 반환(파이프라인은 감정 없이 계속). 타임아웃 적용 별도 빌더, URL은 상수.
- **파이프라인 주입**: `AmbientQuestPipeline.evaluateTurn`에서 WAV 생성(`WavEncoder.pcmToWavBytes`) 후 **magovoice 먼저 호출** → `EmotionResult?`. `PipelineMonitor`에 `EMOTION` 단계 emit("감정 분석: {우세 감정} 우세", detail=점수 요약). 감정 hint를 `GeminiRestClient.evaluateTurn(..., emotionHint = emotion?.toPromptHint())`로 전달.
- **Gemini 요청**: `evaluateTurn(..., emotionHint: String? = null)` — user `contents`의 오디오 `inlineData` **Part 뒤에** `Part(text=emotionHint)`를 (있을 때만) 추가. 모델이 오디오 + 감정 분석 결과를 함께 본다.
- **시스템 프롬프트**: "음성 감정 분석 결과가 함께 제공될 수 있다 — 슬픔/분노/지침엔 위로·휴식·진정(보통 REST), 기쁨/활력엔 격려·도전. 감정이 category와 말투에 공감적으로 묻어나게 하라" 한 단락을 `GeminiAgentConfig`에 추가.

### C. 센서 검증 & 퀘스트 상태머신 (service/verification)
`QuestVerificationManager`가 `verificationMethod`에 따라 검증기를 디스패치:
1. `SCREEN_OFF`: `ACTION_SCREEN_OFF`/`ON` `BroadcastReceiver` 등록, 화면 꺼짐 후 목표 분만큼 유지 시 완료(중간에 켜지면 카운트다운 취소).
2. `STEP_COUNT`: **`Sensor.TYPE_STEP_DETECTOR`(걸음마다 1 이벤트, 무장 후 누적 카운트) 우선** + `TYPE_STEP_COUNTER`(monotonic 총합, baseline 델타) fallback. `registerListener(.., SENSOR_DELAY_FASTEST, maxReportLatencyUs=0)`로 배치를 꺼 걸음 단위 실시간 반영. **⚠️ `ACTIVITY_RECOGNITION` 런타임 권한이 없으면 걸음 이벤트가 아예 오지 않으니** 반드시 런타임 요청에 포함(§A 권한 + §D 권한 플로우). STEP_COUNTER만 쓰면 일부 기기서 배치 지연으로 진행도가 멈춘다.
3. `MEDIA_PLAY`: `AudioManager.isMusicActive` 폴링(권한 불필요한 프록시; 더 풍부한 `MediaSessionManager`는 알림 접근 권한 필요 — 후속 과제로 주석 명시).
4. `USER_MANUAL`: 인앱 체크인 버튼으로 완료.

수락 시 deadline 설정(메서드별 윈도우), deadline 경과 시 `EXPIRED` 처리. 완료 시 보상 지급 + 이벤트 발행. 프로세스 재시작 시 `ACCEPTED` 퀘스트 재무장(`restoreArmed`).

**진행값 노출**: `QuestVerifier`에 `fun progress(questId, now): QuestProgress?`(기본 null) 추가. `QuestProgress(current, target)` + `fraction`. `ScreenOffVerifier`는 화면 꺼진 연속(streak) 시작 시각을 저장해 `(now-start)/분`을, `StepCountVerifier`는 detector 모드면 무장 후 누적 걸음(없으면 `latest-baseline`)을 반환. `QuestVerificationManager.progressOf(questId, now)`가 method에 맞는 verifier로 위임(§D 진행도 UI에서 사용).

### D. RPG 게임화 UI/UX (presentation) — **Google Material Design(머터리얼 3) 기반**
1. **테마**: Material 3 다크 컬러 스킴에 RPG 네온 액센트를 얹음. 팔레트 예: 배경 `#0B0E1A`, 표면 `#1A1F36`, primary 네온 시안 `#00E5FF`, secondary 아케인 퍼플 `#7C4DFF`, tertiary 퀘스트 골드 `#FFC542`, 경험치 에메랄드 `#22E0A1`. 재사용 가능한 `neonGlow`/`glowBorder` Modifier.
2. **HUD**: "레벨/방랑자" 배지, 애니메이션 **경험치 바**(`animateFloatAsState`), **골드** 주머니를 상단에 표시.
3. **퀘스트 트리거**: 도구 호출 수신 시 **햅틱** + 입·퇴장 애니메이션이 있는 정교한 모달(`QuestModal`) 표시. 버튼: "퀘스트 수락" / "나중에".
4. **성공 애니메이션**: 퀘스트가 `COMPLETED`되면 Jetpack Compose **Canvas 파티클 폭발(XP 컨페티)** + 경험치 바 채워지는 애니메이션. (이 환경엔 `Math.random`/`Date.now`가 없으니 인덱스 기반 결정적 분산 사용.)
5. **진행도 & 유효시간**: 수락(ACCEPTED)한 퀘스트 카드에 실시간 상태 표시. `HomeViewModel`에 1초 ticker `flow{ while(true){ emit(now); delay(1000) } }`를 `combine`에 추가하고, 각 퀘스트를 `QuestUiModel(quest, remainingMillis, windowMillis, progress)`로 매핑(`SharingStarted.WhileSubscribed`라 화면 보일 때만 틱). 카드에는 **진행 바**(`progressOf`로 얻은 `QuestProgress.fraction`) + "N걸음/M걸음 · P%"와 **남은 시간 카운트다운**(`deadlineAt - now`, >1시간이면 "H시간 M분", 5분 미만 강조)을 표시. SCREEN_OFF는 "화면을 켜면 진행이 초기화돼요" 안내.
6. **카테고리 배지**: `quest.category != null`이면 카드/모달에 카테고리 배지(이모지+라벨+색) 표시. null(=giveUserQuest)이면 미표시(비파괴).

### E. 피드백 플라이휠 & 최적화 (data/analytics)
1. **Room 스키마**: `QuestLogEntity`(id, timestamp, conversation_summary, generated_quest_json, title, description, verificationMethod, targetValue, rewardExp, rewardGold, **user_reaction_state** ENUM[TRIGGERED, ACCEPTED, DISMISSED, EXPIRED, COMPLETED], acceptedAt, deadlineAt) — 라이브 퀘스트 저장소이자 플라이휠 로그를 겸함. `UserStatsEntity`(level/totalExp/gold 단일 행).
2. **암묵적 피드백**: 모달에서 "나중에"→`DISMISSED`, 수락 후 시간 초과→`EXPIRED`, 완료→`COMPLETED`.
3. **최적화 패널(디버그)**: Room을 집계해 성공(COMPLETED) vs 실패(DISMISSED/EXPIRED) 매칭을 담은 구조화 JSON(`FlywheelExport`)을 만들어 복사/공유. 홈 상단 아이콘으로 진입.

### F. 온디바이스 자가 개선 (few-shot 런타임 주입)
외부 서버·학습 없이, 사용자 본인의 데이터로 에이전트가 점점 더 잘 맞춰지게 한다.
1. `FewShotBuilder`(`data/analytics`, `@Singleton`, `QuestLogDao` 주입): `suspend fun build(maxSuccess=3, maxFailure=3): String?` — COMPLETED를 성공 예시, DISMISSED/EXPIRED를 실패 예시로 추려(placeholder/빈 `conversation_summary` 제외) 한국어 few-shot 블록 생성. 데이터 없으면 null.
   ```
   참고: 이 사용자의 과거 반응 사례다. 성공 패턴은 살리고 실패 패턴은 피하라.
   [성공] 상황: "{summary}" → 제안: "{title}" ({method}) → 사용자가 완료함
   [실패] 상황: "{summary}" → 제안: "{title}" ({method}) → 사용자가 무시함
   ```
2. `AmbientQuestPipeline.evaluateTurn`에서 매 턴 평가 직전 `fewShotBuilder.build()`를 호출해 `GeminiRestClient.evaluateTurn(..., fewShot)`로 전달 → `GeminiAgentConfig.systemInstruction(fewShot)`가 시스템 프롬프트 뒤에 append.

### G. 실시간 파이프라인 모니터 (발표용)
데이터 흐름을 웹페이지/앱에서 실시간으로 보여준다.
1. **신규** `domain/PipelineMonitor`(`@Singleton`): `StateFlow<List<PipelineEvent>>`(최근 200개) + `log(stage, message, detail, channel=PIPELINE)` + 서버 통신용 `netRequest(target, line, body)`/`netResponse(target, line, body, ok)` + `clear()`. **신규** `domain/model/PipelineEvent`(@Serializable, `channel: MonitorChannel = PIPELINE` 포함) + `MonitorChannel` enum(@Serializable: `PIPELINE`=앱 이벤트/왼쪽, `NETWORK`=서버 통신/오른쪽) + `PipelineStage` enum(@Serializable, 한국어 label/emoji): LISTENING/TURN/**EMOTION(감정 ❤️)**/AI_REQUEST/AI_RESPONSE/TOOL_CALL/COOLDOWN/QUEST/VERIFY/REWARD/ERROR.
2. **emit 지점**: `AmbientQuestPipeline`(start→LISTENING, 침묵 턴→TURN, **감정 분석→EMOTION**, 요청→AI_REQUEST, 응답→AI_RESPONSE, toolcall→TOOL_CALL, 쿨다운→COOLDOWN, 생성→QUEST, 실패→ERROR), `QuestVerificationManager`(arm→VERIFY, 완료→REWARD). **서버 통신(NETWORK 채널)**: `GeminiRestClient`·`EmotionClient`가 각 HTTP 호출 직전/직후 `netRequest`/`netResponse`를 호출. **Gemini 요청의 base64 오디오는 `~KB` 요약으로 대체(원문 생략), API 키 헤더는 절대 기록하지 않음.** 응답 본문은 ~1.4KB로 truncate.
3. **신규** `service/MonitorWebServer`(NanoHTTPD, 포트 8080): `GET /`→assets/monitor.html, `GET /events.json`→`PipelineMonitor.events` 직렬화(CORS `*`). `AmbientAudioService` 시작/중지 시 `start()/stop()`. NanoHTTPD 의존성 추가.
4. **신규** `app/src/main/assets/monitor.html` — 1초 폴링(`/events.json`), **2열 다크 콘솔**: 왼쪽 = `channel==PIPELINE` 단계별 색/이모지 타임라인, 오른쪽 = `channel==NETWORK` 요청/응답 카드(방향 칩 →요청/←응답 + 대상 배지 Gemini·magovoice + HTTP 라인 + `<pre>` 본문). 좁은 화면(≤860px)에선 세로 스택. 같은 파일을 [`docs/index.html`](docs/index.html)로 복사해 GitHub Pages 배포(폰 IP 입력 필드 포함; HTTPS→HTTP mixed-content로 Pages에선 직접 연결 제한 → 실시간 메인은 폰 IP 직접 접속).
5. **신규** `presentation/monitor/MonitorScreen` + `MonitorViewModel`(`PipelineMonitor` 구독) — 앱 내 타임라인은 **`PIPELINE` 채널만** 표시(폰 화면이 좁아 서버 통신 상세는 발표 PC 2열 뷰로) + 웹 주소(`http://<wifi-ip>:8080`, `NetworkUtils.localIpv4()`) 안내. 홈 상단 📺 아이콘 → 네비게이션.

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
5. **진행도/유효시간**: 수락한 퀘스트 카드에 진행 바·"N걸음/M걸음 P%"·"남은 시간"이 매초 갱신되는지 확인. 걸음 검증 전 **`ACTIVITY_RECOGNITION` 권한 부여 필수**(다이얼로그 허용 또는 `adb shell pm grant <pkg> android.permission.ACTIVITY_RECOGNITION`); `TYPE_STEP_DETECTOR`라 걸으면 걸음마다 즉시 증가. `granted=false`면 진행도가 0에 멈춘다.
6. **백그라운드**: 에이전트 시작 후 홈으로 보내고 화면 off → 프로세스/FGS 알림 생존, 배터리 화이트리스트 등록(`dumpsys deviceidle whitelist | grep`) 확인. 음악 재생 중에도 캡처 유지.
7. **자가 개선(few-shot)**: 실패 사례(예: "업무 중 맛집 제안=무시")를 시스템 프롬프트에 넣고 비슷한 상황 입력 시 그 패턴을 피하는지 텍스트 입력으로 A/B 확인.
8. **만능 함수**: 앱과 동일 요청에 `triggerDynamicQuest`를 넣어 모델이 `category`/`targetSensor`/단위 정확히 반환하는지 확인(USER_CHECK→USER_MANUAL 매핑).
9. **발표 모니터**: 에이전트 시작 후 같은 Wi-Fi(또는 폰 핫스팟)에서 `curl http://<폰IP>:8080/events.json` → HTTP 200 + 한국어 이벤트, 브라우저로 `http://<폰IP>:8080` → 실시간 타임라인. 앱 모니터 화면(📺)에 주소 표시.

---

## 참고 (이전 구현에서 확정된 사실)
- 많은 Gemini API 키는 Live API가 비활성(ListModels에 bidiGenerateContent 모델 0개, `1008` 거부)이지만 `generateContent`+함수 호출은 정상 → **REST 폴백을 기본 경로로** 둘 것.
- **모델은 `gemini-2.5-flash-lite` 권장** — 오디오 입력 위주·잦은 호출인 이 앱에 오디오 3.3배·출력 6배 저렴, 무료 티어 RPM 2배(`429` 완화). 한국어 오디오 이해 + 함수 호출 + `contextSummary`/few-shot 모두 충분.
- **시스템 프롬프트는 반드시 적극적으로** 작성. "Prefer silence / be sparing" 류는 "배부르다", "운동 안 해서 힘들다" 같은 **명확한 신호도 모델이 무시**하게 만든다. "be generous / err strongly toward creating / 신호 키워드 + 한국어 예시"로.
- **magovoice 감정 API는 인증 키 불필요**, 우리가 만든 WAV를 그대로 수락(HTTP 200, `code 700`). 응답에 `principal_vocal_biomarkers` 등 필드가 많으니 DTO는 필요한 것만 두고 `ignoreUnknownKeys=true`. 감정은 **best-effort**(실패해도 퀘스트 생성은 계속) — 외부 API 장애가 핵심 플로우를 막지 않게 할 것.
- 단위 미지정 시 모델이 SCREEN_OFF에 초 단위(예: 1800)를 넣을 수 있으니 프롬프트에서 "분"임을 명시.
- API 키는 `?key=`(URL)보다 **`x-goog-api-key` 헤더**가 안전(로그 노출 방지). REST/WebSocket 모두 헤더 지원.
- `429`(non-success)는 빈 응답이 아니라 **예외로** 처리해 모니터/스낵바에 명확히 노출(아니면 "함수 호출 없음"으로 오인).
- **걸음 진행도가 0에 멈추면 십중팔구 `ACTIVITY_RECOGNITION` 런타임 권한 미부여** — `dumpsys package <pkg> | grep ACTIVITY_RECOGNITION`으로 `granted=` 확인. 권한 있으면 `TYPE_STEP_DETECTOR`가 걸음마다 즉시 반영(`TYPE_STEP_COUNTER`는 배치 지연).
- 삼성 등 OEM은 FGS도 죽일 수 있어 **배터리 최적화 제외**가 백그라운드 상시 감지에 필수.
- 무료 티어는 오디오 입력 요청 시 레이트 리밋(`429`)에 빨리 도달(RPD 1,500/일)하니, few-shot/프롬프트 A/B는 텍스트 입력으로 가볍게 검증. 발표 안정성엔 유료 티어 권장.
- 발표/실기기 무선 디버깅·웹 모니터는 **기기 간 통신 가능한 네트워크** 필요. 회사/게스트망은 AP isolation으로 막히니 **휴대폰 핫스팟** 권장. GitHub Pages(HTTPS)→폰(HTTP)은 mixed-content로 막혀 폰 IP 직접 접속이 메인.
