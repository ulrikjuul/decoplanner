/**
 * Copyright Andreas Hagberg
 */ 
 
 package decoplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The "Schreiner" equation:
 *  P = Pi0 + R(t - 1/k) - (Pi0 - P0 - (R/k))e^-kt
 *
 * Where
 *  P = resulting compartment inert gas pressure (after time t)
 *  Pi0 = initial inspired (alveolar) inert gas pressure = (initial ambient pressure minus water vapor pressure) times fraction of inert gas = (Pamb0 - PH2O)*Fgas
 *  P0 = initial compartment inert gas pressure
 *  R = rate of change in inspired gas pressure with change in ambient pressure = rate of ascent/descent times the fraction of inert gas
 *  t = time (of exposure or interval)
 *  k = half-time constant = ln2/half-time
 *  water vapor pressure = 0,627 msw (2,041 fsw)  for Bühlmann.
 *
 * This equation is used to compute the partial pressure gas loading for each gas separately during ascent/descent.
 * The sum of these is then the total compartment gas loading.
 */
public final class BuhlmannDeco
{
//    private static Body body; //the body we are currently working on
    private static Dive currentDive; //the dive we are currently working on
    
//    private static double PH2O;	// Water vapor Pressure
    private static double surfacePressure;

    private static double Currently_Max_Allowed_Gradient_Factor;
    
//    private static ObservableList<DiveDecoSegment> resultingDecoPlan;


//    private double[] Fraction_Oxygen = new double[10];
//    private int Number_of_Changes;
//    private int Mix_Number;
//    private double[] Depth_Change;
//    private int[] Mix_Change;		
//    private int[] Rate_Change;  //use for GUE's variable ascent rates. Whole feet/meters.
//    private int[] Step_Size_Change; // detta kan jag ju använda för att införa ett alternativ att ändra till 1m/1ft de sista 9 metrarna..
//    private int Step_Size;

//    private static double Last_Run_Time, Run_Time,Stop_Time;

    private static double[] PO2LO = new double[11];
    private static double[] PO2HI = new double[11];
    private static double[] LIMSLP = new double[11];
    private static double[] LIMINT = new double[11];

    private static double CNStoxicityPercentage;
    private static double OTUbuildup;


    // N2 compartment pressures
    private static double[] nitrogenCompartmentPressure = new double[16]; //PN2
    
    private static double[] nitrogenHalfTimes = new double[16];

    // N2 Half-time constants
    private static double[] kN2 = new double[16];

    // He compartment pressures
    private static double[] heliumCompartmentPressure = new double[16]; //PHe

    private static double[] heliumHalfTimes = new double[16];
    
    // He Half-time constants
    private static double[] kHe = new double[16];

    // surfacing N2 M-values
//	private double[] N2_M0;
//	private final double[] N2_deltaM = {1.7928, 1.5352, 1.3847, 1.2780, 1.2306, 1.1857, 1.1504, 1.1223, 1.0999, 1.0844, 1.0731, 1.0635, 1.0552, 1.0478, 1.0414, 1.0359};

    // surfacing He M-values
//	private double[] He_M0;
//	private final double[] He_deltaM = {2.3557, 2.0964, 1.74, 1.5321, 1.3845, 1.3189, 1.2568, 1.2079, 1.1692, 1.1419, 1.1232, 1.1115, 1.1022, 1.0963, 1.0904, 1.085, 1.0791};

    private static double[] N2_a = new double[16];
    private static double[] N2_a_Bar = new double[16];
    private static double[] N2_b = new double[16];
    private static double[] He_a = new double[16];
    private static double[] He_a_Bar = new double[16];
    private static double[] He_b = new double[16];
    
    private static int currentMinimumDecoStopDuration;
    
    private static BuhlmannDeco buhlmannDeco = new BuhlmannDeco();
    
    // Debug logging
    private static PrintWriter debugLog;
    private static boolean debugEnabled = true;
    private static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        
    private BuhlmannDeco()
    {
        //Oxygen dose/tolerance constants
        PO2LO[1]=0.5;PO2LO[2]=0.6;PO2LO[3]=0.7;PO2LO[4]=0.8;PO2LO[5]=0.9;PO2LO[6]=1.1;PO2LO[7]=1.5;PO2LO[8]=1.6061;PO2LO[9]=1.62;PO2LO[10]=1.74;
        PO2HI[1]=0.6;PO2HI[2]=0.7;PO2HI[3]=0.8;PO2HI[4]=0.9;PO2HI[5]=1.1;PO2HI[6]=1.5;PO2HI[7]=1.6061;PO2HI[8]=1.62;PO2HI[9]=1.74;PO2HI[10]=1.82;
        LIMSLP[1]=-1800.0;LIMSLP[2]=-1500.0;LIMSLP[3]=-1200.0;LIMSLP[4]=-900.0;LIMSLP[5]=-600.0;LIMSLP[6]=-300.0;LIMSLP[7]=-750.0;LIMSLP[8]=-1250.0;LIMSLP[9]=-125.0;LIMSLP[10]=-50.0;
        LIMINT[1]=1800.0;LIMINT[2]=1620.0;LIMINT[3]=1410.0;LIMINT[4]=1170.0;LIMINT[5]=900.0;LIMINT[6]=570.0;LIMINT[7]=1245.0;LIMINT[8]=2045.0;LIMINT[9]=222.5;LIMINT[10]=92.0;
				
        
        nitrogenHalfTimes[0] = 5;  //4 är standard.
        nitrogenHalfTimes[1] = 8;
        nitrogenHalfTimes[2] = 12.5;
        nitrogenHalfTimes[3] = 18.5;
        nitrogenHalfTimes[4] = 27;
        nitrogenHalfTimes[5] = 38.3;
        nitrogenHalfTimes[6] = 54.3;
        nitrogenHalfTimes[7] = 77;
        nitrogenHalfTimes[8] = 109;
        nitrogenHalfTimes[9] = 146;
        nitrogenHalfTimes[10] = 187;
        nitrogenHalfTimes[11] = 239;
        nitrogenHalfTimes[12] = 305;  //306 är standard
        nitrogenHalfTimes[13] = 390;
        nitrogenHalfTimes[14] = 498;
        nitrogenHalfTimes[15] = 635;
        
        // k = ln2 / half-time
        for(int cpt=0; cpt<16; cpt++)
        {
            kN2[cpt] = Math.log(2) / nitrogenHalfTimes[cpt];
        }
        
        heliumHalfTimes[0] = 1.88;  //1.51 är standard?
        heliumHalfTimes[1] = 3.02;
        heliumHalfTimes[2] = 4.72;
        heliumHalfTimes[3] = 6.99;
        heliumHalfTimes[4] = 10.21;
        heliumHalfTimes[5] = 14.48;
        heliumHalfTimes[6] = 20.53;
        heliumHalfTimes[7] = 29.11;
        heliumHalfTimes[8] = 41.20;
        heliumHalfTimes[9] = 55.19;
        heliumHalfTimes[10] = 70.69;
        heliumHalfTimes[11] = 90.34;
        heliumHalfTimes[12] = 115.29;
        heliumHalfTimes[13] = 147.42;
        heliumHalfTimes[14] = 188.24;
        heliumHalfTimes[15] = 240.03;

        // k = ln2 / half-time
        for(int cpt=0; cpt<16; cpt++)
        {
            kHe[cpt] = Math.log(2) / heliumHalfTimes[cpt];
        }

        // a- and b-values for N2 and He  (decoPlanner)
        // a = intercept at zero ambient pressure
        // b = reciprocal of slope of m-value line
        Settings.zhl_model_changed = false;
        if(Settings.ZHL_C)
        {
            N2_a_Bar[0] = 1.1696;
            N2_a_Bar[1] = 1.0;
            N2_a_Bar[2] = 0.8618;
            N2_a_Bar[3] = 0.7562;
            N2_a_Bar[4] = 0.62;
            N2_a_Bar[5] = 0.5043;
            N2_a_Bar[6] = 0.441;
            N2_a_Bar[7] = 0.4;
            N2_a_Bar[8] = 0.375;
            N2_a_Bar[9] = 0.35;
            N2_a_Bar[10] = 0.3295;
            N2_a_Bar[11] = 0.3065;
            N2_a_Bar[12] = 0.2835;
            N2_a_Bar[13] = 0.261;
            N2_a_Bar[14] = 0.248;
            N2_a_Bar[15] = 0.2327;            
        }
        else //means ZH-L16B
        {
            N2_a_Bar[0] = 1.1696;
            N2_a_Bar[1] = 1.0;
            N2_a_Bar[2] = 0.8618;
            N2_a_Bar[3] = 0.7562;
            N2_a_Bar[4] = 0.6667;
            N2_a_Bar[5] = 0.56;
            N2_a_Bar[6] = 0.4947;
            N2_a_Bar[7] = 0.45;
            N2_a_Bar[8] = 0.4187;
            N2_a_Bar[9] = 0.3798;
            N2_a_Bar[10] = 0.3497;
            N2_a_Bar[11] = 0.3223;
            N2_a_Bar[12] = 0.285;
            N2_a_Bar[13] = 0.2737;
            N2_a_Bar[14] = 0.2523;
            N2_a_Bar[15] = 0.2327;            
        }
        
        He_a_Bar[0] = 1.6189;
        He_a_Bar[1] = 1.383;
        He_a_Bar[2] = 1.1919;
        He_a_Bar[3] = 1.0458;
        He_a_Bar[4] = 0.922;
        He_a_Bar[5] = 0.8205;
        He_a_Bar[6] = 0.7305;
        He_a_Bar[7] = 0.6502;
        He_a_Bar[8] = 0.595;
        He_a_Bar[9] = 0.5545;
        He_a_Bar[10] = 0.5333;
        He_a_Bar[11] = 0.5189;
        He_a_Bar[12] = 0.5181;
        He_a_Bar[13] = 0.5176;
        He_a_Bar[14] = 0.5172;
        He_a_Bar[15] = 0.5119;        
        
        //sätt alla metric eller imperial-värden.		
        if(Settings.metric)
        {
            for(int cpt=0; cpt<16; cpt++)
            {
                N2_a[cpt] = N2_a_Bar[cpt] * 10;
                He_a[cpt] = He_a_Bar[cpt] * 10;
            }
        }
        else
        {
            for(int cpt=0; cpt<16; cpt++)
            {
                N2_a[cpt] = N2_a_Bar[cpt] * 32.5684678;
                He_a[cpt] = He_a_Bar[cpt] * 32.5684678;
            }
        }        

        //Default B factors to ZHL16A Theoretical
        N2_b[0] = 0.5578;
        N2_b[1] = 0.6514;
        N2_b[2] = 0.7222;
        N2_b[3] = 0.7825;
        N2_b[4] = 0.8126;
        N2_b[5] = 0.8434;
        N2_b[6] = 0.8693;
        N2_b[7] = 0.891;
        N2_b[8] = 0.9092;
        N2_b[9] = 0.9222;
        N2_b[10] = 0.9319;
        N2_b[11] = 0.9403;
        N2_b[12] = 0.9477;
        N2_b[13] = 0.9544;
        N2_b[14] = 0.9602;
        N2_b[15] = 0.9653;

        He_b[0] = 0.477;
        He_b[1] = 0.5747;
        He_b[2] = 0.6527;
        He_b[3] = 0.7223;
        He_b[4] = 0.7582;
        He_b[5] = 0.7957;
        He_b[6] = 0.8279;
        He_b[7] = 0.8553;
        He_b[8] = 0.8757;
        He_b[9] = 0.8903;
        He_b[10] = 0.8997;
        He_b[11] = 0.9073;
        He_b[12] = 0.9122;
        He_b[13] = 0.9171;
        He_b[14] = 0.9217;
        He_b[15] = 0.9267;

        /*for(int cpt=0; cpt<16; cpt++)
        {
            // a = mValue - (deltaM * Pamb), where Pamb = surfacePressure.
            N2_a[cpt] = N2_M0[cpt] - (N2_deltaM[cpt] * surfacePressure);
            He_a[cpt] = He_M0[cpt] - (He_deltaM[cpt] * surfacePressure);
            // b = 1 / deltaM
            N2_b[cpt] = 1 / N2_deltaM[cpt];
            He_b[cpt] = 1 / He_deltaM[cpt];
        }*/
    }
    
    /* Static 'instance' method */
    public static BuhlmannDeco getInstance( ) 
    {
      return buhlmannDeco;
    }
    
    public static void init()
    {
        //Calling this makes it run the constructor if we haven't run it before.
        //But, if we have switched Imperial/Metric mode between planning dives, we have to edit the a-factors, since the constructor won't run a second time, and that's where we set these
        //First we have to check if we have changed the ZH-L16 model between B and C.
        if(Settings.zhl_model_changed)
        {
            if(Settings.ZHL_C)
            {
                N2_a_Bar[0] = 1.1696;
                N2_a_Bar[1] = 1.0;
                N2_a_Bar[2] = 0.8618;
                N2_a_Bar[3] = 0.7562;
                N2_a_Bar[4] = 0.62;
                N2_a_Bar[5] = 0.5043;
                N2_a_Bar[6] = 0.441;
                N2_a_Bar[7] = 0.4;
                N2_a_Bar[8] = 0.375;
                N2_a_Bar[9] = 0.35;
                N2_a_Bar[10] = 0.3295;
                N2_a_Bar[11] = 0.3065;
                N2_a_Bar[12] = 0.2835;
                N2_a_Bar[13] = 0.261;
                N2_a_Bar[14] = 0.248;
                N2_a_Bar[15] = 0.2327;            
            }
            else //means ZH-L16B
            {
                N2_a_Bar[0] = 1.1696;
                N2_a_Bar[1] = 1.0;
                N2_a_Bar[2] = 0.8618;
                N2_a_Bar[3] = 0.7562;
                N2_a_Bar[4] = 0.6667;
                N2_a_Bar[5] = 0.56;
                N2_a_Bar[6] = 0.4947;
                N2_a_Bar[7] = 0.45;
                N2_a_Bar[8] = 0.4187;
                N2_a_Bar[9] = 0.3798;
                N2_a_Bar[10] = 0.3497;
                N2_a_Bar[11] = 0.3223;
                N2_a_Bar[12] = 0.285;
                N2_a_Bar[13] = 0.2737;
                N2_a_Bar[14] = 0.2523;
                N2_a_Bar[15] = 0.2327;            
            }  
            Settings.zhl_model_changed = false;
        }
        if(Settings.metric)
        {
            for(int cpt=0; cpt<16; cpt++)
            {
                N2_a[cpt] = N2_a_Bar[cpt] * 10;
                He_a[cpt] = He_a_Bar[cpt] * 10;
            }
        }
        else
        {
            for(int cpt=0; cpt<16; cpt++)
            {
                N2_a[cpt] = N2_a_Bar[cpt] * 32.5684678;
                He_a[cpt] = He_a_Bar[cpt] * 32.5684678;
            }
        }  
    }


    public static double schreinerEquation(double Initial_Inspired_Gas_Pressure, double Rate_Change_Insp_Gas_Pressure, double Interval_Time, double Gas_Time_Constant, double Initial_Gas_Pressure)
    {
        return Initial_Inspired_Gas_Pressure + Rate_Change_Insp_Gas_Pressure*(Interval_Time - 1.0/Gas_Time_Constant) - (Initial_Inspired_Gas_Pressure - Initial_Gas_Pressure - Rate_Change_Insp_Gas_Pressure/Gas_Time_Constant)*Math.exp(-Gas_Time_Constant*Interval_Time);
    }

    /**
     * Schreiner equation for Nitrogen, for gas loading during ascent or descent
     */
    private static void schreinerEquation_nitrogen(double Pamb0, double FN2, double Rate, double segment_duration)
    {
        double Pi0 = (Pamb0 - Settings.getWaterVaporPressure()) * FN2;
        double R = Rate * FN2;
        // P = Pi0 + R(t - 1/k) - (Pi0 - P0 - (R/k))e^-kt
        for(int cpt=0; cpt<16; cpt++)
        {
            nitrogenCompartmentPressure[cpt] = Pi0 + R*(segment_duration - 1/kN2[cpt]) - (Pi0 - nitrogenCompartmentPressure[cpt] - (R/kN2[cpt]))*(Math.exp(-kN2[cpt]*segment_duration));
        }
    }

    /**
     * Schreiner equation for Helium, for gas loading during ascent or descent
     */
    private static void schreinerEquation_helium(double Pamb0, double FHe, double Rate, double segment_duration)
    {
        double Pi0 = (Pamb0 - Settings.getWaterVaporPressure()) * FHe;
        double R = Rate * FHe;
        // P = Pi0 + R(t - 1/k) - (Pi0 - P0 - (R/k))e^-kt
        for(int cpt=0; cpt<16; cpt++)
        {
            heliumCompartmentPressure[cpt] = Pi0 + R*(segment_duration - 1/kHe[cpt]) - (Pi0 - heliumCompartmentPressure[cpt] - (R/kHe[cpt]))*(Math.exp(-kHe[cpt]*segment_duration));
        }
    }

/* HALDANE_EQUATION = Initial_Gas_Pressure + (Inspired_Gas_Pressure - Initial_Gas_Pressure) *(1.0 - EXP(-Gas_Time_Constant * Interval_Time))
Pt (t) = Palv0 + [Pt0 − Palv0]e−kt
where 
Pt (t) Partial pressure of the gas in the tissue (bar)
Pt0 Initial partial pressure of the gas in the tissue at t=0 (bar)
Palv0 Constant partial pressure of the gas in the breathing mix in the alveoli (bar)
k A constant depending on the type of tissue (min−1)
t Time (min)
 
Eller  
P = Po + (Pi - Po)(1 - 2^(-t/half-time))
där 
P = compartment inert gas pressure (final)
Po = initial compartment inert gas pressure
Pi = inspired inert gas pressure
t = time (of exposure or interval)
k = time constant (in this case, half-time constant)
*/

    public static double haldaneEquation(double Initial_Compartment_Pressure, double Inspired_Gas_Pressure, double Time_Constant, double Segment_Time)
    {
        return Initial_Compartment_Pressure + (Inspired_Gas_Pressure - Initial_Compartment_Pressure)*(1 - Math.exp(-Time_Constant*Segment_Time));
    }
    /**
     * haldaneEquation_helium
     * @param initialCompartmentPressure
     * @param compartmentIndex index 0 to 15
     * @param ambientPressure
     * @param breathingGasHeliumFraction
     * @param segmentDuration in minutes
     * @return The updated compartment pressure
     */
    public static double haldaneEquation_helium(double initialCompartmentPressure, int compartmentIndex, double ambientPressure, double breathingGasHeliumFraction, double segmentDuration)
    {
        double inspiredGasPressure = (ambientPressure - Settings.getWaterVaporPressure()) * breathingGasHeliumFraction;
        // Pt (t) = Palv0 + (Pt0 − Palv0)e^−kt
        return initialCompartmentPressure + (inspiredGasPressure - initialCompartmentPressure)*(1 - Math.exp(-kHe[compartmentIndex]*segmentDuration));
    }
    
