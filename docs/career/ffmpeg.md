# FFmpeg 사용 정리 (blur_api)

이 문서는 `blur_api` 프로젝트에서 FFmpeg/FFprobe가 어떻게 사용되는지(주요 기능 · 사용처 · 실행 환경)와,
이해를 돕기 위한 FFmpeg 이론을 함께 정리한 문서입니다.

---

## 1. 개요

`blur_api`는 영상 업로드 → 메타데이터 추출 → 썸네일/미리보기 생성 → HLS(m3u8) 다중 화질 변환 →
블러/회전/합성 등의 영상 처리를 수행하는 서비스입니다. 이 모든 미디어 처리의 실제 작업은
Java 코드에서 `ProcessBuilder`로 **외부 ffmpeg / ffprobe 바이너리를 직접 호출**하여 처리합니다.

- 영상 인코딩은 가능한 경우 **NVIDIA GPU 하드웨어 가속(CUDA + `h264_nvenc`)** 을 사용합니다.
- 진행률(progress)은 ffmpeg의 stderr 로그(`time=`)를 파싱하여 WebSocket 콜백 등으로 전송합니다.

---

## 2. 디렉터리 구조 (FFmpeg 관련)

```
src/main/java/com/ncn/blur/
├── util/ffmpeg/
│   ├── FFMPEGUtil.java           # ffmpeg/ffprobe 실행의 핵심 유틸 (직접 명령 실행)
│   ├── FFmpegCommandBuilder.java # AI 프로젝트 요소 → -filter_complex 명령 빌더
│   ├── MakeFileHelper.java       # CommandBuilder로 합성 영상 생성하는 헬퍼
│   └── FFmpegAudioFixer.java     # 오디오 스트림 보장(없으면 무음 추가)
├── processor/
│   └── FFMPEGProcessor.java      # FFMPEGUtil을 감싼 상위 프로세서
└── facade/                       # 실제 호출 진입점들 (Upload, Thumbnail 등)
```

---

## 3. 주요 기능

### 3.1 메타데이터 추출 — `FFMPEGUtil.extractMetadata()`
- **도구:** `ffprobe`
- `ffprobe -v quiet -print_format json -show_format -show_streams -i <input>`
- JSON 결과를 파싱하여 `MediaMetadataDto`(포맷, 비디오/오디오 코덱, 해상도, 비트레이트, 길이, 프레임 수, fps, 회전값, 오디오 유무)로 변환.
- **회전 처리:** `side_data_list`의 `Display Matrix` rotation 값을 읽어, 90/-90도면 width/height를 swap.

### 3.2 HLS(m3u8) 다중 화질 변환 — `convertToM3U8For2CH()` / `convertToM3U8For3CH()`
- **2CH:** 원본 + 1280p 두 가지 화질, **3CH:** 원본 + 1280p + 854p 세 가지 화질.
- CUDA 하드웨어 가속(`-hwaccel cuda -hwaccel_output_format cuda`)으로 디코딩.
- `-filter_complex`로 스트림을 `split` 후 `scale_cuda`로 화질별 리사이즈.
- 인코더: `h264_nvenc`, 화질별 비트레이트(5000k/3000k/1500k) 지정.
- 오디오 유무에 따라 `-var_stream_map`을 다르게 구성(있으면 각 화질에 aac 매핑, 없으면 `-an`).
- fps가 60 초과면 `-r 30`으로 제한.
- HLS 출력: `-f hls -master_pl_name master.m3u8 -hls_time 6 -hls_list_size 0`,
  세그먼트는 `v%v/file_%03d.ts`, 플레이리스트는 `v%v/prog.m3u8`.
- 실행은 `runCheckTime()`을 통해 진행률 콜백과 함께 수행.

### 3.3 진행률 추적 — `runCheckTime()`
- `builder.redirectErrorStream(true)`로 ffmpeg 출력을 읽으며 `time=(\S+)` 정규식으로 현재 처리 시간 추출.
- `(현재시간 / 전체duration) * 100`으로 진행률 계산 → 1초에 한 번씩 `ProgressCallback.onProgress()` 호출.
- 120초 내 미완료 시 `destroyForcibly()`로 강제 종료.

### 3.4 구간 자르기 — `splitVideoByTime()` / `splitAudioByTime()`
- 비디오: CUDA 디코딩 + `h264_nvenc` 인코딩(`-preset p5 -rc vbr -cq 21`), 오디오 aac 192k.
- `-ss`(시작) / `-t`(길이)를 **input 뒤에** 배치하여 정확도 향상.
- 오디오: aac 192k로 잘라내기.

