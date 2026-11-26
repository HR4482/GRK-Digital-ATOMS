package com.grk.spatialaudio;

/** Original filtered and delayed stereo height renderer. Not measured HRTF or Dolby decoding. */
final class HeightRenderer {
    private final double[] left,right;
    private final int near,far;
    private final double hp,lp,smooth;
    private int pos;
    private double loL,loR,hiL,hiR,gL,gR;
    double outL,outR;
    HeightRenderer(int rate){
        near=Math.max(1,(int)Math.round(rate*.003));far=Math.max(near+1,(int)Math.round(rate*.00365));
        left=new double[far+1];right=new double[far+1];
        hp=1-Math.exp(-2*Math.PI*700/rate);lp=1-Math.exp(-2*Math.PI*Math.min(6500,rate*.42)/rate);
        smooth=1-Math.exp(-1.0/(rate*.015));
    }
    void render(double l,double r,double targetL,double targetR){
        gL+=smooth*(targetL-gL);gR+=smooth*(targetR-gR);
        loL+=hp*(l-loL);loR+=hp*(r-loR);
        hiL+=lp*(l-loL-hiL);hiR+=lp*(r-loR-hiR);
        left[pos]=hiL*gL;right[pos]=hiR*gR;
        int n=(pos-near+left.length)%left.length,f=(pos-far+left.length)%left.length;
        outL=.65*left[n]+.23*right[f];outR=.65*right[n]+.23*left[f];
        pos=(pos+1)%left.length;
    }
}
