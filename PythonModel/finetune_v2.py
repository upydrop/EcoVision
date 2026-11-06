"""EcoVision: continue training an existing Keras classifier with new + optional old images.
Run from PythonModel: python finetune_v2.py
Requires TensorFlow, numpy, scikit-learn, Pillow (included in typical project requirements).
"""
import hashlib
import json
import random
import shutil
from pathlib import Path

import numpy as np
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix

ROOT = Path(__file__).resolve().parent
CLASSES = ["glass", "metal", "paper", "plastic", "trash"]
EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
SEED = 42
IMAGE_SIZE = (224, 224)
BATCH_SIZE = 16
EPOCHS = 15
LR = 1e-5
LAST_LAYERS = 30

# REQUIRED: new photos, organized as data/raw/<class>/*.jpg
NEW_DIR = ROOT / "data" / "raw"
# OPTIONAL: previously used photos organized as data/old_raw/<class>/*.jpg.
# If the original data/split exists, it is preferred instead.
OLD_RAW = ROOT / "data" / "old_raw"
OLD_SPLIT = ROOT / "data" / "split"
WORK = ROOT / "data" / "finetune_v2_work"
OUT = ROOT / "outputs"
SOURCE = OUT / "waste_classifier.keras"
BEST = OUT / "v2_best_checkpoint.keras"
FINAL_KERAS = OUT / "waste_classifier_v2.keras"
FINAL_TFLITE = OUT / "waste_classifier_v2.tflite"

random.seed(SEED)
np.random.seed(SEED)
tf.random.set_seed(SEED)


def images(folder):
    return sorted(p for p in folder.rglob("*") if p.is_file() and p.suffix.lower() in EXTS) if folder.exists() else []


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def unique_by_class(folder):
    return {name: images(folder / name) for name in CLASSES}


def partition(paths, train_frac=0.7, val_frac=0.15):
    paths = list(paths)
    random.Random(SEED).shuffle(paths)
    n = len(paths)
    if n < 7:
        raise ValueError("Need at least 7 unique new images in each class for train/val/test. Add more photos.")
    n_train = max(1, int(n * train_frac))
    n_val = max(1, int(n * val_frac))
    return {"train": paths[:n_train], "val": paths[n_train:n_train+n_val], "test": paths[n_train+n_val:]}


def build_data():
    if not SOURCE.is_file():
        raise FileNotFoundError(f"Missing existing model: {SOURCE}")
    if not NEW_DIR.is_dir():
        raise FileNotFoundError(f"Missing new photos: {NEW_DIR}")

    old_split_available = all((OLD_SPLIT / split).is_dir() for split in ("train", "val", "test"))
    old_raw_available = OLD_RAW.is_dir() and any(images(OLD_RAW / c) for c in CLASSES)
    old_mode = "split" if old_split_available else ("raw" if old_raw_available else "none")
    print("Old images source:", old_mode)
    if old_mode == "none":
        print("WARNING: training only on new images; old knowledge may degrade.")

    # Keep original held-out test photos out of fitting and model selection.
    old = {s: unique_by_class(OLD_SPLIT / s) for s in ("train", "val", "test")} if old_mode == "split" else None
    heldout_hashes = set()
    if old_mode == "split":
        heldout_hashes = {digest(p) for paths in old["test"].values() for p in paths}

    # Scan every input before splitting. Identical bytes with conflicting class
    # labels are ambiguous: exclude ALL occurrences rather than choosing one.
    source_groups = []
    if old_mode == "split":
        source_groups.extend((c, old[s][c]) for s in ("train", "val", "test") for c in CLASSES)
    elif old_mode == "raw":
        source_groups.extend((c, images(OLD_RAW / c)) for c in CLASSES)
    source_groups.extend((c, images(NEW_DIR / c)) for c in CLASSES)
    hash_classes = {}
    hash_examples = {}
    for cls, paths in source_groups:
        for path in paths:
            h = digest(path)
            hash_classes.setdefault(h, set()).add(cls)
            hash_examples.setdefault(h, []).append((cls, path))
    ambiguous_hashes = {h for h, labels in hash_classes.items() if len(labels) > 1}
    if ambiguous_hashes:
        report_path = OUT / "finetune_v2_conflicts.txt"
        with report_path.open("w", encoding="utf-8") as report:
            for h in sorted(ambiguous_hashes):
                report.write(f"SHA256: {h}\n")
                for cls, path in hash_examples[h]:
                    report.write(f"  {cls}: {path}\n")
                report.write("\n")
        print(f"WARNING: Excluding {len(ambiguous_hashes)} images (unique hashes) "
              f"with conflicting labels. Details: {report_path}")

    used_hashes = {}  # digest -> (class, split)
    staged = {s: {c: [] for c in CLASSES} for s in ("train", "val", "test")}

    def add(paths, cls, split):
        for p in paths:
            h = digest(p)
            if h in ambiguous_hashes:
                continue
            if h in used_hashes:
                prev_cls, prev_split = used_hashes[h]
                if prev_cls != cls:
                    raise ValueError(f"Same image assigned different classes: {p} ({cls}) and {prev_cls}")
                continue
            used_hashes[h] = (cls, split)
            staged[split][cls].append((p, h))

    if old_mode == "split":
        # Add old test first to reserve hashes, preventing overlap with training/validation.
        for s in ("test", "val", "train"):
            for c in CLASSES:
                add(old[s][c], c, s)
    elif old_mode == "raw":
        for c in CLASSES:
            old_unique = list({digest(p): p for p in images(OLD_RAW / c)
                               if digest(p) not in ambiguous_hashes}.values())
            parts = partition(old_unique)
            for s in ("test", "val", "train"):
                add(parts[s], c, s)

    for c in CLASSES:
        candidates = []
        seen = set()
        for p in images(NEW_DIR / c):
            h = digest(p)
            if h in ambiguous_hashes or h in seen or h in used_hashes or h in heldout_hashes:
                continue
            seen.add(h)
            candidates.append(p)
        if len(candidates) < 7:
            raise ValueError(f"{c}: only {len(candidates)} genuinely new unique images; need >=7 per class. "
                             "If data/raw includes all old photos, put additional new photos there.")
        parts = partition(candidates)
        for s in ("test", "val", "train"):
            add(parts[s], c, s)

    if WORK.exists():
        shutil.rmtree(WORK)
    for s in ("train", "val", "test"):
        for c in CLASSES:
            target = WORK / s / c
            target.mkdir(parents=True, exist_ok=True)
            for p, h in staged[s][c]:
                shutil.copy2(p, target / f"{h[:24]}{p.suffix.lower()}")
            print(f"{s:5} {c:8}: {len(staged[s][c])}")

    return old_mode