### 3.5 썸네일 / 미리보기 생성
- **`extractAllToFile()`** — `fps=1`로 1초 간격 썸네일(jpg) 추출 + 마지막 프레임(`-sseof -0.1`) 별도 추출.
- **`getThumbnail()`** — CUDA 가속, `scale=<width>:-1:flags=lanczos`로 리사이즈.
- **`makeWebpImage()`** — 추출된 jpg들을 `-framerate 7`로 묶어 애니메이션 WebP 미리보기 생성(`scale=512:-1:flags=lanczos -loop 0`).

### 3.6 회전 처리
- **`rotationImages()`** — `transpose` 필터로 썸네일 이미지 회전 후 원본 덮어쓰기.
- **`rotationVideo()`** — autoblur 후 메타가 삭제된 영상 등을 강제로 다시 세움.
  - `getReverseRotationFilter()`: 90→`transpose=2`, 180→`transpose=2,transpose=2`, 270/-90→`transpose=1`.
  - CUDA 디코딩 + `h264_nvenc` 재인코딩, 오디오는 `-c:a copy`.
- **`rotationVideoMetadata()`** *(@Deprecated)* — `-c copy`로 rotate 메타데이터만 넣으려 했으나 atom에 반영되지 않아 사용 안 함(참고용으로 남김).

### 3.7 블러 — `manualGBlurTest()` / `FFmpegCommandBuilder.addBlur()`
- `split` → `crop`(블러 영역) → `gblur=sigma=<강도>` → `overlay`로 원본 위에 합성.
- `enable='between(t,start,end)'`로 특정 시간 구간에만 블러 적용.

### 3.8 AI 프로젝트 합성 — `FFmpegCommandBuilder` + `MakeFileHelper`
- 타임라인 요소(VIDEO/IMAGE/TEXT/BLUR/AUDIO)를 각각의 `add*()`로 받아 하나의 거대한 `-filter_complex` 명령을 빌드.
  - `addVideo`: `scale` + `fps` + `setpts`(시작 지연) + 오디오 `adelay`/`volume` + `overlay`.
  - `addText`: `drawtext`(폰트/색/위치/시간 구간).
  - `overlayImage`: 이미지/gif `scale` 후 `overlay`(gif는 `-ignore_loop 0`, `shortest=1`).
  - `addAudio`: `adelay` + `volume` 적용 후 `amix`로 믹싱.
- `build()`에서 `color` 소스로 검은 배경(전체 길이) 생성 후 그 위에 모든 요소를 차례로 overlay.
- 인코더: `h264_nvenc -preset slow -cq:v 18`, 오디오 aac 128k.

### 3.9 오디오 스트림 보장 — `FFmpegAudioFixer`
- **`hasAudioStream()`** — `ffprobe`로 오디오 스트림 존재 여부 확인.
- **`ensureAudioExists()`** — 오디오가 없으면 `anullsrc`(무음)를 입력으로 추가해 `_with_audio.mp4` 생성.
  - 합성 시 오디오 트랙 수가 안 맞아 발생하는 오류 방지용.

---

## 4. 사용처 (호출 흐름)

```
Facade (진입점)
   └─> FFMPEGProcessor (래퍼)
          └─> FFMPEGUtil / FFmpegCommandBuilder / MakeFileHelper / FFmpegAudioFixer
                 └─> ProcessBuilder → ffmpeg / ffprobe 바이너리 실행
```

### 4.1 `FFMPEGProcessor` (processor/FFMPEGProcessor.java)
FFMPEGUtil을 감싸 상위 레이어에 제공:
- `extractAll()` — 썸네일 전체 추출 (`workDir/<uuid>/thumbnail`)
- `convertToM3U8()` — 2CH m3u8 변환
- `extractMetadata()` — 메타데이터 추출
- `makeWebpImage()` — WebP 미리보기 생성

### 4.2 호출 Facade / Service
| 클래스 | 용도 |
|--------|------|
| `facade/UploadFacade.java` | 업로드 처리 |
| `facade/UploadV2Facade.java` | 업로드 처리(V2) |
| `facade/UploadVideoBatchFacade.java` | 비디오 배치 업로드 |
| `facade/UploadImageAudioBatchFacade.java` | 이미지/오디오 배치 업로드 |
| `facade/ThumbnailExtractFacade.java` | 썸네일 추출 |
| `facade/AiFileSetMakeFacade.java` | AI 파일셋(합성) 생성 |
| `service/file/AiFileObjectService.java` | 파일 객체 처리 |