    /**
     * haldaneEquation_nitrogen
     * @param initialCompartmentPressure
     * @param compartmentIndex index 0 to 15
     * @param ambientPressure
     * @param breathingGasNitrogenFraction
     * @param segmentDuration in minutes
     * @return The updated compartment pressure
     */
    public static double haldaneEquation_nitrogen(double initialCompartmentPressure,  int compartmentIndex, double ambientPressure, double breathingGasNitrogenFraction, double segmentDuration)
    {
        double inspiredGasPressure = (ambientPressure - Settings.getWaterVaporPressure()) * breathingGasNitrogenFraction;
        // Pt (t) = Palv0 + (Pt0 − Palv0)e^−kt
        return initialCompartmentPressure + (inspiredGasPressure - initialCompartmentPressure)*(1 - Math.exp(-kN2[compartmentIndex]*segmentDuration));    
    }
    
    /**
     * To be used in OpenCircuitDivePlan
     * @param compartmentIndex
     * @param nitrogenCompartmentPressure
     * @param heliumCompartmentPressure
     * @return The calculated A-factor
     */
    public static double calculateAfactor(int compartmentIndex, double nitrogenCompartmentPressure, double heliumCompartmentPressure)
    {
        return ((N2_a[compartmentIndex] * nitrogenCompartmentPressure) + (He_a[compartmentIndex] * heliumCompartmentPressure)) / (nitrogenCompartmentPressure + heliumCompartmentPressure);
    }
    /**
     * To be used in OpenCircuitDivePlan
     * @param compartmentIndex
     * @param nitrogenCompartmentPressure
     * @param heliumCompartmentPressure
     * @return The calculated B-factor
     */
    public static double calculateBfactor(int compartmentIndex, double nitrogenCompartmentPressure, double heliumCompartmentPressure)
    {
        return ((N2_b[compartmentIndex] * nitrogenCompartmentPressure) + (He_b[compartmentIndex] * heliumCompartmentPressure)) / (nitrogenCompartmentPressure + heliumCompartmentPressure);
    }
	
    public static void updateHighestMvaluePercentageAndGradientFactor(double ambientPressure)
    {
        // calculate the M-value percentage, and GF
        double highestmValueFraction = -1;
        double mValuePercent;
        double mValue;
        double highestGradientFactor = -1;
        double gradientFactor;
        double a;
        double b;

        for(int cpt=0; cpt<16; cpt++)
        {
            a = ((N2_a[cpt] * nitrogenCompartmentPressure[cpt]) + (He_a[cpt] * heliumCompartmentPressure[cpt])) / (nitrogenCompartmentPressure[cpt] + heliumCompartmentPressure[cpt]);	// calculate a(N2+He)
            b = ((N2_b[cpt] * nitrogenCompartmentPressure[cpt]) + (He_b[cpt] * heliumCompartmentPressure[cpt])) / (nitrogenCompartmentPressure[cpt] + heliumCompartmentPressure[cpt]);	// calculate b(N2+He)
            mValue = (ambientPressure / b) + a;
            mValuePercent = (nitrogenCompartmentPressure[cpt] + heliumCompartmentPressure[cpt]) / mValue;	// calculate the mValuePercent for the current Compartment...
            highestmValueFraction = Math.max(highestmValueFraction, mValuePercent);

            // och nu till gradientFactors..
            // GF: (TotalCmpPressure(He+N2) - ambientPressure) / (M-value - ambientPressure)
            // Analys-delen i DecoPlanner är jävligt fel i många fall, så kolla att denna formel verkligen funkar i alla lägen..			
            gradientFactor = ((nitrogenCompartmentPressure[cpt]+heliumCompartmentPressure[cpt]) - ambientPressure) / (mValue - ambientPressure);
//System.out.println("[updateHighestMvaluePercentageAndGradientFactor] ambientPressure: " + ambientPressure + " Cpt" + cpt + " mValue: " + mValue + " nitrogenPressure: " + nitrogenCompartmentPressure[cpt] + " gradientFactor: " + gradientFactor);
            highestGradientFactor = Math.max(highestGradientFactor, gradientFactor);
        }
        currentDive.highestCurrentMvalueFraction = highestmValueFraction;
        currentDive.highestCurrentGradientFactor = highestGradientFactor;
    }
    
    
    
    /**===============================================================================
    * SUBROUTINE ALTITUDE_DIVE_ALGORITHM
    * Purpose: This subprogram updates gas loadings (as required) based on
    * whether or not diver is acclimatized at altitude or makes an ascent to
    * altitude before the dive.
    **/
    private static void ALTITUDE_DIVE_ALGORITHM()
    {
        
        double Ascent_to_Altitude_Time, Time_at_Altitude_Before_Dive;
        double Starting_Ambient_Pressure, Ending_Ambient_Pressure;
        double Initial_Inspired_N2_Pressure, Rate, Nitrogen_Rate;
        double Inspired_Nitrogen_Pressure, Initial_Nitrogen_Pressure;
        double Barometric_Pressure;
        double Water_Vapor_Pressure = Settings.getWaterVaporPressure();
        

        Ascent_to_Altitude_Time = Settings.hoursToAltitude * 60.0;
        Time_at_Altitude_Before_Dive = Settings.hoursAtAltitudeBeforeDive * 60.0;
        if(Settings.acclimatizedAtDiveAltitude)
        {
            Barometric_Pressure = Util.calculateBarometricPressure(Settings.altitude);
            if(currentDive.missionDiveNumber == 1)
            {
    //System.out.println("altitudeSubProgram - " + Barometric_Pressure);            
                for(int i=0;i<16;i++)
                {
                    heliumCompartmentPressure[i] = 0.0;
                    nitrogenCompartmentPressure[i] = (Barometric_Pressure - Water_Vapor_Pressure)*0.79;
                }
            }
        }
        else
        {
            Barometric_Pressure = Util.calculateBarometricPressure(Settings.altitudeAcclimatized);
            Starting_Ambient_Pressure = Barometric_Pressure;
            //if this is a repetitive dive, we do NOT initialize with ambient pressure, since we already have a gas load
            if(currentDive.missionDiveNumber == 1)
            {
                for(int i=0;i<16;i++)
                {
                    heliumCompartmentPressure[i] = 0.0;
                    nitrogenCompartmentPressure[i] = (Barometric_Pressure - Water_Vapor_Pressure)*0.79;
                }
            }
            Barometric_Pressure = Util.calculateBarometricPressure(Settings.altitude);
            Ending_Ambient_Pressure = Barometric_Pressure;
            Initial_Inspired_N2_Pressure = (Starting_Ambient_Pressure - Water_Vapor_Pressure)*0.79;
            Rate = (Ending_Ambient_Pressure - Starting_Ambient_Pressure) / Ascent_to_Altitude_Time;
            Nitrogen_Rate = Rate*0.79;
            for(int i=0;i<16;i++)
            {
                Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[i];
                nitrogenCompartmentPressure[i] = schreinerEquation(Initial_Inspired_N2_Pressure, Nitrogen_Rate, Ascent_to_Altitude_Time, kN2[i], Initial_Nitrogen_Pressure);
            }
            Inspired_Nitrogen_Pressure = (Barometric_Pressure - Water_Vapor_Pressure)*0.79;
            for(int i=0;i<16;i++)
            {
                Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[i];
                nitrogenCompartmentPressure[i] = haldaneEquation(Initial_Nitrogen_Pressure, Inspired_Nitrogen_Pressure, kN2[i], Time_at_Altitude_Before_Dive);
            }
        }
        Settings.setSurfacePressure(Barometric_Pressure);
    }
    
    
    
    
    /**
     * @param theDive
     * @return Dive
     */
    //public static ObservableList<DiveDecoSegment> CalculateOpenCircuitDeco(Body theBody)
    public static Dive CalculateOpenCircuitDeco(Dive theDive)
    {
//        body = theBody;
        currentDive = theDive; // body.getCurrentDive();
        
        // Initialize debug logging (only if not already open)
        if (debugEnabled && debugLog == null) {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String logFileName = "decoplanner_debug_" + timestamp + ".txt";
                debugLog = new PrintWriter(new FileWriter(logFileName));
                debugLog.println("=== DECOPLANNER DEBUG LOG ===");
                debugLog.println("Generated: " + new Date());
                debugLog.println();
                logDebugSettings();
            } catch (IOException e) {
                System.err.println("Could not create debug log: " + e.getMessage());
                debugEnabled = false;
            }
        }
        
        //reset the currentDive object to surface state (but not tissue pressures and oxygen exposure, since this might be a repetitive dive)
        currentDive.activeGasID = 0;
        currentDive.currentDepth = 0;
        currentDive.currentRate = Settings.descentRate;
        currentDive.currentRunTime = 0;
        
//        ArrayList<DecoGas> decoGases = currentDive.decoGases;
        nitrogenCompartmentPressure = currentDive.initialNitrogenCompartmentPressure.clone(); //body.nitrogenCompartmentPressure; //array
        heliumCompartmentPressure = currentDive.initialHeliumCompartmentPressure.clone(); //body.heliumCompartmentPressure;  //array
        
        if (debugEnabled && debugLog != null) {
            logTissues("Initial");
        }
        
        if(Settings.diveAtAltitude)
        {
            ALTITUDE_DIVE_ALGORITHM();
            //I forgot to do the following before version 4.6.4 ...
            currentDive.surfacePressure = Settings.getSurfacePressure();
            currentDive.surfaceN2Pressure = Settings.getSurfaceN2Saturation();
        }
        
        
        CNStoxicityPercentage = currentDive.initialCNStoxicityPercentage;
        OTUbuildup = currentDive.initialOTUbuildup;
        
//        Settings.setWaterVaporPressure(currentDive.waterVaporPressure);
        surfacePressure = currentDive.surfacePressure;

        Currently_Max_Allowed_Gradient_Factor = Settings.gradientFactorFirstStop;
        
        currentMinimumDecoStopDuration = Math.round(Settings.Minimum_Deco_Stop_Time);

        currentDive.resultingDecoPlan = FXCollections.observableArrayList();    //FXCollections.observableArrayList<DecoSegment>();

        double Pamb0;   // initial ambient pressure
        double Pamb;    // ambient pressure
        double Pi0;	// initial inspired inert gas pressure = (Pamb0 - PH2O) * [FN2||FHe]
        double FN2;	// fraction N2.  0.xx
        double FHe;	// fraction He.  0.xx
        //double RATE;    // ascent/descent rate msw/min (fsw/min). RATE is negative at ascent.
        double R;	// RATE * [FN2||FHe]
        double segmentDuration;	    // duration of interval (minutes)
        boolean ascentDone = false;
        boolean atManualDecoStop = false;
        boolean doneAdecoStop = false;
        boolean stoppedAfterAscent = false;
        boolean firstShallowerUserInput = false;

        DiveSegment diveSegment = null;
        //kör nu igenom alla segment och uppdatera vävnaderna och se om det behövs nån deko mellan de segment som användaren lagt in
        //for(DiveSegment diveSegment : currentDive.diveSegments)    
        
