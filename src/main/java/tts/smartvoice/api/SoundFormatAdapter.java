package tts.smartvoice.api;

import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicReference;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.TextToSpeech;

import com.laszlosystems.libresample4j.Resampler;
import com.laszlosystems.libresample4j.SampleBuffers;

public class SoundFormatAdapter implements SynthesisCallback, SampleBuffers {

    private static final int OUTPUT_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int OUTPUT_FREQUENCY = AudioTrack.getNativeOutputSampleRate(TextToSpeech.Engine.DEFAULT_STREAM);
    private static final int OUTPUT_CHANNELS = 1;
    private static final float FULL_GAIN = 1.0f;

    private static final int SAMPLE_MIN = -32768;
    private static final int SAMPLE_MAX = 32767;

    private static final int MIN_FREQ = 8000;
    private static final int MAX_FREQ = 48000;

    private final Resampler resampler;
    private final AtomicReference<TtsEngine> speechEngine;
    private final AtomicReference<SynthesisCallback> synthesisCallback;

    private volatile int status;
    private volatile boolean isResampling;
    private volatile boolean isCanceled;

    private int nSamplesRemain;
    private int inputOffset;
    private int outputOffset;

    private AudioFilter audioFilter;
    private byte[] inBuffer;
    private byte[] outBuffer;

    private int inputFormat;
    private int inputFrequency;
    private int inputChannels;
    private float gain;


    public SoundFormatAdapter() {
        resampler = new Resampler(true, factor(MAX_FREQ), factor(MIN_FREQ));
        speechEngine = new AtomicReference<TtsEngine>();
        synthesisCallback = new AtomicReference<SynthesisCallback>();
    }


    public synchronized int init(SynthesisCallback callback) {
        isResampling = false;
        nSamplesRemain = 0;
        inputOffset = 0;
        outputOffset = 0;
        gain = FULL_GAIN;
        status = (callback != null) ? callback.start(OUTPUT_FREQUENCY, OUTPUT_FORMAT, OUTPUT_CHANNELS) : TextToSpeech.ERROR;
        isCanceled = (status != TextToSpeech.SUCCESS);
        audioFilter = null;
        inBuffer = null;
        outBuffer = isCanceled ? null : new byte[callback.getMaxBufferSize()];
        synthesisCallback.set(callback);
        return status;
    }

    public int serve(TtsEngine engine, String text) {
        speechEngine.set(engine);
        int result = engine.synthesizeText(text, this);
        speechEngine.set(null);
        return result;
    }

    public void setAudioFilter(AudioFilter filter) {
        if (filter != null) {
            filter.reset();
            filter.setSamplingFrequency(OUTPUT_FREQUENCY);
        }
        audioFilter = filter;
    }

    public void setGain(float value) {
        gain = (value < 0.0f) ? 0.0f : ((float)Math.pow(10.0, Math.min(1.25f, value) * 2.0) / 100f);
    }

    public void setFullGain() {
        gain = FULL_GAIN;
    }

    public void cancel() {
        isCanceled = true;
        TtsEngine client = speechEngine.getAndSet(null);
        if (client != null)
            client.stop();
    }

    public synchronized void finish(int state) {
        SynthesisCallback consumer = synthesisCallback.getAndSet(null);
        if (consumer != null) {
            if (state != TextToSpeech.SUCCESS) {
                consumer.error(state);
            } else if (isCanceled) {
                consumer.error(TextToSpeech.ERROR_SYNTHESIS);
            }
            status = consumer.done();
        }
        speechEngine.set(null);
        inBuffer = null;
        outBuffer = null;
    }


    @Override
    public synchronized int start(int sampleRate, int audioFormat, int channelCount) {
        if (isCanceled) {
            setStopState();
        } else if ((channelCount > 0) && (channelCount <= 2) &&
                   (sampleRate >= MIN_FREQ) && (sampleRate <= MAX_FREQ) &&
                   ((audioFormat == AudioFormat.ENCODING_PCM_16BIT) ||
                    (audioFormat == AudioFormat.ENCODING_PCM_8BIT))) {
            inputFrequency = sampleRate;
            inputFormat = audioFormat;
            inputChannels = channelCount;
        } else {
            status = TextToSpeech.ERROR;
        }
        setAudioFilter(null);
        isResampling = false;
        return status;
    }

    @Override
    public int getMaxBufferSize() {
        return (outBuffer != null) ? outBuffer.length : 0;
    }

