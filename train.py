
import json
import random
import shutil
from pathlib import Path

import numpy as np
import tensorflow as tf
import matplotlib.pyplot as plt

from sklearn.metrics import classification_report, confusion_matrix
from sklearn.utils.class_weight import compute_class_weight


# ============================================================
# CONFIG
# ============================================================

SEED = 42
IMAGE_SIZE = (224, 224)
BATCH_SIZE = 32

HEAD_EPOCHS = 10
FINE_TUNE_EPOCHS = 10
FINE_TUNE_LAST_LAYERS = 30

CLASS_NAMES = ["glass", "metal", "paper", "plastic", "trash"]

RAW_DIR = Path("data/raw")
SPLIT_DIR = Path("data/split")
OUTPUT_DIR = Path("outputs")

TRAIN_DIR = SPLIT_DIR / "train"
VAL_DIR = SPLIT_DIR / "val"
TEST_DIR = SPLIT_DIR / "test"

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

random.seed(SEED)
np.random.seed(SEED)
tf.random.set_seed(SEED)


# ============================================================
# 1. DATASET PREPARATION
# ============================================================

def prepare_dataset():
    """Split raw images into train/val/test once.

    Expected input:
        data/raw/<class_name>/*

    Existing split is preserved on subsequent runs.
    """

    if TRAIN_DIR.exists() and VAL_DIR.exists() and TEST_DIR.exists():
        print("Using existing data/split dataset.")
        return

    valid_extensions = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

    for class_name in CLASS_NAMES:
        class_dir = RAW_DIR / class_name

        if not class_dir.exists():
            raise FileNotFoundError(
                f"Missing directory: {class_dir}"
            )

        images = [
            p for p in class_dir.rglob("*")
            if p.is_file() and p.suffix.lower() in valid_extensions
        ]

        if len(images) < 10:
            raise ValueError(
                f"Class '{class_name}' has only {len(images)} images. "
                "Add more images before training."
            )

    if SPLIT_DIR.exists():
        raise RuntimeError(
            f"{SPLIT_DIR} exists but its split is incomplete. "
            "Inspect it and remove it manually before retrying."
        )

    for split in ["train", "val", "test"]:
        for class_name in CLASS_NAMES:
            (SPLIT_DIR / split / class_name).mkdir(
                parents=True, exist_ok=True
            )

    for class_name in CLASS_NAMES:
        images = [
            p for p in (RAW_DIR / class_name).rglob("*")
            if p.is_file() and p.suffix.lower() in valid_extensions
        ]

        random.shuffle(images)

        n = len(images)
        n_train = int(n * 0.70)
        n_val = int(n * 0.15)

        partitions = {
            "train": images[:n_train],
            "val": images[n_train:n_train + n_val],
            "test": images[n_train + n_val:],
        }

        for split, files in partitions.items():
            for i, src in enumerate(files):
                # Unique names avoid collisions between subfolders.
                dst = (
                    SPLIT_DIR / split / class_name /
                    f"{i:06d}_{src.name}"
                )
                shutil.copy2(src, dst)

        print(
            f"{class_name}: "
            f"train={len(partitions['train'])}, "
            f"val={len(partitions['val'])}, "
            f"test={len(partitions['test'])}"
        )

    print("Dataset split created.")


# ============================================================
# 2. LOAD DATA
# ============================================================

def load_datasets():
    common = {
        "labels": "inferred",
        "label_mode": "int",
        "class_names": CLASS_NAMES,
        "image_size": IMAGE_SIZE,
        "batch_size": BATCH_SIZE,
        "shuffle": False,
    }

    train_ds = tf.keras.utils.image_dataset_from_directory(
        TRAIN_DIR,
        shuffle=True,
        seed=SEED,
        **{k: v for k, v in common.items() if k != "shuffle"}
    )

    val_ds = tf.keras.utils.image_dataset_from_directory(
        VAL_DIR, **common
    )

    test_ds = tf.keras.utils.image_dataset_from_directory(
        TEST_DIR, **common
    )

    # Cache only if it comfortably fits in RAM.
    autotune = tf.data.AUTOTUNE

    train_ds = train_ds.prefetch(autotune)
    val_ds = val_ds.prefetch(autotune)
    test_ds = test_ds.prefetch(autotune)

    return train_ds, val_ds, test_ds


def get_class_weights():
    counts = []

    for class_name in CLASS_NAMES:
        count = len(list((TRAIN_DIR / class_name).glob("*")))
        counts.append(count)

    weights = compute_class_weight(
        class_weight="balanced",
        classes=np.arange(len(CLASS_NAMES)),
        y=np.concatenate([
            np.full(count, i, dtype=np.int32)
            for i, count in enumerate(counts)
        ])
    )

    result = {i: float(w) for i, w in enumerate(weights)}
    print("Class weights:", result)
    return result


# ============================================================
# 3. BUILD TRANSFER-LEARNING MODEL
# ============================================================

def build_model():
    # Input images are RGB with values in [0, 255].
    inputs = tf.keras.Input(
        shape=(*IMAGE_SIZE, 3),
        name="image"
    )

    # Augmentation is active only during training.
    x = tf.keras.layers.RandomFlip("horizontal")(inputs)
    x = tf.keras.layers.RandomRotation(0.08)(x)
    x = tf.keras.layers.RandomZoom(0.12)(x)
    x = tf.keras.layers.RandomContrast(0.15)(x)

    # MobileNetV2 expects pixels scaled to [-1, 1].
    x = tf.keras.applications.mobilenet_v2.preprocess_input(x)

    backbone = tf.keras.applications.MobileNetV2(
        input_shape=(*IMAGE_SIZE, 3),
        include_top=False,
        weights="imagenet"
    )

    backbone.trainable = False

    # Keep BatchNorm in inference mode for stable fine-tuning.
    x = backbone(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.25)(x)

    outputs = tf.keras.layers.Dense(
        len(CLASS_NAMES),
        activation="softmax",
        name="predictions"
    )(x)

    model = tf.keras.Model(inputs, outputs)
    return model, backbone


