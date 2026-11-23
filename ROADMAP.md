# Remaining product milestones (no more blind V6 fine-tuning cycles)

1. **MVP (this ZIP):** Kotlin verified solvers, OCR, confirmed fields, offline UI. Benchmarks focus on solver correctness and fails-closed cases.
2. **Phone inference:** Add llama.cpp Android JNI/NDK integration for Qwen3-1.7B Q4_K_M model. Load from app-private storage; test on actual 6 GB RAM phone. Extract JSON only, never trust numeric calculation from the model. Keep the confirmed-value gate.
3. **Diagram understanding:** Evaluate compact on-device VLM or dedicated scientific diagram extraction. Must distinguish OCR from actual diagram interpretation; ask for user corrections where uncertain.
4. **Offline vetted knowledge:** Import owned/licensed NCERT-aligned chapter content into SQLite; include source attribution and retrieval. Confirm redistribution rights.
5. **Release gates:** locked independent items, subject coverage, structured extraction agreement, explanation consistency, peak RAM, latency, airplane-mode and crash testing.
6. **Optional new LoRA:** only if specific measurable failure cannot be fixed by parser, solver or knowledge retrieval.
