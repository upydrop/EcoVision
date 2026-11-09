# Waste Classification — Test Report

**Date:** 2026-11-09 09:42:25  
**Model:** `PythonModel/waste_classifier.tflite`  
**Test images:** 100  
**Classes:** glass, metal, paper, plastic, trash

## 1. Pareizo prognožu īpatsvars (Accuracy)

Accuracy parāda, cik liela daļa no visām prognozēm bija pareizas.

**Formula:** Accuracy = Pareizās prognozes / Visas prognozes

**Aprēķins:** 93 / 100 = **93.00%**

## 2. Confusion Matrix

Rindas = patiesās klases (Actual). Kolonnas = modeļa prognozes (Predicted).

| Actual / Predicted | glass | metal | paper | plastic | trash |
|---|---:|---:|---:|---:|---:|
| **glass** | 18 | 1 | 0 | 1 | 0 |
| **metal** | 0 | 19 | 1 | 0 | 0 |
| **paper** | 0 | 0 | 19 | 1 | 0 |
| **plastic** | 1 | 0 | 0 | 18 | 1 |
| **trash** | 1 | 0 | 0 | 0 | 19 |

Diagonāles vērtības norāda pareizās prognozes. Pārējās vērtības norāda kļūdas.

## 3. Precizitāte (Precision)

Precision parāda, cik no objektiem, kurus modelis pieskaitīja konkrētai klasei, patiešām pieder šai klasei.

**Formula:** Precision = TP / (TP + FP)

TP = True Positive, FP = False Positive.

| Klase | TP | FP | Aprēķins | Precision |
|---|---:|---:|---|---:|
| glass | 18 | 2 | 18/20 | 90.00% |
| metal | 19 | 1 | 19/20 | 95.00% |
| paper | 19 | 1 | 19/20 | 95.00% |
| plastic | 18 | 2 | 18/20 | 90.00% |
| trash | 19 | 1 | 19/20 | 95.00% |

**Macro Precision: 93.00%**

## 4. Jutība (Recall)

Recall parāda, cik no visiem konkrētās klases objektiem modelis pareizi atpazina.

**Formula:** Recall = TP / (TP + FN)

TP = True Positive, FN = False Negative.

| Klase | TP | FN | Aprēķins | Recall |
|---|---:|---:|---|---:|
| glass | 18 | 2 | 18/20 | 90.00% |
| metal | 19 | 1 | 19/20 | 95.00% |
| paper | 19 | 1 | 19/20 | 95.00% |
| plastic | 18 | 2 | 18/20 | 90.00% |
| trash | 19 | 1 | 19/20 | 95.00% |

**Macro Recall: 93.00%**

## 5. F1-score

F1-score apvieno Precision un Recall vienā rādītājā.

**Formula:** F1 = 2 × Precision × Recall / (Precision + Recall)

| Klase | Precision | Recall | F1-score | Attēli |
|---|---:|---:|---:|---:|
| glass | 90.00% | 90.00% | 90.00% | 20 |
| metal | 95.00% | 95.00% | 95.00% | 20 |
| paper | 95.00% | 95.00% | 95.00% | 20 |
| plastic | 90.00% | 90.00% | 90.00% | 20 |
| trash | 95.00% | 95.00% | 95.00% | 20 |

**Macro F1-score: 93.00%**

## 6. Nepareizās prognozes (Errors)

**Kļūdu skaits:** 7

| Attēls | Patiesā klase | Prognoze | Score |
|---|---|---|---:|
| `test/glass/0a03e1abddfde7fb459220a2.jpg` | glass | metal | 93.53% |
| `test/glass/1b0242850c065183f2b265d3.jpg` | glass | plastic | 87.87% |
| `test/metal/0a2e4b041ae91bc2ec05a206.jpg` | metal | paper | 94.60% |
| `test/paper/0bdc7f6b123aac8556ae1246.jpg` | paper | plastic | 77.64% |
| `test/plastic/0a259b9e581cec3ba96ba56e.jpg` | plastic | trash | 83.90% |
| `test/plastic/0b0347a09824a9920cafdd51.jpg` | plastic | glass | 49.06% |
| `test/trash/1bdb33820b7bffc4d600f956.jpg` | trash | glass | 93.50% |

## 7. Visas prognozes (All Predictions)