        for(int currentSegmentIndex = 0; currentSegmentIndex < currentDive.diveSegments.size(); currentSegmentIndex++)
        {
            diveSegment = currentDive.diveSegments.get(currentSegmentIndex);
            
            Pamb0 = diveSegment.startDepth + surfacePressure;		// ambient pressure at start of this segment
            Pamb = diveSegment.endDepth + surfacePressure;		// ambient pressure at the end of this segment
            segmentDuration = diveSegment.duration;
            currentDive.currentRate = (Pamb - Pamb0) / segmentDuration;	// ascent/descent rate msw/min or fsw/min
//System.out.println("About to process input segment, starting Pamb: " + Pamb0 + " ending Pamb: " + Pamb + " Duration: " + segmentDuration);                        
            FHe = currentDive.getCurrentHeliumFraction();		// fraction He.  0.xx
            FN2 = 1 - FHe - currentDive.getCurrentOxygenFraction();	// fraction N2.  0.xx
            
            //Update compartment pressures
            switch(diveSegment.segmentType) 
            {                    
                case DiveSegment.CONSTANT_DEPTH:

//System.out.println("\nDiveSegment.CONSTANT_DEPTH: " + diveSegment.startDepth + " duration: " + diveSegment.duration);
                    
                    double originalSegmentDuration = diveSegment.duration;
                    //Check if the previous segment was an ascent without deco, and if we want to include the ascent time in the run time of this constant-depth segment
                    if(ascentDone && Settings.includeTravelTimeInDiveDuration && stoppedAfterAscent)
                    {
//System.out.println("This is a manual deco stop, NOT the first.");  
                        //And also actually change the duration in the diveSegment
                        diveSegment.duration -= currentDive.diveSegments.get(currentSegmentIndex - 1).duration; //subtract the ascent-segment's duration
                        diveSegment.endRunTime = diveSegment.startRunTime + diveSegment.duration;
                        currentDive.diveSegments.set(currentSegmentIndex, diveSegment);
                        if(doneAdecoStop)
                        {
                            //update allowed gradient factor
                            Currently_Max_Allowed_Gradient_Factor = (diveSegment.startDepth - Settings.decoStopInterval) * currentDive.gradientFactorSlope + Settings.gradientFactorSurfacing;
                        }
                    }
                    
                    if(ascentDone && !stoppedAfterAscent)
                    {
//System.out.println("This is a manual first shallower level, not necessarily a required deco stop.");
                        firstShallowerUserInput = true;
                        //Här rundar vi upp så run_time slutar på en hel minut (eller vad minimum_deco_stop_time nu är) när vi avslutar detta dekostoppet.
                        if(diveSegment.endRunTime % 1 != 0)
                        {
                            diveSegment.endRunTime = Math.round(diveSegment.endRunTime);
                            diveSegment.duration = diveSegment.endRunTime - diveSegment.startRunTime;
//System.out.println("diveSegment.duration ändrades till " + diveSegment.duration);                            
                        }
                    }
                    
                    if(ascentDone && atManualDecoStop && doneAdecoStop == false)
                    {
//System.out.println("This is a manual first deco stop."); 
                        //Vi kommer hit om användaren har satt ett manuellt första stopp, så det inte har behövts deko innan detta constant_depth-segment, men segmentet är exakt på första stopp-djupet
                        //SET GRADIENT FACTOR SLOPE
                        if(currentDive.gradientFactorSlope == 0.0)
                        {
                            currentDive.gradientFactorSlope = (Settings.gradientFactorSurfacing - Settings.gradientFactorFirstStop)/(0.0 - diveSegment.startDepth);
                        }
                        Currently_Max_Allowed_Gradient_Factor = (diveSegment.startDepth - Settings.decoStopInterval) * currentDive.gradientFactorSlope + Settings.gradientFactorSurfacing;
                        doneAdecoStop = true;
                    }

                    //Se till att vi verkligen har rätt gas aktiv, eftersom vi kan ha växlat gas under tidigare dekostopp
                    currentDive.activeGasID = diveSegment.gasID;
//System.out.println("Constant depth input at " + diveSegment.startDepth + ". currentDive.activeGasID set to: " + diveSegment.gasID + " Duration: " + diveSegment.duration);
                    GAS_LOADINGS_CONSTANT_DEPTH(diveSegment.startDepth, diveSegment.duration);

/*
System.out.println("Buhlman OC: depth " + diveSegment.startDepth + " run time " + (currentDive.currentRunTime + diveSegment.duration));
System.out.print("Helium: ");
for(int i=0; i<16; i++)
{
    System.out.print(heliumCompartmentPressure[i] + ", ");
}
System.out.println();
System.out.print("Nitrogen: ");
for(int i=0; i<16; i++)
{
    System.out.print(nitrogenCompartmentPressure[i] + ", ");
}
System.out.println();
*/                    
                    currentDive.currentDepth = diveSegment.startDepth;
                                        
                    currentDive.currentRunTime += diveSegment.duration;
                    
                    //skapa ett decoSegment och lägg till i resulting deco plan
                    DecoTableSegment decoSegment = DIVEDATA_CONSTANT_DEPTH(diveSegment.startDepth, Settings.RMV_During_Dive, diveSegment.duration);

//System.out.println("DiveSegment.CONSTANT_DEPTH: " + diveSegment.startDepth + " duration: " + diveSegment.duration + " original duration: " + originalSegmentDuration);
                    
                    //if(ascentDone && atManualDecoStop)
                    if(ascentDone && atManualDecoStop && !stoppedAfterAscent) // added  "&& !stoppedAfterAscent" in v.4.3.11
                    {
//System.out.println("ascentDone && atManualDecoStop && !stoppedAfterAscent");
                        //for the first deco stop we set endRunTime to follow the real schedule, but we adjust the startRunTime based on the stop duration
                        decoSegment.setEndRunTime(currentDive.diveSegments.get(currentSegmentIndex - 1).endRunTime + diveSegment.duration);
                        
                        //we don't include the ascent-time in the first deco stop duration
                        decoSegment.setStartRunTime(diveSegment.startRunTime);
                        //decoSegment.setDuration(Util.roundToOneDecimal(currentDive.diveSegments.get(currentSegmentIndex - 1).endRunTime + diveSegment.duration - diveSegment.startRunTime));
                        decoSegment.setDuration(originalSegmentDuration);
                    }
                    else if(stoppedAfterAscent || !ascentDone) //this is not the first stop of an ascent
                    {
//System.out.println("stoppedAfterAscent || !ascentDone");
                        //set the duration back to the user-input's value if we included ascent time in this
                        if(Settings.includeTravelTimeInDiveDuration)
                        {
                           decoSegment.setDuration(diveSegment.duration + currentDive.diveSegments.get(currentSegmentIndex - 1).duration);
                           // decoSegment.setDuration(originalSegmentDuration);
                        }
                    }                    
                    
                    //2022-01-21: from v4.3.5: Is there ever a reason to NOT show the user's original input duration for a segment in the deco table..?
                    //let's set it back for any user defined segment.
                    //2022-07-30: the added check for if the segment depth is the same as previous can be true if you plan two segments at the depth, mostly likely during bailout-planning.
                    if(diveSegment.userDefined && (firstShallowerUserInput || (diveSegment.endDepth == currentDive.diveSegments.get(currentSegmentIndex - 1).startDepth)))
                    {
//System.out.println("Setting back the duration to " + originalSegmentDuration);
                        decoSegment.setDuration(originalSegmentDuration);
                    }
                    
                    
                    //check if we need to adjust the start-runtime of this segment to make it a nicely rounded number
                    if(Double.parseDouble(decoSegment.getStartRunTime()) % currentMinimumDecoStopDuration > 0)
                    {
                        decoSegment.setStartRunTime(Math.round(Double.parseDouble(decoSegment.getStartRunTime())));
                    }
                    //Add the gas volume used on the previous travel-segment
                    decoSegment.setGasVolumeUsedDuringSegment(Double.parseDouble(decoSegment.getGasVolumeUsedDuringSegment()) + currentDive.diveSegments.get(currentSegmentIndex - 1).gasVolumeUsedDuringSegment);
                    if(diveSegment.userDefined)
                    {
                        decoSegment.setUserDefined(true);
                        
                        if(currentDive.isBailoutDive && diveSegment.pO2SetPoint > 0)
                        {
                            //Visa med decimaler så man verkligen ser att det är en loop-gas
                            decoSegment.setHeliumPercentage(Util.roundToOneDecimal(currentDive.getCurrentHeliumFraction() * 100));
                            decoSegment.setOxygenPercentage(Util.roundToOneDecimal(currentDive.getCurrentOxygenFraction() * 100));
                            //och nollställ gasförbrukningen igen. Fast, jag bör väl egentligen beräkna volymen av användarens lungor gånger djupets tryck..
                            decoSegment.setGasVolumeUsedDuringSegment(0.0);
                        }
                    }
                    
                    currentDive.resultingDecoPlan.add(decoSegment);
                    //Update the body's supersaturation values. The gradient factor for the decoSegment needs to show the value for the beginning of the segment, NOT the end.
                    updateHighestMvaluePercentageAndGradientFactor(Pamb0);
                    //Update this diveSegment's compartment pressures
                    diveSegment.nitrogenCompartmentPressuresAtEndOfDuration = nitrogenCompartmentPressure.clone();
                    diveSegment.heliumCompartmentPressuresAtEndOfDuration = heliumCompartmentPressure.clone();
                    //Now update the diveSegment object in the actual array (since the diveSegment is NOT a reference)
                    currentDive.diveSegments.set(currentSegmentIndex, diveSegment);
                    
                    if(ascentDone)
                    {
                        stoppedAfterAscent = true;
                    }
                    
                    ascentDone = false;
                    atManualDecoStop = false;
                    firstShallowerUserInput = false;
                    break;
                case DiveSegment.ASCENT:
                    double current_deco_ceiling = CALC_DECO_CEILING();
//System.out.println("\nDiveSegment.ASCENT: " + diveSegment.startDepth + " -> " + diveSegment.endDepth + " - ceiling: " + current_deco_ceiling);
                    // FÖRST MÅSTE JAG KOLLA SÅ INTE DECO-CEILING ÖVERSKRIDS NÄR VI GÅR GRUNDARE!
                    if(current_deco_ceiling > diveSegment.endDepth)
                    {
                        //fr.o.m. v4.0-beta10: kolla ifall current_deco_ceiling är tillräckligt nära diveSegment.endDepth att det kanske faktiskt inte behövs nån deko efter en "projected ascent"
                        //så, kolla om ceiling är inom ett dekostopp-intervall ifrån diveSegment.endDepth
                        if(current_deco_ceiling < (diveSegment.endDepth + Settings.decoStopInterval))
                        {
                            //Detta kan hända om man knappar in ett manuellt första deko stopp.
                            //Så det behövs kanske faktiskt inte nån deko innan man når detta stoppet, men detta segment blir ett manuellt deko stopp.
                            //Kolla med en projected ascent.
                            if(diveSegment.endDepth >= PROJECTED_ASCENT(diveSegment.startDepth, Math.negateExact(Settings.ascentRate), diveSegment.endDepth, Settings.decoStopInterval))
                            {
//System.out.println("No need for deco between " + diveSegment.startDepth + " -> " + diveSegment.endDepth);                                
                                //Behövs alltså ingen deko trots allt. Så uppdatera bara vävnader och syreexponering.
                                
                                if(currentDive.offgassingStartsAtDepth == 0.0 && !ascentDone)
                                {
                                    currentDive.offgassingStartsAtDepth = CALC_START_OF_DECO_ZONE(diveSegment.startDepth);
//System.out.println("DiveSegment.ASCENT: Saving offgassing startdepth 1 = " + currentDive.offgassingStartsAtDepth + " FROM: " + diveSegment.startDepth);                                    
                                }
                                
                                GAS_LOADINGS_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.ascentRate);
//System.out.println(" About to call updateHighestMvaluePercentageAndGradientFactor with endDepth: " + diveSegment.endDepth + " and surface pressure: " + currentDive.surfacePressure);
                                updateHighestMvaluePercentageAndGradientFactor(diveSegment.endDepth + currentDive.surfacePressure);
                                DIVEDATA_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.ascentRate, Settings.RMV_During_Dive);
                                //Update this diveSegment's compartment pressures
                                diveSegment.nitrogenCompartmentPressuresAtEndOfDuration = nitrogenCompartmentPressure.clone();
                                diveSegment.heliumCompartmentPressuresAtEndOfDuration = heliumCompartmentPressure.clone();
                                currentDive.diveSegments.set(currentSegmentIndex, diveSegment);
                                currentDive.currentDepth = diveSegment.endDepth;
                                currentDive.currentRunTime += segmentDuration;
                                
                                ascentDone = true;
                                atManualDecoStop = true;
                                currentDive.currentDepth = diveSegment.endDepth;
                                break;
                            }
                        }
                        
                        //perform decompression up to diveSegment.endDepth and add
                        // all the deco stops to both the diveSegments-array plus the resultingDecoPlan
                        
                        //Jag måste ju ta bort detta ASCENT-segment från arrayen eftersom decompress-functionen lägger till ett ascent-segment till första stoppet.
                        // Och eftersom decompress-funktionen lägger till nya diveSegment i currentDive.diveSegments så måste jag här först spara undan 
                        // alla efterkommande diveSegments som finns INNAN decompress-funktionen, och ta bort dem från currentDive.diveSegments så de nya segmenten läggs till 
                        // i rätt ordning
                        
                        //ta bort segmentet från arrayen
                        currentDive.diveSegments.remove(diveSegment);
                        //och spara undan de efterföljande segmenten vi redan har
                        ArrayList<DiveSegment> tempDiveSegments = new ArrayList<>();
                        for(int i=currentSegmentIndex; i<currentDive.diveSegments.size();i++)
                        {
                            tempDiveSegments.add(currentDive.diveSegments.get(i));
                        }
                        //ta bort efterföljande segment från arrayen
                        currentDive.diveSegments.removeAll(tempDiveSegments);                        
                        //kolla hur många segment vi har innan decompress-funktionen
                        int numberOfSegmentsBeforeAddingDeco = currentDive.diveSegments.size();
                        double runTimeBeforeAddingDeco = currentDive.diveSegments.get(currentDive.diveSegments.size()-1).endRunTime;                        
                        //gör dekompressionen
                        decompress((int)Math.round(diveSegment.startDepth), (int)Math.round(diveSegment.endDepth));
                        //kolla nu hur många segment decompress-funktionen lade till, så vi kan uppdatera currentSegmentIndex (för foor-loopen vi är i)
                        int numberOfSegmentsAddedByDeco = currentDive.diveSegments.size() - numberOfSegmentsBeforeAddingDeco;
                        currentSegmentIndex = currentSegmentIndex + numberOfSegmentsAddedByDeco - 1; // -1 för att vi redan tog bort detta ascent-segment från arrayen
                        //update the currentDive status
                        currentDive.currentDepth = diveSegment.endDepth;
                        currentDive.currentRunTime = currentDive.diveSegments.get(currentDive.diveSegments.size()-1).endRunTime; //current runtime is the endRunTime of the last segment that the decompress-function added
                        //nu är vi på diveSegment.endDepth, så lägg nu till de efterföljande segmenten som vi sparade undan
                        //Först måste jag uppdatera alla StartRunTime och EndRunTimes för alla segmenten i tempDiveSegments, så jag lägger på den dekotid som lagts till på dyket nu.
                        double timeAddedByDeco = currentDive.currentRunTime - runTimeBeforeAddingDeco - diveSegment.duration; //jag tar bort diveSegment.duration eftersom jag tog bort det segmentet från currentDive
                        DiveSegment tmpSegment;
                        for(int i=0; i<tempDiveSegments.size(); i++)
                        {
                            tmpSegment = tempDiveSegments.get(i);
                            tmpSegment.startRunTime += timeAddedByDeco;
                            tmpSegment.endRunTime += timeAddedByDeco;
                            tempDiveSegments.set(i, tmpSegment);
                        }
                        currentDive.diveSegments.addAll(tempDiveSegments);
                        doneAdecoStop = true;
                        stoppedAfterAscent = true;
                    }
                    // Måste även kolla så inte gasupptaget vid denna uppstigning gör att deco-ceiling överskrids
                    else if(diveSegment.endDepth < PROJECTED_ASCENT(diveSegment.startDepth, Math.negateExact(Settings.ascentRate), diveSegment.endDepth, Settings.decoStopInterval))
                    {
//System.out.println("After projected ascent, deco is still needed between " + diveSegment.startDepth + " -> " + diveSegment.endDepth);                        
                        //perform decompression up to diveSegment.endDepth and add
                        // all the deco stops to both the diveSegments-array plus the resultingDecoPlan
                        
                        //Jag måste ju ta bort detta ASCENT-segment från arrayen eftersom decompress-functionen lägger till ett ascent-segment till första stoppet.
                        // Och eftersom decompress-funktionen lägger till nya diveSegment i currentDive.diveSegments så måste jag här först spara undan 
                        // alla efterkommande diveSegments som finns INNAN decompress-funktionen, och ta bort dem från currentDive.diveSegments så de nya segmenten läggs till 
                        // i rätt ordning
                        //ta bort segmentet från arrayen
                        currentDive.diveSegments.remove(diveSegment);
                        //och spara undan de efterföljande segmenten vi redan har
                        ArrayList<DiveSegment> tempDiveSegments = new ArrayList<>();
                        for(int i=currentSegmentIndex; i<currentDive.diveSegments.size();i++)
                        {
                            tempDiveSegments.add(currentDive.diveSegments.get(i));
                        }
                        //ta bort efterföljande segment från arrayen
                        currentDive.diveSegments.removeAll(tempDiveSegments);                        
                        //kolla hur många segment vi har innan decompress-funktionen
                        int numberOfSegmentsBeforeAddingDeco = currentDive.diveSegments.size();
                        //gör dekompressionen
                        decompress((int)Math.round(diveSegment.startDepth), (int)Math.round(diveSegment.endDepth));
                        //kolla nu hur många segment decompress-funktionen lade till, så vi kan uppdatera currentSegmentIndex (för foor-loopen vi är i)
                        int numberOfSegmentsAddedByDeco = currentDive.diveSegments.size() - numberOfSegmentsBeforeAddingDeco;
                        currentSegmentIndex = currentSegmentIndex + numberOfSegmentsAddedByDeco - 1; // -1 för att vi redan tog bort detta ascent-segment från arrayen
                        //nu är vi på diveSegment.endDepth, så lägg nu till de efterföljande segmenten som vi sparade undan
                        currentDive.diveSegments.addAll(tempDiveSegments);     
                    }
                    else
                    {
                        //Deco-ceiling kommer inte överskridas
                        
                        //save the initial offgassing start depth, if needed.
                        if(currentDive.offgassingStartsAtDepth == 0.0 && !ascentDone)
                        { 
                            currentDive.offgassingStartsAtDepth = CALC_START_OF_DECO_ZONE(diveSegment.startDepth);
//System.out.println("DiveSegment.ASCENT: Deco-ceiling won't be breached. Saving offgassing startdepth = " + currentDive.offgassingStartsAtDepth + " FROM: " + diveSegment.startDepth);                            
                        }
                        //uppdatera vävnader och syreexponering.
                        GAS_LOADINGS_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.ascentRate);
//System.out.println(" About to call updateHighestMvaluePercentageAndGradientFactor with endDepth: " + diveSegment.endDepth + " and surface pressure: " + currentDive.surfacePressure);                        
                        updateHighestMvaluePercentageAndGradientFactor(diveSegment.endDepth + currentDive.surfacePressure);
                        DIVEDATA_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.ascentRate, Settings.RMV_During_Dive);
                        //Update this diveSegment's compartment pressures
                        diveSegment.nitrogenCompartmentPressuresAtEndOfDuration = nitrogenCompartmentPressure.clone();
                        diveSegment.heliumCompartmentPressuresAtEndOfDuration = heliumCompartmentPressure.clone();
                        currentDive.diveSegments.set(currentSegmentIndex, diveSegment);
                        currentDive.currentDepth = diveSegment.endDepth;
                        currentDive.currentRunTime += segmentDuration;
                        //kolla ändå om vi nu är troligtvis på ett dekostopp. Kan vi fortsätta till nästa dekodjup utan att gå grundare än nuvarande ceiling?
                        if((diveSegment.endDepth - Settings.decoStopInterval) < current_deco_ceiling)
                        {
                            atManualDecoStop = true;
                        }
                    }
                    ascentDone = true;
                    currentDive.currentDepth = diveSegment.endDepth;
                    break;
                case DiveSegment.DESCENT:
                    logDebug("Processing DESCENT: " + diveSegment.startDepth + "m to " + diveSegment.endDepth + 
                            "m at " + Settings.descentRate + " m/min");
                    GAS_LOADINGS_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.descentRate);
//System.out.println(" DiveSegment.DESCENT: About to call updateHighestMvaluePercentageAndGradientFactor with endDepth: " + diveSegment.endDepth + " and surface pressure: " + currentDive.surfacePressure);                    
                    updateHighestMvaluePercentageAndGradientFactor(diveSegment.endDepth + currentDive.surfacePressure);
                    
                    DIVEDATA_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.descentRate, Settings.RMV_During_Dive);
                    
                    //Update this diveSegment's compartment pressures
                    diveSegment.nitrogenCompartmentPressuresAtEndOfDuration = nitrogenCompartmentPressure.clone();
                    diveSegment.heliumCompartmentPressuresAtEndOfDuration = heliumCompartmentPressure.clone();
                    currentDive.diveSegments.set(currentSegmentIndex, diveSegment);
                    currentDive.currentDepth = diveSegment.endDepth;
                    currentDive.currentRunTime += segmentDuration;
                    ascentDone = false;
                    stoppedAfterAscent = false;
                    currentDive.gradientFactorSlope = 0.0; //if we have done an ascent and then descended again, this will reset the gradient factors again.
                    
                    break;
            }
        }
        
        //If this is a bailout dive and the last segment was on CCR, we need to switch the current gas to the OC-bailout, which has a gasID of the loop gas + 1.
        //Denna förutsättning funkar inte om man har lagt till två eller fler input-segment med samma diluent!
        if(currentDive.isBailoutDive && diveSegment.pO2SetPoint > 0)
        {
            currentDive.activeGasID = currentDive.initialBailoutGasID;
        }
 
        //Now we have processed all the diver-generated input segments.
        // So, decompress up to the surface.
        decompress((int)Math.round(currentDive.currentDepth), 0);

        currentDive.surfacingNitrogenCompartmentPressure = nitrogenCompartmentPressure.clone();
        currentDive.surfacingHeliumCompartmentPressure = heliumCompartmentPressure.clone();
        currentDive.surfacingCNStoxicityPercentage = CNStoxicityPercentage;
        currentDive.surfacingOTUbuildup = OTUbuildup;
        
       
        //And also save the values to the Body object in case we want to do a subsequent dive
