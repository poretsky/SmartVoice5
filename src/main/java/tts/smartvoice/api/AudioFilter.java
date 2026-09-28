package tts.smartvoice.api;

public abstract class AudioFilter {

    public abstract void reset();

    public abstract void setSamplingFrequency(int frequency);

    public abstract float process(float sample);


    int process(int sample) {
        return SoundFormatAdapter.fromFloat(process(SoundFormatAdapter.toFloat(sample)));
    }

    void apply(byte[] buffer, int offset, int length) {
        int limit = offset + length;
        for (int i = offset; i < limit; i += 2) {
            SoundFormatAdapter.encodeSample(process(SoundFormatAdapter.decodeSample(buffer, i)), buffer, i);
        }
    }

}