### 4.3 테스트
- `src/test/.../util/ffmpeg/FFMPEGUtilTest.java`
- `src/test/.../util/ffmpeg/MakeFileHelperTest.java`
- `src/test/.../util/FFMPEGUtilTest.java`
- `src/test/.../service/AiFileThumbnailServiceTest.java`

---

## 5. 실행 환경

### 5.1 바이너리
- 코드에서 `"ffmpeg"`, `"ffprobe"`를 그대로 호출하므로 **실행 환경의 PATH에 ffmpeg/ffprobe가 설치되어 있어야 함.**

### 5.2 Docker 이미지
- **`Dockerfile.base`** — 베이스 이미지 `my-base-java-ffmpeg` 생성
  ```dockerfile
  FROM azul/zulu-openjdk:21-latest
  RUN apt-get update \
   && apt-get install -y ffmpeg \
   && rm -rf /var/lib/apt/lists/*
  ```
- **`Dockerfile`, `DockerfileDev`** — `FROM my-base-java-ffmpeg` (위 베이스 이미지 재사용)

### 5.3 GPU(하드웨어 가속) 요구사항
- 코드 다수가 `-hwaccel cuda`, `h264_nvenc`, `scale_cuda`를 사용 → **NVIDIA GPU + 드라이버**가 필요.
- 컨테이너로 실행 시 GPU 접근(`--gpus all` / NVIDIA Container Toolkit)이 있어야 NVENC가 동작.
- ⚠️ **주의:** `Dockerfile.base`는 apt의 일반 ffmpeg을 설치합니다. NVENC 인코딩은 런타임에 NVIDIA 드라이버/GPU가
  연결되어 있어야 정상 동작하며, GPU 없는 환경에서는 `h264_nvenc` 관련 명령이 실패할 수 있으니 배포 환경을 확인해야 합니다.

---

## 6. FFmpeg 이론 (이해를 돕기 위한 기초)

### 6.1 FFmpeg / FFprobe란?
- **FFmpeg**: 오디오/비디오를 디코딩·인코딩·변환·필터링하는 오픈소스 멀티미디어 프레임워크 겸 CLI 도구.
- **FFprobe**: 미디어 파일의 메타데이터(코덱, 해상도, 비트레이트, 길이 등)를 분석/출력하는 도구.

### 6.2 기본 명령 구조
```
ffmpeg [전역 옵션] [입력 옵션] -i 입력 [출력 옵션] 출력
```
- **옵션 위치가 중요:** `-i` **앞**의 옵션은 입력에, **뒤**의 옵션은 출력에 적용됩니다.
  - 예: `-ss`/`-t`를 `-i` 뒤에 두면(이 프로젝트 방식) 더 정확하게 자르고, 앞에 두면 더 빠르지만 키프레임 단위로 부정확할 수 있음.

### 6.3 컨테이너 vs 코덱
- **컨테이너(Container):** mp4, mkv, ts, m3u8 등 — 영상/오디오/자막 스트림을 담는 "포장지".
- **코덱(Codec):** H.264(`h264`), AAC 등 — 실제 데이터를 압축/해제하는 "압축 방식".
- 하나의 컨테이너 안에 여러 스트림(video/audio/subtitle)이 들어 있습니다.

### 6.4 스트림 지정과 매핑(Mapping)
- 스트림 표기: `0:v:0` = 첫 번째 입력의 첫 번째 비디오, `0:a:0` = 첫 번째 입력의 첫 번째 오디오.
- `-map`: 출력에 포함할 스트림을 명시적으로 선택. `?`(예: `0:a:0?`)는 해당 스트림이 없어도 에러 안 냄.
- `-an`: 오디오 제거, `-c:a copy` / `-c:v copy`: 재인코딩 없이 스트림 복사(무손실·고속).

### 6.5 필터 — `-vf` vs `-filter_complex`
- **`-vf` (simple filter):** 단일 입력→단일 출력의 비디오 필터 체인. 예: `scale`, `transpose`, `drawtext`.
- **`-filter_complex`:** 다중 입력/출력, 분기/합성이 가능한 복합 필터 그래프.
  - 라벨로 스트림을 연결: `[0:v]scale=...[v0];[v0][1:v]overlay=...[out]`.
  - 이 프로젝트의 합성/블러/다중화질 로직이 여기에 해당.