//        Body.saveSurfacingValuesForDive(currentDive.missionDiveNumber, nitrogenCompartmentPressure, heliumCompartmentPressure, CNStoxicityPercentage, OTUbuildup);

        // Log final decompression schedule
        if (debugEnabled && debugLog != null) {
            debugLog.println("\n=== FINAL DECOMPRESSION SCHEDULE ===");
            debugLog.println("Total segments: " + currentDive.diveSegments.size());
            debugLog.println("\nDive Segments:");
            for (DiveSegment seg : currentDive.diveSegments) {
                logSegment(seg);
            }
            
            debugLog.println("\nDeco Table:");
            if (currentDive.resultingDecoPlan != null) {
                for (Object obj : currentDive.resultingDecoPlan) {
                    if (obj instanceof DecoTableSegment) {
                        logDecoStop((DecoTableSegment)obj);
                    }
                }
            }
            
            debugLog.println("\nFinal Runtime: " + currentDive.currentRunTime + " min");
            debugLog.println("Total Deco Duration: " + currentDive.totalDecoDuration + " min");
            debugLog.println("Total Ascent Duration: " + currentDive.totalAscentDuration + " min");
            
            logTissues("Final Surface");
            debugLog.flush(); // Don't close, just flush
        }
        
        // CONSOLE OUTPUT FOR DIVE PLAN CAPTURE
        System.out.println("\n=== DECOPLANNER DIVE SCHEDULE OUTPUT ===");
        System.out.println("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        System.out.println("Deco Model: " + (Settings.decoModel == 1 ? "Buhlmann ZH-L16C" : "VPM-B"));
        if (Settings.decoModel == 2) {
            System.out.println("VPM Conservatism: +" + Settings.vpmConservatismSetting);
        } else {
            System.out.println("Gradient Factors: " + (Settings.gradientFactorFirstStop * 100) + "/" + (Settings.gradientFactorSurfacing * 100));
        }
        System.out.println("\nDive Profile Summary:");
        System.out.println("Total Runtime: " + String.format("%.1f", currentDive.currentRunTime) + " min");
        System.out.println("Total Deco Duration: " + String.format("%.1f", currentDive.totalDecoDuration) + " min");
        System.out.println("Total Ascent Duration: " + String.format("%.1f", currentDive.totalAscentDuration) + " min");
        
        System.out.println("\nComplete Dive Schedule:");
        for (DiveSegment seg : currentDive.diveSegments) {
            String gasName = String.format("%.0f/%.0f", seg.oxygenFraction * 100, seg.heliumFraction * 100);
            String segmentTypeStr = seg.segmentType == DiveSegment.ASCENT ? "ASCENT" : 
                                   seg.segmentType == DiveSegment.DESCENT ? "DESCENT" : "CONSTANT_DEPTH";
            System.out.println(String.format("Depth: %.0fm, Duration: %.1f min, Gas: %s, Runtime: %.1f min, Type: %s",
                seg.endDepth, seg.duration, gasName, seg.endRunTime, segmentTypeStr));
        }
        
        if (currentDive.resultingDecoPlan != null) {
            System.out.println("\nDecompression Stops:");
            for (Object obj : currentDive.resultingDecoPlan) {
                if (obj instanceof DecoTableSegment) {
                    DecoTableSegment stop = (DecoTableSegment)obj;
                    String gasName = String.format("%.0f/%.0f", stop.getOxygenPercentage(), stop.getHeliumPercentage());
                    System.out.println(String.format("Stop: %.0fm for %s min on %s (Runtime: %s min)",
                        stop.getDepthNumber(), stop.getDuration(), gasName, stop.getEndRunTime()));
                }
            }
        }
        System.out.println("=== END DIVE SCHEDULE OUTPUT ===\n");
        
        // Also write to stderr to ensure capture
        System.err.println("\n=== DECOPLANNER DIVE SCHEDULE (STDERR) ===");
        System.err.println("Deco Model: " + (Settings.decoModel == 1 ? "Buhlmann ZH-L16C" : "VPM-B"));
        if (Settings.decoModel == 2) {
            System.err.println("VPM Conservatism: +" + Settings.vpmConservatismSetting);
        }
        System.err.println("Total Runtime: " + String.format("%.1f", currentDive.currentRunTime) + " min");
        
        // Also write to a file directly AND create confirmation file
        try {
            // Extract dive profile information for logical filename
            int maxDepth = 0;
            int bottomTime = 0;
            
            // Get original input bottom time (includes descent time) from input segments
            if (currentDive.inputDiveSegments != null && currentDive.inputDiveSegments.size() > 0) {
                DiveInputSegment firstInput = currentDive.inputDiveSegments.get(0);
                maxDepth = firstInput.depthProperty().get();
                bottomTime = firstInput.durationProperty().get(); // This includes descent time as per convention
            } else {
                // Fallback: Find max depth and bottom time from dive segments
                if (currentDive.diveSegments != null && currentDive.diveSegments.size() > 0) {
                    for (DiveSegment seg : currentDive.diveSegments) {
                        if (seg.endDepth > maxDepth) {
                            maxDepth = (int)seg.endDepth;
                        }
                        // Look for constant depth segment as bottom time
                        if (seg.segmentType == DiveSegment.CONSTANT_DEPTH && seg.endDepth == maxDepth) {
                            bottomTime = (int)seg.duration;
                        }
                    }
                }
            }
            
            // Build gas string from available gases (replace "/" with "-" for file names)
            StringBuilder gasStr = new StringBuilder();
            if (currentDive.gases != null) {
                for (Gas g : currentDive.gases) {
                    if (gasStr.length() > 0) gasStr.append("_");
                    if (g.heliumFraction > 0) {
                        gasStr.append((int)(g.oxygenFraction * 100)).append("-").append((int)(g.heliumFraction * 100));
                    } else if (g.oxygenFraction == 0.21) {
                        gasStr.append("Air");
                    } else if (g.oxygenFraction == 1.0) {
                        gasStr.append("O2");
                    } else {
                        gasStr.append("EAN").append((int)(g.oxygenFraction * 100));
                    }
                }
            }
            
            // Create logical filename with gradient factors
            int gfLow = (int)(Settings.gradientFactorFirstStop * 100);
            int gfHigh = (int)(Settings.gradientFactorSurfacing * 100);
            String logicalName = maxDepth + "m" + bottomTime + "min_" + gasStr + "_GF" + gfLow + "_" + gfHigh;
            
            // Add descent rate to filename if it's not the standard 10m/min
            if (Settings.descentRate != 10) {
                logicalName += "_DR" + Settings.descentRate;
            }
            
            String fileName = logicalName + ".txt";
            
            // Write detailed output file
            java.io.PrintWriter fileOut = new java.io.PrintWriter(new java.io.FileWriter(fileName, false));
            fileOut.println("\n=== DECOPLANNER DIVE SCHEDULE ===");
            fileOut.println("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            fileOut.println("Deco Model: " + (Settings.decoModel == 1 ? "Buhlmann ZH-L16C" : "VPM-B"));
            if (Settings.decoModel == 2) {
                fileOut.println("VPM Conservatism: +" + Settings.vpmConservatismSetting);
            } else {
                fileOut.println("Gradient Factors: " + (Settings.gradientFactorFirstStop * 100) + "/" + (Settings.gradientFactorSurfacing * 100));
            }
            fileOut.println("Descent Rate: " + Settings.descentRate + " m/min");
            fileOut.println("Ascent Rate: " + Settings.ascentRate + " m/min");
            fileOut.println("Total Runtime: " + String.format("%.1f", currentDive.currentRunTime) + " min");
            fileOut.println("Total Deco Duration: " + String.format("%.1f", currentDive.totalDecoDuration) + " min");
            
            // Add gas configuration section
            fileOut.println("\n=== GAS CONFIGURATION ===");
            if (currentDive.gases != null) {
                for (int i = 0; i < currentDive.gases.size(); i++) {
                    Gas gas = currentDive.gases.get(i);
                    String gasName = "";
                    if (gas.heliumFraction > 0) {
                        gasName = String.format("%.0f/%.0f", gas.oxygenFraction*100, gas.heliumFraction*100);
                    } else if (gas.oxygenFraction == 0.21) {
                        gasName = "Air";
                    } else if (gas.oxygenFraction > 0.21) {
                        gasName = String.format("EAN%.0f", gas.oxygenFraction*100);
                    } else {
                        gasName = String.format("%.0f%% O2", gas.oxygenFraction*100);
                    }
                    
                    if (gas.gasType == Gas.DIVE_GAS) {
                        fileOut.println("Bottom Gas: " + gasName + 
                            String.format(" (%.0f%% O2, %.0f%% He, %.0f%% N2)", 
                            gas.oxygenFraction*100, gas.heliumFraction*100, gas.nitrogenFraction*100));
                    } else {
                        fileOut.println("Deco Gas " + i + ": " + gasName + 
                            String.format(" (%.0f%% O2, %.0f%% He, %.0f%% N2) @ %dm switch depth", 
                            gas.oxygenFraction*100, gas.heliumFraction*100, gas.nitrogenFraction*100, gas.switchDepth));
                    }
                }
            }
            
            fileOut.println("\n=== DECOMPRESSION SCHEDULE ===");
            if (currentDive.resultingDecoPlan != null) {
                for (Object obj : currentDive.resultingDecoPlan) {
                    if (obj instanceof DecoTableSegment) {
                        DecoTableSegment stop = (DecoTableSegment)obj;
                        
                        // Format gas name
                        String gasName = "";
                        double o2 = stop.getOxygenPercentage();
                        double he = stop.getHeliumPercentage();
                        if (he > 0) {
                            gasName = String.format("(%.0f/%.0f)", o2, he);
                        } else if (o2 == 21) {
                            gasName = "(Air)";
                        } else if (o2 > 21) {
                            gasName = String.format("(EAN%.0f)", o2);
                        } else {
                            gasName = String.format("(%.0f%% O2)", o2);
                        }
                        
                        fileOut.println(String.format("%.0fm for %s min %s", 
                            stop.getDepthNumber(), stop.getDuration(), gasName));
                    }
                }
            }
            fileOut.close();
            System.out.println("Dive schedule written to: " + fileName);
            
            // Write CONFIRMATION file that's easy to see
            String confirmFile = "LAST_BUHLMANN_CAPTURED.txt";
            java.io.PrintWriter confirmOut = new java.io.PrintWriter(new java.io.FileWriter(confirmFile, false));
            confirmOut.println("=== BUHLMANN DIVE PLAN SUCCESSFULLY CAPTURED ===");
            confirmOut.println("Time: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
            confirmOut.println("GF: " + gfLow + "/" + gfHigh);
            confirmOut.println("Runtime: " + (int)currentDive.currentRunTime + " min");
            confirmOut.println("✓ CAPTURED IN: " + fileName);
            confirmOut.close();
            
            // Also append to a running log
            java.io.PrintWriter logOut = new java.io.PrintWriter(new java.io.FileWriter("all_captures.log", true));
            logOut.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " | " + fileName + " | ✓ CAPTURED");
            logOut.close();
            
        } catch (Exception e) {
            System.err.println("Error writing dive schedule to file: " + e.getMessage());
        }

        return currentDive;
    }
	

	
    /*===============================================================================
     SUBROUTINE CALC_DECO_CEILING
     Purpose: This subprogram calculates the deco ceiling (the safe ascent
     depth) in each compartment, based on M-values modifed by gradient factors,
     and then finds the deepest deco ceiling across all compartments. This
     deepest value (Deco Ceiling Depth) is then used by the Decompression Stop
     subroutine to determine the actual deco schedule.
    =============================================================================== */
    private static double CALC_DECO_CEILING()
    {
        //===============================================================================
        // ARGUMENTS
        //===============================================================================
        double Deco_Ceiling_Depth; //output
        //===============================================================================
        // LOCAL VARIABLES
        //===============================================================================
        double inertGasPressure;
        double Tolerated_Ambient_Pressure;
        double Coefficient_A, Coefficient_B;
        //===============================================================================
        // LOCAL ARRAYS
        //===============================================================================
        double[] Compartment_Deco_Ceiling = new double[16];
        //===============================================================================
        // GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        //===============================================================================
        //Barometric_Pressure;
        //COMMON /Block_18/ Barometric_Pressure
        //COMMON /Block_37/ Gradient_Factor
        //===============================================================================
        // GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        //===============================================================================
        // Helium_Pressure(16), Nitrogen_Pressure(16) //input
        //COMMON /Block_3/ Helium_Pressure, Nitrogen_Pressure
        //REAL Coefficient_AHE(16), Coefficient_BHE(16)
        //REAL Coefficient_AN2(16), Coefficient_BN2(16)
        //COMMON /Block_35/ Coefficient_AHE, Coefficient_BHE, Coefficient_AN2, Coefficient_BN2
        //===============================================================================
        // CALCULATIONS
        //===============================================================================
        double PHe;
        double PN2;
        for(int cpt=0; cpt<16; cpt++)
        {
            PHe = heliumCompartmentPressure[cpt];
            PN2 = nitrogenCompartmentPressure[cpt];
            inertGasPressure = PHe + PN2;
            Coefficient_A = (PHe*He_a[cpt] + PN2*N2_a[cpt])/ inertGasPressure;
            Coefficient_B = (PHe*He_b[cpt] + PN2*N2_b[cpt])/ inertGasPressure;
            //Följande beräkning bör komma från mValue-formeln.  mValue = (ambientPressure / b) + a
            Tolerated_Ambient_Pressure = (inertGasPressure - Coefficient_A*Currently_Max_Allowed_Gradient_Factor)/(Currently_Max_Allowed_Gradient_Factor/Coefficient_B - Currently_Max_Allowed_Gradient_Factor + 1.0);
            //===============================================================================
            // The tolerated ambient pressure cannot be less than zero absolute, i.e.,
            // the vacuum of outer space!
            //===============================================================================
            if(Tolerated_Ambient_Pressure < 0.0)
            {
                Tolerated_Ambient_Pressure = 0.0;
            }

            //===============================================================================
            // The Deco Ceiling Depth is computed in a loop after all of the individual
            // compartment deco ceilings have been calculated. It is important that the
            // Deco Ceiling Depth (max deco ceiling across all compartments) only be
            // extracted from the compartment values and not be compared against some
            // initialization value. For example, if MAX(Deco_Ceiling_Depth . .) was
            // compared against zero, this could cause a program lockup because sometimes
            // the Deco Ceiling Depth needs to be negative (but not less than absolute
            // zero) in order to decompress to the last stop at zero depth.
            //===============================================================================
            Compartment_Deco_Ceiling[cpt] =  Tolerated_Ambient_Pressure - currentDive.surfacePressure;
        }
        //Deco_Ceiling_Depth = Compartment_Deco_Ceiling(1)
        //DO I = 2,16
        //Deco_Ceiling_Depth = MAX(Deco_Ceiling_Depth, Compartment_Deco_Ceiling(I))
        //END DO
        Arrays.sort(Compartment_Deco_Ceiling);
        Deco_Ceiling_Depth = Compartment_Deco_Ceiling[15]; //greatest value

        return Deco_Ceiling_Depth;
    }
     
	
	
    /*===============================================================================
     SUBROUTINE CALC_START_OF_DECO_ZONE
     Purpose: This subroutine uses the Bisection Method to find the depth at
     which the leading compartment just enters the decompression zone.
     Source: "Numerical Recipes in Fortran 77", Cambridge University Press,
     1992.
    =============================================================================== */
    private static double CALC_START_OF_DECO_ZONE(double Starting_Depth)
    {
        //===============================================================================
        // LOCAL VARIABLES
        //===============================================================================
        int Rate = Math.negateExact(Settings.ascentRate);
        double Initial_Helium_Pressure, Initial_Nitrogen_Pressure;
        double Initial_Inspired_He_Pressure;
        double Initial_Inspired_N2_Pressure;
        double Time_to_Start_of_Deco_Zone, Helium_Rate, Nitrogen_Rate;
        double Starting_Ambient_Pressure;
        double Cpt_Depth_Start_of_Deco_Zone, Low_Bound, High_Bound;
        double High_Bound_Helium_Pressure, High_Bound_Nitrogen_Pressure;
        double Mid_Range_Helium_Pressure, Mid_Range_Nitrogen_Pressure;
        double Function_at_High_Bound, Function_at_Low_Bound, Mid_Range_Time;
        double Function_at_Mid_Range, Differential_Change, Last_Diff_Change;

        //===============================================================================
        // CALCULATIONS
        // First initialize some variables
        //===============================================================================
        double Depth_Start_of_Deco_Zone = 0.0;
        Starting_Ambient_Pressure = Starting_Depth + Settings.getSurfacePressure(); //2022-09-03: surfacepressure saknades här..! Under 28m djup blev denna funktion helt fel och visade samma offgas-djup som startdjupet.
        Initial_Inspired_He_Pressure = (Starting_Ambient_Pressure - Settings.getWaterVaporPressure())*currentDive.getCurrentHeliumFraction();
        Initial_Inspired_N2_Pressure = (Starting_Ambient_Pressure - Settings.getWaterVaporPressure())*currentDive.getCurrentNitrogenFraction();
        Helium_Rate = Rate * currentDive.getCurrentHeliumFraction();
        Nitrogen_Rate = Rate * currentDive.getCurrentNitrogenFraction();
        //===============================================================================
        // ESTABLISH THE BOUNDS FOR THE ROOT SEARCH USING THE BISECTION METHOD
        // AND CHECK TO MAKE SURE THAT THE ROOT WILL BE WITHIN BOUNDS. PROCESS
        // EACH COMPARTMENT INDIVIDUALLY AND FIND THE MAXIMUM DEPTH ACROSS ALL
        // COMPARTMENTS (LEADING COMPARTMENT)
        // In this case, we are solving for time - the time when the gas tension in
        // the compartment will be equal to ambient pressure. The low bound for time
        // is set at zero and the high bound is set at the time it would take to
        // ascend to zero ambient pressure (absolute). Since the ascent rate is
        // negative, a multiplier of -1.0 is used to make the time positive. The
        // desired point when gas tension equals ambient pressure is found at a time
        // somewhere between these endpoints. The algorithm checks to make sure that
        // the solution lies in between these bounds by first computing the low bound
        // and high bound function values.
        //===============================================================================
        Low_Bound = 0.0;
        High_Bound = -1.0*(Starting_Ambient_Pressure/Rate);
        for(int i=0; i<16; i++) //DO 200 I = 1,16
        {
            Initial_Helium_Pressure = heliumCompartmentPressure[i]; // body.heliumCompartmentPressure[i];
            Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[i]; // body.nitrogenCompartmentPressure[i];
            Function_at_Low_Bound = Initial_Helium_Pressure + Initial_Nitrogen_Pressure - Starting_Ambient_Pressure;
            High_Bound_Helium_Pressure = schreinerEquation(Initial_Inspired_He_Pressure, Helium_Rate, High_Bound, kHe[i], Initial_Helium_Pressure);
            High_Bound_Nitrogen_Pressure = schreinerEquation(Initial_Inspired_N2_Pressure, Nitrogen_Rate, High_Bound, kN2[i], Initial_Nitrogen_Pressure);
            Function_at_High_Bound = High_Bound_Helium_Pressure + High_Bound_Nitrogen_Pressure;
            if((Function_at_High_Bound * Function_at_Low_Bound) >= 0.0)
            {
                //This compartment is already supersaturated from the start (i.e. it has higher pressure than Starting_Ambient_Pressure
                return Starting_Depth; //eller?
                //PRINT *,'ERROR! ROOT IS NOT WITHIN BRACKETS'
                //PAUSE
//                System.err.println("ERROR 694! ROOT IS NOT WITHIN BRACKETS");
            }
            //===============================================================================
            // APPLY THE BISECTION METHOD IN SEVERAL ITERATIONS UNTIL A SOLUTION WITH
            // THE DESIRED ACCURACY IS FOUND
            // Note: the program allows for up to 100 iterations. Normally an exit will
            // be made from the loop well before that number. If, for some reason, the
            // program exceeds 100 iterations, there will be a pause to alert the user.
            //===============================================================================
            if(Function_at_Low_Bound < 0.0)
            {
                Time_to_Start_of_Deco_Zone = Low_Bound;
                Differential_Change = High_Bound - Low_Bound;
            }
            else
            {
                Time_to_Start_of_Deco_Zone = High_Bound;
                Differential_Change = Low_Bound - High_Bound;
            }
            boolean exceeded_max_iterations = true;
            for(int j=1; j<=100; j++) //DO 150 J = 1, 100
            {
                Last_Diff_Change = Differential_Change;
                Differential_Change = Last_Diff_Change*0.5;
                Mid_Range_Time = Time_to_Start_of_Deco_Zone + Differential_Change;
                Mid_Range_Helium_Pressure = schreinerEquation(Initial_Inspired_He_Pressure, Helium_Rate, Mid_Range_Time, kHe[i], Initial_Helium_Pressure);
                Mid_Range_Nitrogen_Pressure = schreinerEquation(Initial_Inspired_N2_Pressure, Nitrogen_Rate, Mid_Range_Time, kN2[i], Initial_Nitrogen_Pressure);
                Function_at_Mid_Range = Mid_Range_Helium_Pressure + Mid_Range_Nitrogen_Pressure - (Starting_Ambient_Pressure + Rate*Mid_Range_Time);
                if(Function_at_Mid_Range < 0.0) Time_to_Start_of_Deco_Zone = Mid_Range_Time;
                if((Math.abs(Differential_Change) < 0.001) || (Function_at_Mid_Range == 0.0))
                {
                    exceeded_max_iterations = false;
                    break;
                }
            } //150 CONTINUE
            if(exceeded_max_iterations)
            {
                //PRINT *,'ERROR! ROOT SEARCH EXCEEDED MAXIMUM ITERATIONS'
                //PAUSE
                System.err.println("ERROR 733! ROOT SEARCH EXCEEDED MAXIMUM ITERATIONS");
            }
            //===============================================================================
            // When a solution with the desired accuracy is found, the program jumps out
            // of the loop to Line 170 and assigns the solution value for the individual
            // compartment.
            //===============================================================================
            Cpt_Depth_Start_of_Deco_Zone = (Starting_Ambient_Pressure + Rate*Time_to_Start_of_Deco_Zone) - surfacePressure;
            //===============================================================================
            // The overall solution will be the compartment with the maximum depth where
            // gas tension equals ambient pressure (leading compartment).
            //===============================================================================
            Depth_Start_of_Deco_Zone = Math.max(Depth_Start_of_Deco_Zone, Cpt_Depth_Start_of_Deco_Zone);
        } //200 CONTINUE
        //===============================================================================
        // END OF SUBROUTINE
        //===============================================================================
        //RETURN
//System.out.println("------ CALC_START_OF_DECO_ZONE: " + Depth_Start_of_Deco_Zone + " From " + Starting_Depth);        
        return Depth_Start_of_Deco_Zone;
        //END
    }	
	
    /**
     * perform decompression up to ending_depth and add all the decompression 
     *  stops to both the diveSegments-array plus the resultingDecoPlan
     * @param starting_depth
     * @param ending_depth 
     */
    private static void decompress(int starting_depth, int ending_depth)
    {

// System.out.println("DP4 tissue pressures before ascent:");
// System.out.println(Arrays.toString(heliumCompartmentPressure));
// System.out.println(Arrays.toString(nitrogenCompartmentPressure));
        
/*
Remember that this method can be called in the middle of a dive, before all input-segments are processed.        
I can skip Rate_Change, and just use ascent/descent-rates in Settings.
Depth_Change can be skipped, since I KNOW that there is no user-input between starting_depth and ending_depth in this method/function.
Step_Size_Change I can replace by checking Settings.smallDecoStopIntervalShallow when deco stop depth is 9m/30'
Mix_Change can be replaced with currentDive.gases?
*/

//        int[] Mix_Change = new int[15];
//        double[] Depth_Change = new double[15];
//        int[] Rate_Change = new int[15];
//        int[] Step_Size_Change = new int[15];

/*        
        int Number_of_Changes = decoGases.size() + currentDive.inputDiveSegments.size() - 1; //there is no change if we have ONE inputDiveSegment
        int inputCounter = 0;

        if(Number_of_Changes > 0 || Settings.smallDecoStopIntervalShallow)
        {
            boolean entryMadeThisIteration;

            for(int i=0; i<Number_of_Changes; i++)
            {
                entryMadeThisIteration = false;

                //kolla efter ändring av stop-size interval så jag kan inkludera det i xxx_Change-arrayerna..
                if(Settings.smallDecoStopIntervalShallow)
                {
                    //kolla om jag ska lägga in en entry i alla X_Change-arrayerna mellan denna och nästa loop-varv
                    if(decoGases.get(i).getSwitchDepth() == Settings.decoStopIntervalChangeDepth)
                    {
                        Mix_Change[inputCounter] = i; //gasens index i decoGases-arrayen
                        Depth_Change[inputCounter] = decoGases.get(i).getSwitchDepth();
                        Rate_Change[inputCounter] = Settings.ascentRate;
                        Step_Size_Change[inputCounter] = Settings.decoStopIntervalShallow;
                        entryMadeThisIteration = true;
                        inputCounter++;
                    }
                    else if(decoGases.get(i).getSwitchDepth() < Settings.decoStopIntervalChangeDepth && decoGases.get(i+1).getSwitchDepth() > Settings.decoStopIntervalChangeDepth)
                    {
                        Mix_Change[inputCounter] = Mix_Change[inputCounter-1]; //gasens index i decoGases-arrayen
                        Depth_Change[inputCounter] = Settings.decoStopIntervalChangeDepth;
                        Rate_Change[inputCounter] = Rate_Change[inputCounter-1];
                        Step_Size_Change[inputCounter] = Settings.decoStopIntervalShallow;
                        entryMadeThisIteration = false;
                        inputCounter++;
                    }
                }
                if(!entryMadeThisIteration)
                {
                    Mix_Change[inputCounter] = i; //gasens index i decoGases-arrayen
                    Depth_Change[inputCounter] = decoGases.get(i).getSwitchDepth(); det behöver inte va decoGases som är ifylld för att komma hit, så den kan va tom!
                    Rate_Change[inputCounter] = Settings.ascentRate;
                    Step_Size_Change[inputCounter] = Settings.decoStopInterval;
                    inputCounter++;
                }
            }
        }
        if(inputCounter != 0)
        {
            Number_of_Changes = inputCounter;
        }
        //Reduce the size of the _Change-arrays
        Mix_Change = java.util.Arrays.copyOf(Mix_Change, Number_of_Changes);
        Depth_Change = java.util.Arrays.copyOf(Depth_Change, Number_of_Changes);
        Rate_Change = java.util.Arrays.copyOf(Rate_Change, Number_of_Changes);
        Step_Size_Change = java.util.Arrays.copyOf(Step_Size_Change, Number_of_Changes);		
*/
        int Starting_Depth = starting_depth;
        //currentDive.activeGasID = 0;
        currentDive.currentRate = Settings.ascentRate;
        currentDive.currentDecoStopIntervalSize = Settings.decoStopInterval;
//        Last_Run_Time = 0.0;

/*        if(Number_of_Changes != 0)
        {
            currentDive.activeGasID = Mix_Change[1];
            currentDive.currentRate = Rate_Change[1];
            currentDive.currentDecoStopIntervalSize = Step_Size_Change[1];
        }        
*/        
        /*===============================================================================
         CALCULATE THE DEPTH WHERE THE DECOMPRESSION ZONE BEGINS FOR THIS PROFILE
         BASED ON THE INITIAL ASCENT PARAMETERS AND WRITE THE DEEPEST POSSIBLE
         DECOMPRESSION STOP DEPTH TO THE OUTPUT FILE
         Knowing where the decompression zone starts is very important. Below
         that depth there is no possibility for bubble formation because there
         will be no supersaturation gradients. Deco stops should never start
         below the deco zone. The deepest possible stop deco stop depth is
         defined as the next "standard" stop depth above the point where the
         leading compartment enters the deco zone. Thus, the program will not
         base this calculation on step sizes larger than 10 fsw or 3 msw. The
         deepest possible stop depth is not used in the program, per se, rather
         it is information to tell the diver where to start putting on the brakes
         during ascent. This should be prominently displayed by any deco program.
        ===============================================================================*/
        //Debug, diveSegments
        /*
        System.out.println("At " + starting_depth + "m, about to start ascent :");
        for(int i=0; i<16; i++)
        {
            System.out.println("Cmp " + i + ": " + nitrogenCompartmentPressure[i]);
        } 
        */
        double Depth_Start_of_Deco_Zone = CALC_START_OF_DECO_ZONE(starting_depth);
//System.out.println("In decompress(...) - Depth_Start_of_Deco_Zone: " + Depth_Start_of_Deco_Zone + ", starting from " + starting_depth);
        if(currentDive.offgassingStartsAtDepth == 0.0)
        {
            currentDive.offgassingStartsAtDepth = Depth_Start_of_Deco_Zone;
        }
        /*
        double Deepest_Possible_Stop_Depth;
        if(Settings.metric)
        {
            if(Settings.decoStopInterval < 3.0)
            {
                double Rounding_Operation1 = (Depth_Start_of_Deco_Zone/Settings.decoStopInterval) - 0.5;
                Deepest_Possible_Stop_Depth = Math.round(Rounding_Operation1) * Settings.decoStopInterval;
            }
            else
            {
                double Rounding_Operation1 = (Depth_Start_of_Deco_Zone/3.0) - 0.5;
                Deepest_Possible_Stop_Depth = Math.round(Rounding_Operation1) * 3.0;
            }			
        }
        else
        {
            if(Settings.decoStopInterval < 10.0)
            {
                double Rounding_Operation1 = (Depth_Start_of_Deco_Zone/Settings.decoStopInterval) - 0.5;
                Deepest_Possible_Stop_Depth = Math.round(Rounding_Operation1) * Settings.decoStopInterval;
            }
            else
            {
                double Rounding_Operation1 = (Depth_Start_of_Deco_Zone/10.0) - 0.5;
                Deepest_Possible_Stop_Depth = Math.round(Rounding_Operation1) * 10.0;
            }
        } 
        */
        /*===============================================================================
         CALCULATE CURRENT DECO CEILING AND SET FIRST DECO STOP. CHECK TO MAKE
         SURE THAT SELECTED STEP SIZE WILL NOT ROUND UP FIRST STOP TO A DEPTH THAT
         IS BELOW THE DECO ZONE.
        ===============================================================================*/
        
//System.out.println("In decompress(...) - Currently_Max_Allowed_Gradient_Factor: " + Currently_Max_Allowed_Gradient_Factor);        
        double Deco_Ceiling_Depth = CALC_DECO_CEILING();
//System.out.println("In decompress(...) - Deco ceiling: " + Deco_Ceiling_Depth);        
        double Deco_Stop_Depth;
        boolean manually_violating_deco_ceiling = false; //2022-01-22: introduced in version 4.3.6, as some profiles ran into the ERROR 924 below
        if(Deco_Ceiling_Depth <= 0.0)
        {
            Deco_Stop_Depth = 0.0;
        }
        else
        {
            //check if we are super close, but deeper than a stop depth (like 30.08 feet for example).
            double remainder = Deco_Ceiling_Depth % Settings.decoStopInterval;
            //remainder *= 10; //make it bigger, to compare with the stop interval. I first compared the remainder to 10% of the decoStopInterval, but the comparison didn't work for some reason.
//System.out.println("In decompress(...) - remainder: " + remainder);
            //See if the remainder is less than 10 percent of 10'/3m
//System.out.println("In decompress(...) - 10% of decoStopInterval: " + (Settings.decoStopInterval / 10.0));            
            if(remainder <= (Settings.decoStopInterval * 0.10))
            {
                Deco_Stop_Depth = Math.floor(Deco_Ceiling_Depth); //Since v4.3.4
                manually_violating_deco_ceiling = true;
//System.out.println("In decompress(...) - Deco_Stop_Depth rounded down: " + Deco_Stop_Depth);
            }
            else
            {
                double Rounding_Operation2 = (Deco_Ceiling_Depth/Settings.decoStopInterval) + 0.5;
                Deco_Stop_Depth = Math.round(Rounding_Operation2) * Settings.decoStopInterval;
//System.out.println("In decompress(...) - Deco_Stop_Depth rounded up to: " + Deco_Stop_Depth);
            }
        }
//System.out.println("In decompress(...) - Stop depth: " + Deco_Stop_Depth);
        if(Deco_Stop_Depth > Depth_Start_of_Deco_Zone)
        {
            //2021-07-01: if we have done a series of manual decompression stops earlier we need to adjust the max allowed gradient factor
//System.out.println("Currently_Max_Allowed_Gradient_Factor: " + Currently_Max_Allowed_Gradient_Factor);
//            if(currentDive.gradientFactorSlope != 0.0)
//            {
//                Currently_Max_Allowed_Gradient_Factor = (Deco_Stop_Depth - Settings.decoStopInterval) * currentDive.gradientFactorSlope + Settings.gradientFactorSurfacing;
//            }

            
            //System.err.println("ERROR 907! STEP SIZE IS TOO LARGE TO DECOMPRESS");
//System.out.println("ERROR 907! STEP SIZE IS TOO LARGE TO DECOMPRESS");            
            //System.err.println("PROGRAM TERMINATED");
            //TODO: lägg till felhantering som denna, så programmet visar meddelandet.
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(Main.myStage); //this should set the dialog to use the same icon as the main program icon
            alert.setTitle("First stop conflict");
            alert.setHeaderText("Please raise your GF Lo");
            if(currentDive.isPartOfTables)
            {
                alert.setContentText("For " + starting_depth + (Settings.metric ? " m" : " ft") + " / " + currentDive.tablesBottomTime + " minutes :\nOff-gassing starts at " + Util.roundToOneDecimal(Depth_Start_of_Deco_Zone) + (Settings.metric ? " m" : " ft") + ", while the chosen Gradient Factor\nresults in an initial deco ceiling of " + Util.roundToTwoDecimals(Deco_Ceiling_Depth) + (Settings.metric ? " m" : " ft") + ",\nwhich requires a first stop at " + Deco_Stop_Depth + (Settings.metric ? " m" : " ft") + ".\nPlease increase GF Lo and try again.");
            }
            else
            {
                alert.setContentText("For ascent from " + starting_depth + (Settings.metric ? " m" : " ft") + ":\nOff-gassing starts at " + Util.roundToOneDecimal(Depth_Start_of_Deco_Zone) + (Settings.metric ? " m" : " ft") + ", while the chosen Gradient Factor\nresults in an initial deco ceiling of " + Util.roundToTwoDecimals(Deco_Ceiling_Depth) + (Settings.metric ? " m" : " ft") + ",\nwhich requires a first stop at " + Deco_Stop_Depth + (Settings.metric ? " m" : " ft") + ".\nPlease increase GF Lo and try again.");
            }
            Optional<ButtonType> result = alert.showAndWait();
            if(result.isPresent() && result.get() == ButtonType.OK) 
            {
                currentDive.error = true;
                return;
            }
        }
        /*===============================================================================
         PERFORM A SEPARATE "PROJECTED ASCENT" OUTSIDE OF THE MAIN PROGRAM TO MAKE
         SURE THAT AN INCREASE IN GAS LOADINGS DURING ASCENT TO THE FIRST STOP WILL
         NOT CAUSE A VIOLATION OF THE DECO CEILING. IF SO, ADJUST THE FIRST STOP
         DEEPER BASED ON STEP SIZE UNTIL A SAFE ASCENT CAN BE MADE.
         Note: this situation is a possibility when ascending from extremely deep
         dives or due to an unusual gas mix selection.
         CHECK AGAIN TO MAKE SURE THAT ADJUSTED FIRST STOP WILL NOT BE BELOW THE
         DECO ZONE.
        ===============================================================================*/
        if(!manually_violating_deco_ceiling)
        {
            Deco_Stop_Depth = PROJECTED_ASCENT(Starting_Depth, Math.negateExact(Settings.ascentRate), Deco_Stop_Depth, Settings.decoStopInterval);
        }
//System.out.println("In decompress(...) - Stop depth, after projected ascent: " + Deco_Stop_Depth);        
        if(Deco_Stop_Depth > Depth_Start_of_Deco_Zone)
        {
            System.out.println("ERROR 924! STEP SIZE IS TOO LARGE TO DECOMPRESS");
            System.out.println("PROGRAM TERMINATED");
            //TODO: lägg till felhantering som denna, så programmet visar meddelandet.
            return; //STOP
        }
        
        //v4.0.2: Kolla om 6m ska va sista stoppet, och om första stoppet nu är satt till 3m.
        boolean firstStopIsLastStop = false;
        if(Settings.lastStopDoubleInterval && Deco_Stop_Depth <= (2 * Settings.decoStopInterval) && Deco_Stop_Depth > 0)
        {
            Deco_Stop_Depth = 2 * Settings.decoStopInterval;
            firstStopIsLastStop = true;
//System.out.println("first stop is last stop.");            
        }
        
        
        //if we have done a series of decompression stops earlier we need to adjust the max allowed gradient factor
        if(currentDive.gradientFactorSlope != 0.0)
        {
            Currently_Max_Allowed_Gradient_Factor = (Deco_Stop_Depth - Settings.decoStopInterval) * currentDive.gradientFactorSlope + Settings.gradientFactorSurfacing;
        }
        //Kolla nu om det faktiskt går att sätta första stoppet ett stopp-intervall grundare, eftersom de snabbaste vävnaderna vädrar ut gas på vägen till första stoppet
        // och därmed höjer deco-ceiling. DecoPlanner 3 får ju i praktiken denna effekten eftersom de utvärderar 3m i taget och ser om gradient factor överskrids vid varje 3m-intervall
        //Kolla bara detta om detta faktiskt är första dekompressionen detta dyk. Om vi redan har gjort manuella stop vill vi skippa detta för att inte skippa ett stopp och få förvirrande tabeller.
        //Men, om vi redan är i deko så vill vi inte förlänga ett stopp som vi redan gjort, så kolla om vi föreslås att stanna på djupet vi är på.
        if(!firstStopIsLastStop && Deco_Stop_Depth > 0) //2022-09-03, lade till kollen && Deco_Stop_Depth > 0
        {
            if(currentDive.gradientFactorSlope == 0.0 || Deco_Stop_Depth == starting_depth)
            {
                double testDecoDepth = PROJECTED_ASCENT(Starting_Depth, Math.negateExact(Settings.ascentRate), Deco_Stop_Depth - Settings.decoStopInterval, Settings.decoStopInterval);
                if(testDecoDepth < Deco_Stop_Depth)
                {
//System.out.println("japp " + testDecoDepth);
                    Deco_Stop_Depth = testDecoDepth;
                    if(currentDive.gradientFactorSlope != 0.0)
                    {
                        //And since Deco_Stop_Depth change, we also need to adjust the allowed gradient factor
                        Currently_Max_Allowed_Gradient_Factor = (Deco_Stop_Depth - Settings.decoStopInterval) * currentDive.gradientFactorSlope + Settings.gradientFactorSurfacing;
                    }
                }
            }
        }
        /*===============================================================================
         SET GRADIENT FACTOR SLOPE
        ===============================================================================*/
        if(currentDive.gradientFactorSlope == 0.0)
        {
            if(Deco_Stop_Depth > 0.0)
            {
                currentDive.gradientFactorSlope = (Settings.gradientFactorSurfacing - Settings.gradientFactorFirstStop)/(0.0 - Deco_Stop_Depth);
            }
        }
        /*===============================================================================
         DECO STOP LOOP BLOCK FOR DECOMPRESSION SCHEDULE
        ===============================================================================*/
        double Gradient_Factor_Current_Stop;
        double Gradient_Factor_Next_Stop;
        double Next_Stop;
        double decoStopInterval = Settings.decoStopInterval;
        double averageAmbientPressureATA;
        DecoTableSegment decoSegment;
        DiveSegment ascentSegment;
        DiveSegment stopSegment;
        boolean firstStop = true;
        //In the following while-loop we will process one ascent-segment and one stop-segment
        // until we reach the ending_depth.
        while(true) //loop will run continuously until there is an exit statement
        {
            //We start with the ascent-segment leading up to the deco stop            
            GAS_LOADINGS_ASCENT_DESCENT(Starting_Depth, Deco_Stop_Depth, Settings.ascentRate);
//System.out.println(" Row 1472: About to call updateHighestMvaluePercentageAndGradientFactor with Deco_Stop_Depth: " + Deco_Stop_Depth + " and surface pressure: " + currentDive.surfacePressure);            
            updateHighestMvaluePercentageAndGradientFactor(Deco_Stop_Depth + currentDive.surfacePressure);
            DIVEDATA_ASCENT_DESCENT(Starting_Depth, Deco_Stop_Depth, Settings.ascentRate, Settings.RMV_During_Deco);

            // Skapa nu ett ascent-diveSegment och lägg till
            ascentSegment = new DiveSegment();
            ascentSegment.segmentType = DiveSegment.ASCENT;
            ascentSegment.startDepth = currentDive.currentDepth;
            ascentSegment.startRunTime = currentDive.currentRunTime;
            ascentSegment.duration = (Deco_Stop_Depth - Starting_Depth) / Math.negateExact(Settings.ascentRate);
            ascentSegment.endDepth = Deco_Stop_Depth;
            ascentSegment.gasID = currentDive.activeGasID;
            ascentSegment.oxygenFraction = currentDive.diveSegments.get(currentDive.diveSegments.size() - 1).oxygenFraction; //same gas as on the previous segment
            ascentSegment.heliumFraction = currentDive.diveSegments.get(currentDive.diveSegments.size() - 1).heliumFraction;
            ascentSegment.endRunTime = ascentSegment.startRunTime + ascentSegment.duration;
            ascentSegment.nitrogenCompartmentPressuresAtEndOfDuration = nitrogenCompartmentPressure.clone();
            ascentSegment.heliumCompartmentPressuresAtEndOfDuration = heliumCompartmentPressure.clone();
            //Calculate the gas volume used during this segment
            averageAmbientPressureATA = ((Starting_Depth + Deco_Stop_Depth)/2 + currentDive.surfacePressure)/Settings.Depth_Per_ATM;
            ascentSegment.gasVolumeUsedDuringSegment = Settings.RMV_During_Deco * averageAmbientPressureATA * ascentSegment.duration;
            //Add the segment
            currentDive.diveSegments.add(ascentSegment);
            //Update the current depth and runtime
            currentDive.currentDepth = Deco_Stop_Depth;
            currentDive.currentRunTime = ascentSegment.endRunTime;
            currentDive.totalAscentDuration += ascentSegment.duration;
            //Don't include the first ascent segment in the total deco time.
            if(!firstStop)
            {
                currentDive.totalDecoDuration += ascentSegment.duration;
            }
//System.out.println("adding ascent, " + ascentSegment.duration + " minutes. " + ascentSegment.startDepth + " to " + ascentSegment.endDepth);
           
            //See if we are done
            if(Deco_Stop_Depth <= ending_depth) break;
            
            //Now deal with the constant-depth deco stop
            //See if it's time to switch gases or decoStopInterval..
            Gas decoGas;
            Gas currentGas = currentDive.getCurrentGas(); //added in version 4.6.3
            //Loop through all gases and see if we need to switch to any of them
            
//System.out.println("At deco depth: " + Deco_Stop_Depth + " Going to check if we need to switch to a new deco gas. Currently active gasID = " + currentDive.activeGasID + " (BuhlmannDeco.java, rad 1534)");            
            for(int gasID=0; gasID<currentDive.gases.size(); gasID++)
            {
                decoGas = currentDive.gases.get(gasID);
//System.out.println("  Checking " + decoGas.getGasText() + " gasID=" + gasID + " switchDepth: " + decoGas.switchDepth + " Gas type=" + decoGas.gasType + " (BuhlmannDeco.java, rad 1538)");                
                //Check that
                //1) Current depth is less or equal to the switchdepth of the gas we're now checking
                //2) The switchdepth of the gas we're now checking is less/shallower than the switchdepth of the gas we're currently using
                //3) The gas we're checking is a deco gas
                //4) The gas we're now checking is not the one we're already using
                if(Deco_Stop_Depth <= decoGas.switchDepth && decoGas.gasType == Gas.DECO_GAS && currentDive.activeGasID != gasID)
                {                    
                    //Now we have to check that, IF we are on a deco gas already, make sure the switch depth of the new gas is less than the one we are currently on
                    if(currentGas.gasType == Gas.DIVE_GAS || (currentGas.gasType == Gas.DECO_GAS && decoGas.switchDepth < currentGas.switchDepth))
                    {
//System.out.println("Switching to this gas. gasID = " + gasID);
                        //switch to this deco gas
                        currentDive.activeGasID = gasID;
                        //check if we need to change the minimumStopTime along with this gas
                        if(decoGas.minimumDecoStopTime > 0)
                        {
                            currentMinimumDecoStopDuration = decoGas.minimumDecoStopTime;
                        }
                        else
                        {
                            //If this deco gas has not defined a minimum stop time, we use the default minimum time, from Settings.
                            currentMinimumDecoStopDuration = Math.round(Settings.Minimum_Deco_Stop_Time);
                        }
                    }
                }
            }
            //Now check if we need to change our decoStopInterval
            /*
            if(Settings.metric && Deco_Stop_Depth == 9.0 && Settings.smallDecoStopIntervalShallow)
            {
                decoStopInterval = 1; //1 meter
            }
            else if(!Settings.metric && Deco_Stop_Depth == 30 && Settings.smallDecoStopIntervalShallow)
            {
                decoStopInterval = 1; //1 foot
            }
            */

            Gradient_Factor_Current_Stop = Currently_Max_Allowed_Gradient_Factor;
            
            //Check if we are at 20' and if this should be the last deco stop
            if(((Deco_Stop_Depth == 6 && Settings.metric) || (Deco_Stop_Depth == 20 && !Settings.metric)) && Settings.lastStopDoubleInterval)
            {
                decoStopInterval = Deco_Stop_Depth;
            }
            Next_Stop = Deco_Stop_Depth - decoStopInterval;
            //Check if Next_Stop is shallower than ending_depth, and if so adjust it
            if(Next_Stop < ending_depth)
            {
                Next_Stop = ending_depth;
            }
            Gradient_Factor_Next_Stop = Next_Stop * currentDive.gradientFactorSlope + Settings.gradientFactorSurfacing;
            Currently_Max_Allowed_Gradient_Factor = Gradient_Factor_Next_Stop;
            
            // DEBUG logging
            if (debugEnabled && debugLog != null) {
                debugLog.printf("\n### Calculating stop at %.1fm ###\n", Deco_Stop_Depth);
                debugLog.printf("Next Stop: %.1f, GF Slope: %.4f\n", Next_Stop, currentDive.gradientFactorSlope);
                debugLog.printf("GF for next stop: %.3f (set as current GF)\n", Gradient_Factor_Next_Stop);
            }
            
            double stopDuration = DECOMPRESSION_STOP(Deco_Stop_Depth, Deco_Stop_Depth - Next_Stop); 
            
            /*===============================================================================
             This next bit just rounds up the stop time at the first stop to be in
             whole increments of the minimum stop time (to make for a nice deco table).
            ===============================================================================*/
            
            GAS_LOADINGS_CONSTANT_DEPTH(Deco_Stop_Depth, stopDuration);
            
            //Now create the visual deco segment
            decoSegment = new DecoTableSegment(); //clears the decoSegment object
            decoSegment = DIVEDATA_CONSTANT_DEPTH(Deco_Stop_Depth, Settings.RMV_During_Deco, stopDuration);

//System.out.println(" Row 1565: About to call updateHighestMvaluePercentageAndGradientFactor with Deco_Stop_Depth: " + Deco_Stop_Depth + " and surface pressure: " + currentDive.surfacePressure);                        
            updateHighestMvaluePercentageAndGradientFactor(Deco_Stop_Depth + currentDive.surfacePressure); //jag gör detta EFTER att gradient-factor sätts på decoSegment, eftersom vi vill se gradientFactor i början av segmentet (när man anländer till ett stopp)
//            decoSegment.setGradientFactor(Util.roundToOneDecimal(Gradient_Factor_Current_Stop*100));
            //Add the gas-volume used at the travel-segment before this deco stop
            decoSegment.setGasVolumeUsedDuringSegment(Double.parseDouble(decoSegment.getGasVolumeUsedDuringSegment()) + ascentSegment.gasVolumeUsedDuringSegment);
            if(firstStop)
            {
                //We won't report the EXACT times for the first stop, since the DECOMPRESSION_STOP-function adjusted the stopDuration to end up on a whole 
                // multiplier of the Minimum_Deco_Stop_Time. 
                //So for the first deco stop we set endRunTime to follow the real schedule, but we adjust the startRunTime based on the Minimum_Deco_Stop_Time
                decoSegment.setEndRunTime(ascentSegment.endRunTime + stopDuration);
                //Now adjust the stopDuration, just to make for a nice-looking (rounded values) table
                double adjustedStopDuration;
                //BUT, ONLY IF it's NOT already a whole number
                if(stopDuration % 1 == 0)
                {
                    adjustedStopDuration = stopDuration;
                }
                else
                {
                    adjustedStopDuration = Math.round((stopDuration/currentMinimumDecoStopDuration) + 0.5) * currentMinimumDecoStopDuration;
                }
                //we don't include the ascent-time in the first deco stop duration
                decoSegment.setStartRunTime(ascentSegment.endRunTime + stopDuration - adjustedStopDuration);
                decoSegment.setDuration(Util.roundToOneDecimal(adjustedStopDuration));
            }
            else
            {
                decoSegment.setStartRunTime(ascentSegment.startRunTime); //start runtime of the ascent-segment leading up to this deco stop.
                decoSegment.setDuration(Util.roundToOneDecimal(stopDuration + ascentSegment.duration)); //the deco stop's duration, plus the ascent-segment's duration
                decoSegment.setEndRunTime(ascentSegment.endRunTime + stopDuration);
            }
            
            currentDive.resultingDecoPlan.add(decoSegment);
            
            //now create a diveSegment for this decoStop..
            stopSegment = new DiveSegment();
            stopSegment.segmentType = DiveSegment.CONSTANT_DEPTH;
            stopSegment.startDepth = Deco_Stop_Depth;
            stopSegment.startRunTime = ascentSegment.endRunTime;
            stopSegment.duration = stopDuration;
            stopSegment.endDepth = stopSegment.startDepth;
            stopSegment.gasID = currentDive.activeGasID;
            stopSegment.oxygenFraction = currentDive.gases.get(currentDive.activeGasID).oxygenFraction;
            stopSegment.heliumFraction = currentDive.gases.get(currentDive.activeGasID).heliumFraction;
            stopSegment.nitrogenFraction = 1 - stopSegment.oxygenFraction - stopSegment.heliumFraction;
            stopSegment.endRunTime = stopSegment.startRunTime + stopDuration;
            stopSegment.nitrogenCompartmentPressuresAtEndOfDuration = nitrogenCompartmentPressure.clone();
            stopSegment.heliumCompartmentPressuresAtEndOfDuration = heliumCompartmentPressure.clone();
            //Calculate the gas volume used during this segment
            averageAmbientPressureATA = (Deco_Stop_Depth + currentDive.surfacePressure)/Settings.Depth_Per_ATM;
            stopSegment.gasVolumeUsedDuringSegment = Settings.RMV_During_Deco * averageAmbientPressureATA * stopDuration;
            //Add the segment
            currentDive.diveSegments.add(stopSegment);
            //Update the current depth and runtime
            currentDive.currentDepth = Deco_Stop_Depth;
            currentDive.currentRunTime = stopSegment.endRunTime;
            currentDive.totalDecoDuration += stopSegment.duration;
            currentDive.totalAscentDuration += stopSegment.duration;
//System.out.println("adding stop, " + stopSegment.duration + " minutes. " + stopSegment.startDepth + " to " + stopSegment.endDepth);            
           
            firstStop = false; //we have now processed at least one ascent and one deco stop, so the next round is definitely not the first stop.
            
            

            /*===============================================================================
             IF MINIMUM STOP TIME PARAMETER IS A WHOLE NUMBER (i.e. 1 minute) THEN
             WRITE DECO SCHEDULE USING INTEGER NUMBERS (looks nicer). OTHERWISE, USE
             DECIMAL NUMBERS.
             Note: per the request of a noted exploration diver(!), program now allows
             a minimum stop time of less than one minute so that total ascent time can
             be minimized on very long dives. In fact, with step size set at 1 fsw or
             0.2 msw and minimum stop time set at 0.1 minute (6 seconds), a near
             continuous decompression schedule can be computed.
            ===============================================================================*/
/*            if(Math.round(currentMinimumDecoStopDuration) == currentMinimumDecoStopDuration)
            {
		WRITE (8,862) Segment_Number, Segment_Time, Run_Time, Mix_Number, Gradient_Factor_Current_Stop, INT(Deco_Stop_Depth), INT(Stop_Time), INT(Run_Time)
            }
            else
            {
		WRITE (8,863) Segment_Number, Segment_Time, Run_Time, Mix_Number, Gradient_Factor_Current_Stop, Deco_Stop_Depth, Stop_Time, Run_Time
            }
*/          Starting_Depth = (int)Math.round(Deco_Stop_Depth);
            Deco_Stop_Depth = Next_Stop;
//            Last_Run_Time = Run_Time;
        } //80 end of deco stop loop block
        
        if(ending_depth == 0)
        {
            //Now create the visual segment for the surface
            decoSegment = new DecoTableSegment(); //clears the decoSegment object
            decoSegment = DIVEDATA_CONSTANT_DEPTH(0, Settings.RMV_During_Deco, 0);
            decoSegment.setGradientFactor(Util.roundToOneDecimal((currentDive.highestCurrentGradientFactor*100)+0.5)); //we need to round up, since we will never arrive at the exact "GF Hi", since we force the deco times to be multipliers of a "minimum stop time"
            decoSegment.setEndRunTime(0);
            decoSegment.setStartRunTime(Math.round((currentDive.currentRunTime/currentMinimumDecoStopDuration) + 0.5) * currentMinimumDecoStopDuration);
            decoSegment.setUserDefined(true);
            currentDive.resultingDecoPlan.add(decoSegment);
            currentDive.currentRunTime = Double.parseDouble(decoSegment.getStartRunTime());
        }

//        body.setCurrentDive(currentDive);
        
        
        /*===============================================================================
         PROCESSING OF DIVE COMPLETE. READ INPUT FILE TO DETERMINE IF THERE IS A
         REPETITIVE DIVE. IF NONE, THEN EXIT REPETITIVE LOOP.
        ===============================================================================*/
/*		READ (7,*) Repetitive_Dive_Flag
        IF (Repetitive_Dive_Flag .EQ. 0) THEN
        EXIT //exit repetitive dive loop at Line 330
        /*===============================================================================
         IF THERE IS A REPETITIVE DIVE, COMPUTE GAS LOADINGS (OFF-GASSING) DURING
         SURFACE INTERVAL TIME. RE-INITIALIZE SELECTED VARIABLES AND RETURN TO
         START OF REPETITIVE LOOP AT LINE 30.
        ===============================================================================*/
/*		ELSE IF (Repetitive_Dive_Flag .EQ. 1) THEN
        READ (7,*) Surface_Interval_Time
        CALL GAS_LOADINGS_SURFACE_INTERVAL (Surface_Interval_Time)
        Run_Time = 0.0
        Segment_Number = 0
        Gradient_Factor = Gradient_Factor_Lo
        Running_CNS = 0.0
        Running_OTU = 0.0
        WRITE (8,880)
        WRITE (8,890)
        WRITE (8,813)
        WRITE (13,880)
        WRITE (13,890)
        WRITE (13,813)
        CYCLE //Return to start of repetitive loop to process another dive
        /*===============================================================================
         WRITE ERROR MESSAGE AND TERMINATE PROGRAM IF THERE IS AN ERROR IN THE
         INPUT FILE FOR THE REPETITIVE DIVE FLAG
        ===============================================================================*/
/*		ELSE
        CALL SYSTEMQQ (OS_Command)
        WRITE (*,908)
        WRITE (*,900)
        STOP 'PROGRAM TERMINATED'
        END IF
                        */
    }
	
    private static double PROJECTED_ASCENT(double Starting_Depth, double Rate, double Deco_Stop_Depth, double Step_Size)
    {
        /*===============================================================================
         SUBROUTINE PROJECTED_ASCENT
         Purpose: This subprogram performs a simulated ascent outside of the main
         program to ensure that a deco ceiling will not be violated due to unusual
         gas loading during ascent (on-gassing). If the deco ceiling is violated,
         the stop depth will be adjusted deeper by the step size until a safe
         ascent can be made.
        ===============================================================================*/
        /*===============================================================================
         ARGUMENTS
        ===============================================================================*/
        //REAL Starting_Depth, Rate, Step_Size !input
        //REAL Deco_Stop_Depth !input and output
        /*===============================================================================
         LOCAL VARIABLES
        ===============================================================================*/
        //INTEGER I !loop counter
        double Initial_Inspired_He_Pressure, Initial_Inspired_N2_Pressure;
        double Helium_Rate, Nitrogen_Rate;
        double Starting_Ambient_Pressure, Ending_Ambient_Pressure;
        double New_Ambient_Pressure, Segment_Time;
        double Temp_Helium_Pressure, Temp_Nitrogen_Pressure;
        double Coefficient_A, Coefficient_B;
        //double SCHREINER_EQUATION !function subprogram
        /*===============================================================================
         LOCAL ARRAYS
        ===============================================================================*/
        double[] Initial_Helium_Pressure = new double[16];
        double[] Initial_Nitrogen_Pressure = new double[16];
        double[] Temp_Gas_Loading = new double[16];
        double[] Allowable_Gas_Loading = new double[16];
        /*===============================================================================
         GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
        ===============================================================================*/
        //REAL Water_Vapor_Pressure
        //COMMON /Block_8/ Water_Vapor_Pressure
        /*===============================================================================
         GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        ===============================================================================*/
        /*INTEGER Mix_Number
        COMMON /Block_9/ Mix_Number
        REAL Barometric_Pressure
        COMMON /Block_18/ Barometric_Pressure
        REAL Gradient_Factor
        COMMON /Block_37/ Gradient_Factor */
        /*===============================================================================
         GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        ===============================================================================*/
        /* REAL Helium_Time_Constant(16)
        COMMON /Block_1A/ Helium_Time_Constant
        REAL Nitrogen_Time_Constant(16)
        COMMON /Block_1B/ Nitrogen_Time_Constant
        REAL Helium_Pressure(16), Nitrogen_Pressure(16) !input
        COMMON /Block_3/ Helium_Pressure, Nitrogen_Pressure
        REAL Fraction_Helium(10), Fraction_Nitrogen(10)
        COMMON /Block_5/ Fraction_Helium, Fraction_Nitrogen
        REAL Coefficient_AHE(16), Coefficient_BHE(16)
        REAL Coefficient_AN2(16), Coefficient_BN2(16)
        COMMON /Block_35/ Coefficient_AHE, Coefficient_BHE, Coefficient_AN2, Coefficient_BN2 */
        /*===============================================================================
         CALCULATIONS
        ===============================================================================*/
        New_Ambient_Pressure = Deco_Stop_Depth + surfacePressure;
        Starting_Ambient_Pressure = Starting_Depth + surfacePressure;
        Initial_Inspired_He_Pressure = (Starting_Ambient_Pressure - Settings.getWaterVaporPressure())*currentDive.getCurrentHeliumFraction();
        Initial_Inspired_N2_Pressure = (Starting_Ambient_Pressure - Settings.getWaterVaporPressure())*currentDive.getCurrentNitrogenFraction();
        Helium_Rate = Rate * currentDive.getCurrentHeliumFraction();
        Nitrogen_Rate = Rate * currentDive.getCurrentNitrogenFraction();
        for(int i=0;i<16;i++)
        {
            Initial_Helium_Pressure[i] = heliumCompartmentPressure[i]; // heliumCompartmentPressure[i];
            Initial_Nitrogen_Pressure[i] = nitrogenCompartmentPressure[i]; // nitrogenCompartmentPressure[i];
        }
        boolean loop;
        do{
            loop = false;
            Ending_Ambient_Pressure = New_Ambient_Pressure; // 665
            Segment_Time = (Ending_Ambient_Pressure - Starting_Ambient_Pressure)/Rate;
            for(int i=0;i<16;i++) //DO 670 I = 1,16
            {
                Temp_Helium_Pressure = schreinerEquation(Initial_Inspired_He_Pressure, Helium_Rate, Segment_Time, kHe[i], Initial_Helium_Pressure[i]);
                Temp_Nitrogen_Pressure = schreinerEquation(Initial_Inspired_N2_Pressure, Nitrogen_Rate, Segment_Time, kN2[i], Initial_Nitrogen_Pressure[i]);
                Temp_Gas_Loading[i] = Temp_Helium_Pressure + Temp_Nitrogen_Pressure;
                Coefficient_A = (Temp_Helium_Pressure*He_a[i] + Temp_Nitrogen_Pressure*N2_a[i])/ (Temp_Helium_Pressure+Temp_Nitrogen_Pressure);
                Coefficient_B = (Temp_Helium_Pressure*He_b[i] + Temp_Nitrogen_Pressure*N2_b[i])/ (Temp_Helium_Pressure+Temp_Nitrogen_Pressure);
                Allowable_Gas_Loading[i] = Ending_Ambient_Pressure * (Currently_Max_Allowed_Gradient_Factor/Coefficient_B - Currently_Max_Allowed_Gradient_Factor + 1.0) + Currently_Max_Allowed_Gradient_Factor*Coefficient_A;
            } //670 CONTINUE
            for(int i=0;i<16;i++) // DO 671 I = 1,16
            {
                if(Temp_Gas_Loading[i] > Allowable_Gas_Loading[i])
                {
                    New_Ambient_Pressure = Ending_Ambient_Pressure + Step_Size;
                    Deco_Stop_Depth = Deco_Stop_Depth + Step_Size;
                    loop = true; break; //GOTO 665
                }
            } //671 CONTINUE
        }while(loop);
        return Deco_Stop_Depth;
    }
	
//===============================================================================
// SUBROUTINE GAS_LOADINGS_ASCENT_DESCENT
// Purpose: This subprogram applies the Schreiner equation to update the
// gas loadings (partial pressures of helium and nitrogen) in the half-time
// compartments due to a linear ascent or descent segment at a constant rate.
//===============================================================================
public static void GAS_LOADINGS_ASCENT_DESCENT(double Starting_Depth, double Ending_Depth, double Rate)
{
    double Initial_Helium_Pressure;
    double Initial_Nitrogen_Pressure;
    double Starting_Ambient_Pressure;
    double Initial_Inspired_He_Pressure,Initial_Inspired_N2_Pressure;
    double Helium_Rate,Nitrogen_Rate;
//===============================================================================
// CALCULATIONS
//===============================================================================
    //Just make sure that Rate has the correct sign. negative on ascent and positive on descent.
    if((Ending_Depth < Starting_Depth && Rate > 0) || (Ending_Depth > Starting_Depth && Rate < 0))
    {
        Rate = -Rate;
    }
    
    double Segment_Time = (Ending_Depth - Starting_Depth)/Rate;
    Starting_Ambient_Pressure = Starting_Depth + surfacePressure;
    Initial_Inspired_He_Pressure = (Starting_Ambient_Pressure - Settings.getWaterVaporPressure())*currentDive.getCurrentHeliumFraction();
    Initial_Inspired_N2_Pressure = (Starting_Ambient_Pressure - Settings.getWaterVaporPressure())*currentDive.getCurrentNitrogenFraction();
    Helium_Rate = Rate * currentDive.getCurrentHeliumFraction();
    Nitrogen_Rate = Rate * currentDive.getCurrentNitrogenFraction();
   
    for(int i=0; i<16; i++)
    {
        Initial_Helium_Pressure = heliumCompartmentPressure[i];
        Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[i];
        heliumCompartmentPressure[i] = schreinerEquation(Initial_Inspired_He_Pressure, Helium_Rate, Segment_Time, kHe[i], Initial_Helium_Pressure);
        nitrogenCompartmentPressure[i] = schreinerEquation(Initial_Inspired_N2_Pressure, Nitrogen_Rate, Segment_Time, kN2[i], Initial_Nitrogen_Pressure);        
    }
}

//===============================================================================
// SUBROUTINE GAS_LOADINGS_CONSTANT_DEPTH
// Purpose: This subprogram applies the Haldane equation to update the
// gas loadings (partial pressures of helium and nitrogen) in the half-time
// compartments for a segment at constant depth.
//===============================================================================
public static void GAS_LOADINGS_CONSTANT_DEPTH (double Depth, double Segment_Time)
{
    //int Last_Segment_Number;
    double Initial_Helium_Pressure, Initial_Nitrogen_Pressure;
    double Inspired_Helium_Pressure, Inspired_Nitrogen_Pressure;
    double Ambient_Pressure;
//===============================================================================
// CALCULATIONS
//===============================================================================

    Ambient_Pressure = Depth + currentDive.surfacePressure;
    
    Inspired_Helium_Pressure = (Ambient_Pressure - currentDive.waterVaporPressure)*currentDive.getCurrentHeliumFraction();
    Inspired_Nitrogen_Pressure = (Ambient_Pressure - currentDive.waterVaporPressure)*currentDive.getCurrentNitrogenFraction();
    
       
    for(int i=0; i<16; i++)
    {
        Initial_Helium_Pressure = heliumCompartmentPressure[i];
        Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[i];
        heliumCompartmentPressure[i] = haldaneEquation(Initial_Helium_Pressure, Inspired_Helium_Pressure, kHe[i], Segment_Time);
        nitrogenCompartmentPressure[i] = haldaneEquation(Initial_Nitrogen_Pressure, Inspired_Nitrogen_Pressure, kN2[i], Segment_Time);  
    }
}




    private static void offGas1(int pTime)
    {
        //pTime is in minutes
        double vInspN2; //  'inspired PP
        double vInspHe; //  'inspired PP
        double vOldp; //    'old tissue PP
        double vModKN2;
        double vModKHe;

        vInspN2 = (currentDive.surfacePressure - currentDive.waterVaporPressure)*0.79; // Get inspired N2
        vInspHe = 0; // Get inspired He

        for(int vCount = 0; vCount <= pTime; vCount++)
        {
            for(int vLoop = 0; vLoop<16; vLoop++)
            {
                vOldp = nitrogenCompartmentPressure[vLoop];
                vModKN2 = (0.9 - 0.005555 * vCount) * kN2[vLoop];
                nitrogenCompartmentPressure[vLoop] = haldaneEquation(vOldp, vInspN2, vModKN2, 1);

                vOldp = heliumCompartmentPressure[vLoop];
                vModKHe = (0.9 - 0.005555 * vCount) * kHe[vLoop];
                heliumCompartmentPressure[vLoop] = haldaneEquation(vOldp, vInspHe, vModKHe, 1);
            }
        }  
    }
    
    private static void offGas2(int pTime)
    {
        //pTime is in minutes
        double vInspN2; //  'inspired PP
        double vInspHe; //  'inspired PP
        double vOldp; //    'old tissue PP
        double vModKN2;
        double vModKHe;

        vInspN2 = (currentDive.surfacePressure - currentDive.waterVaporPressure)*0.79; // Get inspired N2
        vInspHe = 0; // Get inspired He

        for(int vLoop = 0; vLoop<16; vLoop++)
        {
            vOldp = nitrogenCompartmentPressure[vLoop];
            vModKN2 = 0.65 * kN2[vLoop];
            nitrogenCompartmentPressure[vLoop] = haldaneEquation(vOldp, vInspN2, vModKN2, pTime);

            vOldp = heliumCompartmentPressure[vLoop];
            vModKHe = 0.65 * kHe[vLoop];
            heliumCompartmentPressure[vLoop] = haldaneEquation(vOldp, vInspHe, vModKHe, pTime);
        }
    }    

    private static void offGas3(int pTime)
    {
        //pTime is in minutes
        double vInspN2; //  'inspired PP
        double vInspHe; //  'inspired PP
        double vOldp; //    'old tissue PP
        double vModKN2;
        double vModKHe;

        vInspN2 = (currentDive.surfacePressure - currentDive.waterVaporPressure)*0.79; // Get inspired N2
        vInspHe = 0; // Get inspired He

        for(int vCount = 0; vCount <= pTime; vCount++)
        {
            for(int vLoop = 0; vLoop<16; vLoop++)
            {
                vOldp = nitrogenCompartmentPressure[vLoop];
                vModKN2 = (0.65 + 0.0020833 * vCount) * kN2[vLoop];
                nitrogenCompartmentPressure[vLoop] = haldaneEquation(vOldp, vInspN2, vModKN2, 1);

                vOldp = heliumCompartmentPressure[vLoop];
                vModKHe = (0.65 + 0.0020833 * vCount) * kHe[vLoop];
                heliumCompartmentPressure[vLoop] = haldaneEquation(vOldp, vInspHe, vModKHe, 1);
            }
        }  
    }    
    
    private static void offGas4(int pTime)
    {
        //pTime is in minutes
        double vInspN2; //  'inspired PP
        double vInspHe; //  'inspired PP
        double vOldp; //    'old tissue PP
        double vModKN2;
        double vModKHe;

        vInspN2 = (currentDive.surfacePressure - currentDive.waterVaporPressure)*0.79; // Get inspired N2
        vInspHe = 0; // Get inspired He

        for(int vLoop = 0; vLoop<16; vLoop++)
        {
            vOldp = nitrogenCompartmentPressure[vLoop];
            vModKN2 = 0.9 * kN2[vLoop];
            nitrogenCompartmentPressure[vLoop] = haldaneEquation(vOldp, vInspN2, vModKN2, pTime);

            vOldp = heliumCompartmentPressure[vLoop];
            vModKHe = 0.9 * kHe[vLoop];
            heliumCompartmentPressure[vLoop] = haldaneEquation(vOldp, vInspHe, vModKHe, pTime);
        }
    }      


    public static void performSurfaceInterval(double[] nitrogenTissuePressures, double[] heliumTissuePressures, int minutes)
    {
        nitrogenCompartmentPressure = nitrogenTissuePressures.clone();
        heliumCompartmentPressure = heliumTissuePressures.clone();       
        
        if(minutes <= 45)
        {
            offGas1(minutes);
        }
        else if(minutes > 45 && minutes <= 60)
        {
            offGas1(45);
            offGas2(minutes - 45);
        }
        else if(minutes > 60 && minutes <= 180)
        {
            offGas1(45);
            offGas2(15);
            offGas3(minutes - 60);
        }
        else
        {
            offGas1(45);
            offGas2(15);
            offGas3(120);
            offGas4(minutes - 180);
        }

    /*    
        double Initial_Nitrogen_Pressure;
        double Inspired_Nitrogen_Pressure;

        Inspired_Nitrogen_Pressure = (currentDive.surfacePressure - currentDive.waterVaporPressure)*0.79;

        for(int i=0; i<16; i++)
        {
            Initial_Nitrogen_Pressure = nitrogenTissuePressures[i];
            nitrogenTissuePressures[i] = haldaneEquation(Initial_Nitrogen_Pressure, Inspired_Nitrogen_Pressure, kN2[i], hours*60);
        }
        */
    }
        
    public static double[] getNitrogenCompartmentPressures()
    {
        return nitrogenCompartmentPressure;
    }
    public static double[] getHeliumCompartmentPressures()
    {
        return heliumCompartmentPressure;
    }
        
        
        
    /*===============================================================================
     SUBROUTINE DIVEDATA_ASCENT_DESCENT
    =============================================================================== */
    private static void DIVEDATA_ASCENT_DESCENT(double Starting_Depth, double Ending_Depth, double Rate, double Respiratory_Minute_Volume)
    {
        double Segment_Time = (Ending_Depth - Starting_Depth)/Rate;
        if(Segment_Time < 0) //in case Rate is erronously negative or positive
        {
            Segment_Time = Math.abs(Segment_Time);
        }
        
        /*===============================================================================
         LOCAL VARIABLES
        ===============================================================================*/
        double PARATE, startingPressureATA, endingPressureATA, SEGVOL;
        double MAXATA, MINATA, MAXPO2;
        double SUMCNS, TMPCNS;
        double OTU, MAXD, ENDN2, ENDNO2;
        double MINPO2, LOWPO2, O2TIME, IPO2, FPO2;
        /*===============================================================================
         LOCAL ARRAYS
        ===============================================================================*/
        double[] OTIME = new double[10];
        double[] SEGPO2 = new double[10];
        double[] PO2O = new double[10]; 
        double[] PO2F = new double[10];
        double[] TLIMO = new double[10];
        double[] SLPCON = new double[10];
        double[] CNS = new double[10];
        /*===============================================================================
         GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        ===============================================================================*/
        /*int Segment_Number;
        double Run_Time, Segment_Time;
        COMMON /Block_2/ Run_Time, Segment_Number, Segment_Time
        int Mix_Number;
        COMMON /Block_9/ Mix_Number
        double Depth_Per_ATM;
        COMMON /Block_16/ Depth_Per_ATM
        double Barometric_Pressure
        COMMON /Block_18/ Barometric_Pressure
        double Running_CNS, Running_OTU
        COMMON /Block_32/ Running_CNS, Running_OTU
        C===============================================================================
        C GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        C===============================================================================
        REAL Fraction_Helium(10), Fraction_Nitrogen(10)
        COMMON /Block_5/ Fraction_Helium, Fraction_Nitrogen
        REAL PO2LO(10), PO2HI(10), LIMSLP(10), LIMINT(10)
        COMMON /Block_29/ PO2LO, PO2HI, LIMSLP, LIMINT
        REAL Fraction_Oxygen(10)
        COMMON /Block_30/ Fraction_Oxygen
        REAL Running_Gas_Volume(10)
        COMMON /Block_31/ Running_Gas_Volume*/
        /*===============================================================================
         CALCULATIONS
        ===============================================================================*/
        SUMCNS = 0.0;
        OTU = 0.0;
        PARATE = Rate/Settings.Depth_Per_ATM;
        startingPressureATA = (Starting_Depth + surfacePressure )/Settings.Depth_Per_ATM; //TODO: NOAA's oxygen-exposure tables är baserade på ATA..  Men Metric använder Bar.. 
        endingPressureATA = (Ending_Depth + surfacePressure)/Settings.Depth_Per_ATM;
        SEGVOL = Respiratory_Minute_Volume*(startingPressureATA*Segment_Time + 0.5* PARATE*Segment_Time*Segment_Time);
//        TEMPRV = Running_Gas_Volume[Mix_Number];
//        Running_Gas_Volume[Mix_Number] = TEMPRV + SEGVOL;
        MAXATA = Math.max(startingPressureATA, endingPressureATA);
        MINATA = Math.min(startingPressureATA, endingPressureATA);
        MAXPO2 = MAXATA * currentDive.getCurrentOxygenFraction();
        MINPO2 = MINATA * currentDive.getCurrentOxygenFraction();
        MAXD = Math.max(Starting_Depth, Ending_Depth);
        ENDN2 = (currentDive.getCurrentNitrogenFraction()*(MAXD + surfacePressure)/0.79) - surfacePressure;
        ENDNO2 = currentDive.getCurrentNitrogenFraction()*(MAXD + surfacePressure)+ currentDive.getCurrentOxygenFraction()*(MAXD + surfacePressure)-surfacePressure;
        
        //System.out.println("DIVEDATA_ASCENT_DESCENT() - MAXPO2: " + MAXPO2 + " MINPO2: " + MINPO2 + " at Ending_Depth: " + Ending_Depth + " Current oxygen fraction: " + currentDive.getCurrentOxygenFraction() + "  Segment time: " + Segment_Time);

        
        if(MAXPO2 > 0.5) //GOTO 50
        {
            if(MINPO2 < 0.5)
            {
                LOWPO2 = 0.5;
            }
            else
            {
                LOWPO2 = MINPO2;
            }
            O2TIME = Segment_Time*(MAXPO2 - LOWPO2)/(MAXPO2 - MINPO2);
            
            if(MAXPO2 > 1.82)
            {
                //SUMCNS = 2.0; 
                //2021-11-16: The line above means that we suddenly add 200% to the "CNS clock" even if this PO2 is just encountered for seconds during a descent to a gas switch with a better gas.
                //So, instead, let's make it 20% increase per minute. Just a random number, but perhaps better than 200%. If it was constant depth we would set it higher, but this is likely a small portion of time beyond this limit.
                SUMCNS = O2TIME * 0.2;
                OTU = 3.0/11.0*O2TIME/(MAXPO2-LOWPO2)*(Math.pow((MAXPO2-0.5)/0.5, 11.0/6.0) - Math.pow((LOWPO2-0.5)/0.5, 11.0/6.0));
                //GOTO 50
            }
            else
            {
                for(int I=0;I<10;I++)
                {
                    if((MAXPO2 > PO2LO[I]) && (LOWPO2 <= PO2HI[I])) 
                    {
                        if((MAXPO2 >= PO2HI[I]) && (LOWPO2 < PO2LO[I]))
                        {
                            if(Starting_Depth > Ending_Depth)
                            {
                                PO2O[I] = PO2HI[I];
                                PO2F[I] = PO2LO[I];
                            }
                            else
                            {
                                PO2O[I] = PO2LO[I];
                                PO2F[I] = PO2HI[I];
                            }
                            SEGPO2[I] = PO2F[I] - PO2O[I];
                        }
                        else if((MAXPO2 < PO2HI[I]) && (LOWPO2 <= PO2LO[I]))
                        {
                            if(Starting_Depth > Ending_Depth)
                            {
                                PO2O[I] = MAXPO2;
                                PO2F[I] = PO2LO[I];
                            }
                            else
                            {
                                PO2O[I] = PO2LO[I];
                                PO2F[I] = MAXPO2;
                            }
                            SEGPO2[I] = PO2F[I] - PO2O[I];
                        }
                        else if((LOWPO2 > PO2LO[I]) && (MAXPO2 >= PO2HI[I]))
                        {
                            if(Starting_Depth > Ending_Depth)
                            {
                                PO2O[I] = PO2HI[I];
                                PO2F[I] = LOWPO2;
                            }
                            else
                            {
                                PO2O[I] = LOWPO2;
                                PO2F[I] = PO2HI[I];
                            }
                            SEGPO2[I] = PO2F[I] - PO2O[I];
                        }
                        else
                        {
                            if(Starting_Depth > Ending_Depth)
                            {
                                PO2O[I] = MAXPO2;
                                PO2F[I] = LOWPO2;
                            }
                            else
                            {
                                PO2O[I] = LOWPO2;
                                PO2F[I] = MAXPO2;
                            }
                            SEGPO2[I] = PO2F[I] - PO2O[I];
                        }
                        OTIME[I] = O2TIME*Math.abs(SEGPO2[I])/(MAXPO2 - LOWPO2);
                    }
                    else
                    {
                        OTIME[I] = 0.0;
                    }
                } //10 CONTINUE
                for(int I=0;I<10;I++) //DO 20 I = 1,10
                {
                    if(OTIME[I] == 0.0)
                    {
                        CNS[I] = 0.0;
                        //GOTO 20
                    }
                    else
                    {
                        TLIMO[I] = LIMSLP[I]*PO2O[I] + LIMINT[I];
                        SLPCON[I] = LIMSLP[I]*(SEGPO2[I]/OTIME[I]);
                        CNS[I] = 1.0/SLPCON[I]*Math.log(Math.abs(TLIMO[I] + SLPCON[I]*OTIME[I])) - 1.0/SLPCON[I]*Math.log(Math.abs(TLIMO[I]));
                    }
                } //20 CONTINUE
                for(int I=0;I<10;I++) //DO 30 I = 1, 10
                {
                    TMPCNS = SUMCNS;
                    SUMCNS = TMPCNS + CNS[I];
                } //30 CONTINUE
                if(Starting_Depth > Ending_Depth)
                {
                    IPO2 = MAXPO2;
                    FPO2 = LOWPO2;
                }
                else
                {
                    IPO2 = LOWPO2;
                    FPO2 = MAXPO2;
                }
                OTU = 3.0/11.0*O2TIME/(FPO2-IPO2)*(Math.pow((FPO2-0.5)/0.5, 11.0/6.0) - Math.pow((IPO2-0.5)/0.5, 11.0/6.0));
            }
        }
        CNStoxicityPercentage += SUMCNS; //50
        OTUbuildup += OTU;
        
//System.out.println("SUMCNS: " + SUMCNS + " max PO2: " + MAXPO2 + " at depth: " + Ending_Depth + " endingPressureATA: " + endingPressureATA + " Current oxygen fraction: " + currentDive.getCurrentOxygenFraction());        
        
        
        //WRITE (13,100) Segment_Number, Segment_Time, Run_Time, Mix_Number, Respiratory_Minute_Volume, SEGVOL, MAXD, MAXPO2, SUMCNS, OTU, ENDN2, ENDNO2
      
        
        //100 FORMAT (I3,3X,F5.1,1X,F6.1,1X,'|',3X,I2,3X,'|',F5.2,1X,F7.1,1X,F6.0,2X,F4.2,1X,2P,F7.1,'%',0P,F7.1,2X,F6.0,2X,F6.0)
/*        if(MAXPO2 > 1.6061)
        {
            //System.out.println("PO2 exceeds 1.6 in segment " + Segment_Number);
        }
        if(MAXPO2 > 1.82)
        {
            //System.out.println("PO2 exceeds 1.82 in segment " + Segment_Number);
        } */
    }

    /*===============================================================================
     SUBROUTINE DIVEDATA_CONSTANT_DEPTH
    ===============================================================================*/
    private static DecoTableSegment DIVEDATA_CONSTANT_DEPTH(double Depth, double Respiratory_Minute_Volume, double Segment_Time)
    {
        /*===============================================================================
         LOCAL VARIABLES
        ===============================================================================*/
        double Ambient_Pressure_ATA, Segment_Volume, Temp_Running_Volume;
        double SUMCNS, PO2, TLIM, OTU, MAXD, MAXPO2;
        double ENDN2, ENDNO2, CNSTMP, OTUTMP;
        /*===============================================================================
         CALCULATIONS
        ===============================================================================*/
        SUMCNS = 0.0;
        OTU = 0.0;
        TLIM = 0.0;
        Ambient_Pressure_ATA = (Depth + Settings.getSurfacePressure())/Settings.Depth_Per_ATM;
        Segment_Volume = Respiratory_Minute_Volume * Ambient_Pressure_ATA * Segment_Time;
//        Temp_Running_Volume = Running_Gas_Volume[Mix_Number];
//        Running_Gas_Volume[Mix_Number] = Temp_Running_Volume + Segment_Volume;
        PO2 = Ambient_Pressure_ATA * currentDive.getCurrentOxygenFraction();
        
        
//System.out.println("DIVEDATA_CONSTANT_DEPTH() - PO2: " + PO2 + " at depth: " + Depth + " Ambient Pressure ATA: " + Ambient_Pressure_ATA + " Current oxygen fraction: " + currentDive.getCurrentOxygenFraction() + "  Segment time: " + Segment_Time);

        MAXPO2 = PO2;
        MAXD = Depth;
        ENDN2 = (currentDive.getCurrentNitrogenFraction()*(Depth + surfacePressure)/0.79) - surfacePressure;
        ENDNO2 = currentDive.getCurrentNitrogenFraction()*(Depth + surfacePressure)+currentDive.getCurrentOxygenFraction()*(Depth+surfacePressure)-surfacePressure;
        if(PO2 > 0.5)
        {
            if(PO2 > 1.82)
            {
                //SUMCNS = 2.0; 
                //2021-11-16: The line above means that we suddenly add 200% to the "CNS clock" independent of exposure time.
                //So, instead, let's make it 100% increase per minute. (NOAA says that the limit beyond 1.8 is 1 minute?)
                SUMCNS = Segment_Time;
                //GOTO 30
            }
            else
            {
                //DO 10 I = 1,10
                for(int I=1;I<=10;I++)
                {
                    if((PO2 > PO2LO[I]) && (PO2 <= PO2HI[I]))
                    {
                        TLIM = LIMSLP[I]*PO2 + LIMINT[I];
                        break; //GOTO 20
                    }
                } //10 CONTINUE
                SUMCNS = Segment_Time/TLIM; //20 
            }
            OTU = Segment_Time*Math.pow((0.5/(PO2-0.5)),(-5.0/6.0)); //30
        }
        CNStoxicityPercentage += SUMCNS; //50
        OTUbuildup += OTU;
//System.out.println(" - CNStoxicityPercentage: " + CNStoxicityPercentage + " SUMCNS: " + SUMCNS + " OTUbuildup: " + OTUbuildup + " OTU: " + OTU);
        //WRITE (13,100) Segment_Number, Segment_Time, Run_Time, Mix_Number, Respiratory_Minute_Volume, Segment_Volume, MAXD, MAXPO2, SUMCNS, OTU, ENDN2, ENDNO2
        DecoTableSegment decoSegment = new DecoTableSegment();
        decoSegment.setDepth(Depth);
        decoSegment.setDuration(Util.roundToOneDecimal(Segment_Time));
        decoSegment.setEndRunTime(currentDive.currentRunTime);
        decoSegment.setStartRunTime(currentDive.currentRunTime - Segment_Time);
        decoSegment.setGasVolumeUsedDuringSegment(Segment_Volume);
        decoSegment.setPO2(Util.roundToTwoDecimals(MAXPO2));
        decoSegment.setHeliumPercentage(Math.round(currentDive.getCurrentHeliumFraction() * 100));
        decoSegment.setOxygenPercentage(Math.round(currentDive.getCurrentOxygenFraction() * 100));
        decoSegment.setCNSatEndOfSegment(CNStoxicityPercentage);
        decoSegment.setOTUatEndOfSegment(OTUbuildup);
        decoSegment.setMvaluePercent(Util.roundToOneDecimal(currentDive.highestCurrentMvalueFraction*100));
        decoSegment.setGradientFactor(Util.roundToOneDecimal(currentDive.highestCurrentGradientFactor*100));

        return decoSegment;
   }
	
    /*===============================================================================
     SUBROUTINE DECOMPRESSION_STOP
     Purpose: This subprogram calculates the required time at each
     decompression stop.
    ===============================================================================*/
    private static double DECOMPRESSION_STOP(double Deco_Stop_Depth, double Step_Size)
    {        
        /*===============================================================================
         LOCAL VARIABLES
        ===============================================================================*/
        double Segment_Time;
        double Ambient_Pressure;
        double Inspired_Helium_Pressure, Inspired_Nitrogen_Pressure;
        double Last_Run_Time;
        double Deco_Ceiling_Depth, Next_Stop;
        double Round_Up_Operation, Temp_Segment_Time, Time_Counter;
        double Allowable_Gas_Loading;
        double Coefficient_A, Coefficient_B;
        double Initial_Helium_Pressure = 0;
        double Initial_Nitrogen_Pressure = 0;

        //save the compartment values and reset them at the end of this method
        double[] temp_nitrogenCompartments = nitrogenCompartmentPressure.clone();
        double[] temp_heliumCompartments = heliumCompartmentPressure.clone();
        
        // DEBUG logging
        if (debugEnabled && debugLog != null) {
            debugLog.printf("\n=== DECOMPRESSION_STOP START ===\n");
            debugLog.printf("Stop Depth: %.1f, Step Size: %.1f\n", Deco_Stop_Depth, Step_Size);
            debugLog.printf("Current GF: %.3f\n", Currently_Max_Allowed_Gradient_Factor);
            debugLog.printf("Current Runtime: %.2f\n", currentDive.currentRunTime);
            debugLog.printf("Gas: O2=%.1f%% He=%.1f%%\n", 
                currentDive.getCurrentOxygenFraction() * 100, 
                currentDive.getCurrentHeliumFraction() * 100);
        }
        

        /*===============================================================================
         CALCULATIONS
        ===============================================================================*/
        Last_Run_Time = currentDive.currentRunTime;
//System.out.print("Now at stop depth: " + Deco_Stop_Depth + " runtime: " + Last_Run_Time);        
        //Här rundar vi upp så run_time slutar på en hel minut (eller vad minimum_deco_stop_time nu är) när vi avslutar detta dekostoppet.
        //Round_Up_Operation = Math.round((Last_Run_Time/currentMinimumDecoStopDuration) + 0.5) * currentMinimumDecoStopDuration;
        Round_Up_Operation = Math.floor(Last_Run_Time) + currentMinimumDecoStopDuration; //ändrade till detta istället för raden ovan i.o.m. v4.1.1.
//System.out.println(" Round-up-operation: " + Round_Up_Operation);
        Segment_Time = Round_Up_Operation - Last_Run_Time;
        Temp_Segment_Time = Segment_Time;
        Ambient_Pressure = Deco_Stop_Depth + surfacePressure;
        Next_Stop = Deco_Stop_Depth - Step_Size; 
        Inspired_Helium_Pressure = (Ambient_Pressure - currentDive.waterVaporPressure)*currentDive.getCurrentHeliumFraction();
        Inspired_Nitrogen_Pressure = (Ambient_Pressure - currentDive.waterVaporPressure)*currentDive.getCurrentNitrogenFraction();
        /*===============================================================================
         Check to make sure that program won't lock up if unable to decompress
         to the next stop. If so, write error message and terminate program.
        ===============================================================================*/
        for(int I=0;I<16;I++)
        {
            if((Inspired_Helium_Pressure + Inspired_Nitrogen_Pressure) > 0.0)
            {
                Coefficient_A = (Inspired_Helium_Pressure*He_a[I] + Inspired_Nitrogen_Pressure*N2_a[I])/ (Inspired_Helium_Pressure + Inspired_Nitrogen_Pressure);
                Coefficient_B = (Inspired_Helium_Pressure*He_b[I] + Inspired_Nitrogen_Pressure*N2_b[I])/ (Inspired_Helium_Pressure + Inspired_Nitrogen_Pressure);
                Allowable_Gas_Loading = (Next_Stop + surfacePressure) * (Currently_Max_Allowed_Gradient_Factor/Coefficient_B - Currently_Max_Allowed_Gradient_Factor + 1.0) + Currently_Max_Allowed_Gradient_Factor*Coefficient_A;
                if((Inspired_Helium_Pressure + Inspired_Nitrogen_Pressure) > Allowable_Gas_Loading)
                {
                    //TODO: handle this error
                    System.err.println("OFF-GASSING GRADIENT IS TOO SMALL TO DECOMPRESS AT THE " + Deco_Stop_Depth + " STOP. PLEASE REDUCE STEP SIZE OR INCREASE OXYGEN FRACTION. PROGRAM TERMINATED");
                }
            }
        }
        //700 DO 720 I = 1,16
        boolean do_loop;
        int iteration = 0;
        do{
            do_loop = false;
            iteration++;
            for(int I=0;I<16;I++)
            {
                Initial_Helium_Pressure = heliumCompartmentPressure[I]; //PHe[I];
                Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[I]; //PN2[I];
                heliumCompartmentPressure[I] = haldaneEquation(Initial_Helium_Pressure, Inspired_Helium_Pressure, kHe[I], Segment_Time); //PHe[I] = 
                nitrogenCompartmentPressure[I] = haldaneEquation(Initial_Nitrogen_Pressure, Inspired_Nitrogen_Pressure, kN2[I], Segment_Time); //PN2[I] = 
            } //720 CONTINUE
            //Det är lite fusk i DecoPlanner 3, för att de gör en uppstigning och kollar sen så att det är okej,
            // istället för att kolla så att det är okej att göra uppstigningen. Bör bli samma, men blir inte det av nån anledning. Det blir något kortare deko som DecoPlanner gör det.
            //spara undan vävnadstrycken först
            //<DecoPlanner hack> Detta bör jag kunna ta bort..
/*            double[] temp2_nitrogenCompartments = nitrogenCompartmentPressure.clone();
            double[] temp2_heliumCompartments = heliumCompartmentPressure.clone();
            GAS_LOADINGS_ASCENT_DESCENT(Deco_Stop_Depth, Next_Stop, Math.negateExact(Settings.ascentRate)); */
            Deco_Ceiling_Depth = CALC_DECO_CEILING();  // denna rad är inte del av <DecoPlanner hack>
// System.out.println("After " + Temp_Segment_Time + " minutes at depth " + Deco_Stop_Depth + ", ceiling is now " + Deco_Ceiling_Depth);
            //återställ nu vävnadstrycken
/*            nitrogenCompartmentPressure = temp2_nitrogenCompartments.clone();
            heliumCompartmentPressure = temp2_heliumCompartments.clone(); */
            //</DecoPlanner hack>
            
            // DEBUG logging
            if (debugEnabled && debugLog != null) {
                debugLog.printf("  Iteration %d: Segment_Time=%.2f, Temp_Segment_Time=%.2f\n", 
                    iteration, Segment_Time, Temp_Segment_Time);
                debugLog.printf("    Ceiling=%.3f, Rounded=%.2f, Next_Stop=%.1f\n", 
                    Deco_Ceiling_Depth, Util.roundToTwoDecimals(Deco_Ceiling_Depth), Next_Stop);
                // Log first 3 compartments
                for (int i = 0; i < 3; i++) {
                    debugLog.printf("    Comp %d: N2=%.3f, He=%.3f, Total=%.3f\n", 
                        i+1, nitrogenCompartmentPressure[i], heliumCompartmentPressure[i],
                        nitrogenCompartmentPressure[i] + heliumCompartmentPressure[i]);
                }
            }
            
            //if(Util.roundToOneDecimal(Deco_Ceiling_Depth) > Next_Stop)
//System.out.println("After " + Temp_Segment_Time + " minutes at depth " + Deco_Stop_Depth + ", ceiling is now " + Deco_Ceiling_Depth);
            if(Util.roundToTwoDecimals(Deco_Ceiling_Depth) > Next_Stop)
            {
//System.out.println("After " + Temp_Segment_Time + " minutes at depth " + Deco_Stop_Depth + ", ceiling is now " + Deco_Ceiling_Depth);                
                Segment_Time = currentMinimumDecoStopDuration;
                Time_Counter = Temp_Segment_Time;
                Temp_Segment_Time = Time_Counter + currentMinimumDecoStopDuration;
                do_loop = true; //GOTO 700
                
                if (debugEnabled && debugLog != null) {
                    debugLog.printf("    Need more time - continuing iterations\n");
                }
            }
        }while(do_loop);
        
        //Now reset the compartment values, since we changed them in this method
        nitrogenCompartmentPressure = temp_nitrogenCompartments.clone();
        heliumCompartmentPressure = temp_heliumCompartments.clone();

        // DEBUG logging
        if (debugEnabled && debugLog != null) {
            debugLog.printf("=== DECOMPRESSION_STOP END ===\n");
            debugLog.printf("Total iterations: %d\n", iteration);
            debugLog.printf("Returning stop time: %.2f min\n", Temp_Segment_Time);
            debugLog.flush();
        }

        return Temp_Segment_Time;
    }
    
    // Debug logging methods
    private static void logDebugSettings() {
        if (!debugEnabled || debugLog == null) return;
        
        debugLog.println("=== SETTINGS ===");
        debugLog.println("Metric: " + Settings.metric);
        debugLog.println("ZHL-16C: " + Settings.ZHL_C);
        debugLog.println("Descent Rate: " + Settings.descentRate + " m/min");
        debugLog.println("Ascent Rate: " + Settings.ascentRate + " m/min");
        debugLog.println("Deco Stop Interval: " + Settings.decoStopInterval + " m");
        debugLog.println("Last Stop 6m: " + Settings.lastStopDoubleInterval);
        debugLog.println("Gradient Factor Low: " + (Settings.gradientFactorFirstStop * 100) + "%");
        debugLog.println("Gradient Factor High: " + (Settings.gradientFactorSurfacing * 100) + "%");
        debugLog.println("Surface Pressure: " + Settings.getSurfacePressure() + " msw");
        debugLog.println("Water Vapor Pressure: " + Settings.getWaterVaporPressure() + " msw");
        debugLog.println("Minimum Deco Stop Time: " + Settings.Minimum_Deco_Stop_Time + " min");
        debugLog.println();
        
        // Log gases
        debugLog.println("=== GASES ===");
        if (currentDive != null && currentDive.gases != null) {
            for (int i = 0; i < currentDive.gases.size(); i++) {
                Gas gas = currentDive.gases.get(i);
                debugLog.printf("Gas %d: %s (O2=%.1f%%, He=%.1f%%, Type=%d, Switch=%dm)\n",
                    i, gas.getGasText(), gas.oxygenFraction * 100, gas.heliumFraction * 100,
                    gas.gasType, gas.switchDepth);
            }
        }
        debugLog.println();
    }
    
    private static void logDebug(String message) {
        if (!debugEnabled || debugLog == null) return;
        debugLog.println("[" + timeFormat.format(new Date()) + "] " + message);
        debugLog.flush();
    }
    
    private static void logTissues(String phase) {
        if (!debugEnabled || debugLog == null) return;
        
        debugLog.println("=== TISSUE COMPARTMENTS (" + phase + ") ===");
        debugLog.println("Runtime: " + currentDive.currentRunTime + " min, Depth: " + currentDive.currentDepth + "m");
        debugLog.println("Comp#  N2_Press  He_Press  Total    N2_a     He_a     N2_b     He_b");
        for (int i = 0; i < 16; i++) {
            debugLog.printf("%2d    %8.4f  %8.4f  %8.4f  %7.4f  %7.4f  %7.4f  %7.4f\n",
                i+1, nitrogenCompartmentPressure[i], heliumCompartmentPressure[i], 
                nitrogenCompartmentPressure[i] + heliumCompartmentPressure[i],
                N2_a[i], He_a[i], N2_b[i], He_b[i]);
        }
        debugLog.println();
    }
    
    private static void logSegment(DiveSegment segment) {
        if (!debugEnabled || debugLog == null || segment == null) return;
        
        try {
            debugLog.printf("SEGMENT: %s from %.1fm to %.1fm, Duration: %.2f min, Gas: O2=%.1f%% He=%.1f%%, Runtime: %.2f-%.2f\n",
                segment.segmentType, segment.startDepth, segment.endDepth, segment.duration,
                segment.oxygenFraction * 100, segment.heliumFraction * 100,
                segment.startRunTime, segment.endRunTime);
        } catch (Exception e) {
            debugLog.println("Error logging segment: " + e.getMessage());
        }
    }
    
    private static void logDecoStop(DecoTableSegment stop) {
        if (!debugEnabled || debugLog == null || stop == null) return;
        
        try {
            debugLog.printf("DECO STOP: %.0fm for %.1f min (Runtime: %.1f-%.1f)\n",
                stop.getDepth(), stop.getDuration(),
                stop.getStartRunTime(), stop.getEndRunTime());
        } catch (Exception e) {
            debugLog.println("Error logging deco stop: " + e.getMessage());
        }
    }
    
    private static void closeDebugLog() {
        if (debugLog != null) {
            debugLog.println("=== END OF DEBUG LOG ===");
            debugLog.close();
            debugLog = null;
        }
    }
}