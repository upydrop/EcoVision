# EcoVision

EcoVision is an Android application that uses on-device machine learning to classify waste into five categories: **glass, metal, paper, plastic, and trash**.

The project includes an Android application written in Kotlin and a Python-based machine learning pipeline for training, evaluating, and exporting the image classification model.

## Project Structure

- **Android application** — Handles the user interface, image input, and integration with the TensorFlow Lite model. Kotlin implementation details are kept within the Android project.
- **`PythonModel/`** — Contains the Python scripts for baseline training and fine-tuning, along with model artifacts and evaluation outputs.
- **`PythonModel/requirements.txt`** — Lists the Python dependencies required for model training.

## PythonModel

### Training Scripts

| File | Purpose |
|---|---|
| `train.py` | Trains the initial waste classifier using transfer learning. |
| `finetune.py` | Fine-tunes the existing baseline on a larger dataset and selects whether the updated model should replace it. |

### Model Training Method

EcoVision uses **transfer learning with MobileNetV2**, a pretrained convolutional neural network initialized with ImageNet weights. Instead of learning all visual features from scratch, the model reuses pretrained image features and adapts them to waste classification.

The classifier predicts one of five classes: `glass`, `metal`, `paper`, `plastic`, or `trash`.

#### Baseline Training

The baseline model was trained using approximately **2,500 waste images**.

- Images are resized to `224 × 224` pixels.
- The dataset is split into training (70%), validation (15%), and testing (15%) sets.
- A new classification head is trained first, with the pretrained MobileNetV2 backbone frozen.
- The final 30 backbone layers are then unfrozen for additional fine-tuning.
- Data augmentation, class weighting, early stopping, and learning-rate reduction help improve generalization and stabilize training.

#### Fine-Tuning

The fine-tuned version uses approximately **10,000 images**, including the images already used for baseline training.

Rather than training a new model from scratch, `finetune.py` loads the saved baseline and adapts it using the expanded dataset.

Key characteristics:

- **Duplicate detection:** SHA-256 hashes help identify duplicate images and prevent images from the original dataset splits from being added again.
- **Preserved baseline:** The original model is backed up and retained for comparison.
- **Selective fine-tuning:** Only the last 30 MobileNetV2 layers are eligible for training; Batch Normalization layers remain frozen.
- **Conservative optimization:** Adam with a learning rate of `1e-5` is used, with early stopping and learning-rate reduction.
- **Validation-based selection:** The candidate replaces the baseline only if validation accuracy improves, or if accuracy ties and validation loss decreases.
- **Independent testing:** Both models are evaluated on the same held-out test set.

The selected model is exported to TensorFlow Lite for use in the Android application.

### Outputs

The `PythonModel/outputs/` directory stores trained models and evaluation artifacts.

| Artifact | Purpose |
|---|---|
| `waste_classifier1.tflite` | Baseline TensorFlow Lite model. |
| `baseline_waste_classifier.keras` | Preserved baseline model used during fine-tuning. |
| `waste_classifier.keras` | Current selected Keras model. |
| `finetuned_candidate.keras` | Candidate produced by fine-tuning before model selection. |
| `best_finetune.keras` | Best fine-tuning checkpoint based on validation loss. |
| `waste_classifier.tflite` | Exported TensorFlow Lite model selected for deployment. |
| `labels.json` | Class labels associated with the exported model. |
| `metrics_*.json` | Per-class evaluation metrics for the baseline and selected model. |
| `confusion_matrix_*.png` | Confusion matrix visualizations for model evaluation. |

Additional checkpoints may be generated during baseline training.

## Installation and Running

### 1. Prerequisites

Install:

- [Git](https://git-scm.com/downloads)
- [Android Studio](https://developer.android.com/studio)
- An Android device or emulator

### 2. Download the needed files

- AndroidManifest.xml
- MainActivity.kt
- WasteClassifier.kt
- build.gradle.kts
- labels.json
- waste_classifier.tflite

### 3. Open the Android Project

1. Launch Android Studio.
2. Create a new Empty Views project with SDK 26 named ecovision
3. Allow Gradle synchronization to finish.
4. Replace the gradle app's(/src) build configuration.
5. Replace the app's(/src) AndroidManifest.xml file
6. Add all the .kt files in the apps directory(com.example.ecovision)
7. Create the /assets folder in the /src directory and paste the labels.json and waste_classifier.tflite files into it.

### 4. Run the Application

1. Connect an Android device with USB debugging enabled, or start an Android emulator.
2. Select the target device in Android Studio.
3. Click **Run** to build and launch the application.

If the project builds successfully, the app should install and launch on the selected device.

### Optional: Run the Python Training Pipeline

Python training is not required simply to run the Android application with its existing model.

To install the training dependencies:

```bash
cd PythonModel
python -m venv .venv
```

Activate the environment:

**Windows**

```powershell
.venv\Scripts\Activate.ps1
```

**Linux/macOS**

```bash
source .venv/bin/activate
```

Install the dependencies:

```bash
python -m pip install -r requirements.txt
```

The training scripts expect images organized by class under `PythonModel/data/raw/`:

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

Run fine-tuning:

```bash
python finetune.py
```

**Note:** Fine-tuning requires the original dataset splits and baseline model. The expected model and data paths must exist before running it. Training generates or updates files under `PythonModel/outputs/`.

## Technologies

- **Android:** Kotlin
- **Machine Learning:** TensorFlow / Keras
- **Model Architecture:** MobileNetV2
- **Mobile Inference:** TensorFlow Lite
- **Training Utilities:** NumPy, Matplotlib, scikit-learn
