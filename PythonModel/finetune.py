
import hashlib
import json
import random
import shutil
from pathlib import Path

import numpy as np
import tensorflow as tf
import matplotlib.pyplot as plt

from sklearn.metrics import classification_report, confusion_matrix


# ============================================================
# CONFIG
# ============================================================

SEED = 42
IMAGE_SIZE = (224, 224)
BATCH_SIZE = 32

EPOCHS = 15
FINE_TUNE_LAST_LAYERS = 30
LEARNING_RATE = 1e-5
PATIENCE = 4

CLASS_NAMES = ["glass", "metal", "paper", "plastic", "trash"]
VALID_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

RAW_DIR = Path("data/raw")
SPLIT_DIR = Path("data/split")
WORK_DIR = Path("data/finetune_work")
OUTPUT_DIR = Path("outputs")

OLD_TRAIN = SPLIT_DIR / "train"
OLD_VAL = SPLIT_DIR / "val"
OLD_TEST = SPLIT_DIR / "test"

BASELINE_PATH = OUTPUT_DIR / "baseline_waste_classifier.keras"
CURRENT_PATH = OUTPUT_DIR / "waste_classifier.keras"
CANDIDATE_PATH = OUTPUT_DIR / "finetuned_candidate.keras"
CHECKPOINT_PATH = OUTPUT_DIR / "best_finetune.keras"

FINAL_TFLITE = OUTPUT_DIR / "waste_classifier.tflite"

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

random.seed(SEED)
np.random.seed(SEED)
tf.random.set_seed(SEED)


# ============================================================
# 1. FILE UTILITIES
# ============================================================

def image_files(directory):
    if not directory.exists():
        return []

    return sorted(
        p for p in directory.rglob("*")
        if p.is_file()
        and p.suffix.lower() in VALID_EXTENSIONS
    )


def sha256_file(path):
    digest = hashlib.sha256()

    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)

    return digest.hexdigest()


def copy_into_split(source, destination_dir):
    destination_dir.mkdir(parents=True, exist_ok=True)

    # Hash-based names prevent filename collisions.
    digest = sha256_file(source)
    destination = (
        destination_dir /
        f"{digest[:16]}_{source.name}"
    )

    if not destination.exists():
        shutil.copy2(source, destination)


def validate_project():
    for directory in [OLD_TRAIN, OLD_VAL, OLD_TEST]:
        if not directory.exists():
            raise FileNotFoundError(
                f"Missing {directory}. "
                "The original split must exist."
            )

    for class_name in CLASS_NAMES:
        if not (RAW_DIR / class_name).exists():
            raise FileNotFoundError(
                f"Missing class directory: {RAW_DIR / class_name}"
            )

    if not CURRENT_PATH.exists():
        raise FileNotFoundError(
            f"Missing baseline model: {CURRENT_PATH}"
        )

    # Save the baseline once; never overwrite this backup.
    if not BASELINE_PATH.exists():
        shutil.copy2(CURRENT_PATH, BASELINE_PATH)
        print("Baseline backup created:", BASELINE_PATH)
    else:
        print("Using preserved baseline:", BASELINE_PATH)


# ============================================================
# 2. PREPARE NEW DATA WITHOUT REUSING OLD SPLIT IMAGES
# ============================================================

def prepare_data():
    print("\n=== PREPARING DATA ===")

    # Hashes from all original splits, including the old test set.
    old_hashes = set()

    for split_dir in [OLD_TRAIN, OLD_VAL, OLD_TEST]:
        for path in image_files(split_dir):
            old_hashes.add(sha256_file(path))

    print("Unique old image hashes:", len(old_hashes))

    # Collect unique images from raw that do not exist in old splits.
    new_by_class = {name: [] for name in CLASS_NAMES}
    seen_raw_hashes = set()
    duplicate_count = 0
    old_image_count = 0

    for class_name in CLASS_NAMES:
        for path in image_files(RAW_DIR / class_name):
            digest = sha256_file(path)

            if digest in old_hashes:
                old_image_count += 1
                continue

            if digest in seen_raw_hashes:
                duplicate_count += 1
                continue

            seen_raw_hashes.add(digest)
            new_by_class[class_name].append(path)

    print("Raw images matching old splits:", old_image_count)
    print("Duplicate raw images skipped:", duplicate_count)

    # Rebuild only the generated workspace, never data/split or data/raw.
    if WORK_DIR.exists():
        shutil.rmtree(WORK_DIR)

    for split in ["train", "val", "test"]:
        for class_name in CLASS_NAMES:
            (WORK_DIR / split / class_name).mkdir(
                parents=True, exist_ok=True
            )

    # Copy original split images unchanged into the workspace.
    # The original directories remain untouched.
    for split_name, source_dir in [
        ("train", OLD_TRAIN),
        ("val", OLD_VAL),
        ("test", OLD_TEST),
    ]:
        for class_name in CLASS_NAMES:
            for path in image_files(source_dir / class_name):
                copy_into_split(
                    path,
                    WORK_DIR / split_name / class_name
                )

    # Divide genuinely new images by class.
    # They are assigned only once: train, validation, or test.
    for class_name, paths in new_by_class.items():
        rng = random.Random(SEED)
        paths = list(paths)
        rng.shuffle(paths)

        n = len(paths)
        n_train = int(n * 0.70)
        n_val = int(n * 0.15)

        # For very small groups, remaining images go to train.
        # This avoids creating unnecessary one-image holdouts.
        if n >= 3:
            train_paths = paths[:n_train]
            val_paths = paths[n_train:n_train + n_val]
            test_paths = paths[n_train + n_val:]
        else:
            train_paths = paths
            val_paths = []
            test_paths = []

        for path in train_paths:
            copy_into_split(path, WORK_DIR / "train" / class_name)

        for path in val_paths:
            copy_into_split(path, WORK_DIR / "val" / class_name)

        for path in test_paths:
            copy_into_split(path, WORK_DIR / "test" / class_name)

        print(
            f"{class_name:8s}: new={n}, "
            f"new_train={len(train_paths)}, "
            f"new_val={len(val_paths)}, "
            f"new_test={len(test_paths)}"
        )

    print("\nCombined split sizes:")
    for split in ["train", "val", "test"]:
        print(f"\n{split.upper()}")
        for class_name in CLASS_NAMES:
            count = len(
                image_files(WORK_DIR / split / class_name)
            )
            print(f"  {class_name:8s}: {count}")

    total_new = sum(len(v) for v in new_by_class.values())

    if total_new == 0:
        raise ValueError(
            "No new unique images found. "
            "Check that data/raw contains the new photos."
        )

    return total_new


