
import tensorflow as tf
import numpy as np
from PIL import Image
import json

MODEL_PATH = "outputs/waste_classifier.tflite"
IMAGE_PATH = "plastic_test.jpg"
LABELS_PATH = "outputs/labels.json"

# Загружаем названия классов
with open(LABELS_PATH, "r") as f:
    labels = json.load(f)

# Загружаем модель
interpreter = tf.lite.Interpreter(
    model_path=MODEL_PATH,
    num_threads=4
)

interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

# Проверяем вход модели
print("Input shape:", input_details[0]["shape"])
print("Input dtype:", input_details[0]["dtype"])
print("Output shape:", output_details[0]["shape"])

# Загружаем фотографию
image = Image.open(IMAGE_PATH).convert("RGB")

# Точно как в Android
image = image.resize((224, 224), Image.Resampling.BILINEAR)

# Преобразуем в массив RGB Float32
input_data = np.array(image, dtype=np.float32)

# Добавляем batch dimension
input_data = np.expand_dims(input_data, axis=0)

# Проверяем совместимость
if tuple(input_data.shape) != tuple(input_details[0]["shape"]):
    raise ValueError("Image shape does not match model input")

if input_details[0]["dtype"] != np.float32:
    raise ValueError("Model input is not Float32")

# Запускаем классификацию
interpreter.set_tensor(
    input_details[0]["index"],
    input_data
)

interpreter.invoke()

# Получаем результат
output = interpreter.get_tensor(
    output_details[0]["index"]
)[0]

# Выводим результаты
print("\n========== WASTE CLASSIFICATION ==========")

for i in np.argsort(output)[::-1]:
    print(f"{labels[i]}: {output[i]:.4f}")

best_index = np.argmax(output)

print("\n========== FINAL RESULT ==========")
print("Class:", labels[best_index])
print("Confidence:", f"{output[best_index] * 100:.2f}%")