def dataset(split, shuffle=False):
    ds = tf.keras.utils.image_dataset_from_directory(
        WORK / split, labels="inferred", label_mode="int", class_names=CLASSES,
        image_size=IMAGE_SIZE, batch_size=BATCH_SIZE, shuffle=shuffle,
        seed=SEED if shuffle else None,
    )
    return ds.prefetch(tf.data.AUTOTUNE)


def metrics(model, ds):
    probs = model.predict(ds, verbose=0)
    targets = np.concatenate([labels.numpy() for _, labels in ds])
    preds = np.argmax(probs, axis=1)
    report = classification_report(targets, preds, labels=list(range(5)),
                                   target_names=CLASSES, output_dict=True, zero_division=0)
    return {"accuracy": float(report["accuracy"]), "macro_f1": float(report["macro avg"]["f1-score"]),
            "by_class": {c: report[c] for c in CLASSES},
            "confusion_matrix": confusion_matrix(targets, preds, labels=list(range(5))).tolist()}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    print("=== ECOVISION FINE TUNE V2 ===")
    mode = build_data()
    train, val, test = dataset("train", True), dataset("val"), dataset("test")

    original = tf.keras.models.load_model(SOURCE, compile=False)
    if original.output_shape[-1] != len(CLASSES):
        raise ValueError("Output layer does not match 5 classes")
    backbone = next((l for l in original.layers
                     if isinstance(l, tf.keras.Model) and "mobilenetv2" in l.name.lower()), None)
    if backbone is None:
        raise ValueError("No MobileNetV2 backbone found in model; inspect model.summary()")

    # Model preprocesses RGB 0..255 inside its graph. Do not divide images by 255.
    original_val = metrics(original, val)
    print("Original validation:", original_val["accuracy"], original_val["macro_f1"])

    backbone.trainable = True
    for l in backbone.layers:
        l.trainable = False
    for l in backbone.layers[-LAST_LAYERS:]:
        if not isinstance(l, tf.keras.layers.BatchNormalization):
            l.trainable = True
    original.compile(optimizer=tf.keras.optimizers.Adam(LR),
                     loss="sparse_categorical_crossentropy", metrics=["accuracy"])

    counts = np.array([len(images(WORK / "train" / c)) for c in CLASSES], dtype=float)
    if np.any(counts == 0):
        raise ValueError("Missing training images for one or more classes")
    weights = {i: float(v) for i, v in enumerate(np.clip(counts.sum()/(5*counts), 0.5, 3.0))}
    original.fit(train, validation_data=val, epochs=EPOCHS, class_weight=weights,
                 callbacks=[
                     tf.keras.callbacks.ModelCheckpoint(str(BEST), monitor="val_loss", save_best_only=True),
                     tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=4, restore_best_weights=True),
                     tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.2,
                                                         patience=2, min_lr=1e-7),
                 ])

    candidate = tf.keras.models.load_model(BEST, compile=False)
    candidate_val = metrics(candidate, val)
    print("Candidate validation:", candidate_val["accuracy"], candidate_val["macro_f1"])
    # Prefer improvement in macro-F1; if not, keep original as the selected v2 result.
    selected = candidate if candidate_val["macro_f1"] > original_val["macro_f1"] else tf.keras.models.load_model(SOURCE, compile=False)
    selected_type = "candidate" if selected is candidate else "original"
    print("Selected:", selected_type)
    selected.save(FINAL_KERAS)

    # Only use held-out test after selection.
    test_results = {"original": metrics(tf.keras.models.load_model(SOURCE, compile=False), test),
                    "selected": metrics(selected, test)}
    summary = {"old_data_mode": mode, "selected": selected_type,
               "validation": {"original": original_val, "candidate": candidate_val},
               "test": test_results}
    (OUT / "finetune_v2_metrics.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print("Test macro-F1 old/new:", test_results["original"]["macro_f1"], test_results["selected"]["macro_f1"])
    converter = tf.lite.TFLiteConverter.from_keras_model(selected)
    FINAL_TFLITE.write_bytes(converter.convert())
    (OUT / "labels_v2.json").write_text(json.dumps(CLASSES, indent=2), encoding="utf-8")
    print("Saved:", FINAL_KERAS, FINAL_TFLITE)
    print("NOTE: Inspect finetune_v2_metrics.json before putting new TFLite in Android.")


if __name__ == "__main__":
    main()