# ============================================================
# 3. LOAD DATASETS
# ============================================================

def load_directory(directory, shuffle=False):
    return tf.keras.utils.image_dataset_from_directory(
        directory,
        labels="inferred",
        label_mode="int",
        class_names=CLASS_NAMES,
        image_size=IMAGE_SIZE,
        batch_size=BATCH_SIZE,
        shuffle=shuffle,
        seed=SEED if shuffle else None,
    )


def load_datasets():
    train_ds = load_directory(
        WORK_DIR / "train", shuffle=True
    ).prefetch(tf.data.AUTOTUNE)

    val_ds = load_directory(
        WORK_DIR / "val"
    ).prefetch(tf.data.AUTOTUNE)

    test_ds = load_directory(
        WORK_DIR / "test"
    ).prefetch(tf.data.AUTOTUNE)

    return train_ds, val_ds, test_ds


# ============================================================
# 4. CLASS WEIGHTS
# ============================================================

def get_class_weights():
    counts = np.array([
        len(image_files(WORK_DIR / "train" / name))
        for name in CLASS_NAMES
    ], dtype=np.float64)

    if np.any(counts == 0):
        raise ValueError(
            f"Missing training images for a class: {counts.tolist()}"
        )

    # Balanced weights, capped to avoid extreme gradients.
    weights = counts.sum() / (len(CLASS_NAMES) * counts)
    weights = np.clip(weights, 0.5, 3.0)

    result = {
        i: float(weight)
        for i, weight in enumerate(weights)
    }

    print("\nClass weights:", result)
    return result


# ============================================================
# 5. FIND MOBILENETV2 BACKBONE
# ============================================================

def find_backbone(model):
    for layer in model.layers:
        if (
            isinstance(layer, tf.keras.Model)
            and "mobilenetv2" in layer.name.lower()
        ):
            return layer

    raise ValueError(
        "MobileNetV2 backbone not found in the saved model."
    )


# ============================================================
# 6. FINE-TUNE BASELINE
# ============================================================

def fine_tune(train_ds, val_ds, class_weights):
    print("\nLoading preserved baseline...")

    model = tf.keras.models.load_model(
        BASELINE_PATH,
        compile=False
    )

    if model.output_shape[-1] != len(CLASS_NAMES):
        raise ValueError(
            "Model output does not match the five configured classes."
        )

    backbone = find_backbone(model)

    # Unfreeze the backbone, but train only its last layers.
    backbone.trainable = True

    for layer in backbone.layers:
        layer.trainable = False

    for layer in backbone.layers[-FINE_TUNE_LAST_LAYERS:]:
        if not isinstance(
            layer, tf.keras.layers.BatchNormalization
        ):
            layer.trainable = True

    # Keep BatchNormalization frozen.
    for layer in backbone.layers:
        if isinstance(layer, tf.keras.layers.BatchNormalization):
            layer.trainable = False

    model.compile(
        optimizer=tf.keras.optimizers.Adam(
            learning_rate=LEARNING_RATE
        ),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )

    model.summary()

    callbacks = [
        tf.keras.callbacks.ModelCheckpoint(
            str(CHECKPOINT_PATH),
            monitor="val_loss",
            mode="min",
            save_best_only=True,
            verbose=1,
        ),
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=PATIENCE,
            restore_best_weights=True,
            verbose=1,
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.2,
            patience=2,
            min_lr=1e-7,
            verbose=1,
        ),
    ]

    model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=EPOCHS,
        class_weight=class_weights,
        callbacks=callbacks,
    )

    candidate = tf.keras.models.load_model(
        CHECKPOINT_PATH,
        compile=False
    )

    candidate.save(CANDIDATE_PATH)
    print("Candidate saved:", CANDIDATE_PATH)

    return candidate