    @Override
    public synchronized int audioAvailable(byte[] buffer, int offset, int length) {
        if (isCanceled) {
            setStopState();
        } else if ((inputFrequency != OUTPUT_FREQUENCY) ||
                   (inputFormat != OUTPUT_FORMAT) ||
                   (inputChannels != OUTPUT_CHANNELS) ||
                   (gain != FULL_GAIN)) {
            inBuffer = buffer;
            inputOffset = offset;
            nSamplesRemain = length;
            if (inputFormat == AudioFormat.ENCODING_PCM_16BIT)
                nSamplesRemain >>= 1;
            if (inputFrequency != OUTPUT_FREQUENCY) {
                while ((status == TextToSpeech.SUCCESS) && !resampler.process(factor(inputFrequency), this, false));
                isResampling = true;
            } else {
                while ((status == TextToSpeech.SUCCESS) && (getInputBufferLength() > 0)) {
                    int nSamples = Math.min(getInputBufferLength(), getOutputBufferLength());
                    outputOffset = 0;
                    for (int i = 0; i < nSamples; i++) {
                        if (isCanceled)
                            break;
                        int sample = getSample();
                        if (inputChannels > OUTPUT_CHANNELS) {
                            sample += getSample();
                            sample >>= 1;
                        }
                        if (gain != FULL_GAIN) {
                            putSample(toFloat(sample) * gain);
                        } else {
                            putSample((audioFilter != null) ? audioFilter.process(sample) : sample);
                        }
                    }
                    if (isCanceled) {
                        setStopState();
                        break;
                    }
                    if (outputOffset > 0) {
                        SynthesisCallback consumer = synthesisCallback.get();
                        status = (consumer != null) ? consumer.audioAvailable(outBuffer, 0, outputOffset) : TextToSpeech.ERROR;
                    }
                }
            }
        } else if (length > 0) {
            if (audioFilter != null)
                audioFilter.apply(buffer, offset, length);
            SynthesisCallback consumer = synthesisCallback.get();
            status = (consumer != null) ? consumer.audioAvailable(buffer, offset, length) : TextToSpeech.ERROR;
        }
        if (status != TextToSpeech.SUCCESS)
            isCanceled = true;
        inBuffer = null;
        return status;
    }

    @Override
    public synchronized int done() {
        if (isResampling) {
            while (!resampler.process(factor(inputFrequency), this, true));
            isResampling = false;
        }
        return status;
    }

    @Override
    public void error() {
        status = TextToSpeech.ERROR;
        isCanceled = true;
    }

    @Override
    public void error(int errorCode) {
        status = errorCode;
        isCanceled = true;
    }

    @Override
    public boolean hasStarted() {
        return hasStarted(synthesisCallback.get());
    }

    @Override
    public boolean hasFinished() {
        return hasFinished(synthesisCallback.get());
    }


    @Override
    public int getInputBufferLength() {
        return (inBuffer != null) ? (nSamplesRemain / inputChannels) : 0;
    }

    @Override
    public int getOutputBufferLength() {
        return getMaxBufferSize() / 2;
    }

    @Override
    public void produceInput(float[] array, int offset, int length) {
        for (int i = offset; i < (offset + length); i++) {
            int sample = getSample();
            if (inputChannels > OUTPUT_CHANNELS) {
                sample += getSample();
                array[i] = toFloat(sample) * gain / 2.0f;
            } else {
                array[i] = toFloat(sample) * gain;
            }
        }
    }

    @Override
    public void consumeOutput(float[] array, int offset, int length) {
        SynthesisCallback consumer = synthesisCallback.get();
        if (isRunning(consumer) && (status == TextToSpeech.SUCCESS) && (length > 0)) {
            outputOffset = 0;
            for (int i = offset; i < (offset + length); i++) {
                if (isCanceled) {
                    setStopState();
                    return;
                }
                putSample(array[i]);
            }
            status = consumer.audioAvailable(outBuffer, 0, outputOffset);
        }
    }


    static float toFloat(int sample) {
        return ((float) sample) / ((float) (-SAMPLE_MIN));
    }

    static int fromFloat(float sample) {
        return Math.round(sample * ((float) SAMPLE_MAX));
    }

    static int decodeSample(byte[] buffer, int offset) {
        int sample = (int) buffer[offset++];
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            sample &= 0xFF;
            sample += ((int) buffer[offset]) << 8;
        } else {
            sample <<= 8;
            sample += ((int) buffer[offset]) & 0xFF;
        }
        return sample;
    }

    static void encodeSample(int value, byte[] buffer, int offset) {
        if (value < SAMPLE_MIN)
            value = SAMPLE_MIN;
        else if (value > SAMPLE_MAX)
            value = SAMPLE_MAX;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            buffer[offset++] = (byte)(value & 0xFF);
            buffer[offset] = (byte)((value >> 8) & 0xFF);
        } else {
            buffer[offset++] = (byte)((value >> 8) & 0xFF);
            buffer[offset] = (byte)(value & 0xFF);
        }
    }


    private int getSample() {
        int sample;
        if (inputFormat != OUTPUT_FORMAT) {
            sample = (int)inBuffer[inputOffset++];
            sample &= 0xFF;
            sample -= 128;
            sample <<= 8;
        } else {
            sample = decodeSample(inBuffer, inputOffset);
            inputOffset += 2;
        }
        nSamplesRemain--;
        return sample;
    }

    private void putSample(int value) {
        encodeSample(value, outBuffer, outputOffset);
        outputOffset += 2;
    }

    private void putSample(float value) {
        float x = (audioFilter != null) ? audioFilter.process(value) : value;
        int sample = fromFloat(x);
        putSample(sample);
    }

    private double factor(int frequency) {
        return ((double)OUTPUT_FREQUENCY) / ((double)frequency);
    }

    private boolean hasStarted(SynthesisCallback consumer) {
        return (consumer != null) && consumer.hasStarted();
    }

    private boolean hasFinished(SynthesisCallback consumer) {
        return (consumer != null) && consumer.hasFinished();
    }

    private boolean isRunning(SynthesisCallback consumer) {
        return (consumer != null) && consumer.hasStarted() && !consumer.hasFinished();
    }

    private void setStopState() {
        if (status == TextToSpeech.SUCCESS)
            status = TextToSpeech.STOPPED;
    }

}
