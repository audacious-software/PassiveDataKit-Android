//
// Created by Chris Karr on 2020-01-22.
//

#include <jni.h>
#include <string>
#include <iostream>

#include <android/log.h>

#include "VadUtil.h"

#include "webrtc/common_audio/vad/include/webrtc_vad.h"
#include "webrtc/common_audio/vad/include/vad.h"

#define SMOOTHING_MEDIAN 0
#define SMOOTHING_MEAN 1

int compare (const void * a, const void * b) {
    return ( *(int*)a - *(int*)b );
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_audacious_1software_passive_1data_1kit_generators_environment_VoiceActivityGenerator_00024VadUtil_detectVoice(
        JNIEnv *env, jobject thiz, jbyteArray samples, jint sampleCount, jint sampleRateHz, jint smoothMode, jint windowSamples) {
    VadInst* vadInst = WebRtcVad_Create();

    jboolean isCopy;

    const int16_t * rawSamples = (const int16_t *) env->GetByteArrayElements(samples, &isCopy);

    int16_t * smoothedSamples = (int16_t *) malloc(sampleCount * sizeof(int16_t));

    int halfWindow = windowSamples / 2;

    for (unsigned int i = 0; i < sampleCount; i++) {
        int16_t preCount = 0;

        if (smoothMode == SMOOTHING_MEAN) {
            int16_t sum = rawSamples[i];

            for (int j = i - 1; j > 0 && preCount < halfWindow; j--) {
                sum += rawSamples[j];
                preCount += 1;
            }

            int16_t postCount = 0;

            for (int j = i + 1; j < sampleCount && postCount < halfWindow; j++) {
                sum += rawSamples[j];
                postCount += 1;
            }

            smoothedSamples[i] = (int16_t) (sum / (preCount + postCount + 1));
        } else if (smoothMode == SMOOTHING_MEDIAN) {
            int16_t * values = (int16_t *) malloc(sizeof(int16_t) * windowSamples);

            memset(values, rawSamples[i], sizeof(int16_t));

            for (int j = i - 1; j > 0 && preCount < halfWindow; j--) {
                values[i - j] = rawSamples[j];

                preCount += 1;
            }

            int16_t postCount = 0;

            for (int j = i + 1; j < sampleCount && postCount < halfWindow; j++) {
                values[j - i] = rawSamples[j];

                postCount += 1;
            }

            qsort(values, (size_t) windowSamples, sizeof(int16_t), compare);

            smoothedSamples[i] = values[halfWindow];
        }
    }

    env->ReleaseByteArrayElements(samples, (jbyte *) rawSamples, JNI_ABORT);

    int result = WebRtcVad_Init(vadInst);

    if (result == 1) {
        __android_log_print(ANDROID_LOG_ERROR, "PDK", "Unable to init VadInst.");
    } else if (result == 0){
        // __android_log_print(ANDROID_LOG_ERROR, "PDK", "Inited VadInst.");

        result = WebRtcVad_set_mode(vadInst, webrtc::Vad::Aggressiveness::kVadVeryAggressive);

        if (result == 1) {
            __android_log_print(ANDROID_LOG_ERROR, "PDK", "Unable to set mode on VadInst.");
        } else if (result == 0) {
            // __android_log_print(ANDROID_LOG_ERROR, "PDK", "Set mode on VadInst.");

            // Checks for valid combinations of |rate| and |frame_length|. We support 10,
            // 20 and 30 ms frames and the rates 8000, 16000 and 32000 Hz.
            //
            // - rate         [i] : Sampling frequency (Hz).
            // - frame_length [i] : Speech frame buffer length in number of samples.
            //
            // returns            : 0 - (valid combination), -1 - (invalid combination)
            // int WebRtcVad_ValidRateAndFrameLength(int rate, size_t frame_length);

            int frameLength = (sampleRateHz / 1000) * 30;

            result = WebRtcVad_ValidRateAndFrameLength((int) sampleRateHz, frameLength);

            if (result == 0) {
                // __android_log_print(ANDROID_LOG_ERROR, "PDK", "VadInst valid frame config.");
            } else if (result == 1) {
                __android_log_print(ANDROID_LOG_ERROR, "PDK", "VadInst invalid frame config.");
            }

            // Calculates a VAD decision for the |audio_frame|. For valid sampling rates
            // frame lengths, see the description of WebRtcVad_ValidRatesAndFrameLengths().
            //
            // - handle       [i/o] : VAD Instance. Needs to be initialized by
            //                        WebRtcVad_Init() before call.
            // - fs           [i]   : Sampling frequency (Hz): 8000, 16000, or 32000
            // - audio_frame  [i]   : Audio frame buffer.
            // - frame_length [i]   : Length of audio frame buffer in number of samples.
            //
            // returns              : 1 - (Active Voice),
            //                        0 - (Non-active Voice),
            //                       -1 - (Error)
            //            int WebRtcVad_Process(VadInst* handle,
            //                                  int fs,
            //                                  const int16_t* audio_frame,
            //                                  size_t frame_length);
            //

            int16_t * frame = &(smoothedSamples[(sampleCount - frameLength) / 2]);

            result = WebRtcVad_Process(vadInst, sampleRateHz, frame, frameLength);

            if (result == 0) {
                // __android_log_print(ANDROID_LOG_ERROR, "PDK", "VadInst provided result: No voice.");
            } else if (result == 1) {
                // __android_log_print(ANDROID_LOG_ERROR, "PDK", "VadInst provided result: Voice present.");
            } else {
                __android_log_print(ANDROID_LOG_ERROR, "PDK", "VadInst provided result: Error.");
            }
        }
    }

    WebRtcVad_Free(vadInst);

    free(smoothedSamples);

    // __android_log_print(ANDROID_LOG_ERROR, "PDK", "Freed VadInst.");

    return result;
}