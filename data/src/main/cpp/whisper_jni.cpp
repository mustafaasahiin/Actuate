#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static size_t asset_read(void *ctx, void *output, size_t read_size) {
    return (size_t) AAsset_read((AAsset *) ctx, output, read_size);
}

static bool asset_is_eof(void *ctx) {
    return AAsset_getRemainingLength64((AAsset *) ctx) <= 0;
}

static void asset_close(void *ctx) {
    AAsset_close((AAsset *) ctx);
}

static jlong native_init_context_from_asset(JNIEnv *env, jobject assetManager, jstring model_path_str) {
    if (!assetManager || !model_path_str) {
        LOGE("Null assetManager or model path passed");
        return 0;
    }

    const char *model_path = env->GetStringUTFChars(model_path_str, NULL);
    LOGI("Loading whisper model from asset: %s", model_path);

    AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) {
        LOGE("Failed to obtain AAssetManager from Java");
        env->ReleaseStringUTFChars(model_path_str, model_path);
        return 0;
    }

    AAsset *asset = AAssetManager_open(mgr, model_path, AASSET_MODE_STREAMING);
    if (!asset) {
        LOGE("Failed to open asset '%s'", model_path);
        env->ReleaseStringUTFChars(model_path_str, model_path);
        return 0;
    }

    struct whisper_model_loader loader = {
        /* .context = */ asset,
        /* .read    = */ &asset_read,
        /* .eof     = */ &asset_is_eof,
        /* .close   = */ &asset_close
    };

    struct whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;

    struct whisper_context *ctx = whisper_init_with_params(&loader, params);
    env->ReleaseStringUTFChars(model_path_str, model_path);

    if (!ctx) {
        LOGE("whisper_init_with_params failed for asset '%s'", model_path);
        return 0;
    }

    LOGI("Successfully initialized whisper context at %p", ctx);
    return (jlong) ctx;
}

static void native_free_context(jlong context_ptr) {
    if (context_ptr != 0) {
        struct whisper_context *ctx = (struct whisper_context *) context_ptr;
        LOGI("Freeing whisper context at %p", ctx);
        whisper_free(ctx);
    }
}

static jstring native_transcribe_pcm(JNIEnv *env, jlong context_ptr, jfloatArray audio_data, jint num_threads, jstring language_str) {
    if (context_ptr == 0 || !audio_data) {
        return env->NewStringUTF("");
    }

    struct whisper_context *ctx = (struct whisper_context *) context_ptr;
    jfloat *samples = env->GetFloatArrayElements(audio_data, NULL);
    jsize n_samples = env->GetArrayLength(audio_data);

    std::string lang = "auto";
    if (language_str) {
        const char *lang_chars = env->GetStringUTFChars(language_str, NULL);
        if (lang_chars) {
            lang = lang_chars;
            env->ReleaseStringUTFChars(language_str, lang_chars);
        }
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.translate        = false;
    params.language         = lang.c_str();
    params.n_threads        = (num_threads > 0) ? num_threads : 4;
    params.no_context       = true;
    params.single_segment   = false;

    int ret = whisper_full(ctx, params, samples, n_samples);
    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);

    if (ret != 0) {
        LOGE("whisper_full returned error code: %d", ret);
        return env->NewStringUTF("");
    }

    std::string result_text;
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) {
            result_text += text;
        }
    }

    return env->NewStringUTF(result_text.c_str());
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_actuate_data_speech_whisper_WhisperLib_initContextFromAsset(
        JNIEnv *env, jobject thiz, jobject assetManager, jstring model_path_str) {
    return native_init_context_from_asset(env, assetManager, model_path_str);
}

JNIEXPORT jlong JNICALL
Java_com_actuate_data_speech_whisper_WhisperLib_00024Companion_initContextFromAsset(
        JNIEnv *env, jobject thiz, jobject assetManager, jstring model_path_str) {
    return native_init_context_from_asset(env, assetManager, model_path_str);
}

JNIEXPORT void JNICALL
Java_com_actuate_data_speech_whisper_WhisperLib_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    native_free_context(context_ptr);
}

JNIEXPORT void JNICALL
Java_com_actuate_data_speech_whisper_WhisperLib_00024Companion_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    native_free_context(context_ptr);
}

JNIEXPORT jstring JNICALL
Java_com_actuate_data_speech_whisper_WhisperLib_transcribePcm(
        JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray audio_data, jint num_threads, jstring language_str) {
    return native_transcribe_pcm(env, context_ptr, audio_data, num_threads, language_str);
}

JNIEXPORT jstring JNICALL
Java_com_actuate_data_speech_whisper_WhisperLib_00024Companion_transcribePcm(
        JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray audio_data, jint num_threads, jstring language_str) {
    return native_transcribe_pcm(env, context_ptr, audio_data, num_threads, language_str);
}

}
