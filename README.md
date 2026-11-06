# EcoVision

EcoVision is an Android waste-sorting assistant that classifies materials into five categories: **glass, metal, paper, plastic, and trash**. It uses an on-device TensorFlow Lite model, so image inference runs locally on the Android device.

The repository contains:
- **`Android/`** — Kotlin source files and the Android app module configuration.
- **`PythonModel/`** — Python scripts for training, fine-tuning, evaluating, and exporting the image classifier.

## Features

- Real-time camera-based waste classification.
- Capture/freeze the current frame to inspect a prediction.
- Prediction stabilization to reduce flickering between classes.
- Light, dark, and system themes.
- Adjustable animation and prediction-stabilization settings.
- Developer options for confidence scores and custom confidence thresholds.
- On-device inference with TensorFlow Lite; camera frames are processed locally and are not saved by the app.

The app requires camera permission. The classifier predicts one of five labels: `glass`, `metal`, `paper`, `plastic`, or `trash`.

## Project structure

```text
EcoVision/
├── Android/
│   ├── AndroidManifest.xml
│   ├── build.gradle.kts
│   ├── EcoTheme.kt
│   ├── MainActivity.kt
│   ├── MainMenuScreen.kt
│   ├── SettingsScreen.kt
│   ├── WasteClassifier.kt
│   ├── labels.json
│   └── waste_classifier.tflite
└── PythonModel/
    ├── train.py
    ├── finetune.py
    ├── finetune_v2.py
    ├── requirements.txt
    ├── data/
    └── outputs/
```

The `Android/` directory contains the app's source/module files. Create or open an Android Studio project and place these files in the corresponding app module locations as described below.

---

## Machine learning

### Model architecture

EcoVision uses **MobileNetV2** with ImageNet pretrained weights. The network is adapted to classify waste images into five classes. Input images are resized to `224 × 224`; preprocessing is included in the model.

### Baseline training

The initial model was trained with approximately **2,500 images**.

- The dataset is split into training (70%), validation (15%), and test (15%) subsets.
- The classification head is trained while the MobileNetV2 backbone is frozen.
- The final 30 backbone layers are then fine-tuned.
- Data augmentation, class weighting, early stopping, and learning-rate reduction are used to support generalization.

Run the baseline pipeline with `PythonModel/train.py`.

### Fine-tuning v1

The first fine-tuning stage expanded the dataset to approximately **10,000 images**, including images already used for baseline training.

The `finetune.py` script:
- Uses SHA-256 hashes to detect duplicate images.
- Preserves the original train/validation/test split and creates a separate working dataset.
- Keeps a backup of the baseline model.
- Fine-tunes only the last 30 MobileNetV2 layers; Batch Normalization layers remain frozen.
- Uses Adam with a learning rate of `1e-5`, early stopping, and learning-rate reduction.
- Selects the candidate using validation accuracy (and validation loss as a tie-breaker).
- Evaluates the baseline and selected model on the same held-out test set.

### Fine-tuning v2

The second fine-tuning iteration used **10,500 images**. It is implemented in `PythonModel/finetune_v2.py` and continues training the existing Keras model rather than starting from ImageNet weights again.

Main characteristics:
- **Existing model as the starting point:** loads `outputs/waste_classifier.keras`.
- **Old and new data support:** prefers the original `data/split/{train,val,test}` folders; alternatively, it can use optional `data/old_raw/<class>/` images. New images are placed in `data/raw/<class>/`.
- **Duplicate and label-conflict handling:** SHA-256 hashes prevent duplicate images from crossing dataset splits. Identical image files assigned to different classes are excluded and recorded in `outputs/finetune_v2_conflicts.txt`.
- **Test-set protection:** when the original split is available, its test images are reserved and excluded from training and validation.
- **Fine-tuning configuration:** image size `224 × 224`, batch size `16`, up to `15` epochs, learning rate `1e-5`, and the last 30 MobileNetV2 layers eligible for training. Batch Normalization layers remain frozen.
- **Class imbalance:** capped balanced class weights are used during training.
- **Model selection:** compares the original model and fine-tuning candidate on validation macro-F1. The candidate is selected only if its macro-F1 is higher; otherwise, the original model is retained.
- **Final evaluation:** evaluates the original and selected models on the held-out test set after model selection, then exports the selected model to TensorFlow Lite.

The script requires at least **7 genuinely new, unique images per class** in `data/raw/`. The 10,500 figure describes the dataset used for this v2 iteration; the script reports actual split counts when it runs.

#### Fine-tuning v2 dataset layout

Run the script from the `PythonModel/` directory. The required new images must be arranged as follows:

```text
PythonModel/
├── data/
│   ├── raw/
│   │   ├── glass/
│   │   ├── metal/
│   │   ├── paper/
│   │   ├── plastic/
│   │   └── trash/
│   └── split/                 # preferred: original train/val/test split
│       ├── train/<class>/
│       ├── val/<class>/
│       └── test/<class>/
└── outputs/
    └── waste_classifier.keras
```