def make_callbacks(stage):
    return [
        tf.keras.callbacks.ModelCheckpoint(
            filepath=str(OUTPUT_DIR / f"best_{stage}.keras"),
            monitor="val_accuracy",
            save_best_only=True,
            mode="max",
            verbose=1
        ),
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=4,
            restore_best_weights=True,
            verbose=1
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.2,
            patience=2,
            min_lr=1e-7,
            verbose=1
        ),
    ]


# ============================================================
# 4. TRAIN IN TWO STAGES
# ============================================================

def train_model(train_ds, val_ds, class_weights):
    model, backbone = build_model()

    # Stage A: train the new classification head.
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )

    print("\n=== STAGE A: train classification head ===")

    history_head = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=HEAD_EPOCHS,
        class_weight=class_weights,
        callbacks=make_callbacks("head")
    )

    # Stage B: unfreeze only the last layers.
    backbone.trainable = True

    for layer in backbone.layers[:-FINE_TUNE_LAST_LAYERS]:
        layer.trainable = False

    # Do not update BatchNormalization statistics on a small dataset.
    for layer in backbone.layers:
        if isinstance(layer, tf.keras.layers.BatchNormalization):
            layer.trainable = False

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )

    print("\n=== STAGE B: fine-tune upper backbone layers ===")

    history_fine = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=FINE_TUNE_EPOCHS,
        class_weight=class_weights,
        callbacks=make_callbacks("fine")
    )

    # Select the checkpoint with the best validation accuracy.
    candidates = [
        OUTPUT_DIR / "best_head.keras",
        OUTPUT_DIR / "best_fine.keras",
    ]

    best_path = max(
        candidates,
        key=lambda p: float(
            tf.keras.models.load_model(p).evaluate(
                val_ds, verbose=0
            )[1]
        )
    )

    best_model = tf.keras.models.load_model(best_path)
    print("Selected checkpoint:", best_path)

    best_model.save(OUTPUT_DIR / "waste_classifier.keras")

    return best_model, history_head, history_fine


# ============================================================
# 5. EVALUATE ON HELD-OUT TEST SET
# ============================================================

def evaluate_model(model, test_ds):
    y_true = np.concatenate([y.numpy() for _, y in test_ds])
    probabilities = model.predict(test_ds, verbose=1)
    y_pred = probabilities.argmax(axis=1)

    report = classification_report(
        y_true,
        y_pred,
        labels=list(range(len(CLASS_NAMES))),
        target_names=CLASS_NAMES,
        output_dict=True,
        zero_division=0
    )

    print("\n=== TEST CLASSIFICATION REPORT ===")
    print(classification_report(
        y_true,
        y_pred,
        labels=list(range(len(CLASS_NAMES))),
        target_names=CLASS_NAMES,
        zero_division=0
    ))

    matrix = confusion_matrix(
        y_true, y_pred, labels=list(range(len(CLASS_NAMES)))
    )

    print("Confusion matrix (rows=true, columns=predicted):")
    print(matrix)

    with open(OUTPUT_DIR / "metrics.json", "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    plt.figure(figsize=(7, 6))
    plt.imshow(matrix, cmap="Blues")
    plt.xticks(range(len(CLASS_NAMES)), CLASS_NAMES, rotation=45)
    plt.yticks(range(len(CLASS_NAMES)), CLASS_NAMES)
    plt.xlabel("Predicted")
    plt.ylabel("Actual")
    plt.title("Test confusion matrix")

    for i in range(len(CLASS_NAMES)):
        for j in range(len(CLASS_NAMES)):
            plt.text(j, i, str(matrix[i, j]), ha="center", va="center")

    plt.tight_layout()
    plt.savefig(OUTPUT_DIR / "confusion_matrix.png", dpi=160)
    plt.close()

    return probabilities


# ============================================================
# 6. EXPORT FOR ANDROID
# ============================================================

def export_tflite(model):
    # Float32 export is a good first baseline for Android.
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    tflite_path = OUTPUT_DIR / "waste_classifier.tflite"
    tflite_path.write_bytes(tflite_model)

    (OUTPUT_DIR / "labels.json").write_text(
        json.dumps(CLASS_NAMES, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )

    # Verify that the exported model can be loaded.
    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()

    print("\nTFLite export successful:")
    print("Model:", tflite_path)
    print("Input:", interpreter.get_input_details()[0]["shape"])
    print("Output:", interpreter.get_output_details()[0]["shape"])


# ============================================================
# MAIN
# ============================================================

def main():
    prepare_dataset()

    with open(OUTPUT_DIR / "labels.json", "w", encoding="utf-8") as f:
        json.dump(CLASS_NAMES, f, ensure_ascii=False, indent=2)

    train_ds, val_ds, test_ds = load_datasets()
    class_weights = get_class_weights()

    model, history_head, history_fine = train_model(
        train_ds, val_ds, class_weights
    )

    evaluate_model(model, test_ds)
    export_tflite(model)

    print("\n=== ALPHA COMPLETE ===")
    print("Keras model: outputs/waste_classifier.keras")
    print("Android model: outputs/waste_classifier.tflite")
    print("Labels: outputs/labels.json")
    print("Metrics: outputs/metrics.json")
    print("Confusion matrix: outputs/confusion_matrix.png")


if __name__ == "__main__":
    main()