# ============================================================
# 7. SELECT MODEL USING VALIDATION ONLY
# ============================================================

def evaluate_loss_accuracy(model, dataset):
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )

    loss, accuracy = model.evaluate(dataset, verbose=0)
    return float(loss), float(accuracy)


def select_model(candidate, val_ds):
    baseline = tf.keras.models.load_model(
        BASELINE_PATH,
        compile=False
    )

    old_loss, old_acc = evaluate_loss_accuracy(baseline, val_ds)
    new_loss, new_acc = evaluate_loss_accuracy(candidate, val_ds)

    print("\n=== VALIDATION COMPARISON ===")
    print(f"Baseline:  loss={old_loss:.4f}, accuracy={old_acc:.4f}")
    print(f"Candidate: loss={new_loss:.4f}, accuracy={new_acc:.4f}")

    # Do not replace baseline unless validation accuracy improves,
    # or ties while validation loss improves.
    improved = (
        new_acc > old_acc + 1e-6
        or (
            abs(new_acc - old_acc) <= 1e-6
            and new_loss < old_loss
        )
    )

    if improved:
        shutil.copy2(CANDIDATE_PATH, CURRENT_PATH)
        print("Candidate selected.")
    else:
        shutil.copy2(BASELINE_PATH, CURRENT_PATH)
        print("Baseline retained.")

    return tf.keras.models.load_model(
        CURRENT_PATH,
        compile=False
    )


# ============================================================
# 8. TEST REPORT
# ============================================================

def evaluate_test(model, test_ds, prefix):
    y_true = np.concatenate([
        y.numpy() for _, y in test_ds
    ])

    probabilities = model.predict(test_ds, verbose=1)
    y_pred = probabilities.argmax(axis=1)

    report_text = classification_report(
        y_true,
        y_pred,
        labels=list(range(len(CLASS_NAMES))),
        target_names=CLASS_NAMES,
        zero_division=0,
    )

    report_dict = classification_report(
        y_true,
        y_pred,
        labels=list(range(len(CLASS_NAMES))),
        target_names=CLASS_NAMES,
        output_dict=True,
        zero_division=0,
    )

    print(f"\n=== {prefix.upper()} TEST REPORT ===")
    print(report_text)

    with open(
        OUTPUT_DIR / f"metrics_{prefix}.json",
        "w",
        encoding="utf-8",
    ) as file:
        json.dump(report_dict, file, indent=2, ensure_ascii=False)

    matrix = confusion_matrix(
        y_true,
        y_pred,
        labels=list(range(len(CLASS_NAMES))),
    )

    print("Confusion matrix:")
    print(matrix)

    plt.figure(figsize=(7, 6))
    plt.imshow(matrix, cmap="Blues")
    plt.xticks(range(len(CLASS_NAMES)), CLASS_NAMES, rotation=45)
    plt.yticks(range(len(CLASS_NAMES)), CLASS_NAMES)
    plt.xlabel("Predicted")
    plt.ylabel("Actual")
    plt.title(f"Confusion matrix — {prefix}")

    for i in range(len(CLASS_NAMES)):
        for j in range(len(CLASS_NAMES)):
            plt.text(
                j, i, str(matrix[i, j]),
                ha="center", va="center"
            )

    plt.tight_layout()
    plt.savefig(
        OUTPUT_DIR / f"confusion_matrix_{prefix}.png",
        dpi=160
    )
    plt.close()

    return report_dict


# ============================================================
# 9. EXPORT TFLITE
# ============================================================

def export_tflite(model):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    FINAL_TFLITE.write_bytes(tflite_model)

    (OUTPUT_DIR / "labels.json").write_text(
        json.dumps(CLASS_NAMES, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    interpreter = tf.lite.Interpreter(
        model_path=str(FINAL_TFLITE)
    )
    interpreter.allocate_tensors()

    print("\nTFLite export successful:")
    print("Model:", FINAL_TFLITE)
    print("Input:", interpreter.get_input_details()[0]["shape"])
    print("Output:", interpreter.get_output_details()[0]["shape"])


# ============================================================
# MAIN
# ============================================================

def main():
    print("=== WASTE CLASSIFIER FINE-TUNING ===")

    validate_project()
    prepare_data()

    train_ds, val_ds, test_ds = load_datasets()
    class_weights = get_class_weights()

    candidate = fine_tune(
        train_ds,
        val_ds,
        class_weights
    )

    selected = select_model(candidate, val_ds)

    # Evaluate both models on exactly the same test dataset.
    baseline = tf.keras.models.load_model(
        BASELINE_PATH,
        compile=False
    )

    evaluate_test(baseline, test_ds, "baseline")
    evaluate_test(selected, test_ds, "selected")

    export_tflite(selected)

    print("\n=== COMPLETE ===")
    print("Selected Keras model:", CURRENT_PATH)
    print("Baseline backup:", BASELINE_PATH)
    print("TFLite model:", FINAL_TFLITE)
    print("Working datasets:", WORK_DIR)


if __name__ == "__main__":
    main()