- 자주 쓰는 필터:
  - `scale=w:h` — 리사이즈(`-1`/`-2`는 비율 유지, `-2`는 짝수 보정). `flags=lanczos`는 고품질 보간.
  - `crop=w:h:x:y` — 영역 잘라내기.
  - `gblur=sigma=N` — 가우시안 블러(N이 클수록 강함).
  - `overlay=x:y` — 한 영상을 다른 영상 위에 합성.
  - `transpose=1|2` — 회전(1: 시계 90도, 2: 반시계 90도).
  - `drawtext` — 텍스트 렌더링.
  - `split` — 스트림 복제(같은 입력을 여러 필터에 사용).
  - `adelay`, `volume`, `amix` — 오디오 지연/볼륨/믹싱.
  - `enable='between(t,start,end)'` — 특정 시간 구간에만 필터 적용.

### 6.6 인코딩 품질/속도 옵션
- **`-preset`** — 인코딩 속도 vs 압축효율 트레이드오프.
  - libx264: `ultrafast`~`veryslow`(느릴수록 같은 화질에 용량↓).
  - NVENC: `p1`~`p7`(또는 `slow`/`medium`/`fast`).
- **비트레이트 제어:**
  - `-b:v 5000k` — 목표 비트레이트(용량 예측 쉬움).
  - `-crf`(libx264) / `-cq`(NVENC) — 품질 기준(값이 낮을수록 고화질, 보통 18~23). `-rc vbr` 가변비트레이트.
- **`-c:v` / `-c:a`** — 비디오/오디오 코덱 지정. `copy`면 재인코딩 안 함.

### 6.7 하드웨어 가속 (이 프로젝트의 핵심)
- **`-hwaccel cuda`** — NVIDIA GPU로 디코딩 가속.
- **`-hwaccel_output_format cuda`** — 디코딩된 프레임을 GPU 메모리에 유지(CPU↔GPU 복사 최소화).
- **`h264_nvenc`** — NVIDIA의 H.264 GPU 인코더(CPU 대비 매우 빠름).
- **`scale_cuda`** — GPU 메모리상에서 리사이즈.
- `hwupload` / `hwdownload` — 프레임을 GPU 메모리로 올리거나 CPU 메모리로 내림.
  - 일부 필터(`gblur` 등)는 GPU 메모리에서 직접 못 쓰므로 `hwdownload`로 내려 처리해야 함.
- ⚠️ NVENC는 GPU 인코더 특성상 같은 비트레이트에서 libx264(CPU)보다 품질이 약간 낮을 수 있으나, 속도가 압도적.
  (그래서 이 프로젝트 블러 강도도 CUDA 기준으로 보정값 `BLUR_WEIGHT=70`을 따로 둠.)

### 6.8 HLS (HTTP Live Streaming)
- 긴 영상을 짧은 `.ts` 세그먼트들로 쪼개고, `.m3u8` 플레이리스트로 목록을 관리하는 스트리밍 방식.
- **마스터 플레이리스트(master.m3u8):** 여러 화질(variant)을 가리키는 상위 목록 → 플레이어가 네트워크에 맞춰 화질 자동 전환(ABR).
- 주요 옵션:
  - `-f hls` — HLS 출력.
  - `-hls_time 6` — 세그먼트 길이(초).
  - `-hls_list_size 0` — 플레이리스트에 모든 세그먼트 유지(VOD).
  - `-master_pl_name master.m3u8` — 마스터 플레이리스트 파일명.
  - `-var_stream_map` — 어떤 비디오/오디오 스트림을 어떤 variant로 묶을지 지정.
  - `%v` — variant 인덱스 치환자(화질별 폴더 분리).

### 6.9 진행률 파싱 원리
- ffmpeg은 처리 상태를 stderr로 출력: `frame=... time=00:00:12.34 bitrate=... speed=...`.
- `time=` 값(현재까지 처리한 영상 시간)을 전체 길이로 나눠 진행률(%)을 계산.
- 이 프로젝트는 `redirectErrorStream(true)`로 stderr를 stdout과 합쳐 읽고 정규식으로 `time=`을 추출.

---

## 7. 요약

- **모든 미디어 처리는 외부 ffmpeg/ffprobe 바이너리를 `ProcessBuilder`로 호출**하여 수행.
- 핵심 클래스는 `FFMPEGUtil`(직접 실행) + `FFmpegCommandBuilder`/`MakeFileHelper`(합성 명령 빌드) + `FFmpegAudioFixer`(오디오 보장).
- 메타데이터·썸네일·WebP·HLS 다중화질·자르기·회전·블러·합성 기능을 제공하며, 대부분 **CUDA/NVENC 하드웨어 가속**을 사용.
- 실행 환경은 **ffmpeg이 설치된 Docker 이미지(`my-base-java-ffmpeg`)** 이며, NVENC 사용을 위해 **NVIDIA GPU**가 필요.