If the original `data/split/` is unavailable, the script can instead use optional old images from `data/old_raw/<class>/`. If neither old source exists, it warns that training will use only the new images, which may cause catastrophic forgetting.

Run v2:

```bash
python finetune_v2.py
```

V2 creates:
- `outputs/waste_classifier_v2.keras` — selected Keras model.
- `outputs/waste_classifier_v2.tflite` — selected TensorFlow Lite model.
- `outputs/labels_v2.json` — class labels for the v2 model.
- `outputs/finetune_v2_metrics.json` — original/candidate validation metrics and original/selected test metrics.
- `outputs/v2_best_checkpoint.keras` — best fine-tuning checkpoint.
- `outputs/finetune_v2_conflicts.txt` — created only when conflicting labels are detected.

**Important:** v2 writes separate `*_v2` files. It does not automatically replace `waste_classifier.tflite` or `labels.json` used by the Android app. Review `finetune_v2_metrics.json` first. To deploy the v2 model, copy `waste_classifier_v2.tflite` into the Android assets folder under the exact name `waste_classifier.tflite`, and copy `labels_v2.json` as `labels.json`.

---

## Installation and running

### 1. Clone the repository

```bash
git clone https://github.com/upydrop/EcoVision.git
cd EcoVision
```

### 2. Run the Android application

#### Requirements

- Android Studio with Android SDK and a compatible Android Gradle Plugin/Kotlin toolchain.
- Android device or emulator with **API 26 (Android 8.0) or newer**.
- A camera (or a device with a virtual camera in the emulator).

#### Set up the Android Studio project

The repository's `Android/` folder contains the app module source files and module Gradle configuration, not a complete standalone Android Studio project with root settings and Gradle wrapper.

1. Open Android Studio and create a new **Empty Activity** project using **Jetpack Compose**.
2. Set the project name to `EcoVision`, package/application ID to `com.example.ecovision`, and minimum SDK to **26**.
3. In the app module, copy the Kotlin files from the repository's `Android/` folder into `app/src/main/java/com/example/ecovision/`:
   - `MainActivity.kt`
   - `WasteClassifier.kt`
   - `EcoTheme.kt`
   - `MainMenuScreen.kt`
   - `SettingsScreen.kt`
4. Copy `Android/AndroidManifest.xml` to `app/src/main/AndroidManifest.xml`.
5. Create `app/src/main/assets/` and copy both model files from `Android/` into it:
   - `waste_classifier.tflite`
   - `labels.json`
6. Add the dependencies below to the app module's `dependencies { }` block. Keep the Compose dependencies generated by Android Studio; the Compose BOM should manage their versions.

```kotlin
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
implementation("androidx.camera:camera-core:1.4.2")
implementation("androidx.camera:camera-camera2:1.4.2")
implementation("androidx.camera:camera-lifecycle:1.4.2")
implementation("androidx.camera:camera-view:1.4.2")
implementation("org.tensorflow:tensorflow-lite:2.16.1")
implementation("androidx.compose.material:material-icons-extended")
```

7. Make sure the app module has `buildFeatures { compose = true }`, Java/Kotlin target 11, and `minSdk = 26`. The repository's `Android/build.gradle.kts` can be used as a reference for the module configuration; it uses a Gradle version catalog for some standard AndroidX/Compose dependencies.
8. Sync Gradle. Connect an Android device with USB debugging enabled or start an emulator, then click **Run**.

When the app starts, grant camera permission. The app performs classification locally using the TensorFlow Lite model in `app/src/main/assets/`.

### 3. Set up the Python training environment (optional)

Python is needed only to train or fine-tune the model. It is **not** required to run the Android app with the model already included in the repository.

Use **Python 3.10 or 3.11** for the pinned TensorFlow 2.15.1 environment.

From the repository root:

```bash
cd PythonModel
python -m venv .venv
```

Activate the environment:

**Windows PowerShell**
```powershell
.venv\Scripts\Activate.ps1
```

**Linux/macOS**
```bash
source .venv/bin/activate
```

Upgrade pip and install dependencies:

```bash
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

For baseline training and v1 fine-tuning, the input images are expected under `data/raw/<class>/`:

```text
data/raw/
├── glass/
├── metal/
├── paper/
├── plastic/
└── trash/
```

Run baseline training:

```bash
python train.py
```

Run v1 fine-tuning (requires the original split and baseline model):

```bash
python finetune.py
```

Run v2 fine-tuning (requires `outputs/waste_classifier.keras` and the new images described in the v2 section):

```bash
python finetune_v2.py
```

Training outputs are written to `PythonModel/outputs/`. Keep the original data split and model artifacts backed up before running training scripts.

## Technologies

- **Android:** Kotlin, Jetpack Compose, CameraX
- **Machine learning:** TensorFlow / Keras
- **Model architecture:** MobileNetV2
- **On-device inference:** TensorFlow Lite
- **Python utilities:** NumPy, scikit-learn, Matplotlib, Pillow
