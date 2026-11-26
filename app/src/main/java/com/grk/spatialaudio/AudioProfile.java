package com.grk.spatialaudio;

/** Device-independent preset and local playback attenuation calculations. */
final class AudioProfile {
    static final String[] NAMES = {"Flat", "Clarity", "Cinema", "Deep bass", "Voice"};
    private static final int[][] CURVES = {{0,0,0,0,0},{0,-2,0,2,2},{3,-1,0,2,1},{4,2,-1,0,0},{-3,-1,2,3,0}};
    private static final int[] FREQ = {60,250,1000,4000,16000};
    static int bandGain(int preset, double hz, int min, int max) {
        int near=0; double best=Double.MAX_VALUE;
        for(int k=0;k<FREQ.length;k++) { double d=Math.abs(Math.log(Math.max(1,hz)/FREQ[k])); if(d<best){near=k;best=d;} }
        return Math.max(min,Math.min(max,CURVES[preset][near]*100));
    }
    static float volume(boolean enabled, int maxBandMb, boolean bassActive, boolean spatialActive) {
        if(!enabled) return 1f;
        double db=Math.max(0,maxBandMb)/100.0+(bassActive?4:0)+(spatialActive?2:0);
        return (float)Math.pow(10,-db/20);
    }
}
