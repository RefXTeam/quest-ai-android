# 용사님 퀘스트 (ChronicleQuest)

맥락을 인식하는 **게임화 라이프 어시스턴트** Android 앱입니다. 포그라운드 서비스가 주변
음성을 캡처하고, 로컬 VAD로 음성 구간만 걸러, **Gemini**에 보내 함수 호출로 RPG 퀘스트를
동적으로 생성합니다. 퀘스트는 기기 센서로 검증되고 EXP/골드로 보상되며, 모든 퀘스트와
사용자 반응은 Room에 기록되어 오프라인 프롬프트 최적화("플라이휠")에 쓰입니다.

퀘스트가 등장하면 신뢰감 있는 한국어 여성 목소리가 **"용사님…"** 하고 읽어줍니다.

---

## 주요 기능

- 🎙️ **항상 켜진 주변 음성 캡처** — `microphone` 포그라운드 서비스, 통화 시 마이크 자동 해제
- 🔊 **로컬 RMS/VAD 게이트** — 음성 구간만 전송해 대역폭·토큰 절약
- 🧠 **Gemini 함수 호출** — 라이프스타일 문제·감정을 감지해 `giveUserQuest` / `sendInsightTip` 호출
- ⏱️ **3초 침묵 턴 종료 + 20분 쿨다운** — 말 중간 끊김 방지, 과도한 퀘스트 억제
- 🎮 **Material 3 다크 RPG UI** — 레벨/경험치 바/골드 HUD, 퀘스트 모달, Canvas 컨페티
- 📱 **센서 검증** — 화면 끄기 / 걸음 수 / 미디어 재생 / 직접 인증
- 🗣️ **TTS 음성 안내** — 한국어 여성 목소리로 퀘스트 낭독, 액션 시 중단
- 📊 **피드백 플라이휠** — 성공/실패 사례를 JSON으로 내보내 few-shot 프롬프트 튜닝
- 🇰🇷 **전체 한국어** — UI와 AI가 생성하는 퀘스트 모두 한국어

---

## 전체 플로우

```mermaid
flowchart TD
    subgraph SVC["AmbientAudioService (포그라운드 · microphone)"]
        Mic["AudioRecord<br/>PCM 16-bit · 16kHz · mono"] --> VAD{"VadProcessor<br/>RMS dB ≥ 45?"}
        VAD -->|"음성"| Buf["오디오 버퍼<br/>(최대 30초)"]
        VAD -->|"무음"| Sil["SilenceDetector"]
        Buf --> Sil
        Sil -->|"3초 침묵 → 턴 종료"| Eval
    end

    Eval["AmbientQuestPipeline<br/>턴 평가"] --> Wav["WavEncoder<br/>PCM → WAV"]
    Wav --> Rest["GeminiRestClient<br/>generateContent (gemini-2.5-flash)"]
    Rest -->|"functionCall"| Cool{"CooldownEngine<br/>20분 경과?"}
    Cool -->|"아니오"| Drop["억제 (퀘스트 생성 안 함)"]
    Cool -->|"예"| Gen["GenerateQuestFromToolCallUseCase"]

    Gen --> Room[("Room: quest_log<br/>state = TRIGGERED")]
    Room --> VM["HomeViewModel<br/>(StateFlow)"]
    VM --> Modal["QuestModal 등장<br/>+ 햅틱"]
    Modal --> TTS["QuestNarrator<br/>🗣️ '용사님…'"]

    Modal -->|"수락"| Arm["QuestVerificationManager.arm()"]
    Modal -->|"나중에"| Dismiss["state = DISMISSED"]
    Arm --> Verify["센서 검증기<br/>화면끄기 / 걸음 / 미디어 / 수동"]
    Verify -->|"목표 달성"| Done["state = COMPLETED<br/>보상 지급"]
    Verify -->|"기한 초과"| Exp["state = EXPIRED"]
    Done --> Conf["🎉 컨페티 + 경험치 애니메이션"]
```

## 퀘스트 생명주기 (시퀀스)

```mermaid
sequenceDiagram
    actor U as 사용자
    participant S as AmbientAudioService
    participant P as AmbientQuestPipeline
    participant G as Gemini REST
    participant DB as Room
    participant UI as HomeScreen
    participant T as QuestNarrator
    participant V as VerificationManager

    U->>S: 한국어로 말함
    S->>P: 음성 청크 (VAD 통과)
    Note over P: 3초 침묵 → 턴 종료
    P->>G: WAV + 시스템 프롬프트 + tools
    G-->>P: giveUserQuest(한국어 title/desc)
    Note over P: 쿨다운 통과 시에만
    P->>DB: 퀘스트 저장 (TRIGGERED)
    DB-->>UI: StateFlow 갱신
    UI->>UI: QuestModal + 햅틱
    UI->>T: "용사님. {title}. {desc}" 낭독
    U->>UI: 수락
    UI->>T: 음성 중단
    UI->>V: arm(quest)
    U->>V: (걸음/화면끄기 등 수행)
    V->>DB: COMPLETED + 보상
    DB-->>UI: 🎉 컨페티 + EXP 애니메이션
```

## 퀘스트 상태 머신

```mermaid
stateDiagram-v2
    [*] --> TRIGGERED: LLM 함수 호출
    TRIGGERED --> ACCEPTED: 수락
    TRIGGERED --> DISMISSED: 나중에 / 스와이프
    ACCEPTED --> COMPLETED: 센서/수동 검증 성공
    ACCEPTED --> EXPIRED: 기한 초과
    COMPLETED --> [*]
    DISMISSED --> [*]
    EXPIRED --> [*]

    note right of DISMISSED
        모든 상태는 플라이휠 로그의
        암묵적 피드백 신호로 기록됨
    end note
```

