# Research boundary

Source reviewed: the supplied nine-page `pusoalalay.pdf`, titled “PusoAlalay: An Exploratory Comparative Evaluation of Machine Learning and Rule-Based Screening of Artificial Disturbances in Recorded Pulse Signals,” dated 23 October 2026.

The supplied paper describes a three-recording synthetic-disturbance experiment. Its archived nearest-centroid result is F1 0.7914, balanced accuracy 0.4909, and specificity 0.1951. The rule-based comparison has balanced accuracy 0.6006. The paper explicitly says that the work does not validate heart-rate accuracy, clinical signal-quality classification, diagnosis, arrhythmia detection, or emergency prediction.

This project preserves that boundary:

- The app presents those figures only as reported/archived paper results in its research-information screen.
- It does not retrain or re-execute that experiment.
- It ships no waveform excerpt, dataset, model, classifier, or clinically defined thresholds.
- A measurement entry is a user transcription from a separate measuring device.
- A symptom entry is a self-reported note.
- Trend lines summarize only the recorded values and their timestamps; no score or medical interpretation is derived.
- No smartwatch compatibility or signal-rate assumptions are made.
- The v1.1 statistics (mean, SD, median, range, coverage, and the modified z-score "unusual compared with your earlier readings" flag) are descriptive statistics on the user's own recorded values. They are separate from the paper's classifier, use no clinical thresholds, and are presented as prompts to review, not as diagnoses or alerts. Their usefulness has not been validated.

The paper's bibliography and archived numerical results were not independently revalidated as part of building this Android journal. The source code of the paper's experiments was not attached to this task. The Android implementation is therefore separate from the paper's reproducibility package.

Future sensor/model integration requires its own explicit requirements, authentic data access and licensing, device/API compatibility checks, independently evaluated performance, and appropriate expert review. Do not turn these exploratory paper results into patient-facing warnings or reassurance.
