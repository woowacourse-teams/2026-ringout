# 도착 화면 씨앗 애니메이션

사용자가 제공한 [Blender 원본 영상](arrival-assets/smiling-seed-transparent.webm)을 사용한다.

- 원본: VP9 WebM, 900×900, 24fps, 41프레임, 약 1.709초, 오디오 없음, 알파 채널 있음.
- 앱 리소스: `arrival_seed_animation.webp`, **정적** 무손실 WebP 이미지에 프레임을 행 우선으로 배열.
- 한 프레임: 384×384. 배열: 7열×6행. 마지막 칸은 비어 있으며 재생하지 않는다.
- 전체 이미지: 2688×2304, 약 1.48MiB. 디코딩된 RGBA 픽셀은 약 23.6MiB.
- `arrival_seed.png`: 같은 영상의 첫 프레임. Preview·로드 중·로드 실패 시 표시.

Android와 iOS의 투명 영상 코덱 지원 차이를 피하기 위해 공통 Compose Canvas에서 같은 프레임을 표시한다. 이미지를 한 번 디코딩하고 원본 영역만 바꾸므로 프레임마다 비트맵을 만들지 않는다. 기존 186dp 영역에서 원본 비율을 유지한다.

참고: [Android Media3의 투명 영상 지원 논의](https://github.com/androidx/media/issues/1388), [Apple의 HEVC 알파 영상 지원](https://developer.apple.com/documentation/avfoundation/using-hevc-video-with-alpha).

화면이 활성화되고 씨앗이 보일 때 반복 재생한다. 앱 일시 정지·화면 제거·완전히 가려진 상태에서는 정지하며, 시스템 모션 비활성화를 존중한다. 완성된 나무는 기존 정적 이미지로 표시한다.

## 리소스 재생성

FFmpeg의 **libvpx-vp9 디코더**를 명시해야 알파 채널을 유지할 수 있다. 기본 VP9 디코더로 변환해 투명도가 유실되지 않도록 한다. 아래 명령은 클라이언트 디렉터리 기준이며 Python Pillow가 필요하다.

```sh
mkdir -p /tmp/ringout-seed-frames
ffmpeg -c:v libvpx-vp9 \
  -i docs/design/arrival-assets/smiling-seed-transparent.webm \
  -vf scale=384:384:flags=lanczos -fps_mode passthrough -pix_fmt rgba \
  /tmp/ringout-seed-frames/seed-%03d.png
```

```python
from pathlib import Path
from PIL import Image

frames = sorted(Path('/tmp/ringout-seed-frames').glob('seed-*.png'))
assert len(frames) == 41
resources = Path('shared/src/commonMain/composeResources/drawable')
atlas = Image.new('RGBA', (384 * 7, 384 * 6))
for index, frame in enumerate(frames):
    atlas.paste(Image.open(frame), ((index % 7) * 384, (index // 7) * 384))
atlas.save(resources / 'arrival_seed_animation.webp', lossless=True, method=6)
Image.open(frames[0]).save(resources / 'arrival_seed.png')
```

프레임 수·크기·fps를 변경하면 `ArrivalSeedAnimation.kt`의 상수도 함께 갱신한다.
