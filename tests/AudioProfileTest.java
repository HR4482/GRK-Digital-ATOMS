package com.grk.spatialaudio;
public final class AudioProfileTest {
  private static int count=0;
  private static void check(String name, boolean ok) { if(!ok) throw new AssertionError(name); count++; System.out.println("PASS: "+name); }
  private static boolean approx(float actual,double expected) {return Math.abs(actual-expected)<0.00001;}
  public static void main(String[] args){
    int[] f={60,250,1000,4000,16000};
    int[][] expected={{0,0,0,0,0},{0,-200,0,200,200},{300,-100,0,200,100},{400,200,-100,0,0},{-300,-100,200,300,0}};
    for(int p=0;p<5;p++) { boolean ok=true; for(int b=0;b<5;b++) ok &= AudioProfile.bandGain(p,f[b],-1500,1500)==expected[p][b]; check(AudioProfile.NAMES[p]+" preset all 5 bands",ok); }
    check("Device band frequency mapping",AudioProfile.bandGain(2,63,-1500,1500)==300 && AudioProfile.bandGain(2,3800,-1500,1500)==200);
    check("Positive device gain limit",AudioProfile.bandGain(3,60,-100,100)==100);
    check("Negative device gain limit",AudioProfile.bandGain(4,60,-100,100)==-100);
    check("Master bypass unity gain",AudioProfile.volume(false,400,true,true)==1f);
    check("Flat unity gain",AudioProfile.volume(true,0,false,false)==1f);
    check("Negative gain does not increase volume",AudioProfile.volume(true,-500,false,false)==1f);
    check("3 dB boost attenuation",approx(AudioProfile.volume(true,300,false,false),Math.pow(10,-3.0/20)));
    check("Bass-only attenuation",approx(AudioProfile.volume(true,0,true,false),Math.pow(10,-4.0/20)));
    check("Spatial-only attenuation",approx(AudioProfile.volume(true,0,false,true),Math.pow(10,-2.0/20)));
    check("Combined attenuation",approx(AudioProfile.volume(true,400,true,true),Math.pow(10,-10.0/20)));
    boolean bounded=true; for(int m=-1500;m<=1500;m++) for(boolean b:new boolean[]{false,true})for(boolean s:new boolean[]{false,true}){float g=AudioProfile.volume(true,m,b,s);bounded &= Float.isFinite(g)&&g>0&&g<=1;}
    check("12004 attenuation boundary combinations",bounded);
    System.out.println("TOTAL: "+count+" passed, 0 failed");
  }
}
