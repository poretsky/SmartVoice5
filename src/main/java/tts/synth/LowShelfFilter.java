package tts.synth;

import tts.smartvoice.api.AudioFilter;

class LowShelfFilter extends AudioFilter {

    private final float attenuation;
    private final float transitionFrequency;
    private final float slope;
    private final float gain;

    private boolean contrastEnhancement;

    private double mFa;
    private double mFb;
    private double mFc;
    private double mFd;
    private double mFe;

    private double x0;
    private double x1;
    private double x2;

    private double out0;
    private double out1;
    private double out2;


    LowShelfFilter(float attenuation, float transitionFrequency, float slope, float gain) {
        this.attenuation = attenuation;
        this.transitionFrequency = transitionFrequency;
        this.slope = slope;
        this.gain = gain;
        contrastEnhancement = false;
    }

    void enableContrastEnhancement(boolean enabled) {
        contrastEnhancement = enabled;
    }

    @Override
    public void reset() {
        x0 = 0.0f;
        x1 = 0.0f;
        x2 = 0.0f;
        out0 = 0.0f;
        out1 = 0.0f;
        out2 = 0.0f;
    }

    @Override
    public void setSamplingFrequency(int frequency) {
        double amp = Math.pow(10.0, attenuation / 40.0);
        double w = 2.0 * Math.PI * (transitionFrequency / frequency);
        double sinw = Math.sin(w);
        double cosw = Math.cos(w);
        double beta = Math.sqrt(amp) / slope;

        double b0 = amp * ((amp + 1.0) - ((amp - 1.0) * cosw) + (beta * sinw));
        double b1 = 2.0 * amp * ((amp - 1.0) - ((amp + 1.0) * cosw));
        double b2 = amp * ((amp + 1.0) - ((amp - 1.0) * cosw) - (beta * sinw));
        double a0 = (amp + 1.0) + ((amp - 1.0) * cosw) + (beta * sinw);
        double a1 = 2.0 * ((amp - 1.0) + ((amp + 1.0) * cosw));
        double a2 = -((amp + 1.0) + ((amp - 1.0) * cosw) - (beta * sinw));

        mFa = gain * b0 / a0;
        mFb = gain * b1 / a0;
        mFc = gain * b2 / a0;
        mFd = a1 / a0;
        mFe = a2 / a0;
    }

    @Override
    public float process(float sample) {
        x0 = sample;
        out0 = (mFa * x0) + (mFb * x1) + (mFc * x2) + (mFd * out1) + (mFe * out2);
        x2 = x1;
        x1 = x0;
        out2 = out1;
        out1 = out0;
        if (contrastEnhancement) {
            out0 *= Math.PI / 2.0;
            out0 = Math.sin(out0 + 0.1 * Math.sin(out0 * 4.0));
        }
        return (float) out0;
    }

}
