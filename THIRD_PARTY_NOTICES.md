# Third-Party Notices

WetterWallpaper itself is released under the MIT License (see [LICENSE](LICENSE)).
It bundles or uses the following third-party software, models, fonts and data.
Licences were checked at the original source on 2026-11-11.

## Software

| Component | Version | Licence | Source |
|---|---|---|---|
| ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android`) | 1.30.0 | MIT | https://github.com/microsoft/onnxruntime |
| AndroidX AppCompat (`androidx.appcompat:appcompat`) and its AndroidX dependencies | 1.7.0 | Apache-2.0 | https://github.com/androidx/androidx |
| Material Components for Android (`com.google.android.material:material`) | 1.12.0 | Apache-2.0 | https://github.com/material-components/material-components-android |

## Model

**Depth Anything V2 Small** — Apache-2.0

- Original work: Lihe Yang et al., "Depth Anything V2" (NeurIPS 2024),
  https://github.com/DepthAnything/Depth-Anything-V2
- Model card: https://huggingface.co/depth-anything/Depth-Anything-V2-Small (licence: apache-2.0).
  Only the **Small** variant is Apache-2.0; the Base, Large and Giant variants are CC BY-NC 4.0 and are not used here.
- Bundled file: `app/src/main/assets/depth.onnx`, the unmodified fp16 ONNX export
  `onnx/model_fp16.onnx` from https://huggingface.co/onnx-community/depth-anything-v2-small
- SHA-256: `2df6223f206b5164e21f664ace61dabeb9bb6a49b8b5a3e00510b4807d0f5b04` (49,642,442 bytes)

## Font

**Lexend** (weights 200–700, static TTF) — SIL Open Font License 1.1

- Copyright 2018 The Lexend Project Authors (https://github.com/googlefonts/lexend),
  with Reserved Font Name "RevReading Lexend".
- Files: `app/src/main/res/font/lexend_*.ttf`, taken unmodified from `fonts/lexend/ttf/` at commit `7894f02b2e`.
- Full licence text: [licenses/OFL-Lexend.txt](licenses/OFL-Lexend.txt)

## Data

**Weather and air-quality data by [Open-Meteo.com](https://open-meteo.com/)** — CC BY 4.0
(https://creativecommons.org/licenses/by/4.0/)

- Endpoints used: `api.open-meteo.com/v1/forecast`, `air-quality-api.open-meteo.com/v1/air-quality`
- Licence page: https://open-meteo.com/en/licence
- The app shows the attribution under ☰ → Info.
- Note: Open-Meteo's free API is limited to non-commercial use (https://open-meteo.com/en/terms).

## Licence texts

- MIT: https://opensource.org/license/mit
- Apache-2.0: https://www.apache.org/licenses/LICENSE-2.0
- SIL OFL 1.1: [licenses/OFL-Lexend.txt](licenses/OFL-Lexend.txt)
- CC BY 4.0: https://creativecommons.org/licenses/by/4.0/legalcode