## 아키텍처 (Clean Architecture)

```mermaid
flowchart LR
    subgraph P["presentation"]
        direction TB
        Home["HomeScreen / ViewModel"]
        Comp["components<br/>HUD · QuestModal · Confetti"]
        Narr["QuestNarrator (TTS)"]
        Debug["OptimizationPanel"]
    end
    subgraph D["domain"]
        direction TB
        Model["model · usecase"]
        Eng["engine<br/>Silence · Cooldown"]
        Repo["repository (interface)"]
    end
    subgraph DA["data"]
        direction TB
        Audio["audio<br/>Capture · VAD · WAV"]
        Remote["remote<br/>GeminiRest/Live"]
        Local["local (Room)"]
        Ana["analytics<br/>FlywheelExporter"]
    end
    subgraph SV["service"]
        direction TB
        Svc["AmbientAudioService"]
        Pipe["AmbientQuestPipeline"]
        Verif["VerificationManager + 검증기"]
    end

    P --> D
    SV --> D
    DA -.-> Repo
    P -.->|Hilt DI| DA
    SV --> DA
```

---

## 기술 스택

- **Kotlin · Jetpack Compose** (Material 3, 다크 RPG 테마)
- **Clean Architecture** (`data` / `domain` / `presentation` / `service`) + **MVI**
- **Hilt** DI · **Room** (KSP) · **Coroutines/Flow**
- **OkHttp** (REST + WebSocket) · **kotlinx.serialization**
- 단일 모듈 `app`, 패키지 루트 `com.chroniclequest`

### 주요 구성요소

| 레이어 | 핵심 클래스 |
|--------|-------------|
| `data/audio` | `AudioCaptureManager`, `VadProcessor`, `WavEncoder`, `PcmUtils` |
| `data/remote` | `GeminiRestClient`(기본), `GeminiLiveClient`(WebSocket), `GeminiAgentConfig` |
| `data/local` | `AppDatabase`, `QuestLogEntity`(라이브 퀘스트 + 플라이휠 로그 겸용) |
| `data/analytics` | `FlywheelExporter` → few-shot 학습 JSON |
| `domain/engine` | `SilenceDetector`(3초), `CooldownEngine`(20분) |
| `domain/usecase` | 퀘스트 생성 / 수락 / 완료 |
| `service` | `AmbientAudioService`(FGS), `AmbientQuestPipeline`, `QuestVerificationManager` + 검증기 |
| `presentation` | `HomeScreen`, `components`, `QuestNarrator`(TTS), `OptimizationPanelScreen` |

---

## AI 트랜스포트 — 두 가지 경로

| 경로 | 사용 시점 | 동작 |
|------|-----------|------|
| **REST 폴백** (기본) | Live 권한 없는 키 | 3초 침묵마다 누적 음성을 **WAV 한 클립**으로 `generateContent`(`gemini-2.5-flash`)에 전송 → 함수 호출 파싱 |
| **Live (WebSocket)** | Live(`bidiGenerateContent`) 권한 있는 키 | base64 PCM 실시간 스트리밍, 서버 `toolCall` 수신 |

> 대부분의 Gemini 키는 Live API가 비활성(`1008` 거부)이라 **REST 폴백을 기본 경로**로 둡니다.
> 두 경로는 동일한 시스템 프롬프트·tool 스키마·쿨다운·퀘스트 로직을 공유합니다.

## 퀘스트 검증 방식

| 메서드 | 검증 방법 |
|--------|-----------|
| `SCREEN_OFF` | `BroadcastReceiver`(화면 on/off) — N분간 화면 꺼짐 유지 |
| `STEP_COUNT` | `TYPE_STEP_COUNTER` 델타 — 시작 기준 N걸음 |
| `MEDIA_PLAY` | `AudioManager.isMusicActive` 폴링 (`MediaSessionManager`는 알림 접근 권한 필요 — 후속 과제) |
| `USER_MANUAL` | 인앱 체크인 버튼 |

---

## 설정 & 빌드

> **JDK 주의**: Android Gradle Plugin은 JDK 25를 지원하지 않습니다. 데몬을 **Corretto 21**로
> 고정하기 위해 [`gradle.properties`](gradle.properties)의 `org.gradle.java.home`을 사용합니다
> (Java 17 바이트코드 컴파일). 경로가 다르면 수정하세요.

1. git-ignore된 `local.properties`에 키 입력:
   ```properties
   sdk.dir=/path/to/Android/sdk
   GEMINI_API_KEY=your_key_here
   ```
   키는 <https://aistudio.google.com/apikey> 에서 발급. 키가 없어도 빌드·실행되며, 에이전트는
   안내만 띄우고 대기합니다.

2. 빌드 / 설치:
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug   # 에뮬레이터/실기기 (API 29+)
   ```

3. 실행 → **`에이전트 시작`** → 마이크·알림 권한 허용 → 주변을 말하면 약 3초 침묵 후
   퀘스트가 등장하고 TTS가 낭독합니다.

> 💡 퀘스트는 20분 쿨다운이 있지만 인메모리이므로, 앱을 **완전 종료 후 재실행**하면 초기화되어
> 바로 재테스트할 수 있습니다.

---

## 처음부터 재생성

빈 저장소에서 이 앱을 동일하게 재현하기 위한 단일 프롬프트는 [`PROMPT.md`](PROMPT.md)에 있습니다.
