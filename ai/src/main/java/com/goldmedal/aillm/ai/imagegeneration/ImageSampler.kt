package com.goldmedal.aillm.ai.imagegeneration

/**
 * The sampling method an image model expects.
 *
 * A model is not just weights: the sampler it was distilled with is part of how
 * it works. A full-step SD 1.5 checkpoint is sampled with DPM++ 2M Karras over
 * ~25 steps, while an LCM-distilled checkpoint reaches the same place in 4–8
 * steps with the LCM sampler and a guidance near 1. Without this distinction a
 * fast checkpoint would be sampled the slow way and come out soft and noisy.
 */
enum class ImageSampler {
    /** Whatever stable-diffusion.cpp picks for the loaded model. */
    DEFAULT,

    /** Latent Consistency Models — few steps, guidance ≈ 1. */
    LCM
}