| # | Attēls | Actual | Predicted | Score | Result |
|---:|---|---|---|---:|---|
| 1 | `test/glass/01b4f020cabd5c33d5a57848.jpg` | glass | glass | 100.00% | Correct |
| 2 | `test/glass/0a03e1abddfde7fb459220a2.jpg` | glass | metal | 93.53% | Wrong |
| 3 | `test/glass/0a69aca5ec50cde24cb19d2d.jpg` | glass | glass | 90.66% | Correct |
| 4 | `test/glass/0b24633bcfe45b8c7ff2d64b.jpg` | glass | glass | 99.92% | Correct |
| 5 | `test/glass/0bf023e8d79e53746901c849.jpg` | glass | glass | 96.07% | Correct |
| 6 | `test/glass/0c8262c2f29c3acfb64422ce.jpg` | glass | glass | 99.49% | Correct |
| 7 | `test/glass/0e3589c288a388b729cf84cb.jpg` | glass | glass | 99.83% | Correct |
| 8 | `test/glass/0f7193f06211e28ce663f525.jpg` | glass | glass | 100.00% | Correct |
| 9 | `test/glass/1a35386d5bf17ab73ef3ead6.jpg` | glass | glass | 91.72% | Correct |
| 10 | `test/glass/1aa33cc9b8206a4b81e95575.jpg` | glass | glass | 52.25% | Correct |
| 11 | `test/glass/1b0242850c065183f2b265d3.jpg` | glass | plastic | 87.87% | Wrong |
| 12 | `test/glass/1c640980872670517ae9dac7.jpg` | glass | glass | 99.86% | Correct |
| 13 | `test/glass/1ce58ccab0e350dc38739f7c.jpg` | glass | glass | 99.92% | Correct |
| 14 | `test/glass/1d1aaf7bfac9dfb27b3703b5.jpg` | glass | glass | 99.99% | Correct |
| 15 | `test/glass/1deb36f01823b9b4f1143b0e.jpg` | glass | glass | 100.00% | Correct |
| 16 | `test/glass/1ed8bda8569127c306a048d5.jpg` | glass | glass | 99.59% | Correct |
| 17 | `test/glass/1ee95e06fc2747b2b87f26c8.jpg` | glass | glass | 99.98% | Correct |
| 18 | `test/glass/1f19da5bc22ec90742e10f67.jpg` | glass | glass | 89.40% | Correct |
| 19 | `test/glass/1f6060263694fee2ad3aa39c.jpg` | glass | glass | 92.98% | Correct |
| 20 | `test/glass/2acef52c403b6b400a99966e.jpg` | glass | glass | 92.18% | Correct |
| 21 | `test/metal/01fd8cfd666c27f05d63f87a.jpg` | metal | metal | 93.82% | Correct |
| 22 | `test/metal/0a2e4b041ae91bc2ec05a206.jpg` | metal | paper | 94.60% | Wrong |
| 23 | `test/metal/0a367e4443ad49fc63fc1000.jpg` | metal | metal | 92.03% | Correct |
| 24 | `test/metal/0a8fb9adb54b5a9e3be407ca.jpg` | metal | metal | 94.81% | Correct |
| 25 | `test/metal/0b1f5dd7efb69805e1d4d90f.jpg` | metal | metal | 99.96% | Correct |
| 26 | `test/metal/0c241bcdea264c920d77f34f.jpg` | metal | metal | 99.52% | Correct |
| 27 | `test/metal/0d1301ea76543944685575ad.jpg` | metal | metal | 100.00% | Correct |
| 28 | `test/metal/0d46dd84904d1c9c5e80aea2.jpg` | metal | metal | 100.00% | Correct |
| 29 | `test/metal/0d7c6356786a169547c88a50.jpg` | metal | metal | 100.00% | Correct |
| 30 | `test/metal/0e450b81d3c3a2f78d653a61.jpg` | metal | metal | 100.00% | Correct |
| 31 | `test/metal/0e9f72ec7d469da0412d7793.jpg` | metal | metal | 88.60% | Correct |
| 32 | `test/metal/0fbba5809d8eeaf7ab980ce7.jpg` | metal | metal | 99.95% | Correct |
| 33 | `test/metal/0fd71c2e973de5faf2990665.jpg` | metal | metal | 99.99% | Correct |
| 34 | `test/metal/1a8179b73b5bed6a64678c83.jpg` | metal | metal | 94.62% | Correct |
| 35 | `test/metal/1ac43424f74276c3ea1c27c2.jpg` | metal | metal | 99.99% | Correct |
| 36 | `test/metal/1c6f5a925bf4513a67bf2e10.jpg` | metal | metal | 79.74% | Correct |
| 37 | `test/metal/1ceb38a5b9689589f5d05105.jpg` | metal | metal | 99.36% | Correct |
| 38 | `test/metal/1d38dff27fc59eb51d6219c5.jpg` | metal | metal | 98.34% | Correct |
| 39 | `test/metal/1d65f8094fc5064363a3f390.jpg` | metal | metal | 99.99% | Correct |
| 40 | `test/metal/1d77dcf8a3856c97c8555418.jpg` | metal | metal | 99.98% | Correct |
| 41 | `test/paper/00e3a80c509f3ce12a8cba13.jpg` | paper | paper | 98.32% | Correct |
| 42 | `test/paper/01e2e35586523c096d2b2c12.png` | paper | paper | 99.59% | Correct |
| 43 | `test/paper/0a167300427aa2d343756011.jpg` | paper | paper | 100.00% | Correct |
| 44 | `test/paper/0a549e776be3376cfaad6be8.jpg` | paper | paper | 99.47% | Correct |
| 45 | `test/paper/0a7dfcf68da63c27c1239e1d.jpg` | paper | paper | 97.21% | Correct |
| 46 | `test/paper/0ae04a1c6395769792edd0af.jpg` | paper | paper | 95.94% | Correct |
| 47 | `test/paper/0b10a1d8d6c09965c27c30e4.jpg` | paper | paper | 99.96% | Correct |
| 48 | `test/paper/0bdc7f6b123aac8556ae1246.jpg` | paper | plastic | 77.64% | Wrong |
| 49 | `test/paper/0de99ccf58fd88570e851dce.jpg` | paper | paper | 99.91% | Correct |
| 50 | `test/paper/0e150b91628826944125a547.jpg` | paper | paper | 99.99% | Correct |
| 51 | `test/paper/0e59c74d7c807699786867d3.jpg` | paper | paper | 99.28% | Correct |
| 52 | `test/paper/0ec8db6f79c2b897bff7be4f.jpg` | paper | paper | 99.80% | Correct |
| 53 | `test/paper/0f3fcd27d7c87db2fb39bfcb.jpg` | paper | paper | 98.51% | Correct |
| 54 | `test/paper/0f8a442c5a8f2bb8275b517f.jpg` | paper | paper | 99.96% | Correct |
| 55 | `test/paper/0fa024dab34a3f87fa0fd523.jpg` | paper | paper | 93.91% | Correct |
| 56 | `test/paper/0fb2cbabd4ebf44831d9f3e8.jpg` | paper | paper | 99.35% | Correct |
| 57 | `test/paper/1a7855a0f2cde76568e610e3.jpg` | paper | paper | 59.52% | Correct |
| 58 | `test/paper/milkbox (62).jpg` | paper | paper | 97.78% | Correct |
| 59 | `test/paper/milkbox (71).jpg` | paper | paper | 97.80% | Correct |
| 60 | `test/paper/napkin (131).jpg` | paper | paper | 85.50% | Correct |
| 61 | `test/plastic/00b9ab5dd70cfbee9c017c93.jpg` | plastic | plastic | 99.97% | Correct |
| 62 | `test/plastic/00e0bbcc0a75007fb79cb8e2.jpg` | plastic | plastic | 99.99% | Correct |
| 63 | `test/plastic/0a259b9e581cec3ba96ba56e.jpg` | plastic | trash | 83.90% | Wrong |
| 64 | `test/plastic/0a848e8a2489469a51db3365.jpg` | plastic | plastic | 99.97% | Correct |
| 65 | `test/plastic/0a907b36863ff12caac662cf.jpg` | plastic | plastic | 99.99% | Correct |
| 66 | `test/plastic/0ab0cc6ad13cc051437180df.jpg` | plastic | plastic | 91.64% | Correct |
| 67 | `test/plastic/0abf380d618232fef1dc7248.jpg` | plastic | plastic | 96.81% | Correct |
| 68 | `test/plastic/0abfcde08f966e60b52344a9.jpg` | plastic | plastic | 99.98% | Correct |
| 69 | `test/plastic/0b0347a09824a9920cafdd51.jpg` | plastic | glass | 49.06% | Wrong |
| 70 | `test/plastic/0b0f53f2eb2b1e1012e83863.jpg` | plastic | plastic | 91.09% | Correct |
| 71 | `test/plastic/0bb43bfff915625a32e00284.jpg` | plastic | plastic | 99.98% | Correct |
| 72 | `test/plastic/0be674a788c5763eb33d226c.jpg` | plastic | plastic | 95.60% | Correct |
| 73 | `test/plastic/0bf60b1617c4a2a80d5f63d8.jpg` | plastic | plastic | 74.19% | Correct |
| 74 | `test/plastic/0c65f93fbb46abaf1a312975.jpg` | plastic | plastic | 98.46% | Correct |
| 75 | `test/plastic/0ca4977b69702d3f6193a38d.jpg` | plastic | plastic | 99.45% | Correct |
| 76 | `test/plastic/0cb3b06fe1f91144e54652e6.jpg` | plastic | plastic | 99.99% | Correct |
| 77 | `test/plastic/0d4e6b02e3f2efa7c2664ab0.jpg` | plastic | plastic | 99.82% | Correct |
| 78 | `test/plastic/0e5498eeb7c54c88280aacb7.jpg` | plastic | plastic | 81.72% | Correct |
| 79 | `test/plastic/0ff91d24198877a665c28aff.jpg` | plastic | plastic | 99.98% | Correct |
| 80 | `test/plastic/27bd9587fb6c15e1e769fde3.jpg` | plastic | plastic | 99.77% | Correct |
| 81 | `test/trash/01ceab11e242a15215d5aee3.jpg` | trash | trash | 100.00% | Correct |
| 82 | `test/trash/0a2b9ae89e5afce774d751d9.jpg` | trash | trash | 99.97% | Correct |
| 83 | `test/trash/0aa35516f84a50aa0223fe2e.jpg` | trash | trash | 99.98% | Correct |
| 84 | `test/trash/0b0ae1bcb43a7485aed8c01b.jpg` | trash | trash | 100.00% | Correct |
| 85 | `test/trash/0b7d780249beb94368025e21.jpg` | trash | trash | 100.00% | Correct |
| 86 | `test/trash/0bf834ae97b7dbd50b42028f.jpg` | trash | trash | 99.98% | Correct |
| 87 | `test/trash/0c1bd89c0901e46867d28c2c.jpg` | trash | trash | 100.00% | Correct |
| 88 | `test/trash/0c927ab12a5e02f60a865208.jpg` | trash | trash | 99.45% | Correct |
| 89 | `test/trash/0d4cb15192ccef82cb7fe696.jpg` | trash | trash | 99.75% | Correct |
| 90 | `test/trash/0d946f80faa12992ffa284bd.jpg` | trash | trash | 100.00% | Correct |
| 91 | `test/trash/0e82d44276d8a12d95942662.jpg` | trash | trash | 99.99% | Correct |
| 92 | `test/trash/0ed739021adcb8b9cf24850b.jpg` | trash | trash | 100.00% | Correct |
| 93 | `test/trash/1a82be3ef42f82f0eeb8fef4.jpg` | trash | trash | 99.97% | Correct |
| 94 | `test/trash/1b785cc1573e741065b5e839.jpg` | trash | trash | 71.36% | Correct |
| 95 | `test/trash/1bdb33820b7bffc4d600f956.jpg` | trash | glass | 93.50% | Wrong |
| 96 | `test/trash/1df68621ba760447f80ae6e6.jpg` | trash | trash | 99.36% | Correct |
| 97 | `test/trash/2c9b4f49cb1776f7daa1922e.jpg` | trash | trash | 99.94% | Correct |
| 98 | `test/trash/bandaid (7).jpeg` | trash | trash | 80.47% | Correct |
| 99 | `test/trash/bowlsanddishes (1).png` | trash | trash | 99.98% | Correct |
| 100 | `test/trash/bowlsanddishes (183).JPG` | trash | trash | 99.69% | Correct |
