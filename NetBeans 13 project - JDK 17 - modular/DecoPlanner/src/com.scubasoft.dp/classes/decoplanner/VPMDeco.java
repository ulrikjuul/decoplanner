/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package decoplanner;

import java.util.ArrayList;
import javafx.collections.FXCollections;

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
public final class VPMDeco
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

    private static final double[] PO2LO = new double[11];
    private static final double[] PO2HI = new double[11];
    private static final double[] LIMSLP = new double[11];
    private static final double[] LIMINT = new double[11];

    private static double CNStoxicityPercentage;
    private static double OTUbuildup;


    // N2 compartment pressures
    private static double[] nitrogenCompartmentPressure = new double[16]; //PN2
    
    private static final double[] nitrogenHalfTimes = new double[16];

    // N2 Half-time constants
    private static final double[] kN2 = new double[16];

    // He compartment pressures
    private static double[] heliumCompartmentPressure = new double[16]; //PHe

    private static final double[] heliumHalfTimes = new double[16];
    
    // He Half-time constants
    private static final double[] kHe = new double[16];

    
    //VPM variables
    private static double Constant_Pressure_Other_Gases;
    private static final double[] Max_Crushing_Pressure_He = new double[16];
    private static final double[] Max_Crushing_Pressure_N2 = new double[16];
    private static double[] Adjusted_Crushing_Pressure_He = new double[16];
    private static double[] Adjusted_Crushing_Pressure_N2 = new double[16];
    private static final double[] Max_Actual_Gradient = new double[16];
    private static final double[] Surface_Phase_Volume_Time = new double[16];
    private static final double[] Amb_Pressure_Onset_of_Imperm = new double[16];
    private static final double[] Gas_Tension_Onset_of_Imperm = new double[16];
    private static final double[] Initial_Critical_Radius_N2 = new double[16];
    private static final double[] Initial_Critical_Radius_He = new double[16];
    private static final double[] Adjusted_Critical_Radius_N2 = new double[16];
    private static final double[] Adjusted_Critical_Radius_He = new double[16];
    private static double[] Regenerated_Radius_N2 = new double[16];
    private static double[] Regenerated_Radius_He = new double[16];
    private static final double[] Initial_Allowable_Gradient_N2 = new double[16];
    private static final double[] Initial_Allowable_Gradient_He = new double[16];
    private static final double[] Allowable_Gradient_N2 = new double[16];
    private static final double[] Allowable_Gradient_He = new double[16];
    private static final double[] N2_Pressure_Start_of_Ascent = new double[16];
    private static final double[] He_Pressure_Start_of_Ascent = new double[16];
    private static double Run_Time_Start_of_Ascent;
    private static int Segment_Number_Start_of_Ascent;
    private static double[] Deco_Gradient_He = new double[16];
    private static double[] Deco_Gradient_N2 = new double[16];
    private static double[] Phase_Volume_Time = new double[16];
    private static double Water_Vapor_Pressure;
    
    private static double firstStopDepthOfTotalAscent; //2019-04-19
    private static double runtimeStartOfInitialDecoZone; //2019-04-21
    
    private static int currentMinimumDecoStopDuration;
    
    private static final VPMDeco vpmDeco = new VPMDeco();
    
    private VPMDeco()
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
        nitrogenHalfTimes[12] = 305;
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

        
        //sätt alla metric eller imperial-värden.		
        if(Settings.metric)
        {
            Water_Vapor_Pressure = 0.493;
        }
        else
        {
            Water_Vapor_Pressure = 1.607;   // based on respiratory quotient of 0.8 (Schreiner value)
        }        
        
        //VPM variables
        for(int cpt=0; cpt<16; cpt++)
        {
            Max_Crushing_Pressure_He[cpt] = 0.0;
            Max_Crushing_Pressure_N2[cpt] = 0.0;
            Max_Actual_Gradient[cpt] = 0.0;
            Surface_Phase_Volume_Time[cpt] = 0.0;
            Amb_Pressure_Onset_of_Imperm[cpt] = 0.0;
            Gas_Tension_Onset_of_Imperm[cpt] = 0.0;
            Initial_Critical_Radius_N2[cpt] = Settings.Critical_Radius_N2_Microns * 1.0E-6;
            Initial_Critical_Radius_He[cpt] = Settings.Critical_Radius_He_Microns * 1.0E-6;
        }
        
        firstStopDepthOfTotalAscent = 0.0;
        runtimeStartOfInitialDecoZone = 0.0;
        
    }
    
    /* Static 'instance' method */
    public static VPMDeco getInstance( ) 
    {
      return vpmDeco;
    }
    
    public static void init()
    {
        //Calling this makes it run the constructor if we haven't run it before.
        //But, if we have switched Imperial/Metric mode between planning dives, we have to edit the unit-dependent variables, since the constructor won't run a second time, and that's where we set these
        if(Settings.metric)
        {
            Water_Vapor_Pressure = 0.493;
        }
        else
        {
            Water_Vapor_Pressure = 1.607;   // based on respiratory quotient of 0.8 (Schreiner value)
        }
        //We also need to update the critical radius, in case the user updated the conservatism setting.
        //And reset the other values too..
        for(int cpt=0; cpt<16; cpt++)
        {
            Max_Crushing_Pressure_He[cpt] = 0.0;
            Max_Crushing_Pressure_N2[cpt] = 0.0;
            Max_Actual_Gradient[cpt] = 0.0;
            Surface_Phase_Volume_Time[cpt] = 0.0;
            Amb_Pressure_Onset_of_Imperm[cpt] = 0.0;
            Gas_Tension_Onset_of_Imperm[cpt] = 0.0;
            Initial_Critical_Radius_N2[cpt] = Settings.Critical_Radius_N2_Microns * 1.0E-6;
            Initial_Critical_Radius_He[cpt] = Settings.Critical_Radius_He_Microns * 1.0E-6;
        }
    }


    //  C===============================================================================
//  C     FUNCTION SUBPROGRAM FOR GAS LOADING CALCULATIONS - ASCENT AND DESCENT
//  C===============================================================================
    private static double SCHREINER_EQUATION(double Initial_Inspired_Gas_Pressure, double Rate_Change_Insp_Gas_Pressure,
                                      double Interval_Time, double Gas_Time_Constant, double Initial_Gas_Pressure)
    {
        //  C===============================================================================
        //  C     Note: The Schreiner equation is applied when calculating the uptake or
        //  C     elimination of compartment gases during linear ascents or descents at a
        //  C     constant rate.  For ascents, a negative number for rate must be used.
        //  C===============================================================================
        double result = Initial_Inspired_Gas_Pressure + Rate_Change_Insp_Gas_Pressure* (Interval_Time - 1.0/Gas_Time_Constant) - (Initial_Inspired_Gas_Pressure - Initial_Gas_Pressure - Rate_Change_Insp_Gas_Pressure/Gas_Time_Constant)* Math.exp(-Gas_Time_Constant*Interval_Time);
        return result;
    }

    // Debug helper functions for decompression stop analysis
    private static void debugDecoStop(String phase, double depth, double segmentTime, int iteration) {
        // DISABLED - Too verbose, causing hangs
        return;
    }
    
    private static void debugCeilingCalculation(String phase, double ceiling, double currentDepth, double currentRunTime) {
        // DISABLED - Too verbose, causing hangs
        return;
        
        /* Commented out to prevent hangs
        // Calculate ceiling for each compartment (show all 16)
        System.out.println("Ceiling calculation for each compartment:");
        for (int i = 0; i < 16; i++) {
            double gasLoading = heliumCompartmentPressure[i] + nitrogenCompartmentPressure[i];
            double weightedGradient = 0;
            
            if (gasLoading > 0.0) {
                weightedGradient = (Deco_Gradient_He[i] * heliumCompartmentPressure[i] + 
                                  Deco_Gradient_N2[i] * nitrogenCompartmentPressure[i]) / gasLoading;
            } else {
                weightedGradient = Math.min(Deco_Gradient_He[i], Deco_Gradient_N2[i]);
            }
            
            double toleratedPressure = gasLoading + Constant_Pressure_Other_Gases - weightedGradient;
            double compartmentCeiling = toleratedPressure - Settings.getSurfacePressure();
            
            System.out.printf("  Comp %2d: Loading=%7.3f Gradient=%7.4f Tolerated=%7.3f Ceiling=%7.3f\n", 
                i+1, gasLoading, weightedGradient, toleratedPressure, compartmentCeiling);
        }
        
        // Show which compartment is controlling
        double maxCeiling = -999;
        int controllingComp = -1;
        for (int i = 0; i < 16; i++) {
            double gasLoading = heliumCompartmentPressure[i] + nitrogenCompartmentPressure[i];
            double weightedGradient = 0;
            
            if (gasLoading > 0.0) {
                weightedGradient = (Deco_Gradient_He[i] * heliumCompartmentPressure[i] + 
                                  Deco_Gradient_N2[i] * nitrogenCompartmentPressure[i]) / gasLoading;
            } else {
                weightedGradient = Math.min(Deco_Gradient_He[i], Deco_Gradient_N2[i]);
            }
            
            double toleratedPressure = gasLoading + Constant_Pressure_Other_Gases - weightedGradient;
            double compartmentCeiling = toleratedPressure - Settings.getSurfacePressure();
            
            if (compartmentCeiling > maxCeiling) {
                maxCeiling = compartmentCeiling;
                controllingComp = i;
            }
        }
        System.out.println("Controlling Compartment: " + (controllingComp + 1));
        */
    }

//  C===============================================================================
//  C     FUNCTION SUBPROGRAM FOR GAS LOADING CALCULATIONS - CONSTANT DEPTH
//  C===============================================================================
    private static double HALDANE_EQUATION(double Initial_Gas_Pressure, double Inspired_Gas_Pressure, double Gas_Time_Constant,
                                    double Interval_Time)
    {
        //  C===============================================================================
        //  C     Note: The Haldane equation is applied when calculating the uptake or
        //  C     elimination of compartment gases during intervals at constant depth (the
        //  C     outside ambient pressure does not change).
        //  C===============================================================================
        double result = Initial_Gas_Pressure + (Inspired_Gas_Pressure - Initial_Gas_Pressure)* (1.0 - Math.exp(-Gas_Time_Constant * Interval_Time));
        return result;
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
        return Initial_Compartment_Pressure + (Inspired_Gas_Pressure - Initial_Compartment_Pressure)*(1.0 - Math.exp(-Time_Constant*Segment_Time));
    }

    // Debug method to trace VPM algorithm execution
    private static void debugVPM(String phase) {
        if (phase.equals("INIT")) {
            System.out.println("\n=== VPM DEBUG: INITIALIZATION ===");
            System.out.println("Conservatism Setting: " + Settings.vpmConservatismSetting);
            System.out.println("Critical Radius N2: " + Settings.Critical_Radius_N2_Microns + " microns");
            System.out.println("Critical Radius He: " + Settings.Critical_Radius_He_Microns + " microns");
        }
        else if (phase.equals("BOTTOM")) {
            System.out.println("\n=== VPM DEBUG: AFTER BOTTOM TIME ===");
            System.out.println("Run Time: " + currentDive.currentRunTime);
            // Show first 4 compartments
            for (int i = 0; i < 4; i++) {
                System.out.printf("Comp %d: N2=%.3f He=%.3f MaxCrush=%.3f\n",
                    i+1, nitrogenCompartmentPressure[i], heliumCompartmentPressure[i],
                    Max_Crushing_Pressure_N2[i]);
            }
        }
        else if (phase.equals("GRADIENT")) {
            System.out.println("\n=== VPM DEBUG: INITIAL GRADIENTS ===");
            // Show first 4 compartments
            for (int i = 0; i < 4; i++) {
                System.out.printf("Comp %d: InitGrad N2=%.4f He=%.4f\n",
                    i+1, Initial_Allowable_Gradient_N2[i], Initial_Allowable_Gradient_He[i]);
            }
        }
        else if (phase.equals("CEILING")) {
            double ceiling = CALC_ASCENT_CEILING();
            System.out.println("\n=== VPM DEBUG: FIRST CEILING ===");
            System.out.printf("Ceiling: %.1f m\n", ceiling);

            // Find controlling compartment
            for (int i = 0; i < 16; i++) {
                double gasLoading = heliumCompartmentPressure[i] + nitrogenCompartmentPressure[i];
                double weightedGrad = gasLoading > 0 ?
                    (Allowable_Gradient_He[i] * heliumCompartmentPressure[i] +
                     Allowable_Gradient_N2[i] * nitrogenCompartmentPressure[i]) / gasLoading :
                    Math.min(Allowable_Gradient_He[i], Allowable_Gradient_N2[i]);
                double tolerated = gasLoading + Constant_Pressure_Other_Gases - weightedGrad;
                double compCeiling = tolerated - Settings.getSurfacePressure();

                if (Math.abs(compCeiling - ceiling) < 0.1) {
                    System.out.printf("Controlling: Comp %d, Loading=%.3f, Gradient=%.4f\n",
                        i+1, gasLoading, weightedGrad);
                    break;
                }
            }
        }
        else if (phase.equals("SCHEDULE")) {
            System.out.println("\n=== VPM DEBUG: FINAL SCHEDULE ===");
            System.out.println("First Stop: " + firstStopDepthOfTotalAscent + "m");
            System.out.println("Total Deco Time: " + (currentDive.currentRunTime - 25) + " min");
        }
    }


    private static void debugGasSwitch(String when, int gasId, double depth) {
        System.out.println("\n=== GAS SWITCH: " + when + " ===");
        System.out.println("Gas ID: " + gasId);
        System.out.println("Depth: " + depth + "m");
        if (currentDive != null && currentDive.gases != null && gasId < currentDive.gases.size()) {
            System.out.println("Gas O2%: " + (currentDive.gases.get(gasId).oxygenFraction * 100));
            System.out.println("Gas He%: " + (currentDive.gases.get(gasId).heliumFraction * 100));
        }
    }

    private static void debugFinalGradients() {
        System.out.println("\n=== FINAL DECO GRADIENTS ===");
        for (int i = 0; i < 16; i++) {
            System.out.printf("Comp %d: N2=%.4f He=%.4f\n",
                i+1, Deco_Gradient_N2[i], Deco_Gradient_He[i]);
        }
    }

    private static void debugStopProgression(double currentDepth, double nextDepth, double stopTime) {
        System.out.printf("\n=== STOP COMPLETE ===\n");
        System.out.printf("Current Depth: %.1fm\n", currentDepth);
        System.out.printf("Next Depth: %.1fm\n", nextDepth);
        System.out.printf("Stop Time: %.1f min\n", stopTime);
        System.out.printf("Current Runtime: %.2f min\n", currentDive.currentRunTime);
    }
    
    
    /**
     *  Jag kan även kolla så jag gör ungefär samma som DecoPlanner compartmentIndex C:\Andreas\VB\DecoPlanner 3.1\Deco Planner 3\DECO.BAS
     * @param theDive
     * @return Dive
     */
    //public static ObservableList<DiveDecoSegment> CalculateOpenCircuitDeco(Body theBody)
    public static Dive CalculateOpenCircuitDeco(Dive theDive)
    {
        // Clear the stops log file at the start of each calculation
        try {
            java.io.FileWriter fw = new java.io.FileWriter("decoplanner_stops_log.txt", false);
            fw.write("");
            fw.close();
        } catch (Exception e) {
            // Ignore file write errors
        }
        
        Constant_Pressure_Other_Gases = (Settings.Pressure_Other_Gases_mmHg/760.0) * Settings.Depth_Per_ATM;
        
        currentDive = theDive; // body.getCurrentDive();
        //reset the currentDive object to surface state (but not tissue pressures and oxygen exposure, since this might be a repetitive dive)
        currentDive.activeGasID = 0;
        currentDive.currentDepth = 0;
        currentDive.currentRate = Settings.descentRate;
        currentDive.currentRunTime = 0;
        
//        ArrayList<DecoGas> decoGases = currentDive.decoGases;
        nitrogenCompartmentPressure = currentDive.initialNitrogenCompartmentPressure.clone(); //body.nitrogenCompartmentPressure; //array
        heliumCompartmentPressure = currentDive.initialHeliumCompartmentPressure.clone(); //body.heliumCompartmentPressure;  //array
        
        //
        /*  C===============================================================================
        //  C     INITIALIZE VARIABLES FOR SEA LEVEL OR ALTITUDE DIVE
        //  C     See subroutines for explanation of altitude calculations.  Purposes are
        //  C     1) to determine barometric pressure and 2) set or adjust the VPM critical
        //  C     radius variables and gas loadings, as applicable, based on altitude,
        //  C     ascent to altitude before the dive, and time at altitude before the dive
        //  C==============================================================================*/
        if(Settings.diveAtAltitude)
        {
            VPM_ALTITUDE_DIVE_ALGORITHM();
            //I forgot to do the following before version 4.6.4 ...
            currentDive.surfacePressure = Settings.getSurfacePressure();
            currentDive.surfaceN2Pressure = Settings.getSurfaceN2Saturation();
        }
        else
        {
            //Altitude_of_Dive = 0.0;
            //CALC_BAROMETRIC_PRESSURE(Altitude_of_Dive);           //subroutine

            for(int compartmentIndex=0; compartmentIndex<16; compartmentIndex++)
            {
                Adjusted_Critical_Radius_N2[compartmentIndex] = Initial_Critical_Radius_N2[compartmentIndex];
                Adjusted_Critical_Radius_He[compartmentIndex] = Initial_Critical_Radius_He[compartmentIndex];
                //Helium_Pressure[compartmentIndex] = 0.0;
                //Nitrogen_Pressure[compartmentIndex] = (Barometric_Pressure - Water_Vapor_Pressure) * 0.79;
            }
        }
        
        // Debug: Show initialization
        debugVPM("INIT");
        
        
        
//        body = theBody;
        
        
      
        CNStoxicityPercentage = currentDive.initialCNStoxicityPercentage;
        OTUbuildup = currentDive.initialOTUbuildup;
        
//        Settings.setWaterVaporPressure(Water_Vapor_Pressure);
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

        DiveSegment diveSegment;
        //kör nu igenom alla segment och uppdatera vävnaderna och se om det behövs nån deko mellan de segment som användaren lagt in
        //for(DiveSegment diveSegment : currentDive.diveSegments)    
        
        for(int currentSegmentIndex = 0; currentSegmentIndex < currentDive.diveSegments.size(); currentSegmentIndex++)
        {
            diveSegment = currentDive.diveSegments.get(currentSegmentIndex);
            
            Pamb0 = diveSegment.startDepth + surfacePressure;		// ambient pressure at start of this segment
            Pamb = diveSegment.endDepth + surfacePressure;		// ambient pressure at the end of this segment
            segmentDuration = diveSegment.duration;
            currentDive.currentRate = (Pamb - Pamb0) / segmentDuration;	// ascent/descent rate msw/min or fsw/min
            FHe = currentDive.getCurrentHeliumFraction();		// fraction He.  0.xx
            FN2 = 1 - FHe - currentDive.getCurrentOxygenFraction();	// fraction N2.  0.xx
            //Update compartment pressures
            switch (diveSegment.segmentType) 
            {
                case DiveSegment.CONSTANT_DEPTH:
                    
                    //Check if the previous segment was an ascent without deco, and if we want to include the ascent time in the run time of this constant-depth segment
                    if(ascentDone && Settings.includeTravelTimeInDiveDuration)
                    {
                        //And also actually change the duration in the diveSegment
                        diveSegment.duration -= currentDive.diveSegments.get(currentSegmentIndex - 1).duration; //subtract the ascent-segment's duration
                        diveSegment.endRunTime = diveSegment.startRunTime + diveSegment.duration;
                        currentDive.diveSegments.set(currentSegmentIndex, diveSegment);
                    }

                    //Se till att vi verkligen har rätt gas aktiv, eftersom vi kan ha växlat gas under tidigare dekostopp
                    currentDive.activeGasID = diveSegment.gasID;

                    GAS_LOADINGS_CONSTANT_DEPTH(diveSegment.startDepth, diveSegment.duration);
                    
                    currentDive.currentDepth = diveSegment.startDepth;
                                        
                    currentDive.currentRunTime += diveSegment.duration;
                    
                    //skapa ett decoSegment och lägg till compartmentIndex resulting deco plan
                    DecoTableSegment decoSegment = DIVEDATA_CONSTANT_DEPTH(diveSegment.startDepth, Settings.RMV_During_Dive, diveSegment.duration);

                    //set the duration back to the user-input's value if we included ascent time in this
                    if(Settings.includeTravelTimeInDiveDuration)
                    {
                       decoSegment.setDuration(diveSegment.duration + currentDive.diveSegments.get(currentSegmentIndex - 1).duration);
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
                    }
                    
                    currentDive.resultingDecoPlan.add(decoSegment);

                    //Update this diveSegment's compartment pressures
                    diveSegment.nitrogenCompartmentPressuresAtEndOfDuration = nitrogenCompartmentPressure.clone();
                    diveSegment.heliumCompartmentPressuresAtEndOfDuration = heliumCompartmentPressure.clone();
                    //Now update the diveSegment object in the actual array (since the diveSegment is NOT a reference)
                    currentDive.diveSegments.set(currentSegmentIndex, diveSegment);
                    
                    ascentDone = false;
                    break;
                case DiveSegment.ASCENT:
                    // FÖRST MÅSTE JAG KOLLA SÅ INTE DECO-CEILING ÖVERSKRIDS NÄR VI GÅR GRUNDARE!
                    if(directAscentIsSafe(diveSegment.startDepth, diveSegment.endDepth))
                    {
                        //Jag behöver väl köra NUCLEAR_GENERATION() först, för tiden på botten
                        NUCLEAR_REGENERATION(currentDive.currentRunTime);
                        
                        GAS_LOADINGS_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.ascentRate);
                        
                        // och sen BOYLES_LAW_COMPENSATION() på nått sätt för att spåra att bubblorna växer under denna uppstigning..?
                        //nä, den körs normalt bara innan DECOMPRESSION_STOP()
                        
                        
                        DIVEDATA_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.descentRate, Settings.RMV_During_Dive);

                        //Update this diveSegment's compartment pressures
                        diveSegment.nitrogenCompartmentPressuresAtEndOfDuration = nitrogenCompartmentPressure.clone();
                        diveSegment.heliumCompartmentPressuresAtEndOfDuration = heliumCompartmentPressure.clone();
                        currentDive.diveSegments.set(currentSegmentIndex, diveSegment);
                        currentDive.currentDepth = diveSegment.endDepth;
                        currentDive.currentRunTime += segmentDuration;
                        ascentDone = true;
                    }
                    else
                    {
                        //perform decompression up to diveSegment.endDepth and add
                        // all the deco stops to both the diveSegments-array plus the resultingDecoPlan
                        
                        //Jag måste ju ta bort detta ASCENT-segment från arrayen eftersom decompress-functionen lägger till ett ascent-segment till första stoppet.
                        // Och eftersom decompress-funktionen lägger till nya diveSegment compartmentIndex currentDive.diveSegments så måste jag här först spara undan 
                        // alla efterkommande diveSegments som finns INNAN decompress-funktionen, och ta bort dem från currentDive.diveSegments så de nya segmenten läggs till 
                        // compartmentIndex rätt ordning
                        
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
                        //kolla nu hur många segment decompress-funktionen lade till, så vi kan uppdatera currentSegmentIndex (för foor-loopen vi är compartmentIndex)
                        int numberOfSegmentsAddedByDeco = currentDive.diveSegments.size() - numberOfSegmentsBeforeAddingDeco;
                        currentSegmentIndex = currentSegmentIndex + numberOfSegmentsAddedByDeco - 1; // -1 för att vi redan tog bort detta ascent-segment från arrayen
                        //update the currentDive status
                        currentDive.currentDepth = diveSegment.endDepth;
                        currentDive.currentRunTime = currentDive.diveSegments.get(currentDive.diveSegments.size()-1).endRunTime; //current runtime is the endRunTime of the last segment that the decompress-function added
                        //nu är vi på diveSegment.endDepth, så lägg nu till de efterföljande segmenten som vi sparade undan
                        //Först måste jag uppdatera alla StartRunTime och EndRunTimes för alla segmenten compartmentIndex tempDiveSegments, så jag lägger på den dekotid som lagts till på dyket nu.
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
                    }
                    // Måste även kolla så inte gasupptaget vid denna uppstigning gör att deco-ceiling överskrids
/*                    else if(diveSegment.endDepth < PROJECTED_ASCENT(diveSegment.startDepth, Math.negateExact(Settings.ascentRate), diveSegment.endDepth, Settings.decoStopInterval))
                    {
                        //perform decompression up to diveSegment.endDepth and add
                        // all the deco stops to both the diveSegments-array plus the resultingDecoPlan
                        
                        //Jag måste ju ta bort detta ASCENT-segment från arrayen eftersom decompress-functionen lägger till ett ascent-segment till första stoppet.
                        // Och eftersom decompress-funktionen lägger till nya diveSegment compartmentIndex currentDive.diveSegments så måste jag här först spara undan 
                        // alla efterkommande diveSegments som finns INNAN decompress-funktionen, och ta bort dem från currentDive.diveSegments så de nya segmenten läggs till 
                        // compartmentIndex rätt ordning
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
                        //kolla nu hur många segment decompress-funktionen lade till, så vi kan uppdatera currentSegmentIndex (för foor-loopen vi är compartmentIndex)
                        int numberOfSegmentsAddedByDeco = currentDive.diveSegments.size() - numberOfSegmentsBeforeAddingDeco;
                        currentSegmentIndex = currentSegmentIndex + numberOfSegmentsAddedByDeco - 1; // -1 för att vi redan tog bort detta ascent-segment från arrayen
                        //nu är vi på diveSegment.endDepth, så lägg nu till de efterföljande segmenten som vi sparade undan
                        currentDive.diveSegments.addAll(tempDiveSegments);     
                    }
                    else
                    {
                        //Deco-ceiling kommer inte överskridas, så uppdatera bara vävnader och syreexponering.
                        GAS_LOADINGS_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.ascentRate);
                        //updateHighestMvaluePercentageAndGradientFactor(diveSegment.endDepth + currentDive.surfacePressure);
                        DIVEDATA_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.ascentRate, Settings.RMV_During_Dive);
                        //Update this diveSegment's compartment pressures
                        diveSegment.nitrogenCompartmentPressuresAtEndOfDuration = nitrogenCompartmentPressure.clone();
                        diveSegment.heliumCompartmentPressuresAtEndOfDuration = heliumCompartmentPressure.clone();
                        currentDive.diveSegments.set(currentSegmentIndex, diveSegment);
                        currentDive.currentDepth = diveSegment.endDepth;
                        currentDive.currentRunTime += segmentDuration;
                    }
                    */
                    ascentDone = true;
                    currentDive.currentDepth = diveSegment.endDepth;
                    break;
                case DiveSegment.DESCENT:
                    
                    if(ascentDone) 
                    {
                        //since we're doing a descent again, reset these variables
                        firstStopDepthOfTotalAscent = 0.0;
                        runtimeStartOfInitialDecoZone = 0.0;
                    }
                    
                    double[] nitrogenCompartmentPressureBeforeDescent = nitrogenCompartmentPressure.clone();
                    double[] heliumCompartmentPressureBeforeDescent = heliumCompartmentPressure.clone();
                    
                    GAS_LOADINGS_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.descentRate);
                    
                    CALC_CRUSHING_PRESSURE(diveSegment.startDepth, diveSegment.endDepth, Settings.descentRate, nitrogenCompartmentPressureBeforeDescent, heliumCompartmentPressureBeforeDescent);
                    
                    //updateHighestMvaluePercentageAndGradientFactor(diveSegment.endDepth + currentDive.surfacePressure);
                    
                    DIVEDATA_ASCENT_DESCENT(diveSegment.startDepth, diveSegment.endDepth, Settings.descentRate, Settings.RMV_During_Dive);
                    
                    //Update this diveSegment's compartment pressures
                    diveSegment.nitrogenCompartmentPressuresAtEndOfDuration = nitrogenCompartmentPressure.clone();
                    diveSegment.heliumCompartmentPressuresAtEndOfDuration = heliumCompartmentPressure.clone();
                    currentDive.diveSegments.set(currentSegmentIndex, diveSegment);
                    currentDive.currentDepth = diveSegment.endDepth;
                    currentDive.currentRunTime += segmentDuration;
                    ascentDone = false;
                    currentDive.gradientFactorSlope = 0.0; //if we have done an ascent and then descended again, this will reset the gradient factors again.
                    
                    break;
            }
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

        // Write confirmation files for VPM calculations
        try {
            // Extract dive profile information for logical filename
            int maxDepth = 0;
            int bottomTime = 0;
            String gasInfo = "";
            
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
            
            // Create logical filename: depth_time_gases_VPM[conservatism]
            String logicalName = maxDepth + "m" + bottomTime + "min_" + gasStr + "_VPM" + Settings.vpmConservatismSetting;
            
            // Add descent rate to filename if it's not the standard 10m/min
            if (Settings.descentRate != 10) {
                logicalName += "_DR" + Settings.descentRate;
            }
            
            String fileName = logicalName + ".txt";
            
            // Write detailed output file
            java.io.PrintWriter fileOut = new java.io.PrintWriter(new java.io.FileWriter(fileName, false));
            fileOut.println("\n=== VPM DIVE SCHEDULE ===");
            fileOut.println("Generated: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            fileOut.println("VPM Conservatism: +" + Settings.vpmConservatismSetting);
            fileOut.println("Descent Rate: " + Settings.descentRate + " m/min");
            fileOut.println("Ascent Rate: " + Settings.ascentRate + " m/min");
            fileOut.println("Total Runtime: " + String.format("%.1f", currentDive.currentRunTime) + " min");
            fileOut.println("Total Deco Duration: " + String.format("%.1f", currentDive.totalDecoDuration) + " min");
            
            fileOut.println("\nDecompression Schedule:");
            if (currentDive.resultingDecoPlan != null) {
                for (Object obj : currentDive.resultingDecoPlan) {
                    if (obj instanceof DecoTableSegment) {
                        DecoTableSegment stop = (DecoTableSegment)obj;
                        fileOut.println(String.format("%.0fm for %s min", 
                            stop.getDepthNumber(), stop.getDuration()));
                    }
                }
            }
            fileOut.close();
            System.out.println("VPM schedule written to: " + fileName);
            
            // Write CONFIRMATION file
            String confirmFile = "LAST_VPM_CAPTURED.txt";
            java.io.PrintWriter confirmOut = new java.io.PrintWriter(new java.io.FileWriter(confirmFile, false));
            confirmOut.println("=== VPM DIVE PLAN SUCCESSFULLY CAPTURED ===");
            confirmOut.println("Time: " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
            confirmOut.println("VPM Conservatism: +" + Settings.vpmConservatismSetting);
            confirmOut.println("Runtime: " + (int)currentDive.currentRunTime + " min");
            confirmOut.println("✓ CAPTURED IN: " + fileName);
            confirmOut.close();
            
            // Append to log
            java.io.PrintWriter logOut = new java.io.PrintWriter(new java.io.FileWriter("all_captures.log", true));
            logOut.println(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + 
                " | " + fileName + " | ✓ CAPTURED");
            logOut.close();
            
        } catch (Exception e) {
            System.err.println("Error writing VPM schedule to file: " + e.getMessage());
        }

        return currentDive;
    }
	

	
    /*===============================================================================
    //     SUBROUTINE CALC_DECO_CEILING
    //     Purpose: This subprogram calculates the deco ceiling (the safe ascent
    //     depth) in each compartment, based on the allowable "deco gradients"
    //     computed in the Boyle's Law Compensation subroutine, and then finds the
    //     deepest deco ceiling across all compartments.  This deepest value
    //     (Deco Ceiling Depth) is then used by the Decompression Stop subroutine
    //     to determine the actual deco schedule.
    //===============================================================================*/
    private static double CALC_DECO_CEILING()
    {
        //IMPLICIT NONE
        //===============================================================================
        //     LOCAL VARIABLES
        //===============================================================================
        double Gas_Loading, Weighted_Allowable_Gradient;
        double Tolerated_Ambient_Pressure;
        //===============================================================================
        //     LOCAL ARRAYS
        //===============================================================================
        double[] Compartment_Deco_Ceiling = new double[16];
        /*===============================================================================
        //     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Constant_Pressure_Other_Gases
        COMMON /Block_17/ Constant_Pressure_Other_Gases
        //===============================================================================
        //     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        //===============================================================================
        double Barometric_Pressure
        COMMON /Block_18/ Barometric_Pressure
        //===============================================================================
        //     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Helium_Pressure(16), Nitrogen_Pressure(16)                     //input
        COMMON /Block_3/ Helium_Pressure, Nitrogen_Pressure

        double Deco_Gradient_He(16), Deco_Gradient_N2(16)                     //input
        COMMON /Block_34/ Deco_Gradient_He, Deco_Gradient_N2
        //===============================================================================
        //     CALCULATIONS
        //     Since there are two sets of deco gradients being tracked, one for
        //     helium and one for nitrogen, a "weighted allowable gradient" must be
        //     computed each time based on the proportions of helium and nitrogen in
        //     each compartment.  This proportioning follows the methodology of
        //     Buhlmann/Keller.  If there is no helium and nitrogen in the compartment,
        //     such as after extended periods of oxygen breathing, then the minimum value
        //     across both gases will be used.  It is important to note that if a
        //     compartment is empty of helium and nitrogen, then the weighted allowable
        //     gradient formula cannot be used since it will result in division by zero.
        //===============================================================================*/
        for(int i=0; i<16; i++)
        {
            Gas_Loading = heliumCompartmentPressure[i] + nitrogenCompartmentPressure[i];

            if(Gas_Loading > 0.0)
            {
                Weighted_Allowable_Gradient = (Deco_Gradient_He[i]* heliumCompartmentPressure[i] + Deco_Gradient_N2[i]* nitrogenCompartmentPressure[i]) / Gas_Loading;

                Tolerated_Ambient_Pressure = (Gas_Loading + Constant_Pressure_Other_Gases) - Weighted_Allowable_Gradient;
            }
            else
            {
                Weighted_Allowable_Gradient = Math.min(Deco_Gradient_He[i], Deco_Gradient_N2[i]);

                Tolerated_Ambient_Pressure = Constant_Pressure_Other_Gases - Weighted_Allowable_Gradient;
            }
            //===============================================================================
            //     The tolerated ambient pressure cannot be less than zero absolute, i.e.,
            //     the vacuum of outer space!
            //===============================================================================
            if(Tolerated_Ambient_Pressure < 0.0)
            {
                Tolerated_Ambient_Pressure = 0.0;
            }
            //===============================================================================
            //     The Deco Ceiling Depth is computed in a loop after all of the individual
            //     compartment deco ceilings have been calculated.  It is important that the
            //     Deco Ceiling Depth (max deco ceiling across all compartments) only be
            //     extracted from the compartment values and not be compared against some
            //     initialization value.  For example, if MAX(Deco_Ceiling_Depth . .) was
            //     compared against zero, this could cause a program lockup because sometimes
            //     the Deco Ceiling Depth needs to be negative (but not less than absolute
            //     zero) in order to decompress to the last stop at zero depth.
            //===============================================================================
            Compartment_Deco_Ceiling[i] = Tolerated_Ambient_Pressure - Settings.getSurfacePressure();
        }
        double Deco_Ceiling_Depth = Compartment_Deco_Ceiling[0];
        //DO I = 2,16
        for(int i=1; i<16; i++)
        {
            Deco_Ceiling_Depth = Math.max(Deco_Ceiling_Depth, Compartment_Deco_Ceiling[i]);
        }
        //===============================================================================
        //     END OF SUBROUTINE
        //===============================================================================
        //RETURN
        //END
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
        int Rate = Math.negateExact(Settings.ascentRate);
        //IMPLICIT NONE
        //===============================================================================
        //     LOCAL VARIABLES
        //===============================================================================
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

        //double SCHREINER_EQUATION                               //function subprogram
        /*===============================================================================
        //     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Water_Vapor_Pressure
        COMMON /Block_8/ Water_Vapor_Pressure

        double Constant_Pressure_Other_Gases
        COMMON /Block_17/ Constant_Pressure_Other_Gases
        //===============================================================================
        //     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        //===============================================================================
        int Mix_Number
        COMMON /Block_9/ Mix_Number

        double Barometric_Pressure
        COMMON /Block_18/ Barometric_Pressure
        //===============================================================================
        //     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Helium_Time_Constant(16)
        COMMON /Block_1A/ Helium_Time_Constant

        double Nitrogen_Time_Constant(16)
        COMMON /Block_1B/ Nitrogen_Time_Constant

        double Helium_Pressure(16), Nitrogen_Pressure(16)
        COMMON /Block_3/ Helium_Pressure, Nitrogen_Pressure

        double Fraction_Helium(10), Fraction_Nitrogen(10)
        COMMON /Block_5/ Fraction_Helium, Fraction_Nitrogen
        //===============================================================================
        //     CALCULATIONS
        //     First initialize some variables
        //===============================================================================*/
        double Depth_Start_of_Deco_Zone = 0.0;
        Starting_Ambient_Pressure = Starting_Depth + Settings.getSurfacePressure();

        Initial_Inspired_He_Pressure = (Starting_Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentHeliumFraction();

        Initial_Inspired_N2_Pressure = (Starting_Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentNitrogenFraction();

        Helium_Rate = Rate * currentDive.getCurrentHeliumFraction();
        Nitrogen_Rate = Rate * currentDive.getCurrentNitrogenFraction();
        //===============================================================================
        //     ESTABLISH THE BOUNDS FOR THE ROOT SEARCH USING THE BISECTION METHOD
        //     AND CHECK TO MAKE SURE THAT THE ROOT WILL BE WITHIN BOUNDS.  PROCESS
        //     EACH COMPARTMENT INDIVIDUALLY AND FIND THE MAXIMUM DEPTH ACROSS ALL
        //     COMPARTMENTS (LEADING COMPARTMENT)
        //     In this case, we are solving for time - the time when the gas tension in
        //     the compartment will be equal to ambient pressure.  The low bound for time
        //     is set at zero and the high bound is set at the time it would take to
        //     ascend to zero ambient pressure (absolute).  Since the ascent rate is
        //     negative, a multiplier of -1.0 is used to make the time positive.  The
        //     desired point when gas tension equals ambient pressure is found at a time
        //     somewhere between these endpoints.  The algorithm checks to make sure that
        //     the solution lies in between these bounds by first computing the low bound
        //     and high bound function values.
        //===============================================================================
        Low_Bound = 0.0;
        High_Bound = -1.0*(Starting_Ambient_Pressure/Rate);
        //DO 200 I = 1,16
        for(int i=0; i<16; i++)
        {
            Initial_Helium_Pressure = heliumCompartmentPressure[i];
            Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[i];

            Function_at_Low_Bound = Initial_Helium_Pressure + Initial_Nitrogen_Pressure + Constant_Pressure_Other_Gases - Starting_Ambient_Pressure;

            High_Bound_Helium_Pressure = SCHREINER_EQUATION(Initial_Inspired_He_Pressure, Helium_Rate, High_Bound, kHe[i], Initial_Helium_Pressure);

            High_Bound_Nitrogen_Pressure = SCHREINER_EQUATION(Initial_Inspired_N2_Pressure, Nitrogen_Rate, High_Bound, kN2[i], Initial_Nitrogen_Pressure);

            Function_at_High_Bound = High_Bound_Helium_Pressure + High_Bound_Nitrogen_Pressure+Constant_Pressure_Other_Gases;

            if((Function_at_High_Bound * Function_at_Low_Bound) >= 0.0)
            {
                //This compartment is already supersaturated from the start (i.e. it has higher pressure than Starting_Ambient_Pressure
                return Starting_Depth; //eller?
                //PRINT *,'ERROR! ROOT IS NOT WITHIN BRACKETS'
                //PAUSE
//                System.err.println("ERROR! ROOT IS NOT WITHIN BRACKETS (788) - CALC_START_OF_DECO_ZONE - starting at " + Starting_Depth);
            }
            //===============================================================================
            //     APPLY THE BISECTION METHOD IN SEVERAL ITERATIONS UNTIL A SOLUTION WITH
            //     THE DESIRED ACCURACY IS FOUND
            //     Note: the program allows for up to 100 iterations.  Normally an exit will
            //     be made from the loop well before that number.  If, for some reason, the
            //     program exceeds 100 iterations, there will be a pause to alert the user.
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
            boolean solutionNotFound = true;
            //DO 150 J = 1, 100
            for(int J=1; J<=100; J++)
            {
                Last_Diff_Change = Differential_Change;
                Differential_Change = Last_Diff_Change*0.5;

                Mid_Range_Time = Time_to_Start_of_Deco_Zone + Differential_Change;

                Mid_Range_Helium_Pressure = SCHREINER_EQUATION(Initial_Inspired_He_Pressure, Helium_Rate, Mid_Range_Time, kHe[i], Initial_Helium_Pressure);

                Mid_Range_Nitrogen_Pressure = SCHREINER_EQUATION(Initial_Inspired_N2_Pressure, Nitrogen_Rate, Mid_Range_Time, kN2[i], Initial_Nitrogen_Pressure);

                Function_at_Mid_Range = Mid_Range_Helium_Pressure + Mid_Range_Nitrogen_Pressure + Constant_Pressure_Other_Gases - (Starting_Ambient_Pressure + Rate*Mid_Range_Time);

                if(Function_at_Mid_Range <= 0.0)
                    Time_to_Start_of_Deco_Zone = Mid_Range_Time;

                if((Math.abs(Differential_Change) < 1.0E-3) || (Function_at_Mid_Range == 0.0))
                {//GOTO 170
                    solutionNotFound = false;
                    break;
                }
                else
                {
                    solutionNotFound = true;
                }
                //150       CONTINUE
            }
            if(solutionNotFound)
            {
                //PRINT *,'ERROR! ROOT SEARCH EXCEEDED MAXIMUM ITERATIONS'
                //PAUSE
                System.out.println("ERROR! ROOT SEARCH EXCEEDED MAXIMUM ITERATIONS");
            }
            //===============================================================================
            //     When a solution with the desired accuracy is found, the program jumps out
            //     of the loop to Line 170 and assigns the solution value for the individual
            //     compartment.
            //===============================================================================
//  170
            Cpt_Depth_Start_of_Deco_Zone = (Starting_Ambient_Pressure + Rate*Time_to_Start_of_Deco_Zone) - Settings.getSurfacePressure();
            //===============================================================================
            //     The overall solution will be the compartment with the maximum depth where
            //     gas tension equals ambient pressure (leading compartment).
            //===============================================================================
            Depth_Start_of_Deco_Zone = Math.max(Depth_Start_of_Deco_Zone, Cpt_Depth_Start_of_Deco_Zone);
//  200   CONTINUE
        }
        //===============================================================================
        //     END OF SUBROUTINE
        //===============================================================================
        //RETURN
        //END
        return Depth_Start_of_Deco_Zone;
    }	
	
    /**
     * perform decompression up to ending_depth and add all the decompression 
     *  stops to both the diveSegments-array plus the resultingDecoPlan
     * @param ascent_starting_depth
     * @param ending_depth 
     */
    private static void decompress(int ascent_starting_depth, int ending_depth)
    {
        // Only print this if we're actually decompressing to surface
        if (ending_depth == 0) {
            System.out.println("\n=== DECOMPRESS TO SURFACE CALLED FROM " + ascent_starting_depth + "m ===");
            System.out.println("Current runtime: " + currentDive.currentRunTime);
        }

//  C===============================================================================
//  C     BEGIN PROCESS OF ASCENT AND DECOMPRESSION
//  C     First, calculate the regeneration of critical radii that takes place over
//  C     the dive time.  The regeneration time constant has a time scale of weeks
//  C     so this will have very little impact on dives of normal length, but will
//  C     have major impact for saturation dives.
//  C===============================================================================

        NUCLEAR_REGENERATION(currentDive.currentRunTime);                           //subroutine
        
        // Debug: Show state after bottom time
        debugVPM("BOTTOM");
        
//  C===============================================================================
//  C     CALCULATE INITIAL ALLOWABLE GRADIENTS FOR ASCENT
//  C     This is based on the maximum effective crushing pressure on critical radii
//  C     in each compartment achieved during the dive profile.
//  C===============================================================================

        CALC_INITIAL_ALLOWABLE_GRADIENT();                           //subroutine
        
        // Debug: Show initial gradients
        debugVPM("GRADIENT");
        
//  C===============================================================================
//  C     SAVE VARIABLES AT START OF ASCENT (END OF BOTTOM TIME) SINCE THESE WILL
//  C     BE USED LATER TO COMPUTE THE FINAL ASCENT PROFILE THAT IS WRITTEN TO THE
//  C     OUTPUT FILE.
//  C     The VPM uses an iterative process to compute decompression schedules so
//  C     there will be more than one pass through the decompression loop.
//  C===============================================================================
        for(int i=0; i<16; i++)
        {
            He_Pressure_Start_of_Ascent[i] = heliumCompartmentPressure[i];
            N2_Pressure_Start_of_Ascent[i] = nitrogenCompartmentPressure[i];
        }
        Run_Time_Start_of_Ascent = currentDive.currentRunTime;
        Segment_Number_Start_of_Ascent = currentDive.diveSegments.size();
        int gasIndexAtStartOfAscent = currentDive.activeGasID;
//  C===============================================================================
//  C     INPUT PARAMETERS TO BE USED FOR STAGED DECOMPRESSION AND SAVE IN ARRAYS.
//  C     ASSIGN INITAL PARAMETERS TO BE USED AT START OF ASCENT
//  C     The user has the ability to change mix, ascent rate, and step size in any
//  C     combination at any depth during the ascent.
//  C===============================================================================
/*        try
        {
                //READ (7,*) Number_of_Changes
                line = inFileReader.readLine().trim();
                Number_of_Changes = Integer.parseInt(line);
                for(int compartmentIndex=1; compartmentIndex<=Number_of_Changes; compartmentIndex++)
                {
                        //READ (7,*) Depth_Change[compartmentIndex], Mix_Change[compartmentIndex], Rate_Change[compartmentIndex], Step_Size_Change[compartmentIndex]
                        line = inFileReader.readLine().trim();
                        String[] values = line.split(",");
                        Depth_Change[compartmentIndex] = Double.parseDouble(values[0]);
                        Mix_Change[compartmentIndex] = Integer.parseInt(values[1]);
                        Rate_Change[compartmentIndex] = Double.parseDouble(values[2]);
                        Step_Size_Change[compartmentIndex] = Double.parseDouble(values[3]);
                }
        }
        catch(IOException e)
        {
                System.err.println(e.getMessage());
        }
        catch(NumberFormatException e)
        {
                System.err.println(e.getMessage());
        }
        Starting_Depth = Depth_Change[1];
        Mix_Number = Mix_Change[1];
        Rate = Rate_Change[1];
        Step_Size = Step_Size_Change[1]; */
//  C===============================================================================
//  C     CALCULATE THE DEPTH WHERE THE DECOMPRESSION ZONE BEGINS FOR THIS PROFILE
//  C     BASED ON THE INITIAL ASCENT PARAMETERS AND WRITE THE DEEPEST POSSIBLE
//  C     DECOMPRESSION STOP DEPTH TO THE OUTPUT FILE
//  C     Knowing where the decompression zone starts is very important.  Below
//  C     that depth there is no possibility for bubble formation because there
//  C     will be no supersaturation gradients.  Deco stops should never start
//  C     below the deco zone.  The deepest possible stop deco stop depth is
//  C     defined as the next "standard" stop depth above the point where the
//  C     leading compartment enters the deco zone.  Thus, the program will not
//  C     base this calculation on step sizes larger than 10 fsw or 3 msw.  The
//  C     deepest possible stop depth is not used in the program, per se, rather
//  C     it is information to tell the diver where to start putting on the brakes
//  C     during ascent.  This should be prominently displayed by any deco program.
//  C===============================================================================
        //CALC_START_OF_DECO_ZONE(Starting_Depth, Rate, Depth_Start_of_Deco_Zone);
        double Depth_Start_of_Deco_Zone = CALC_START_OF_DECO_ZONE(ascent_starting_depth);
        
        System.out.println("\n=== DECO ZONE CALCULATION ===");
        System.out.println("Ascent starting from depth: " + ascent_starting_depth + "m");
        System.out.println("Current runtime at bottom: " + currentDive.currentRunTime + " min");
        System.out.println("Depth where deco zone starts: " + Depth_Start_of_Deco_Zone + "m");
        
        if(currentDive.offgassingStartsAtDepth == 0.0)
        {
            currentDive.offgassingStartsAtDepth = Depth_Start_of_Deco_Zone;
        }
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

        //  C===============================================================================
        //  C     TEMPORARILY ASCEND PROFILE TO THE START OF THE DECOMPRESSION ZONE, SAVE
        //  C     VARIABLES AT THIS POINT, AND INITIALIZE VARIABLES FOR CRITICAL VOLUME LOOP
        //  C     The iterative process of the VPM Critical Volume Algorithm will operate
        //  C     only in the decompression zone since it deals with excess gas volume
        //  C     released as a result of supersaturation gradients (not possible below the
        //  C     decompression zone).
        //  C===============================================================================
        double durationOfAscent = GAS_LOADINGS_ASCENT_DESCENT(ascent_starting_depth, Depth_Start_of_Deco_Zone, Settings.ascentRate);
        currentDive.currentRunTime += durationOfAscent;
        double Run_Time_Start_of_Deco_Zone = currentDive.currentRunTime;
        
        System.out.println("\n=== ASCENT TO DECO ZONE ===");
        System.out.println("Duration of ascent from " + ascent_starting_depth + "m to " + Depth_Start_of_Deco_Zone + "m: " + durationOfAscent + " min");
        System.out.println("Runtime at start of deco zone: " + Run_Time_Start_of_Deco_Zone + " min");
        System.out.println("This is where phase volume time calculation starts from!");
        
        if(runtimeStartOfInitialDecoZone == 0.0) runtimeStartOfInitialDecoZone = Run_Time_Start_of_Deco_Zone;
        double Deco_Phase_Volume_Time = 0.0;
        double Last_Run_Time = 0.0;
        boolean Schedule_Converged = false;
        double[] Last_Phase_Volume_Time = new double[16];
        double[] He_Pressure_Start_of_Deco_Zone = new double[16];
        double[] N2_Pressure_Start_of_Deco_Zone = new double[16];
        for(int i=0; i<16; i++)
        {
            Last_Phase_Volume_Time[i] = 0.0;
            He_Pressure_Start_of_Deco_Zone[i] = heliumCompartmentPressure[i];
            N2_Pressure_Start_of_Deco_Zone[i] = nitrogenCompartmentPressure[i];
            Max_Actual_Gradient[i] = 0.0;
        }
        //  C===============================================================================
        //  C     START OF CRITICAL VOLUME LOOP
        //  C     This loop operates between Lines 50 and 100.  If the Critical Volume
        //  C     Algorithm is toggled "off" in the program settings, there will only be
        //  C     one pass through this loop.  Otherwise, there will be two or more passes
        //  C     through this loop until the deco schedule is "converged" - that is when a
        //  C     comparison between the phase volume time of the present iteration and the
        //  C     last iteration is less than or equal to one minute.  This implies that
        //  C     the volume of released gas in the most recent iteration differs from the
        //  C     "critical" volume limit by an acceptably small amount.  The critical
        //  C     volume limit is set by the Critical Volume Parameter Lambda in the program
        //  C     settings (default setting is 7500 fsw-min with adjustability range from
        //  C     from 6500 to 8300 fsw-min according to Bruce Wienke).
        //  C===============================================================================
        double Ascent_Ceiling_Depth;
        double Deco_Stop_Depth;
        double First_Stop_Depth;
        double Rounding_Operation2;
        double Starting_Depth;
        double Ending_Depth;
        double Next_Stop;
        double Critical_Volume_Comparison;
        DecoTableSegment decoSegment = new DecoTableSegment();
        
        // CVA iteration counter for debugging
        int cvaIteration = 0;
                
        //50    DO 100, WHILE (true)                   //loop will run continuously until
        while(true)                                    //there is an exit statement
        {
            cvaIteration++;
            // System.out.println("=== CVA ITERATION " + cvaIteration + " START ===");
            //  C===============================================================================
            //  C     CALCULATE INITIAL ASCENT CEILING BASED ON ALLOWABLE SUPERSATURATION
            //  C     GRADIENTS AND SET FIRST DECO STOP.  CHECK TO MAKE SURE THAT SELECTED STEP
            //  C     SIZE WILL NOT ROUND UP FIRST STOP TO A DEPTH THAT IS BELOW THE DECO ZONE.
            //  C===============================================================================
            Ascent_Ceiling_Depth = CALC_ASCENT_CEILING();                //subroutine
            
            // Debug: Show first ceiling calculation (only on first iteration)
            if (currentDive.diveSegments.size() == Segment_Number_Start_of_Ascent) {
                debugVPM("CEILING");
            }
            if(Ascent_Ceiling_Depth <= 0.0)
            {
                Deco_Stop_Depth = 0.0;
            }
            else
            {
                Rounding_Operation2 = (Ascent_Ceiling_Depth/Settings.decoStopInterval) + 0.5;
                Deco_Stop_Depth = Math.rint(Rounding_Operation2) * Settings.decoStopInterval;
            }

            if(Deco_Stop_Depth > Depth_Start_of_Deco_Zone)
            {
                //WRITE (*,905)
                //WRITE (*,900)
                //STOP 'PROGRAM TERMINATED'
                System.out.println("ERROR! STEP SIZE IS TOO LARGE TO DECOMPRESS. Deco_Stop_Depth = " + Deco_Stop_Depth + " and Depth_Start_of_Deco_Zone = " + Depth_Start_of_Deco_Zone);
                System.out.println("PROGRAM TERMINATED");
                System.exit(1);
            }
            //  C===============================================================================
            //  C     PERFORM A SEPARATE "PROJECTED ASCENT" OUTSIDE OF THE MAIN PROGRAM TO MAKE
            //  C     SURE THAT AN INCREASE IN GAS LOADINGS DURING ASCENT TO THE FIRST STOP WILL
            //  C     NOT CAUSE A VIOLATION OF THE DECO CEILING.  IF SO, ADJUST THE FIRST STOP
            //  C     DEEPER BASED ON STEP SIZE UNTIL A SAFE ASCENT CAN BE MADE.
            //  C     Note: this situation is a possibility when ascending from extremely deep
            //  C     dives or due to an unusual gas mix selection.
            //  C     CHECK AGAIN TO MAKE SURE THAT ADJUSTED FIRST STOP WILL NOT BE BELOW THE
            //  C     DECO ZONE.
            //  C===============================================================================
            
            double originalDecoStop = Deco_Stop_Depth;
            PROJECTED_ASCENT(Depth_Start_of_Deco_Zone, Settings.ascentRate, Deco_Stop_Depth, Settings.decoStopInterval);
            
            // Debug: Show PROJECTED_ASCENT adjustment (only on first iteration)
            if (currentDive.diveSegments.size() == Segment_Number_Start_of_Ascent) {
                System.out.println("PROJECTED_ASCENT: Initial ceiling=" + originalDecoStop + 
                                   " -> Adjusted to " + Deco_Stop_Depth);
            }

            if(Deco_Stop_Depth > Depth_Start_of_Deco_Zone)
            {
                //WRITE (*,905)
                //WRITE (*,900)
                //STOP 'PROGRAM TERMINATED'
                System.out.println("ERROR! STEP SIZE IS TOO LARGE TO DECOMPRESS, after Projected_Ascent");
                System.out.println("PROGRAM TERMINATED");
                System.exit(1);
            }
            //  C===============================================================================
            //  C     HANDLE THE SPECIAL CASE WHEN NO DECO STOPS ARE REQUIRED - ASCENT CAN BE
            //  C     MADE DIRECTLY TO THE SURFACE
            //  C     Write ascent data to output file and exit the Critical Volume Loop.
            //  C===============================================================================
            if(Deco_Stop_Depth == ending_depth) //if(Deco_Stop_Depth == 0.0)
            {
                for(int i=0; i<16; i++)
                {
                    heliumCompartmentPressure[i] = He_Pressure_Start_of_Ascent[i];
                    nitrogenCompartmentPressure[i] = N2_Pressure_Start_of_Ascent[i];
                }
                currentDive.currentRunTime = Run_Time_Start_of_Ascent;
                //Segment_Number = Segment_Number_Start_of_Ascent;
                Starting_Depth = ascent_starting_depth;
                Ending_Depth = 0.0;
                GAS_LOADINGS_ASCENT_DESCENT(Starting_Depth, Ending_Depth, Settings.ascentRate);
                //WRITE (8,860) Segment_Number, Segment_Time, Run_Time, Mix_Number, Deco_Stop_Depth, Rate
                //I3,3X,F5.1,1X,F6.1,1X,'|',3X,I2,3X,'|',2X,F4.0,3X,F6.1,10X,'|'
                
                break; //EXIT                       //exit the critical volume loop at Line 100
            }
            //  C===============================================================================
            //  C     ASSIGN VARIABLES FOR ASCENT FROM START OF DECO ZONE TO FIRST STOP.  SAVE
            //  C     FIRST STOP DEPTH FOR LATER USE WHEN COMPUTING THE FINAL ASCENT PROFILE
            //  C===============================================================================
            Starting_Depth = Depth_Start_of_Deco_Zone;
            First_Stop_Depth = Deco_Stop_Depth;
            
            if(firstStopDepthOfTotalAscent < First_Stop_Depth) //2019-04-19 ifall man gör manuella stopp, så denna funktion anropas flera gånger. Främst för att användas i BOYES_LAW
                firstStopDepthOfTotalAscent = First_Stop_Depth; 
            
            
            double decoStopTime = 0;
            //  C===============================================================================
            //  C     DECO STOP LOOP BLOCK WITHIN CRITICAL VOLUME LOOP
            //  C     This loop computes a decompression schedule to the surface during each
            //  C     iteration of the critical volume loop.  No output is written from this
            //  C     loop, rather it computes a schedule from which the in-water portion of the
            //  C     total phase volume time (Deco_Phase_Volume_Time) can be extracted.  Also,
            //  C     the gas loadings computed at the end of this loop are used the subroutine
            //  C     which computes the out-of-water portion of the total phase volume time
            //  C     (Surface_Phase_Volume_Time) for that schedule.
            //  C
            //  C     Note that exit is made from the loop after last ascent is made to a deco
            //  C     stop depth that is less than or equal to zero.  A final deco stop less
            //  C     than zero can happen when the user makes an odd step size change during
            //  C     ascent - such as specifying a 5 msw step size change at the 3 msw stop!
            //  C===============================================================================
            while(true)                        //loop will run continuously until
            {                                         //there is an exit statement
                durationOfAscent = GAS_LOADINGS_ASCENT_DESCENT(Starting_Depth, Deco_Stop_Depth, Settings.ascentRate);
                currentDive.currentRunTime += durationOfAscent;

                if(Deco_Stop_Depth <= ending_depth)
                {
                    if(ending_depth != 0) {
                        System.out.println("BOYLES_LAW_COMPENSATION called for depth: " + ending_depth);
                        System.out.println("First_Stop_Depth: " + First_Stop_Depth);
                        BOYLES_LAW_COMPENSATION(First_Stop_Depth, ending_depth, Settings.decoStopInterval); //2019-04-19
                    }
                    
                    break;  // if(Deco_Stop_Depth <= 0.0) break;                  //exit at Line 60
                }
                /*if(Number_of_Changes > 1)
                {
                    for(int compartmentIndex=2; compartmentIndex<=Number_of_Changes; compartmentIndex++)
                    {
                        if(Depth_Change[compartmentIndex] >= Deco_Stop_Depth)
                        {
                            Mix_Number = Mix_Change[compartmentIndex];
//                            Rate = Rate_Change[compartmentIndex];
//                            Step_Size = Step_Size_Change[compartmentIndex];
                        }
                    }
                }*/
                //See if it's time to switch gases or decoStopInterval..
                Gas decoGas;
                Gas currentGas = currentDive.getCurrentGas(); //added in version 4.6.3
                
                //Loop through all gases and see if we need to switch to any of them
                for(int gasID=0; gasID<currentDive.gases.size(); gasID++)
                {
                    decoGas = currentDive.gases.get(gasID);
                    //Check that
                    //1) Current depth is less or equal to the switchdepth of the gas we're now checking
                    //2) The switchdepth of the gas we're now checking is less/shallower than the switchdepth of the gas we're currently using
                    //3) The gas we're checking is a deco gas
                    //4) The gas we're now checking is now the one we're already using
                    if(Deco_Stop_Depth <= decoGas.switchDepth && decoGas.gasType == Gas.DECO_GAS && currentDive.activeGasID != gasID)
                    {
                        //Now we have to check that, IF we are on a deco gas already, make sure the switch depth of the new gas is less than the one we are currently on
                        if(currentGas.gasType == Gas.DIVE_GAS || (currentGas.gasType == Gas.DECO_GAS && decoGas.switchDepth < currentGas.switchDepth))
                        {
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
                
                // Debug: Before Boyles Law compensation
                System.out.println("BOYLES_LAW_COMPENSATION called for depth: " + Deco_Stop_Depth);
                System.out.println("First_Stop_Depth: " + First_Stop_Depth);
                // System.out.println("Gradients before Boyles Law:");
                for (int i = 0; i < 4; i++) {
                    System.out.printf("  Comp %d: N2=%.4f He=%.4f\n", i+1, 
                        Allowable_Gradient_N2[i], Allowable_Gradient_He[i]);
                }
                
                BOYLES_LAW_COMPENSATION(First_Stop_Depth, Deco_Stop_Depth, Settings.decoStopInterval);       //subroutine
                
                // Debug: After Boyles Law compensation - DISABLED (too verbose)
                if (false) { // Disabled to prevent hangs
                    // This was causing hangs with too much output
                }

                decoStopTime = DECOMPRESSION_STOP(Deco_Stop_Depth, Settings.decoStopInterval);       //subroutine
                Starting_Depth = Deco_Stop_Depth;
                Next_Stop = Deco_Stop_Depth - Settings.decoStopInterval;
                Deco_Stop_Depth = Next_Stop;
                //currentDive.currentRunTime += decoStopTime; 
                Last_Run_Time = currentDive.currentRunTime;
                //60
            }                                        //end of deco stop loop block

            //  C===============================================================================
            //  C   //  COMPUTE TOTAL PHASE VOLUME TIME AND MAKE CRITICAL VOLUME COMPARISON
            //  C     The deco phase volume time is computed from the run time.  The surface
            //  C     phase volume time is computed in a subroutine based on the surfacing gas
            //  C     loadings from previous deco loop block.  Next the total phase volume time
            //  C     (in-water + surface) for each compartment is compared against the previous
            //  C     total phase volume time.  The schedule is converged when the difference is
            //  C     less than or equal to 1 minute in any one of the 16 compartments.
            //  C
            //  C     Note:  the "phase volume time" is somewhat of a mathematical concept.
            //  C     It is the time divided out of a total integration of supersaturation
            //  C     gradient x time (in-water and surface).  This integration is multiplied
            //  C     by the excess bubble number to represent the amount of free-gas released
            //  C     as a result of allowing a certain number of excess bubbles to form.
            //  C===============================================================================
            Deco_Phase_Volume_Time = currentDive.currentRunTime - runtimeStartOfInitialDecoZone; // Run_Time_Start_of_Deco_Zone;
            
            // Debug: Phase volume time calculation
            System.out.println("\n=== PHASE VOLUME TIME CALCULATION ===");
            System.out.println("Run_Time: " + currentDive.currentRunTime);
            System.out.println("Run_Time_Start_of_Deco_Zone: " + runtimeStartOfInitialDecoZone);
            // System.out.println("Deco_Phase_Volume_Time: " + Deco_Phase_Volume_Time);

            CALC_SURFACE_PHASE_VOLUME_TIME();                            //subroutine
            
            // Debug: Surface phase volume times
            for (int i = 0; i < 4; i++) {
                // System.out.println("Comp " + (i+1) + " Surface_Phase_Volume_Time: " + Surface_Phase_Volume_Time[i]);
            }

            Schedule_Converged = true; // Assume converged unless we find otherwise
            for(int i=0; i<16; i++)
            {
                Phase_Volume_Time[i] = Deco_Phase_Volume_Time + Surface_Phase_Volume_Time[i];
                Critical_Volume_Comparison = Math.abs(Phase_Volume_Time[i] - Last_Phase_Volume_Time[i]);
                if(Critical_Volume_Comparison > 1.0)
                {
                    Schedule_Converged = false; // If ANY compartment hasn't converged, schedule not converged
                }
            }
            //  C===============================================================================
            //  C     CRITICAL VOLUME DECISION TREE BETWEEN LINES 70 AND 99
            //  C     There are two options here.  If the Critical Volume Agorithm setting is
            //  C     "on" and the schedule is converged, or the Critical Volume Algorithm
            //  C     setting was "off" in the first place, the program will re-assign variables
            //  C     to their values at the start of ascent (end of bottom time) and process
            //  C     a complete decompression schedule once again using all the same ascent
            //  C     parameters and first stop depth.  This decompression schedule will match
            //  C     the last iteration of the Critical Volume Loop and the program will write
            //  C     the final deco schedule to the output file.
            //  C
            //  C     Note: if the Critical Volume Agorithm setting was "off", the final deco
            //  C     schedule will be based on "Initial Allowable Supersaturation Gradients."
            //  C     If it was "on", the final schedule will be based on "Adjusted Allowable
            //  C     Supersaturation Gradients" (gradients that are "relaxed" as a result of
            //  C     the Critical Volume Algorithm).
            //  C
            //  C     If the Critical Volume Agorithm setting is "on" and the schedule is not
            //  C     converged, the program will re-assign variables to their values at the
            //  C     start of the deco zone and process another trial decompression schedule.
            //  C===============================================================================
            //  70
            double decoStopInterval = Settings.decoStopInterval;
            double averageAmbientPressureATA;
            DiveSegment ascentSegment;
            DiveSegment stopSegment;
            boolean firstStop = true;
            
            if((Schedule_Converged) || (!Settings.Critical_Volume_Algorithm))
            {
                for(int i=0; i<16; i++)
                {
                    heliumCompartmentPressure[i] = He_Pressure_Start_of_Ascent[i];
                    nitrogenCompartmentPressure[i] = N2_Pressure_Start_of_Ascent[i];
                }
                currentDive.currentRunTime = Run_Time_Start_of_Ascent;
                //Segment_Number = Segment_Number_Start_of_Ascent;
                Starting_Depth = ascent_starting_depth;
                //Mix_Number = Mix_Change[1];
                currentDive.activeGasID = gasIndexAtStartOfAscent;
                //Rate = Rate_Change[1];
                //Step_Size = Step_Size_Change[1];
                Deco_Stop_Depth = First_Stop_Depth;
                Last_Run_Time = 0.0;
//  C===============================================================================
//  C     DECO STOP LOOP BLOCK FOR FINAL DECOMPRESSION SCHEDULE
//  C===============================================================================
                while(true)                    //loop will run continuously until
                {	                                         //there is an exit statement

                    durationOfAscent = GAS_LOADINGS_ASCENT_DESCENT(Starting_Depth, Deco_Stop_Depth, Settings.ascentRate);
                    
//  C===============================================================================
//  C     DURING FINAL DECOMPRESSION SCHEDULE PROCESS, COMPUTE MAXIMUM ACTUAL
//  C     SUPERSATURATION GRADIENT RESULTING IN EACH COMPARTMENT
//  C     If there is a repetitive dive, this will be used later in the VPM
//  C     Repetitive Algorithm to adjust the values for critical radii.
//  C===============================================================================
                    CALC_MAX_ACTUAL_GRADIENT(Deco_Stop_Depth);        //subroutine

                    DIVEDATA_ASCENT_DESCENT(Starting_Depth, Deco_Stop_Depth, Settings.ascentRate, Settings.RMV_During_Deco);

                    // Skapa nu ett ascent-diveSegment och lägg till
                    ascentSegment = new DiveSegment();
                    ascentSegment.segmentType = DiveSegment.ASCENT;
                    ascentSegment.startDepth = Starting_Depth;
                    ascentSegment.startRunTime = currentDive.currentRunTime;
                    ascentSegment.duration = durationOfAscent; //(Deco_Stop_Depth - Starting_Depth) / Math.negateExact(Settings.ascentRate);
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
                    currentDive.currentRunTime += durationOfAscent; // currentDive.currentRunTime = ascentSegment.endRunTime;
                    currentDive.totalAscentDuration += ascentSegment.duration;
                    if(!firstStop)
                    {
                        currentDive.totalDecoDuration += ascentSegment.duration;
                    }

                    //See if we are done
                    if(Deco_Stop_Depth <= ending_depth) break;

                    
                    //Now deal with the constant-depth deco stop
                    //See if it's time to switch gases or decoStopInterval..
                    Gas decoGas;
                    //Loop through all gases and see if we need to switch to any of them
                    for(int gasID=0; gasID<currentDive.gases.size(); gasID++)
                    {
                        decoGas = currentDive.gases.get(gasID);
                        //Check that
                        //1) Current depth is less or equal to the switchdepth of the gas we're now checking
                        //2) The switchdepth of the gas we're now checking is less/shallower than the switchdepth of the gas we're currently using
                        //3) The gas we're checking is a deco gas
                        //4) The gas we're now checking is now the one we're already using
                        if(Deco_Stop_Depth <= decoGas.switchDepth && decoGas.switchDepth < currentDive.getCurrentGas().switchDepth && decoGas.gasType == Gas.DECO_GAS && currentDive.activeGasID != gasID)
                        {
                            //switch to this deco gas
                            currentDive.activeGasID = gasID;
                        }
                    }
                    //Now check if we need to change our decoStopInterval
                    if(Settings.metric && Deco_Stop_Depth == 9.0 && Settings.smallDecoStopIntervalShallow)
                    {
                        decoStopInterval = 1; //1 meter
                    }
                    else if(!Settings.metric && Deco_Stop_Depth == 30 && Settings.smallDecoStopIntervalShallow)
                    {
                        decoStopInterval = 1; //1 foot
                    }

//                    Gradient_Factor_Current_Stop = Currently_Max_Allowed_Gradient_Factor;

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
                    
                    //WRITE (8,860) Segment_Number, Segment_Time, Run_Time, Mix_Number, Deco_Stop_Depth, Rate
/*                    try
                    {
                        outFileWriter.write(Misc.formatInteger(Segment_Number,3,true)+"   "+Misc.formatDouble(Segment_Time,5,1,true)+" "+Misc.formatDouble(Run_Time,6,1,true)+" |   "+Misc.formatInteger(Mix_Number,2,true)+"   |  "+Misc.formatInteger((int)Math.round(Deco_Stop_Depth),4,true)+"   "+Misc.formatDouble(Rate,6,1,true)+"          |"); outFileWriter.newLine();
                    }
                    catch(IOException e)
                    {
                        System.err.println(e.getMessage());
                    }*/
/*                    if(Number_of_Changes > 1)
                    {
                        for(int compartmentIndex=2; compartmentIndex<=Number_of_Changes; compartmentIndex++)
                        {
                            if(Depth_Change[compartmentIndex] >= Deco_Stop_Depth)
                            {
                                Mix_Number = Mix_Change[compartmentIndex];
                                Rate = Rate_Change[compartmentIndex];
                                Step_Size = Step_Size_Change[compartmentIndex];
                            }
                        }
                    }
*/
                    // Debug: Before Boyles Law compensation (final schedule)
                    System.out.println("BOYLES_LAW_COMPENSATION called for depth: " + Deco_Stop_Depth + " (final schedule)");
                    System.out.println("First_Stop_Depth: " + First_Stop_Depth);
                    
                    BOYLES_LAW_COMPENSATION(First_Stop_Depth, Deco_Stop_Depth, decoStopInterval);       //subroutine
                    
                    // Debug: After Boyles Law compensation (second call)
                    System.out.println("\n=== AFTER BOYLES LAW (2nd call) ===");
                    System.out.println("Stop Depth: " + Deco_Stop_Depth + "m");
                    for (int i = 0; i < 4; i++) {
                        System.out.printf("Comp %d Deco Gradients: N2=%.4f He=%.4f\n",
                            i+1, Deco_Gradient_N2[i], Deco_Gradient_He[i]);
                    }

                    double stopDuration = DECOMPRESSION_STOP(Deco_Stop_Depth, decoStopInterval);   //subroutine
              

                    //Now create the visual deco segment
                    decoSegment = new DecoTableSegment(); //clears the decoSegment object
                    decoSegment = DIVEDATA_CONSTANT_DEPTH(Deco_Stop_Depth, Settings.RMV_During_Deco, stopDuration);

                    //updateHighestMvaluePercentageAndGradientFactor(Deco_Stop_Depth + currentDive.surfacePressure); //jag gör detta EFTER att gradient-factor sätts på decoSegment, eftersom vi vill se gradientFactor compartmentIndex början av segmentet (när man anländer till ett stopp)
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
                        //BUT, ONLY IF it's NOT already an whole number
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
                    
                    // Debug: Show each stop as it's added to the final schedule - KEEP THIS ONE
                    System.out.println("ADDING STOP TO FINAL SCHEDULE: " + Deco_Stop_Depth + "m for " + 
                        String.format("%.2f", stopDuration) + " min");
                    
                    // Only write to file if this is the final converged schedule
                    if (Schedule_Converged) {
                        // Also append to file
                        try {
                            java.io.FileWriter fw = new java.io.FileWriter("decoplanner_stops_log.txt", true);
                            fw.write("STOP: " + Deco_Stop_Depth + "m for " + String.format("%.2f", stopDuration) + " min\n");
                            fw.close();
                        } catch (Exception e) {
                            // Ignore file write errors
                        }
                    }

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

                    firstStop = false; //we have now processed at least one ascent and one deco stop, so the next round is definitely not the first stop.
                    
//  C===============================================================================
//  C     This next bit justs rounds up the stop time at the first stop to be in
//  C     whole increments of the minimum stop time (to make for a nice deco table).
//  C===============================================================================
/*                    if(Last_Run_Time == 0.0)
                    {
                        Stop_Time = Math.rint((Segment_Time/Minimum_Deco_Stop_Time) + 0.5) * Minimum_Deco_Stop_Time;
                    }
                    else
                    {
                        Stop_Time = Run_Time - Last_Run_Time;
                    }  */
//  C===============================================================================
//  C     DURING FINAL DECOMPRESSION SCHEDULE, IF MINIMUM STOP TIME PARAMETER IS A
//  C     WHOLE NUMBER (compartmentIndex.e. 1 minute) THEN WRITE DECO SCHEDULE USING int
//  C     NUMBERS (looks nicer).  OTHERWISE, USE DECIMAL NUMBERS.
//  C     Note: per the request of a noted exploration diver(!), program now allows
//  C     a minimum stop time of less than one minute so that total ascent time can
//  C     be minimized on very long dives.  In fact, with step size set at 1 fsw or
//  C     0.2 msw and minimum stop time set at 0.1 minute (6 seconds), a near
//  C     continuous decompression schedule can be computed.
//  C===============================================================================
                    /*if(Math.floor(Minimum_Deco_Stop_Time) == Minimum_Deco_Stop_Time)
                    {
                        //WRITE (8,862) Segment_Number, Segment_Time, Run_Time, Mix_Number, INT(Deco_Stop_Depth), INT(Stop_Time), INT(Run_Time)
                        //I3,3X,F5.1,1X,F6.1,1X,'|',3X,I2,3X,'|',25X,'|',2X,I4,3X,I4,2X,I5
                        try
                        {
                            outFileWriter.write(Misc.formatInteger(Segment_Number,3,true)+"   "+Misc.formatDouble(Segment_Time,5,1,true)+" "+Misc.formatDouble(Run_Time,6,1,true)+" |   "+Misc.formatInteger(Mix_Number,2,true)+"   |                         |  "+Misc.formatInteger((int)Deco_Stop_Depth,4,true)+"   "+Misc.formatInteger((int)Stop_Time,4,true)+"  "+Misc.formatInteger((int)Run_Time,5,true)); outFileWriter.newLine();
                        }
                        catch(IOException e)
                        {
                            System.err.println(e.getMessage());
                        }
                    }
                    else
                    {
                        //WRITE (8,863) Segment_Number, Segment_Time, Run_Time, Mix_Number, Deco_Stop_Depth, Stop_Time, Run_Time
                        //I3,3X,F5.1,1X,F6.1,1X,'|',3X,I2,3X,'|',25X,'|',2X,F5.0,1X,F6.1,1X,F7.1
                        try
                        {
                            outFileWriter.write(Misc.formatInteger(Segment_Number,3,true)+"   "+Misc.formatDouble(Segment_Time,5,1,true)+" "+Misc.formatDouble(Run_Time,6,1,true)+" |   "+Misc.formatInteger(Mix_Number,2,true)+"   |                         |  "+Misc.formatInteger((int)Math.round(Deco_Stop_Depth),5,true)+" "+Misc.formatDouble(Stop_Time,6,1,true)+" "+Misc.formatDouble(Run_Time,7,1,true)); outFileWriter.newLine();
                        }
                        catch(IOException e)
                        {
                            System.err.println(e.getMessage());
                        }
                    }*/
                    Starting_Depth = (int)Math.round(Deco_Stop_Depth);
                    Next_Stop = Deco_Stop_Depth - decoStopInterval;
                    Deco_Stop_Depth = Next_Stop;
                    Last_Run_Time = currentDive.currentRunTime;
                    
                    // Check if we're done with all stops
                    if(Deco_Stop_Depth <= 0.0) {
                        System.out.println("DEBUG: All stops complete, breaking from loop");
                        break;
                    }
                    //80                                        //end of deco stop loop block
                }                                           //for final deco schedule

                System.out.println("=== CVA ITERATION " + cvaIteration + " END (CONVERGED) ===");
                System.out.println("DEBUG: Breaking from CVA loop");
                System.out.println("DEBUG: About to break from while(true) loop at line " + Thread.currentThread().getStackTrace()[1].getLineNumber());
                break;                            //exit critical volume loop at Line 100
            }                                       //final deco schedule written
            else
            {
//  C===============================================================================
//  C     IF SCHEDULE NOT CONVERGED, COMPUTE RELAXED ALLOWABLE SUPERSATURATION
//  C     GRADIENTS WITH VPM CRITICAL VOLUME ALGORITHM AND PROCESS ANOTHER
//  C     ITERATION OF THE CRITICAL VOLUME LOOP
//  C===============================================================================
                CRITICAL_VOLUME(Deco_Phase_Volume_Time);              //subroutine
                
                // Debug: Show allowable gradients after CVA iteration
                // System.out.println("=== ALLOWABLE GRADIENTS AFTER CVA ITERATION " + cvaIteration + " ===");
                for (int i = 0; i < 4; i++) {
                    System.out.printf("Comp %d: N2=%.4f He=%.4f\n",
                        i+1, Allowable_Gradient_N2[i], Allowable_Gradient_He[i]);
                }
                
                Deco_Phase_Volume_Time = 0.0;
                
                // Debug: Resetting runtime for next CVA iteration
                System.out.println("Resetting runtime from " + currentDive.currentRunTime + " to " + Run_Time_Start_of_Deco_Zone);
                
                currentDive.currentRunTime = Run_Time_Start_of_Deco_Zone;
                Starting_Depth = Depth_Start_of_Deco_Zone;
                currentDive.activeGasID = gasIndexAtStartOfAscent;
                //Mix_Number = Mix_Change[1];
                //Rate = Rate_Change[1];
                //Step_Size = Step_Size_Change[1];
                for(int i=0; i<16; i++)
                {
                    Last_Phase_Volume_Time[i] = Phase_Volume_Time[i];
                    heliumCompartmentPressure[i] = He_Pressure_Start_of_Deco_Zone[i];
                    nitrogenCompartmentPressure[i] = N2_Pressure_Start_of_Deco_Zone[i];
                }

                // System.out.println("=== CVA ITERATION " + cvaIteration + " END ===");
                
                //CYCLE                         //Return to start of critical volume loop
                continue;                     //(Line 50) to process another iteration

                //99
            }                               //end of critical volume decision tree

                //100   CONTINUE                                      //end of critical volume loop
        }
        
        System.out.println("DEBUG: Exited CVA while loop successfully!");
        System.out.println("DEBUG: ending_depth = " + ending_depth);
        
        if(ending_depth == 0)
        {
            // Debug: Final TTS calculation
            System.out.println("\n=== FINAL TTS CALCULATION ===");
            System.out.println("Bottom Time: " + 25);
            System.out.println("Total Runtime: " + currentDive.currentRunTime);
            System.out.println("Deco Time: " + (currentDive.currentRunTime - 25));
            System.out.println("Final TTS: " + (currentDive.currentRunTime - 25) + " minutes");
            
            // Debug: Show final gradients
            debugFinalGradients();
            
            // Debug: Show final schedule
            debugVPM("SCHEDULE");
            
            // REMOVED: Surface segment that was causing "empty String" error
            // The surface segment (depth=0, duration=0) was creating an empty 9th segment
            // that caused display issues in the UI table
            /*
            //Now create the visual segment for the surface
            decoSegment = new DecoTableSegment(); //clears the decoSegment object
            decoSegment = DIVEDATA_CONSTANT_DEPTH(0, Settings.RMV_During_Deco, 0);
            //decoSegment.setGradientFactor(Util.roundToOneDecimal((currentDive.highestCurrentGradientFactor*100)+0.5)); //we need to round up, since we will never arrive at the exact "GF Hi", since we force the deco times to be multipliers of a "minimum stop time"
            decoSegment.setEndRunTime(0);
            decoSegment.setStartRunTime(Math.round((currentDive.currentRunTime/currentMinimumDecoStopDuration) + 0.5) * currentMinimumDecoStopDuration);
            decoSegment.setUserDefined(true);
            currentDive.resultingDecoPlan.add(decoSegment);
            currentDive.currentRunTime = Double.parseDouble(decoSegment.getStartRunTime());
            */
            
            // Debug: Final schedule breakdown (after all segments are added)
            System.out.println("\n=== COMPLETE FINAL DECOMPRESSION SCHEDULE ===");
            System.out.println("Total segments in schedule: " + currentDive.resultingDecoPlan.size());
            
            double totalDecoTime = 0;
            int actualStopCount = 0;
            
            if (currentDive.resultingDecoPlan.size() > 0) {
                System.out.println("\nDetailed Stop Schedule:");
                System.out.println("Stop # | Depth(m) | Duration(min) | Start Time | End Time | Type");
                System.out.println("-------|----------|---------------|------------|----------|--------");
                for (int i = 0; i < currentDive.resultingDecoPlan.size(); i++) {
                    try {
                        DecoTableSegment segment = currentDive.resultingDecoPlan.get(i);
                        String depth = segment.getDepth();
                        String duration = segment.getDuration();
                        String startTime = segment.getStartRunTime();
                        String endTime = segment.getEndRunTime();
                        
                        // Check if this is an actual stop (not just ascent)
                        double durVal = Double.parseDouble(duration);
                        if (durVal > 0.5) {  // If duration > 0.5 min, it's likely a real stop
                            actualStopCount++;
                            totalDecoTime += durVal;
                            System.out.printf("  %3d  | %8s | %12s | %10s | %8s | STOP\n",
                                actualStopCount, depth, duration, startTime, endTime);
                        } else {
                            System.out.printf("       | %8s | %12s | %10s | %8s | ASCENT\n",
                                depth, duration, startTime, endTime);
                        }
                    } catch (Exception e) {
                        System.out.println("Error accessing segment " + i + ": " + e.getMessage());
                        e.printStackTrace();
                        // Skip this segment and continue
                        continue;
                    }
                }
                
                System.out.println("\n=== SCHEDULE SUMMARY ===");
                System.out.println("Total actual deco stops: " + actualStopCount);
                System.out.println("Total deco time (excluding ascents): " + String.format("%.2f", totalDecoTime) + " min");
                System.out.println("Total runtime: " + String.format("%.2f", currentDive.currentRunTime) + " min");
                
                // List which depths have stops
                System.out.println("\nStops at depths: ");
                for (int i = 0; i < currentDive.resultingDecoPlan.size(); i++) {
                    DecoTableSegment segment = currentDive.resultingDecoPlan.get(i);
                    double durVal = Double.parseDouble(segment.getDuration());
                    if (durVal > 0.5) {
                        System.out.println("  - " + segment.getDepth() + "m: " + segment.getDuration() + " min");
                    }
                }
                
                // Also write schedule to file for analysis
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("decoplanner_schedule_output.txt");
                    fw.write("=== FINAL DECOMPRESSION SCHEDULE ===\n");
                    fw.write("Dive Profile: 51m/25min (21/35 trimix, EAN50 deco)\n");
                    fw.write("Total deco time (excluding ascents): " + String.format("%.2f", totalDecoTime) + " min\n");
                    fw.write("Total runtime: " + String.format("%.2f", currentDive.currentRunTime) + " min\n");
                    fw.write("\nDecompression Stops:\n");
                    for (int i = 0; i < currentDive.resultingDecoPlan.size(); i++) {
                        DecoTableSegment segment = currentDive.resultingDecoPlan.get(i);
                        double durVal = Double.parseDouble(segment.getDuration());
                        if (durVal > 0.5) {
                            fw.write("  " + segment.getDepth() + "m: " + segment.getDuration() + " min\n");
                        }
                    }
                    fw.close();
                    System.out.println("\nSchedule written to: decoplanner_schedule_output.txt");
                } catch (Exception e) {
                    System.out.println("Could not write schedule to file: " + e.getMessage());
                }
            } else {
                System.out.println("No segments in resultingDecoPlan!");
            }
        }
//  C===============================================================================
//  C     PROCESSING OF DIVE COMPLETE.  READ INPUT FILE TO DETERMINE IF THERE IS A
//  C     REPETITIVE DIVE.  IF NONE, THEN EXIT REPETITIVE LOOP.
//  C===============================================================================
        //READ (7,*) Repetitive_Dive_Flag
/*        try
        {
                line = inFileReader.readLine().trim();
        }
        catch(IOException e)
        {
                System.err.println(e.getMessage());
        }
        Repetitive_Dive_Flag = Integer.parseInt(line);
        if(Repetitive_Dive_Flag == 0)
        {
                break;                                        //exit repetitive dive loop
        }                                                //at Line 330
//  C===============================================================================
//  C     IF THERE IS A REPETITIVE DIVE, COMPUTE GAS LOADINGS (OFF-GASSING) DURING
//  C     SURFACE INTERVAL TIME.  ADJUST CRITICAL RADII USING VPM REPETITIVE
//  C     ALGORITHM.  RE-INITIALIZE SELECTED VARIABLES AND RETURN TO START OF
//  C     REPETITIVE LOOP AT LINE 30.
//  C===============================================================================
        else if(Repetitive_Dive_Flag == 1)
        {
                //READ (7,*) Surface_Interval_Time
                try
                {
                        line = inFileReader.readLine().trim();
                }
                catch(IOException e)
                {
                        System.err.println(e.getMessage());
                }
                Surface_Interval_Time = Double.parseDouble(line);

                GAS_LOADINGS_SURFACE_INTERVAL(Surface_Interval_Time);        //subroutine

                VPM_REPETITIVE_ALGORITHM(Surface_Interval_Time);      //subroutine

                for(int compartmentIndex=0; compartmentIndex<16; compartmentIndex++)
                {
                        Max_Crushing_Pressure_He[compartmentIndex] = 0.0;
                        Max_Crushing_Pressure_N2[compartmentIndex] = 0.0;
                        Max_Actual_Gradient[compartmentIndex] = 0.0;
                }
                Run_Time = 0.0;
                Segment_Number = 0;
                try
                {
                        //WRITE (8,880)
                        outFileWriter.newLine();
                        //WRITE (8,890)
                        outFileWriter.write("REPETITIVE DIVE:"); outFileWriter.newLine();
                        //WRITE (8,813)
                        outFileWriter.newLine();
                }
                catch(IOException e)
                {
                        System.err.println(e.getMessage());
                }

                //CYCLE      //Return to start of repetitive loop to process another dive
                continue;
        }
//  C===============================================================================
//  C     WRITE ERROR MESSAGE AND TERMINATE PROGRAM IF THERE IS AN ERROR IN THE
//  C     INPUT FILE FOR THE REPETITIVE DIVE FLAG
//  C===============================================================================
        else
        {
                //CALL SYSTEMQQ (OS_Command)
                //WRITE (*,908)
                //WRITE (*,900)
                //STOP 'PROGRAM TERMINATED'
                System.out.println("ERROR IN INPUT FILE (REPETITIVE DIVE CODE)");
                System.out.println("PROGRAM TERMINATED");
        }
        //330  CONTINUE                                           //End of repetitive loop
        */
		
    }
	
    private static double PROJECTED_ASCENT(double Starting_Depth, double Rate, double Deco_Stop_Depth, double Step_Size)
    {
        //Just make sure that Rate has the correct sign. negative on ascent and positive on descent.
        if((Deco_Stop_Depth < Starting_Depth && Rate > 0) || (Deco_Stop_Depth > Starting_Depth && Rate < 0))
        {
            Rate = -Rate;
        }
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
        double Weighted_Allowable_Gradient;
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
        Initial_Inspired_He_Pressure = (Starting_Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentHeliumFraction();
        Initial_Inspired_N2_Pressure = (Starting_Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentNitrogenFraction();
        Helium_Rate = Rate * currentDive.getCurrentHeliumFraction();
        Nitrogen_Rate = Rate * currentDive.getCurrentNitrogenFraction();
        for(int i=0;i<16;i++)
        {
            Initial_Helium_Pressure[i] = heliumCompartmentPressure[i]; // heliumCompartmentPressure[compartmentIndex];
            Initial_Nitrogen_Pressure[i] = nitrogenCompartmentPressure[i]; // nitrogenCompartmentPressure[compartmentIndex];
        }
        boolean loop;
        do{
            loop = false;
            Ending_Ambient_Pressure = New_Ambient_Pressure; // 665
            Segment_Time = (Ending_Ambient_Pressure - Starting_Ambient_Pressure)/Rate;
            for(int i=0;i<16;i++) //DO 670 I = 1,16
            {
                Temp_Helium_Pressure = SCHREINER_EQUATION(Initial_Inspired_He_Pressure, Helium_Rate, Segment_Time, kHe[i], Initial_Helium_Pressure[i]);
                Temp_Nitrogen_Pressure = SCHREINER_EQUATION(Initial_Inspired_N2_Pressure, Nitrogen_Rate, Segment_Time, kN2[i], Initial_Nitrogen_Pressure[i]);
                Temp_Gas_Loading[i] = Temp_Helium_Pressure + Temp_Nitrogen_Pressure;
                if(Temp_Gas_Loading[i] > 0.0)
                {
                    Weighted_Allowable_Gradient = (Allowable_Gradient_He[i]* Temp_Helium_Pressure + Allowable_Gradient_N2[i]* Temp_Nitrogen_Pressure) / Temp_Gas_Loading[i];
                }
                else
                {
                    Weighted_Allowable_Gradient = Math.min(Allowable_Gradient_He[i], Allowable_Gradient_N2[i]);
                }
                Allowable_Gas_Loading[i] = Ending_Ambient_Pressure + Weighted_Allowable_Gradient - Constant_Pressure_Other_Gases;
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
    public static double GAS_LOADINGS_ASCENT_DESCENT(double Starting_Depth, double Ending_Depth, double Rate)
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
        Initial_Inspired_He_Pressure = (Starting_Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentHeliumFraction();
        Initial_Inspired_N2_Pressure = (Starting_Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentNitrogenFraction();
        Helium_Rate = Rate * currentDive.getCurrentHeliumFraction();
        Nitrogen_Rate = Rate * currentDive.getCurrentNitrogenFraction();

        for(int i=0; i<16; i++)
        {
            Initial_Helium_Pressure = heliumCompartmentPressure[i];
            Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[i];
            heliumCompartmentPressure[i] = SCHREINER_EQUATION(Initial_Inspired_He_Pressure, Helium_Rate, Segment_Time, kHe[i], Initial_Helium_Pressure);
            nitrogenCompartmentPressure[i] = SCHREINER_EQUATION(Initial_Inspired_N2_Pressure, Nitrogen_Rate, Segment_Time, kN2[i], Initial_Nitrogen_Pressure);        
        }
        return Segment_Time;
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
        Inspired_Helium_Pressure = (Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentHeliumFraction();
        Inspired_Nitrogen_Pressure = (Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentNitrogenFraction();
        for(int i=0; i<16; i++)
        {
            Initial_Helium_Pressure = heliumCompartmentPressure[i];
            Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[i];
            heliumCompartmentPressure[i] = haldaneEquation(Initial_Helium_Pressure, Inspired_Helium_Pressure, kHe[i], Segment_Time);
            nitrogenCompartmentPressure[i] = haldaneEquation(Initial_Nitrogen_Pressure, Inspired_Nitrogen_Pressure, kN2[i], Segment_Time);        
        }

/*        
System.out.println("VPM OC. Depth = " + Depth + " Segment time = " + Segment_Time);
System.out.println("Inspired_Helium_Pressure = " + Inspired_Helium_Pressure + " Inspired_Nitrogen_Pressure = " + Inspired_Nitrogen_Pressure);
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
    }




    private static void offGas1(int pTime)
    {
        //pTime is in minutes
        double vInspN2; //  'inspired PP
        double vInspHe; //  'inspired PP
        double vOldp; //    'old tissue PP
        double vModKN2;
        double vModKHe;

        vInspN2 = (currentDive.surfacePressure - Water_Vapor_Pressure)*0.79; // Get inspired N2
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

        vInspN2 = (currentDive.surfacePressure - Water_Vapor_Pressure)*0.79; // Get inspired N2
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

        vInspN2 = (currentDive.surfacePressure - Water_Vapor_Pressure)*0.79; // Get inspired N2
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

        vInspN2 = (currentDive.surfacePressure - Water_Vapor_Pressure)*0.79; // Get inspired N2
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
        
        GAS_LOADINGS_SURFACE_INTERVAL(minutes);        //subroutine

        VPM_REPETITIVE_ALGORITHM(minutes);      //subroutine

        for(int compartmentIndex=0; compartmentIndex<16; compartmentIndex++)
        {
                Max_Crushing_Pressure_He[compartmentIndex] = 0.0;
                Max_Crushing_Pressure_N2[compartmentIndex] = 0.0;
                Max_Actual_Gradient[compartmentIndex] = 0.0;
        }
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
        //Just make sure that Rate has the correct sign. negative on ascent and positive on descent.
        if((Ending_Depth < Starting_Depth && Rate > 0) || (Ending_Depth > Starting_Depth && Rate < 0))
        {
            Rate = -Rate;
        }
        
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
                SUMCNS = 2.0; 
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
        MAXPO2 = PO2;
        MAXD = Depth;
        ENDN2 = (currentDive.getCurrentNitrogenFraction()*(Depth + surfacePressure)/0.79) - surfacePressure;
        ENDNO2 = currentDive.getCurrentNitrogenFraction()*(Depth + surfacePressure)+currentDive.getCurrentOxygenFraction()*(Depth+surfacePressure)-surfacePressure;
        if(PO2 > 0.5)
        {
            if(PO2 > 1.82)
            {
                SUMCNS = 2.0;
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
        //WRITE (13,100) Segment_Number, Segment_Time, Run_Time, Mix_Number, Respiratory_Minute_Volume, Segment_Volume, MAXD, MAXPO2, SUMCNS, OTU, ENDN2, ENDNO2
        DecoTableSegment decoSegment = new DecoTableSegment();
        decoSegment.setDepth(Depth);
        decoSegment.setDuration(Util.roundToOneDecimal(Segment_Time));
        decoSegment.setEndRunTime(currentDive.currentRunTime);
        decoSegment.setStartRunTime(currentDive.currentRunTime - Segment_Time);
        decoSegment.setGasVolumeUsedDuringSegment(Segment_Volume);
        decoSegment.setPO2(Util.roundToTwoDecimals(MAXPO2));
        decoSegment.setHeliumPercentage(Math.rint(currentDive.getCurrentHeliumFraction() * 100.0));
        decoSegment.setOxygenPercentage(currentDive.getCurrentOxygenFraction() * 100.0);
        decoSegment.setCNSatEndOfSegment(CNStoxicityPercentage);
        decoSegment.setOTUatEndOfSegment(OTUbuildup);
        decoSegment.setMvaluePercent(Util.roundToOneDecimal(currentDive.highestCurrentMvalueFraction*100));
        decoSegment.setGradientFactor(Util.roundToOneDecimal(currentDive.highestCurrentGradientFactor*100));

        return decoSegment;
   }
    
    //===============================================================================
    //     SUBROUTINE BOYLES_LAW_COMPENSATION
    //     Purpose: This subprogram calculates the reduction in allowable gradients
    //     with decreasing ambient pressure during the decompression profile based
    //     on Boyle's Law considerations.
    //===============================================================================
    private static void BOYLES_LAW_COMPENSATION(double First_Stop_Depth, double Deco_Stop_Depth, double Step_Size)
    {
        First_Stop_Depth = firstStopDepthOfTotalAscent;
        
        //IMPLICIT NONE
        //===============================================================================
        //     LOCAL VARIABLES
        //===============================================================================
        double Next_Stop;
        double Ambient_Pressure_First_Stop, Ambient_Pressure_Next_Stop;
        double Amb_Press_First_Stop_Pascals, Amb_Press_Next_Stop_Pascals;
        double A, B, C, Low_Bound, High_Bound, Ending_Radius;
        double Deco_Gradient_Pascals;
        double Allow_Grad_First_Stop_He_Pa, Radius_First_Stop_He;
        double Allow_Grad_First_Stop_N2_Pa, Radius_First_Stop_N2;

        //===============================================================================
        //     LOCAL ARRAYS
        //===============================================================================
        double[] Radius1_He = new double[16];
        double[] Radius2_He = new double[16];
        double[] Radius1_N2 = new double[16];
        double[] Radius2_N2 = new double[16];

        /*===============================================================================
        //     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Surface_Tension_Gamma, Skin_Compression_GammaC
        COMMON /Block_19/ Surface_Tension_Gamma, Skin_Compression_GammaC

        //===============================================================================
        //     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        //===============================================================================
        double Barometric_Pressure
        COMMON /Block_18/ Barometric_Pressure

        double Units_Factor
        COMMON /Block_16/ Units_Factor
        //===============================================================================
        //     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Allowable_Gradient_He(16), Allowable_Gradient_N2(16)
        COMMON /Block_26/ Allowable_Gradient_He, Allowable_Gradient_N2

        double Deco_Gradient_He(16), Deco_Gradient_N2(16)
        COMMON /Block_34/ Deco_Gradient_He, Deco_Gradient_N2
        //===============================================================================
        //     CALCULATIONS
        //===============================================================================*/
        Next_Stop = Deco_Stop_Depth - Step_Size;

        Ambient_Pressure_First_Stop = First_Stop_Depth +  Settings.getSurfacePressure();

        Ambient_Pressure_Next_Stop = Next_Stop + Settings.getSurfacePressure();

        Amb_Press_First_Stop_Pascals = (Ambient_Pressure_First_Stop/Settings.Depth_Per_ATM) * 101325.0;

        Amb_Press_Next_Stop_Pascals = (Ambient_Pressure_Next_Stop/Settings.Depth_Per_ATM) * 101325.0;

        for(int i=0; i<16; i++)
        {
            Allow_Grad_First_Stop_He_Pa = (Allowable_Gradient_He[i]/Settings.Depth_Per_ATM) * 101325.0;

            Radius_First_Stop_He = (2.0 * Settings.Surface_Tension_Gamma ) / Allow_Grad_First_Stop_He_Pa;

            Radius1_He[i] = Radius_First_Stop_He;
            A = Amb_Press_Next_Stop_Pascals;
            B = -2.0 * Settings.Surface_Tension_Gamma;
            C = (Amb_Press_First_Stop_Pascals + (2.0*Settings.Surface_Tension_Gamma)/ Radius_First_Stop_He)* Radius_First_Stop_He* (Radius_First_Stop_He*(Radius_First_Stop_He));
            Low_Bound = Radius_First_Stop_He;
            High_Bound = Radius_First_Stop_He * Math.pow((Amb_Press_First_Stop_Pascals/ Amb_Press_Next_Stop_Pascals),(1.0/3.0));

            Ending_Radius = RADIUS_ROOT_FINDER(A,B,C, Low_Bound, High_Bound);

            Radius2_He[i] = Ending_Radius;
            Deco_Gradient_Pascals = (2.0 * Settings.Surface_Tension_Gamma) / Ending_Radius;

            Deco_Gradient_He[i] = (Deco_Gradient_Pascals / 101325.0)*Settings.Depth_Per_ATM;
        }

        for(int i=0; i<16; i++)
        {
            Allow_Grad_First_Stop_N2_Pa = (Allowable_Gradient_N2[i]/Settings.Depth_Per_ATM) * 101325.0;

            Radius_First_Stop_N2 = (2.0 * Settings.Surface_Tension_Gamma) / Allow_Grad_First_Stop_N2_Pa;

            Radius1_N2[i] = Radius_First_Stop_N2;
            A = Amb_Press_Next_Stop_Pascals;
            B = -2.0 * Settings.Surface_Tension_Gamma;
            C = (Amb_Press_First_Stop_Pascals + (2.0*Settings.Surface_Tension_Gamma)/ Radius_First_Stop_N2)* Radius_First_Stop_N2*(Radius_First_Stop_N2*(Radius_First_Stop_N2));
            Low_Bound = Radius_First_Stop_N2;
            High_Bound = Radius_First_Stop_N2 * Math.pow((Amb_Press_First_Stop_Pascals/Amb_Press_Next_Stop_Pascals),(1.0/3.0));

            Ending_Radius = RADIUS_ROOT_FINDER(A,B,C, Low_Bound, High_Bound);

            Radius2_N2[i] = Ending_Radius;
            Deco_Gradient_Pascals = (2.0 * Settings.Surface_Tension_Gamma) / Ending_Radius;

            Deco_Gradient_N2[i] = (Deco_Gradient_Pascals / 101325.0)* Settings.Depth_Per_ATM;
        }
        //===============================================================================
        //     END OF SUBROUTINE
        //===============================================================================
        //RETURN
        //END
    }
    
	
    /*===============================================================================
     SUBROUTINE DECOMPRESSION_STOP
     Purpose: This subprogram calculates the required time at each
     decompression stop.
    ===============================================================================*/
    private static double DECOMPRESSION_STOP(double Deco_Stop_Depth, double Step_Size)
    {        
        //IMPLICIT NONE
        //===============================================================================
        //     LOCAL VARIABLES
        //===============================================================================
        //String OS_Command;

        int Last_Segment_Number;

        double Ambient_Pressure;
        double Inspired_Helium_Pressure, Inspired_Nitrogen_Pressure;
        double Last_Run_Time;
        double Deco_Ceiling_Depth = 0, Next_Stop;
        double Round_Up_Operation, Temp_Segment_Time, Time_Counter;
        double Weighted_Allowable_Gradient;

        //double HALDANE_EQUATION                                 //function subprogram
        //===============================================================================
        //     LOCAL ARRAYS
        //===============================================================================
        double[] Initial_Helium_Pressure = new double[16];
        double[] Initial_Nitrogen_Pressure = new double[16];
        /*===============================================================================
        //     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Water_Vapor_Pressure
        COMMON /Block_8/ Water_Vapor_Pressure

        double Constant_Pressure_Other_Gases
        COMMON /Block_17/ Constant_Pressure_Other_Gases

        double Minimum_Deco_Stop_Time
        COMMON /Block_21/ Minimum_Deco_Stop_Time
        //===============================================================================
        //     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        //===============================================================================
        int Segment_Number
        double Run_Time, Segment_Time
        COMMON /Block_2/ Run_Time, Segment_Number, Segment_Time

        double Ending_Ambient_Pressure
        COMMON /Block_4/ Ending_Ambient_Pressure

        int Mix_Number
        COMMON /Block_9/ Mix_Number

        double Barometric_Pressure
        COMMON /Block_18/ Barometric_Pressure
        //===============================================================================
        //     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Helium_Time_Constant(16)
        COMMON /Block_1A/ Helium_Time_Constant

        double Nitrogen_Time_Constant(16)
        COMMON /Block_1B/ Nitrogen_Time_Constant

        double Helium_Pressure(16), Nitrogen_Pressure(16)                //both input
        COMMON /Block_3/ Helium_Pressure, Nitrogen_Pressure            //and output

        double Fraction_Helium(10), Fraction_Nitrogen(10)
        COMMON /Block_5/ Fraction_Helium, Fraction_Nitrogen

        double Deco_Gradient_He(16), Deco_Gradient_N2(16)
        COMMON /Block_34/ Deco_Gradient_He, Deco_Gradient_N2
        //===============================================================================
        //     CALCULATIONS
        //===============================================================================*/
        //OS_Command = "CLS";
        
        // Debug: Start of decompression stop
        debugDecoStop("START", Deco_Stop_Depth, 0, 0);
        
        Last_Run_Time = currentDive.currentRunTime;
        Round_Up_Operation = Math.rint((Last_Run_Time/currentMinimumDecoStopDuration) + 0.501) * currentMinimumDecoStopDuration;
        double Segment_Time = Round_Up_Operation - currentDive.currentRunTime;
        currentDive.currentRunTime = Round_Up_Operation;
        Temp_Segment_Time = Segment_Time;
        //Last_Segment_Number = Segment_Number;
        //Segment_Number = Last_Segment_Number + 1;
        Ambient_Pressure = Deco_Stop_Depth + Settings.getSurfacePressure();
        double Ending_Ambient_Pressure = Ambient_Pressure;
        Next_Stop = Deco_Stop_Depth - Step_Size;

        Inspired_Helium_Pressure = (Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentHeliumFraction();

        Inspired_Nitrogen_Pressure = (Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentNitrogenFraction();
        //===============================================================================
        //     Check to make sure that program won't lock up if unable to decompress
        //     to the next stop.  If so, write error message and terminate program.
        //===============================================================================
             
        for(int i=0; i<16; i++)
        {
            if((Inspired_Helium_Pressure + Inspired_Nitrogen_Pressure) > 0.0)
            {
                Weighted_Allowable_Gradient = (Deco_Gradient_He[i]* Inspired_Helium_Pressure + Deco_Gradient_N2[i]* Inspired_Nitrogen_Pressure) / (Inspired_Helium_Pressure + Inspired_Nitrogen_Pressure);

                if((Inspired_Helium_Pressure + Inspired_Nitrogen_Pressure + Constant_Pressure_Other_Gases - Weighted_Allowable_Gradient) > (Next_Stop + Settings.getSurfacePressure()))
                {
                    System.out.println("ERROR! OFF-GASSING GRADIENT IS TOO SMALL TO DECOMPRESS AT THE "+Deco_Stop_Depth+" STOP");
                    System.out.println("REDUCE STEP SIZE OR INCREASE OXYGEN FRACTION");
                    System.out.println("PROGRAM TERMINATED");
                    System.exit(1);
                }
            }
        }

        //700   DO 720 I = 1,16
        boolean goBack = false;   //gör en do-while loop för att ersätta GOTO.
        do
        {
            for(int i=0; i<16; i++)
            {
                Initial_Helium_Pressure[i] = heliumCompartmentPressure[i];
                Initial_Nitrogen_Pressure[i] = nitrogenCompartmentPressure[i];

                heliumCompartmentPressure[i] = HALDANE_EQUATION(Initial_Helium_Pressure[i], Inspired_Helium_Pressure, kHe[i], Segment_Time);

                nitrogenCompartmentPressure[i] = HALDANE_EQUATION(Initial_Nitrogen_Pressure[i], Inspired_Nitrogen_Pressure, kN2[i], Segment_Time);

                //720   CONTINUE
            }
            
            // Debug: Show tissue loading during stop (every minute for 6m and 3m stops)
            if ((Math.abs(Deco_Stop_Depth - 6.0) < 0.1 || Math.abs(Deco_Stop_Depth - 3.0) < 0.1) && 
                Temp_Segment_Time > 0 && ((int)Temp_Segment_Time % 1) == 0) {
                debugDecoStop("DURING", Deco_Stop_Depth, Temp_Segment_Time, (int)(Temp_Segment_Time/currentMinimumDecoStopDuration));
            }
            
            // Debug: Before ceiling calculation
            if (Math.abs(Deco_Stop_Depth - 6.0) < 0.1 || Math.abs(Deco_Stop_Depth - 3.0) < 0.1) {
                debugCeilingCalculation("BEFORE_CEILING_CHECK", 0, Deco_Stop_Depth, currentDive.currentRunTime);
            }
            
            Deco_Ceiling_Depth = CALC_DECO_CEILING();
            
            // Debug: After ceiling calculation and ascent check
            // DEBUG DISABLED - Was causing hangs in the decompression stop loop
            /*
            if (Math.abs(Deco_Stop_Depth - 6.0) < 0.1 || Math.abs(Deco_Stop_Depth - 3.0) < 0.1) {
                debugCeilingCalculation("AFTER_CEILING_CHECK", Deco_Ceiling_Depth, Deco_Stop_Depth, currentDive.currentRunTime);
                System.out.println("\n=== ASCENT CHECK ===");
                System.out.println("Deco_Ceiling_Depth: " + Deco_Ceiling_Depth + " m");
                System.out.println("Next_Stop: " + Next_Stop + " m");
                System.out.println("Can ascend to " + Next_Stop + "m? " + (Deco_Ceiling_Depth <= Next_Stop));
                if (Deco_Ceiling_Depth <= Next_Stop) {
                    if (Math.abs(Deco_Stop_Depth - 6.0) < 0.1) {
                        System.out.println("*** SAFE TO ASCEND TO 3M! Stop time: " + Temp_Segment_Time + " min ***");
                    } else {
                        System.out.println("*** SAFE TO SURFACE! Stop time: " + Temp_Segment_Time + " min ***");
                    }
                }
            }
            */
            
            if(Deco_Ceiling_Depth > Next_Stop)
            {
                Segment_Time = currentMinimumDecoStopDuration;
                Time_Counter = Temp_Segment_Time;
                Temp_Segment_Time =  Time_Counter + currentMinimumDecoStopDuration;
                Last_Run_Time = currentDive.currentRunTime;
                currentDive.currentRunTime = Last_Run_Time + currentMinimumDecoStopDuration;
                //GOTO 700
                goBack = true;
            }
            else
            {
                goBack = false;
            }
        }while(goBack);
        
        // Debug: End of decompression stop
        debugDecoStop("END", Deco_Stop_Depth, Temp_Segment_Time, -1);
        
        return Temp_Segment_Time;

        //RETURN
        //===============================================================================
        //     END OF SUBROUTINE
        //===============================================================================
        //END
    }
    
/*C===============================================================================
//     SUBROUTINE CALC_BAROMETRIC_PRESSURE
//     Purpose: This sub calculates barometric pressure at altitude based on the
//     publication "U.S. Standard Atmosphere, 1976", U.S. Government Printing
//     Office, Washington, D.C. The source for this code is a Fortran 90 program
//     written by Ralph L. Carmichael (retired NASA researcher) and endorsed by
//     the National Geophysical Data Center of the National Oceanic and
//     Atmospheric Administration.  It is available for download free from
//     Public Domain Aeronautical Software at:  http://www.pdas.com/atmos.htm
//===============================================================================*/
    private double CALC_BAROMETRIC_PRESSURE(double Altitude)
    {
        ////IMPLICIT NONE
//  //===============================================================================
//  //     LOCAL CONSTANTS
//  //===============================================================================
        double Radius_of_Earth, Acceleration_of_Gravity;
        double Molecular_weight_of_Air, Gas_Constant_R;
        double Temp_at_Sea_Level, Temp_Gradient;
        double Pressure_at_Sea_Level_Fsw, Pressure_at_Sea_Level_Msw;
//  //===============================================================================
//  //     LOCAL VARIABLES
//  //===============================================================================
        double Pressure_at_Sea_Level = 0, GMR_Factor;
        double Altitude_Feet, Altitude_Meters;
        double Altitude_Kilometers = 0, Geopotential_Altitude;
        double Temp_at_Geopotential_Altitude;
//  //===============================================================================
//  //     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
//  //===============================================================================
//	boolean Units_Equal_Fsw, Units_Equal_Msw;
        //COMMON /Block_15/ Units_Equal_Fsw, Units_Equal_Msw

//	double Barometric_Pressure;                                           //output
        //COMMON /Block_18/ Barometric_Pressure
//  //===============================================================================
//  //     CALCULATIONS
//  //===============================================================================
        Radius_of_Earth = 6369.0;                                   //kilometers
        Acceleration_of_Gravity = 9.80665;                          //meters/second^2
        Molecular_weight_of_Air = 28.9644;                          //mols
        Gas_Constant_R = 8.31432;                                   //Joules/mol*deg Kelvin
        Temp_at_Sea_Level = 288.15;                                 //degrees Kelvin

        Pressure_at_Sea_Level_Fsw = 33.0;       //feet of seawater based on 101325 Pa at sea level (Standard Atmosphere)

        Pressure_at_Sea_Level_Msw = 10.0;       //meters of seawater based on 100000 Pa at sea level (European System)

        Temp_Gradient = -6.5;                   //Change in Temp deg Kelvin with change in geopotential altitude,
        // valid for first layer of atmosphere up to 11 kilometers or 36,000 feet

        GMR_Factor = Acceleration_of_Gravity * Molecular_weight_of_Air / Gas_Constant_R;


        if(Settings.metric)
        {
            Altitude_Meters = Altitude;
            Altitude_Kilometers = Altitude_Meters / 1000.0;
            Pressure_at_Sea_Level = Pressure_at_Sea_Level_Msw;
        }
        else
        {
            Altitude_Feet = Altitude;
            Altitude_Kilometers = Altitude_Feet / 3280.839895;
            Pressure_at_Sea_Level = Pressure_at_Sea_Level_Fsw;
        }

        Geopotential_Altitude =  (Altitude_Kilometers * Radius_of_Earth) / (Altitude_Kilometers + Radius_of_Earth);

        Temp_at_Geopotential_Altitude = Temp_at_Sea_Level + Temp_Gradient * Geopotential_Altitude;

        return Pressure_at_Sea_Level * Math.exp(Math.log(Temp_at_Sea_Level / Temp_at_Geopotential_Altitude) * GMR_Factor / Temp_Gradient);
    }

    /*C===============================================================================
    C     SUBROUTINE NUCLEAR_REGENERATION
    C     Purpose: This subprogram calculates the regeneration of VPM critical
    C     radii that takes place over the dive time.  The regeneration time constant
    C     has a time scale of weeks so this will have very little impact on dives of
    C     normal length, but will have a major impact for saturation dives.
    C===============================================================================*/
    private static void NUCLEAR_REGENERATION(double Dive_Time)
    {
        //IMPLICIT NONE
        //  C===============================================================================
        //  C     LOCAL VARIABLES
        //  C===============================================================================
        double Crushing_Pressure_Pascals_He, Crushing_Pressure_Pascals_N2;
        double Ending_Radius_He, Ending_Radius_N2;
        double Crush_Pressure_Adjust_Ratio_He;
        double Crush_Pressure_Adjust_Ratio_N2;
        double Adj_Crush_Pressure_He_Pascals, Adj_Crush_Pressure_N2_Pascals;
        /*C===============================================================================
        C     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
        C===============================================================================
        float Surface_Tension_Gamma, Skin_Compression_GammaC
        COMMON /Block_19/ Surface_Tension_Gamma, Skin_Compression_GammaC

        float Regeneration_Time_Constant
        COMMON /Block_22/ Regeneration_Time_Constant
        C===============================================================================
        C     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        C===============================================================================
        float Units_Factor
        COMMON /Block_16/ Units_Factor
        C===============================================================================
        C     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        C===============================================================================
        float Adjusted_Critical_Radius_He(16)                                //input
        float Adjusted_Critical_Radius_N2(16)
        COMMON /Block_7/ Adjusted_Critical_Radius_He, Adjusted_Critical_Radius_N2

        float Max_Crushing_Pressure_He(16), Max_Crushing_Pressure_N2(16)     //input
        COMMON /Block_10/ Max_Crushing_Pressure_He, Max_Crushing_Pressure_N2

        float Regenerated_Radius_He(16), Regenerated_Radius_N2(16)          //output
        COMMON /Block_24/ Regenerated_Radius_He, Regenerated_Radius_N2

        float Adjusted_Crushing_Pressure_He(16)                             //output
        float Adjusted_Crushing_Pressure_N2(16)
        COMMON /Block_25/ Adjusted_Crushing_Pressure_He, Adjusted_Crushing_Pressure_N2
        C===============================================================================
        C     CALCULATIONS
        C     First convert the maximum crushing pressure obtained for each compartment
        C     to Pascals.  Next, compute the ending radius for helium and nitrogen
        C     critical nuclei in each compartment.
        C===============================================================================*/
        for(int i=0; i<16; i++)
        {
            Crushing_Pressure_Pascals_He = (Max_Crushing_Pressure_He[i]/Settings.Depth_Per_ATM) * 101325.0;

            Crushing_Pressure_Pascals_N2 = (Max_Crushing_Pressure_N2[i]/Settings.Depth_Per_ATM) * 101325.0;

            Ending_Radius_He = 1.0/(Crushing_Pressure_Pascals_He/(2.0*(Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma)) + 1.0/Adjusted_Critical_Radius_He[i]);

            Ending_Radius_N2 = 1.0/(Crushing_Pressure_Pascals_N2/(2.0*(Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma)) + 1.0/Adjusted_Critical_Radius_N2[i]);
            /*C===============================================================================
            C     A "regenerated" radius for each nucleus is now calculated based on the
            C     regeneration time constant.  This means that after application of
            C     crushing pressure and reduction in radius, a nucleus will slowly grow
            C     back to its original initial radius over a period of time.  This
            C     phenomenon is probabilistic in nature and depends on absolute temperature.
            C     It is independent of crushing pressure.
            C===============================================================================*/
            Regenerated_Radius_He[i] = Adjusted_Critical_Radius_He[i] + (Ending_Radius_He - Adjusted_Critical_Radius_He[i]) * Math.exp(-Dive_Time/Settings.Regeneration_Time_Constant);

            Regenerated_Radius_N2[i] = Adjusted_Critical_Radius_N2[i] + (Ending_Radius_N2 - Adjusted_Critical_Radius_N2[i]) * Math.exp(-Dive_Time/Settings.Regeneration_Time_Constant);
            /*C===============================================================================
            C     In order to preserve reference back to the initial critical radii after
            C     regeneration, an "adjusted crushing pressure" for the nuclei in each
            C     compartment must be computed.  In other words, this is the value of
            C     crushing pressure that would have reduced the original nucleus to the
            C     to the present radius had regeneration not taken place.  The ratio
            C     for adjusting crushing pressure is obtained from algebraic manipulation
            C     of the standard VPM equations.  The adjusted crushing pressure, in lieu
            C     of the original crushing pressure, is then applied in the VPM Critical
            C     Volume Algorithm and the VPM Repetitive Algorithm.
            C===============================================================================*/
            Crush_Pressure_Adjust_Ratio_He = (Ending_Radius_He*(Adjusted_Critical_Radius_He[i] - Regenerated_Radius_He[i])) / (Regenerated_Radius_He[i] * (Adjusted_Critical_Radius_He[i] - Ending_Radius_He));

            Crush_Pressure_Adjust_Ratio_N2 = (Ending_Radius_N2*(Adjusted_Critical_Radius_N2[i] - Regenerated_Radius_N2[i])) / (Regenerated_Radius_N2[i] * (Adjusted_Critical_Radius_N2[i] - Ending_Radius_N2));

            Adj_Crush_Pressure_He_Pascals = Crushing_Pressure_Pascals_He * Crush_Pressure_Adjust_Ratio_He;

            Adj_Crush_Pressure_N2_Pascals = Crushing_Pressure_Pascals_N2 * Crush_Pressure_Adjust_Ratio_N2;

            Adjusted_Crushing_Pressure_He[i] = (Adj_Crush_Pressure_He_Pascals / 101325.0) * Settings.Depth_Per_ATM;

            Adjusted_Crushing_Pressure_N2[i] = (Adj_Crush_Pressure_N2_Pascals / 101325.0) * Settings.Depth_Per_ATM;
        }
        
        // Debug: Nuclear Regeneration values
        System.out.println("\n=== NUCLEAR REGENERATION DEBUG ===");
        System.out.println("Dive Time: " + Dive_Time);
        for (int i = 0; i < 4; i++) {
            System.out.println("Comp " + (i+1) + ":");
            System.out.println("  Max_Crushing_Pressure_N2: " + Max_Crushing_Pressure_N2[i]);
            System.out.println("  Max_Crushing_Pressure_He: " + Max_Crushing_Pressure_He[i]);
            System.out.println("  Adjusted_Critical_Radius_N2: " + (Adjusted_Critical_Radius_N2[i] * 1e6) + " microns");
            System.out.println("  Adjusted_Critical_Radius_He: " + (Adjusted_Critical_Radius_He[i] * 1e6) + " microns");
            System.out.println("  Regenerated_Radius_N2: " + (Regenerated_Radius_N2[i] * 1e6) + " microns");
            System.out.println("  Regenerated_Radius_He: " + (Regenerated_Radius_He[i] * 1e6) + " microns");
            System.out.println("  Adjusted_Crushing_Pressure_N2: " + Adjusted_Crushing_Pressure_N2[i]);
            System.out.println("  Adjusted_Crushing_Pressure_He: " + Adjusted_Crushing_Pressure_He[i]);
        }
        
        //  C===============================================================================
        //  C     END OF SUBROUTINE
        //  C===============================================================================
        //	RETURN
        //	END
    }

    /*  C===============================================================================
    //  C     SUBROUTINE CALC_CRUSHING_PRESSURE
    //  C     Purpose: Compute the effective "crushing pressure" in each compartment as
    //  C     a result of descent segment(s).  The crushing pressure is the gradient
    //  C     (difference in pressure) between the outside ambient pressure and the
    //  C     gas tension inside a VPM nucleus (bubble seed).  This gradient acts to
    //  C     reduce (shrink) the radius smaller than its initial value at the surface.
    //  C     This phenomenon has important ramifications because the smaller the radius
    //  C     of a VPM nucleus, the greater the allowable supersaturation gradient upon
    //  C     ascent.  Gas loading (uptake) during descent, especially in the fast
    //  C     compartments, will reduce the magnitude of the crushing pressure.  The
    //  C     crushing pressure is not cumulative over a multi-level descent.  It will
    //  C     be the maximum value obtained in any one discrete segment of the overall
    //  C     descent.  Thus, the program must compute and store the maximum crushing
    //  C     pressure for each compartment that was obtained across all segments of
    //  C     the descent profile.
    //  C
    //  C     The calculation of crushing pressure will be different depending on
    //  C     whether or not the gradient is in the VPM permeable range (gas can diffuse
    //  C     across skin of VPM nucleus) or the VPM impermeable range (molecules in
    //  C     skin of nucleus are squeezed together so tight that gas can no longer
    //  C     diffuse in or out of nucleus; the gas becomes trapped and further resists
    //  C     the crushing pressure).  The solution for crushing pressure in the VPM
    //  C     permeable range is a simple linear equation.  In the VPM impermeable
    //  C     range, a cubic equation must be solved using a numerical method.
    //  C
    //  C     Separate crushing pressures are tracked for helium and nitrogen because
    //  C     they can have different critical radii.  The crushing pressures will be
    //  C     the same for helium and nitrogen in the permeable range of the model, but
    //  C     they will start to diverge in the impermeable range.  This is due to
    //  C     the differences between starting radius, radius at the onset of
    //  C     impermeability, and radial compression in the impermeable range.
    //  C===============================================================================*/
    private static void CALC_CRUSHING_PRESSURE(double Starting_Depth, double Ending_Depth, double Rate, double[] nitrogenCompartmentPressureBeforeDescent, double[] heliumCompartmentPressureBeforeDescent)
    {
        //IMPLICIT NONE

//  C===============================================================================
//  C     LOCAL VARIABLES
//  C===============================================================================
        double Starting_Ambient_Pressure, Ending_Ambient_Pressure;
        double Starting_Gas_Tension, Ending_Gas_Tension;
        double Crushing_Pressure_He = 0, Crushing_Pressure_N2 = 0;
        double Gradient_Onset_of_Imperm, Gradient_Onset_of_Imperm_Pa;
        double Ending_Ambient_Pressure_Pa, Amb_Press_Onset_of_Imperm_Pa;
        double Gas_Tension_Onset_of_Imperm_Pa;
        double Crushing_Pressure_Pascals_He, Crushing_Pressure_Pascals_N2;
        double Starting_Gradient, Ending_Gradient;
        double A_He, B_He, C_He, Ending_Radius_He = 0, High_Bound_He;
        double Low_Bound_He;
        double A_N2, B_N2, C_N2, Ending_Radius_N2 = 0, High_Bound_N2;
        double Low_Bound_N2;
        double Radius_Onset_of_Imperm_He, Radius_Onset_of_Imperm_N2;
/*  C===============================================================================
C     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
C===============================================================================
float Gradient_Onset_of_Imperm_Atm
COMMON /Block_14/ Gradient_Onset_of_Imperm_Atm

float Constant_Pressure_Other_Gases
COMMON /Block_17/ Constant_Pressure_Other_Gases

float Surface_Tension_Gamma, Skin_Compression_GammaC
COMMON /Block_19/ Surface_Tension_Gamma, Skin_Compression_GammaC
C===============================================================================
C     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
C===============================================================================
float Units_Factor
COMMON /Block_16/ Units_Factor

float Barometric_Pressure
COMMON /Block_18/ Barometric_Pressure
C===============================================================================
C     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
C===============================================================================
float Helium_Pressure(16), Nitrogen_Pressure(16)                     //input
COMMON /Block_3/ Helium_Pressure, Nitrogen_Pressure

float Adjusted_Critical_Radius_He(16)                                //input
float Adjusted_Critical_Radius_N2(16)
COMMON /Block_7/ Adjusted_Critical_Radius_He, Adjusted_Critical_Radius_N2

float Max_Crushing_Pressure_He(16), Max_Crushing_Pressure_N2(16)    //output
COMMON /Block_10/ Max_Crushing_Pressure_He, Max_Crushing_Pressure_N2

float Amb_Pressure_Onset_of_Imperm(16)                               //input
float Gas_Tension_Onset_of_Imperm(16)
COMMON /Block_13/ Amb_Pressure_Onset_of_Imperm, Gas_Tension_Onset_of_Imperm

float Initial_Helium_Pressure(16), Initial_Nitrogen_Pressure(16)     //input
COMMON /Block_23/ Initial_Helium_Pressure, Initial_Nitrogen_Pressure
        C===============================================================================
        C     CALCULATIONS
        C     First, convert the Gradient for Onset of Impermeability from units of
        C     atmospheres to diving pressure units (either fsw or msw) and to Pascals
        C     (SI units).  The reason that the Gradient for Onset of Impermeability is
        C     given in the program settings in units of atmospheres is because that is
        C     how it was reported in the original research papers by Yount and
        C     colleauges.
        C===============================================================================*/
        Gradient_Onset_of_Imperm = Settings.Gradient_Onset_of_Imperm_Atm * Settings.Depth_Per_ATM;     //convert to diving units

        Gradient_Onset_of_Imperm_Pa = Settings.Gradient_Onset_of_Imperm_Atm * 101325.0;      //convert to Pascals
        //  C===============================================================================
        //  C     Assign values of starting and ending ambient pressures for descent segment
        //  C===============================================================================
        Starting_Ambient_Pressure = Starting_Depth + Settings.getSurfacePressure();
        Ending_Ambient_Pressure = Ending_Depth + Settings.getSurfacePressure();
        
        // Calculate segment time for the descent
        double segmentTime = (Ending_Depth - Starting_Depth) / Rate;
        
        // Time-based crushing pressure tracking for compartment 1
        System.out.println("\n=== TIME-BASED CRUSHING PRESSURE TRACKING ===");
        System.out.println("Descent from " + Starting_Depth + "m to " + Ending_Depth + "m at rate " + Rate + " m/min");
        System.out.println("Total segment time: " + segmentTime + " minutes");
        
        // Calculate inspired gas pressures for tracking
        double FN2 = currentDive.getCurrentNitrogenFraction();
        double FHe = currentDive.getCurrentHeliumFraction();
        double Inspired_Nitrogen_Pressure = (Settings.getSurfacePressure() - Water_Vapor_Pressure) * FN2;
        double Inspired_Helium_Pressure = (Settings.getSurfacePressure() - Water_Vapor_Pressure) * FHe;
        double Nitrogen_Rate = Rate * FN2;
        double Helium_Rate = Rate * FHe;
        
        // Track crushing pressure at 0.5 minute intervals for compartment 1
        for (double t = 0.5; t <= segmentTime; t += 0.5) {
            double currentDepth = Starting_Depth + Rate * t;
            double currentAmbient = currentDepth + Settings.getSurfacePressure();
            
            // Calculate tissue loading at time t using Schreiner equation for compartment 1
            double Initial_Inspired_N2_Pressure = Inspired_Nitrogen_Pressure + Nitrogen_Rate * Starting_Depth;
            double Initial_Inspired_He_Pressure = Inspired_Helium_Pressure + Helium_Rate * Starting_Depth;
            
            double n2AtTime = SCHREINER_EQUATION(Initial_Inspired_N2_Pressure, Nitrogen_Rate, t, kN2[0], nitrogenCompartmentPressureBeforeDescent[0]);
            double heAtTime = SCHREINER_EQUATION(Initial_Inspired_He_Pressure, Helium_Rate, t, kHe[0], heliumCompartmentPressureBeforeDescent[0]);
            
            double totalGasTension = n2AtTime + heAtTime + Constant_Pressure_Other_Gases;
            double crushingAtTime = currentAmbient - totalGasTension;
            
            System.out.printf("Time %.1f min: depth=%.1fm ambient=%.2f N2=%.3f He=%.3f crushing=%.3f\n",
                             t, currentDepth, currentAmbient, n2AtTime, heAtTime, crushingAtTime);
        }
        
        //  C===============================================================================
        //  C     MAIN LOOP WITH NESTED DECISION TREE
        //  C     For each compartment, the program computes the starting and ending
        //  C     gas tensions and gradients.  The VPM is different than some dissolved gas
        //  C     algorithms, Buhlmann for example, in that it considers the pressure due to
        //  C     oxygen, carbon dioxide, and water vapor in each compartment in addition to
        //  C     the inert gases helium and nitrogen.  These "other gases" are included in
        //  C     the calculation of gas tensions and gradients.
        //  C===============================================================================
        for(int compartmentIndex=0; compartmentIndex<16; compartmentIndex++)
        {
            Starting_Gas_Tension = heliumCompartmentPressureBeforeDescent[compartmentIndex] + nitrogenCompartmentPressureBeforeDescent[compartmentIndex] + Constant_Pressure_Other_Gases;

            Starting_Gradient = Starting_Ambient_Pressure - Starting_Gas_Tension;

            Ending_Gas_Tension = heliumCompartmentPressure[compartmentIndex] + nitrogenCompartmentPressure[compartmentIndex] + Constant_Pressure_Other_Gases;

            Ending_Gradient = Ending_Ambient_Pressure - Ending_Gas_Tension;
            //  C===============================================================================
            //  C     Compute radius at onset of impermeability for helium and nitrogen
            //  C     critical radii
            //  C===============================================================================
            Radius_Onset_of_Imperm_He = 1.0/(Gradient_Onset_of_Imperm_Pa/(2.0*(Settings.Skin_Compression_GammaC-Settings.Surface_Tension_Gamma)) + 1.0/Adjusted_Critical_Radius_He[compartmentIndex]);

            Radius_Onset_of_Imperm_N2 = 1.0/(Gradient_Onset_of_Imperm_Pa/(2.0*(Settings.Skin_Compression_GammaC-Settings.Surface_Tension_Gamma)) + 1.0/Adjusted_Critical_Radius_N2[compartmentIndex]);
            //  C===============================================================================
            //  C     FIRST BRANCH OF DECISION TREE - PERMEABLE RANGE
            //  C     Crushing pressures will be the same for helium and nitrogen
            //  C===============================================================================
            if(Ending_Gradient <= Gradient_Onset_of_Imperm)
            {
                Crushing_Pressure_He = Ending_Ambient_Pressure - Ending_Gas_Tension;

                Crushing_Pressure_N2 = Ending_Ambient_Pressure - Ending_Gas_Tension;
            }
            //  C===============================================================================
            //  C     SECOND BRANCH OF DECISION TREE - IMPERMEABLE RANGE
            //  C     Both the ambient pressure and the gas tension at the onset of
            //  C     impermeability must be computed in order to properly solve for the ending
            //  C     radius and resultant crushing pressure.  The first decision block
            //  C     addresses the special case when the starting gradient just happens to be
            //  C     equal to the gradient for onset of impermeability (not very likely!).
            //  C===============================================================================
            if(Ending_Gradient > Gradient_Onset_of_Imperm)
            {
                if(Starting_Gradient == Gradient_Onset_of_Imperm)
                {
                    Amb_Pressure_Onset_of_Imperm[compartmentIndex] = Starting_Ambient_Pressure;
                    Gas_Tension_Onset_of_Imperm[compartmentIndex] = Starting_Gas_Tension;
                }
                //  C===============================================================================
                //  C     In most cases, a subroutine will be called to find these values using a
                //  C     numerical method.
                //  C===============================================================================
                if(Starting_Gradient < Gradient_Onset_of_Imperm)
                {
                    ONSET_OF_IMPERMEABILITY(Starting_Ambient_Pressure, Ending_Ambient_Pressure, Rate, compartmentIndex, heliumCompartmentPressureBeforeDescent[compartmentIndex], nitrogenCompartmentPressureBeforeDescent[compartmentIndex]);
                }

                /*C===============================================================================
                C     Next, using the values for ambient pressure and gas tension at the onset
                C     of impermeability, the equations are set up to process the calculations
                C     through the radius root finder subroutine.  This subprogram will find the
                C     root (solution) to the cubic equation using a numerical method.  In order
                C     to do this efficiently, the equations are placed in the form
                C     Ar^3 - Br^2 - C = 0, where r is the ending radius after impermeable
                C     compression.  The coefficients A, B, and C for helium and nitrogen are
                C     computed and passed to the subroutine as arguments.  The high and low
                C     bounds to be used by the numerical method of the subroutine are also
                C     computed (see separate page posted on Deco List ftp site entitled
                C     "VPM: Solving for radius in the impermeable regime").  The subprogram
                C     will return the value of the ending radius and then the crushing
                C     pressures for helium and nitrogen can be calculated.
                C===============================================================================*/
                Ending_Ambient_Pressure_Pa = (Ending_Ambient_Pressure/Settings.Depth_Per_ATM) * 101325.0;

                Amb_Press_Onset_of_Imperm_Pa = (Amb_Pressure_Onset_of_Imperm[compartmentIndex]/Settings.Depth_Per_ATM) * 101325.0;

                Gas_Tension_Onset_of_Imperm_Pa = (Gas_Tension_Onset_of_Imperm[compartmentIndex]/Settings.Depth_Per_ATM) * 101325.0;

                B_He = 2.0*(Settings.Skin_Compression_GammaC-Settings.Surface_Tension_Gamma);

                A_He = Ending_Ambient_Pressure_Pa - Amb_Press_Onset_of_Imperm_Pa + Gas_Tension_Onset_of_Imperm_Pa + (2.0*(Settings.Skin_Compression_GammaC-Settings.Surface_Tension_Gamma)) /Radius_Onset_of_Imperm_He;

                C_He = Gas_Tension_Onset_of_Imperm_Pa * Math.pow(Radius_Onset_of_Imperm_He,3);

                High_Bound_He = Radius_Onset_of_Imperm_He;
                Low_Bound_He = B_He/A_He;

                //RADIUS_ROOT_FINDER(A_He,B_He,C_He, Low_Bound_He, High_Bound_He, Ending_Radius_He);
                Ending_Radius_He = RADIUS_ROOT_FINDER(A_He,B_He,C_He, Low_Bound_He, High_Bound_He);

                Crushing_Pressure_Pascals_He = Gradient_Onset_of_Imperm_Pa + Ending_Ambient_Pressure_Pa - Amb_Press_Onset_of_Imperm_Pa + Gas_Tension_Onset_of_Imperm_Pa * (1.0-Math.pow(Radius_Onset_of_Imperm_He,3) / Math.pow(Ending_Radius_He,3));

                Crushing_Pressure_He = (Crushing_Pressure_Pascals_He/101325.0) * Settings.Depth_Per_ATM;

                B_N2 = 2.0*(Settings.Skin_Compression_GammaC-Settings.Surface_Tension_Gamma);

                A_N2 = Ending_Ambient_Pressure_Pa - Amb_Press_Onset_of_Imperm_Pa + Gas_Tension_Onset_of_Imperm_Pa + (2.0*(Settings.Skin_Compression_GammaC-Settings.Surface_Tension_Gamma)) /Radius_Onset_of_Imperm_N2;

                C_N2 = Gas_Tension_Onset_of_Imperm_Pa * Math.pow(Radius_Onset_of_Imperm_N2,3);

                High_Bound_N2 = Radius_Onset_of_Imperm_N2;
                Low_Bound_N2 = B_N2/A_N2;

                //RADIUS_ROOT_FINDER(A_N2,B_N2,C_N2, Low_Bound_N2,High_Bound_N2, Ending_Radius_N2);
                Ending_Radius_N2 = RADIUS_ROOT_FINDER(A_N2,B_N2,C_N2, Low_Bound_N2,High_Bound_N2);

                Crushing_Pressure_Pascals_N2 = Gradient_Onset_of_Imperm_Pa + Ending_Ambient_Pressure_Pa - Amb_Press_Onset_of_Imperm_Pa + Gas_Tension_Onset_of_Imperm_Pa * (1.0-Math.pow(Radius_Onset_of_Imperm_N2,3)/Math.pow(Ending_Radius_N2,3));

                Crushing_Pressure_N2 = (Crushing_Pressure_Pascals_N2/101325.0) * Settings.Depth_Per_ATM;
            }
//  C===============================================================================
//  C     UPDATE VALUES OF MAX CRUSHING PRESSURE IN GLOBAL ARRAYS
//  C===============================================================================
            Max_Crushing_Pressure_He[compartmentIndex] = Math.max(Max_Crushing_Pressure_He[compartmentIndex], Crushing_Pressure_He);

            Max_Crushing_Pressure_N2[compartmentIndex] = Math.max(Max_Crushing_Pressure_N2[compartmentIndex], Crushing_Pressure_N2);
            
            // Debug output for first compartment only during descent
            if (compartmentIndex == 0) {
                System.out.println("\n=== CRUSHING PRESSURE DEBUG ===");
                System.out.println("At depth: " + Ending_Depth + "m");
                System.out.println("Ambient pressure: " + Ending_Ambient_Pressure);
                System.out.println("Comp 1 N2 pressure: " + nitrogenCompartmentPressure[0]);
                System.out.println("Comp 1 He pressure: " + heliumCompartmentPressure[0]);
                System.out.println("Comp 1 total gas tension: " + (nitrogenCompartmentPressure[0] + heliumCompartmentPressure[0]));
                System.out.println("Crushing pressure: " + (Ending_Ambient_Pressure - (nitrogenCompartmentPressure[0] + heliumCompartmentPressure[0])));
                System.out.println("Max crushing pressure so far: " + Max_Crushing_Pressure_N2[0]);
            }
        }
//  C===============================================================================
//  C     END OF SUBROUTINE
//  C===============================================================================
            //RETURN
            //END
    }
    

/*C===============================================================================
C     SUBROUTINE CALC_INITIAL_ALLOWABLE_GRADIENT
C     Purpose: This subprogram calculates the initial allowable gradients for
C     helium and nitrogren in each compartment.  These are the gradients that
C     will be used to set the deco ceiling on the first pass through the deco
C     loop.  If the Critical Volume Algorithm is set to "off", then these
C     gradients will determine the final deco schedule.  Otherwise, if the
C     Critical Volume Algorithm is set to "on", these gradients will be further
C     "relaxed" by the Critical Volume Algorithm subroutine.  The initial
C     allowable gradients are referred to as "PssMin" in the papers by Yount
C     and colleauges, compartmentIndex.e., the minimum supersaturation pressure gradients
C     that will probe bubble formation in the VPM nuclei that started with the
C     designated minimum initial radius (critical radius).
C
C     The initial allowable gradients are computed directly from the
C     "regenerated" radii after the Nuclear Regeneration subroutine.  These
C     gradients are tracked separately for helium and nitrogen.
C===============================================================================*/
    private static void CALC_INITIAL_ALLOWABLE_GRADIENT()
    {
        //IMPLICIT NONE
//  C===============================================================================
//  C     LOCAL VARIABLES
//  C===============================================================================
        double Initial_Allowable_Grad_He_Pa, Initial_Allowable_Grad_N2_Pa;
/*C===============================================================================
C     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
C===============================================================================
float Surface_Tension_Gamma, Skin_Compression_GammaC
COMMON /Block_19/ Surface_Tension_Gamma, Skin_Compression_GammaC
C===============================================================================
C     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
C===============================================================================
float Units_Factor
COMMON /Block_16/ Units_Factor
C===============================================================================
C     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
C===============================================================================
float Regenerated_Radius_He(16), Regenerated_Radius_N2(16)           //input
COMMON /Block_24/ Regenerated_Radius_He, Regenerated_Radius_N2

float Allowable_Gradient_He(16), Allowable_Gradient_N2 (16)         //output
COMMON /Block_26/ Allowable_Gradient_He, Allowable_Gradient_N2

float Initial_Allowable_Gradient_He(16)                             //output
float Initial_Allowable_Gradient_N2(16)
COMMON /Block_27/ Initial_Allowable_Gradient_He, Initial_Allowable_Gradient_N2
C===============================================================================
C     CALCULATIONS
C     The initial allowable gradients are computed in Pascals and then converted
C     to the diving pressure units.  Two different sets of arrays are used to
C     save the calculations - Initial Allowable Gradients and Allowable
C     Gradients.  The Allowable Gradients are assigned the values from Initial
C     Allowable Gradients however the Allowable Gradients can be changed later
C     by the Critical Volume subroutine.  The values for the Initial Allowable
C     Gradients are saved in a global array for later use by both the Critical
C     Volume subroutine and the VPM Repetitive Algorithm subroutine.
C===============================================================================*/
        for(int i=0; i<16; i++)
        {
            Initial_Allowable_Grad_N2_Pa = ((2.0*Settings.Surface_Tension_Gamma*(Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma)) / (Regenerated_Radius_N2[i]*Settings.Skin_Compression_GammaC));

            Initial_Allowable_Grad_He_Pa = ((2.0*Settings.Surface_Tension_Gamma*(Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma)) / (Regenerated_Radius_He[i]*Settings.Skin_Compression_GammaC));

            Initial_Allowable_Gradient_N2[i] = (Initial_Allowable_Grad_N2_Pa / 101325.0) * Settings.Depth_Per_ATM;

            Initial_Allowable_Gradient_He[i] = (Initial_Allowable_Grad_He_Pa / 101325.0) * Settings.Depth_Per_ATM;

            Allowable_Gradient_He[i] = Initial_Allowable_Gradient_He[i];
            Allowable_Gradient_N2[i] = Initial_Allowable_Gradient_N2[i];
        }
        
        // Debug: Initial gradients calculation
        System.out.println("\n=== INITIAL GRADIENTS CALCULATION ===");
        for (int i = 0; i < 4; i++) {
            System.out.println("Comp " + (i+1) + ":");
            System.out.println("  Regenerated_Radius_N2: " + (Regenerated_Radius_N2[i] * 1e6) + " microns");
            System.out.println("  Regenerated_Radius_He: " + (Regenerated_Radius_He[i] * 1e6) + " microns");
            // System.out.println("  Initial_Allowable_Gradient_N2: " + Initial_Allowable_Gradient_N2[i]);
            // System.out.println("  Initial_Allowable_Gradient_He: " + Initial_Allowable_Gradient_He[i]);
        }
        
//  C===============================================================================
//  C     END OF SUBROUTINE
//  C===============================================================================
        //RETURN
        //END
    }

/*C===============================================================================
C     SUBROUTINE CALC_ASCENT_CEILING
C     Purpose: This subprogram calculates the ascent ceiling (the safe ascent
C     depth) in each compartment, based on the allowable gradients, and then
C     finds the deepest ascent ceiling across all compartments.
C===============================================================================*/
    private static double CALC_ASCENT_CEILING()
    {
        //IMPLICIT NONE
//  C===============================================================================
//  C     LOCAL VARIABLES
//  C===============================================================================
        double Gas_Loading, Weighted_Allowable_Gradient;
        double Tolerated_Ambient_Pressure;
//  C===============================================================================
//  C     LOCAL ARRAYS
//  C===============================================================================
        //float Compartment_Ascent_Ceiling(16)
        double[] Compartment_Ascent_Ceiling = new double[16];
/*C===============================================================================
C     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
C===============================================================================
float Constant_Pressure_Other_Gases
COMMON /Block_17/ Constant_Pressure_Other_Gases
C===============================================================================
C     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
C===============================================================================
float Barometric_Pressure
COMMON /Block_18/ Barometric_Pressure
C===============================================================================
C     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
C===============================================================================
float Helium_Pressure(16), Nitrogen_Pressure(16)                     //input
COMMON /Block_3/ Helium_Pressure, Nitrogen_Pressure

float Allowable_Gradient_He(16), Allowable_Gradient_N2 (16)          //input
COMMON /Block_26/ Allowable_Gradient_He, Allowable_Gradient_N2
C===============================================================================
C     CALCULATIONS
C     Since there are two sets of allowable gradients being tracked, one for
C     helium and one for nitrogen, a "weighted allowable gradient" must be
C     computed each time based on the proportions of helium and nitrogen in
C     each compartment.  This proportioning follows the methodology of
C     Buhlmann/Keller.  If there is no helium and nitrogen in the compartment,
C     such as after extended periods of oxygen breathing, then the minimum value
C     across both gases will be used.  It is important to note that if a
C     compartment is empty of helium and nitrogen, then the weighted allowable
C     gradient formula cannot be used since it will result in division by zero.
C===============================================================================*/
        for(int i=0; i<16; i++)
        {
            Gas_Loading = heliumCompartmentPressure[i] + nitrogenCompartmentPressure[i];

            if(Gas_Loading > 0.0)
            {
                Weighted_Allowable_Gradient = (Allowable_Gradient_He[i]* heliumCompartmentPressure[i] + Allowable_Gradient_N2[i]* nitrogenCompartmentPressure[i]) / (heliumCompartmentPressure[i] + nitrogenCompartmentPressure[i]);

                Tolerated_Ambient_Pressure = (Gas_Loading + Constant_Pressure_Other_Gases) - Weighted_Allowable_Gradient;
            }
            else
            {
                Weighted_Allowable_Gradient = Math.min(Allowable_Gradient_He[i], Allowable_Gradient_N2[i]);

                Tolerated_Ambient_Pressure = Constant_Pressure_Other_Gases - Weighted_Allowable_Gradient;
            }
/*C===============================================================================
C     The tolerated ambient pressure cannot be less than zero absolute, compartmentIndex.e.,
C     the vacuum of outer space!
C===============================================================================*/
            if(Tolerated_Ambient_Pressure < 0.0)
            {
                Tolerated_Ambient_Pressure = 0.0;
            }
/*  C===============================================================================
C     The Ascent Ceiling Depth is computed in a loop after all of the individual
C     compartment ascent ceilings have been calculated.  It is important that
C     the Ascent Ceiling Depth (max ascent ceiling across all compartments) only
C     be extracted from the compartment values and not be compared against some
C     initialization value.  For example, if MAX(Ascent_Ceiling_Depth . .) was
C     compared against zero, this could cause a program lockup because sometimes
C     the Ascent Ceiling Depth needs to be negative (but not less than zero
C     absolute ambient pressure) in order to decompress to the last stop at zero
C     depth.
C===============================================================================*/
            Compartment_Ascent_Ceiling[i] = Tolerated_Ambient_Pressure - Settings.getSurfacePressure();
        }
        double Ascent_Ceiling_Depth = Compartment_Ascent_Ceiling[0];
        for(int i=1; i<16; i++)
        {
            Ascent_Ceiling_Depth = Math.max(Ascent_Ceiling_Depth, Compartment_Ascent_Ceiling[i]);
        }
//  C===============================================================================
//  C     END OF SUBROUTINE
//  C===============================================================================
        //RETURN
        //END
        return Ascent_Ceiling_Depth;
    }

/*C===============================================================================
//     SUBROUTINE CALC_MAX_ACTUAL_GRADIENT
//     Purpose: This subprogram calculates the actual supersaturation gradient
//     obtained in each compartment as a result of the ascent profile during
//     decompression.  Similar to the concept with crushing pressure, the
//     supersaturation gradients are not cumulative over a multi-level, staged
//     ascent.  Rather, it will be the maximum value obtained in any one discrete
//     step of the overall ascent.  Thus, the program must compute and store the
//     maximum actual gradient for each compartment that was obtained across all
//     steps of the ascent profile.  This subroutine is invoked on the last pass
//     through the deco stop loop block when the final deco schedule is being
//     generated.
//
//     The max actual gradients are later used by the VPM Repetitive Algorithm to
//     determine if adjustments to the critical radii are required.  If the max
//     actual gradient did not exceed the initial alllowable gradient, then no
//     adjustment will be made.  However, if the max actual gradient did exceed
//     the intitial allowable gradient, such as permitted by the Critical Volume
//     Algorithm, then the critical radius will be adjusted (made larger) on the
//     repetitive dive to compensate for the bubbling that was allowed on the
//     previous dive.  The use of the max actual gradients is intended to prevent
//     the repetitive algorithm from being overly conservative.
//===============================================================================*/
    private static void CALC_MAX_ACTUAL_GRADIENT(double Deco_Stop_Depth)
    {
        //IMPLICIT NONE
        //===============================================================================
        //     LOCAL VARIABLES
        //===============================================================================
        double Compartment_Gradient;
        /*===============================================================================
        //     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
        //===============================================================================
        float Constant_Pressure_Other_Gases
        COMMON /Block_17/ Constant_Pressure_Other_Gases
        //===============================================================================
        //     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        //===============================================================================
        float Barometric_Pressure
        COMMON /Block_18/ Barometric_Pressure
        //===============================================================================
        //     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        //===============================================================================
        float Helium_Pressure(16), Nitrogen_Pressure(16)                     //input
        COMMON /Block_3/ Helium_Pressure, Nitrogen_Pressure

        float Max_Actual_Gradient(16)
        COMMON /Block_12/ Max_Actual_Gradient                              //output
        //===============================================================================
        //     CALCULATIONS
        //     Note: negative supersaturation gradients are meaningless for this
        //     application, so the values must be equal to or greater than zero.
        //===============================================================================*/
        for(int i=0; i<16; i++)
        {
            Compartment_Gradient = (heliumCompartmentPressure[i] + nitrogenCompartmentPressure[i] + Constant_Pressure_Other_Gases) - (Deco_Stop_Depth + Settings.getSurfacePressure());
            if(Compartment_Gradient <= 0.0)
            {
                Compartment_Gradient = 0.0;
            }
            Max_Actual_Gradient[i] = Math.max(Max_Actual_Gradient[i], Compartment_Gradient);
        }
        //===============================================================================
        //     END OF SUBROUTINE
        //===============================================================================
        //RETURN
        //END
    }


    //===============================================================================
    //     SUBROUTINE CALC_SURFACE_PHASE_VOLUME_TIME
    //     Purpose: This subprogram computes the surface portion of the total phase
    //     volume time.  This is the time factored out of the integration of
    //     supersaturation gradient x time over the surface interval.  The VPM
    //     considers the gradients that allow bubbles to form or to drive bubble
    //     growth both in the water and on the surface after the dive.
    //
    //     This subroutine is a new development to the VPM algorithm in that it
    //     computes the time course of supersaturation gradients on the surface
    //     when both helium and nitrogen are present.  Refer to separate write-up
    //     for a more detailed explanation of this algorithm.
    //===============================================================================
    private static void CALC_SURFACE_PHASE_VOLUME_TIME()
    {
        //IMPLICIT NONE
        //===============================================================================
        //     LOCAL VARIABLES
        //===============================================================================
        double Integral_Gradient_x_Time, Decay_Time_to_Zero_Gradient;
        double Surface_Inspired_N2_Pressure;
        /*===============================================================================
        //     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
        //===============================================================================
        float Water_Vapor_Pressure
        COMMON /Block_8/ Water_Vapor_Pressure
        //===============================================================================
        //     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        //===============================================================================
        float Barometric_Pressure
        COMMON /Block_18/ Barometric_Pressure
        //===============================================================================
        //     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        //===============================================================================
        float Helium_Time_Constant(16)
        COMMON /Block_1A/ Helium_Time_Constant

        float Nitrogen_Time_Constant(16)
        COMMON /Block_1B/ Nitrogen_Time_Constant

        float Helium_Pressure(16), Nitrogen_Pressure(16)                     //input
        COMMON /Block_3/ Helium_Pressure, Nitrogen_Pressure

        float Surface_Phase_Volume_Time(16)                                 //output
        COMMON /Block_11/ Surface_Phase_Volume_Time
        //===============================================================================
        //     CALCULATIONS
        //===============================================================================*/
        Surface_Inspired_N2_Pressure = (Settings.getSurfacePressure() - Water_Vapor_Pressure)*0.79;
        for(int i=0; i<16; i++)
        {
            if(nitrogenCompartmentPressure[i] > Surface_Inspired_N2_Pressure)
            {
                Surface_Phase_Volume_Time[i] = (heliumCompartmentPressure[i]/kHe[i] + (nitrogenCompartmentPressure[i]-Surface_Inspired_N2_Pressure) / kN2[i]) /(heliumCompartmentPressure[i] + nitrogenCompartmentPressure[i] - Surface_Inspired_N2_Pressure);
            }
            else if((nitrogenCompartmentPressure[i] <= Surface_Inspired_N2_Pressure) && (heliumCompartmentPressure[i]+nitrogenCompartmentPressure[i] >= Surface_Inspired_N2_Pressure))
            {
                Decay_Time_to_Zero_Gradient = 1.0/(kN2[i]-kHe[i]) * Math.log((Surface_Inspired_N2_Pressure - nitrogenCompartmentPressure[i])/heliumCompartmentPressure[i]);

                Integral_Gradient_x_Time = heliumCompartmentPressure[i]/kHe[i]*(1.0-Math.exp(-kHe[i]*Decay_Time_to_Zero_Gradient))+(nitrogenCompartmentPressure[i]-Surface_Inspired_N2_Pressure)/kN2[i]*(1.0-Math.exp(-kN2[i]*Decay_Time_to_Zero_Gradient));
                Surface_Phase_Volume_Time[i] = Integral_Gradient_x_Time/(heliumCompartmentPressure[i] + nitrogenCompartmentPressure[i] - Surface_Inspired_N2_Pressure);
            }
            else
            {
                Surface_Phase_Volume_Time[i] = 0.0;
            }
        }
        //===============================================================================
        //     END OF SUBROUTINE
        //===============================================================================
        //RETURN
        //END
    }


    //===============================================================================
    //     SUBROUTINE CRITICAL_VOLUME
    //     Purpose: This subprogram applies the VPM Critical Volume Algorithm.  This
    //     algorithm will compute "relaxed" gradients for helium and nitrogen based
    //     on the setting of the Critical Volume Parameter Lambda.
    //===============================================================================
    private static void CRITICAL_VOLUME(double Deco_Phase_Volume_Time)
    {
        //IMPLICIT NONE
        //===============================================================================
        //     LOCAL VARIABLES
        //===============================================================================
        double Parameter_Lambda_Pascals;
        double Adj_Crush_Pressure_He_Pascals, Adj_Crush_Pressure_N2_Pascals;
        double Initial_Allowable_Grad_He_Pa, Initial_Allowable_Grad_N2_Pa;
        double New_Allowable_Grad_He_Pascals, New_Allowable_Grad_N2_Pascals;
        double B, C;
        //===============================================================================
        //     LOCAL ARRAYS
        //===============================================================================
        //float Phase_Volume_Time(16)
        double[] Phase_Volume_Time = new double[16];
        /*===============================================================================
        //     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
        //===============================================================================
        float Surface_Tension_Gamma, Skin_Compression_GammaC
        COMMON /Block_19/ Surface_Tension_Gamma, Skin_Compression_GammaC

        float Crit_Volume_Parameter_Lambda
        COMMON /Block_20/ Crit_Volume_Parameter_Lambda
        //===============================================================================
        //     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        //===============================================================================
        float Units_Factor
        COMMON /Block_16/ Units_Factor
        //===============================================================================
        //     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        //===============================================================================
        float Adjusted_Critical_Radius_He(16)                                //input
        float Adjusted_Critical_Radius_N2(16)
        COMMON /Block_7/ Adjusted_Critical_Radius_He,
        *           Adjusted_Critical_Radius_N2

        float Surface_Phase_Volume_Time(16)                                  //input
        COMMON /Block_11/ Surface_Phase_Volume_Time

        float Adjusted_Crushing_Pressure_He(16)                              //input
        float Adjusted_Crushing_Pressure_N2(16)
        COMMON /Block_25/ Adjusted_Crushing_Pressure_He,
        *                  Adjusted_Crushing_Pressure_N2

        float Allowable_Gradient_He(16), Allowable_Gradient_N2 (16)         //output
        COMMON /Block_26/ Allowable_Gradient_He, Allowable_Gradient_N2

        float Initial_Allowable_Gradient_He(16)                              //input
        float Initial_Allowable_Gradient_N2(16)
        COMMON /Block_27/
        *    Initial_Allowable_Gradient_He, Initial_Allowable_Gradient_N2
        //===============================================================================
        //     CALCULATIONS
        //     Note:  Since the Critical Volume Parameter Lambda was defined in units of
        //     fsw-min in the original papers by Yount and colleauges, the same
        //     convention is retained here.  Although Lambda is adjustable only in units
        //     of fsw-min in the program settings (range from 6500 to 8300 with default
        //     7500), it will convert to the proper value in Pascals-min in this
        //     subroutine regardless of which diving pressure units are being used in
        //     the main program - feet of seawater (fsw) or meters of seawater (msw).
        //     The allowable gradient is computed using the quadratic formula (refer to
        //     separate write-up posted on the Deco List web site).
        //===============================================================================*/
        Parameter_Lambda_Pascals = (Settings.Crit_Volume_Parameter_Lambda/33.0)* 101325.0;
        for(int i=0; i<16; i++)
        {
            Phase_Volume_Time[i] = Deco_Phase_Volume_Time + Surface_Phase_Volume_Time[i];
        }
        
        // Debug: CVA Parameters for first compartment
        // System.out.println("=== CVA PARAMETERS (Comp 1) ===");
        // System.out.println("Deco_Phase_Volume_Time: " + Deco_Phase_Volume_Time + " min");
        // System.out.println("Surface_Phase_Volume_Time[0]: " + Surface_Phase_Volume_Time[0] + " min");
        // System.out.println("Total Phase_Volume_Time[0]: " + Phase_Volume_Time[0] + " min");
        System.out.println("Adjusted_Crushing_Pressure_N2[0]: " + Adjusted_Crushing_Pressure_N2[0]);
        System.out.println("Adjusted_Crushing_Pressure_He[0]: " + Adjusted_Crushing_Pressure_He[0]);
        // System.out.println("Initial_Allowable_Gradient_N2[0]: " + Initial_Allowable_Gradient_N2[0]);
        // System.out.println("Initial_Allowable_Gradient_He[0]: " + Initial_Allowable_Gradient_He[0]);
        System.out.println("Parameter_Lambda: " + Settings.Crit_Volume_Parameter_Lambda + " fsw-min");
        System.out.println("Parameter_Lambda_Pascals: " + Parameter_Lambda_Pascals + " Pa-min");
        System.out.println("Surface_Tension_Gamma: " + Settings.Surface_Tension_Gamma);
        System.out.println("Skin_Compression_GammaC: " + Settings.Skin_Compression_GammaC);

        for(int i=0; i<16; i++)
        {
            Adj_Crush_Pressure_He_Pascals = (Adjusted_Crushing_Pressure_He[i]/Settings.Depth_Per_ATM) * 101325.0;

            Initial_Allowable_Grad_He_Pa = (Initial_Allowable_Gradient_He[i]/Settings.Depth_Per_ATM) * 101325.0;

            B = Initial_Allowable_Grad_He_Pa + (Parameter_Lambda_Pascals*Settings.Surface_Tension_Gamma)/ (Settings.Skin_Compression_GammaC*Phase_Volume_Time[i]);

            C = (Settings.Surface_Tension_Gamma*(Settings.Surface_Tension_Gamma*(Parameter_Lambda_Pascals*Adj_Crush_Pressure_He_Pascals)))/(Settings.Skin_Compression_GammaC*(Settings.Skin_Compression_GammaC*Phase_Volume_Time[i]));

            New_Allowable_Grad_He_Pascals = (B + Math.sqrt(Math.pow(B,2) - 4.0*C))/2.0;

            Allowable_Gradient_He[i] = (New_Allowable_Grad_He_Pascals/101325.0)*Settings.Depth_Per_ATM;
            
            // Debug: Detailed quadratic formula for compartment 1 He
            if (i == 0) {
                // System.out.println("\n=== CVA QUADRATIC FORMULA - HELIUM (Comp 1) ===");
                System.out.println("Adj_Crush_Pressure_He_Pascals: " + Adj_Crush_Pressure_He_Pascals + " Pa");
                System.out.println("Initial_Allowable_Grad_He_Pa: " + Initial_Allowable_Grad_He_Pa + " Pa");
                System.out.println("B = " + B + " Pa");
                System.out.println("C = " + C + " Pa²");
                System.out.println("B² = " + Math.pow(B,2));
                System.out.println("4C = " + (4.0*C));
                System.out.println("B² - 4C = " + (Math.pow(B,2) - 4.0*C));
                System.out.println("sqrt(B² - 4C) = " + Math.sqrt(Math.pow(B,2) - 4.0*C));
                System.out.println("New_Allowable_Grad_He_Pascals: " + New_Allowable_Grad_He_Pascals + " Pa");
                // System.out.println("Final Allowable_Gradient_He[0]: " + Allowable_Gradient_He[i] + " (diving units)");
            }
        }

        for(int i=0; i<16; i++)
        {
            Adj_Crush_Pressure_N2_Pascals = (Adjusted_Crushing_Pressure_N2[i]/Settings.Depth_Per_ATM) * 101325.0;

            Initial_Allowable_Grad_N2_Pa = (Initial_Allowable_Gradient_N2[i]/Settings.Depth_Per_ATM) * 101325.0;

            B = Initial_Allowable_Grad_N2_Pa + (Parameter_Lambda_Pascals*Settings.Surface_Tension_Gamma)/ (Settings.Skin_Compression_GammaC*Phase_Volume_Time[i]);

            C = (Settings.Surface_Tension_Gamma*(Settings.Surface_Tension_Gamma*(Parameter_Lambda_Pascals*Adj_Crush_Pressure_N2_Pascals)))/(Settings.Skin_Compression_GammaC*(Settings.Skin_Compression_GammaC*Phase_Volume_Time[i]));

            New_Allowable_Grad_N2_Pascals = (B + Math.sqrt(Math.pow(B,2) - 4.0*C))/2.0;

            Allowable_Gradient_N2[i] = (New_Allowable_Grad_N2_Pascals/101325.0)*Settings.Depth_Per_ATM;
            
            // Debug: Detailed quadratic formula for compartment 1 N2
            if (i == 0) {
                // System.out.println("\n=== CVA QUADRATIC FORMULA - NITROGEN (Comp 1) ===");
                System.out.println("Adj_Crush_Pressure_N2_Pascals: " + Adj_Crush_Pressure_N2_Pascals + " Pa");
                System.out.println("Initial_Allowable_Grad_N2_Pa: " + Initial_Allowable_Grad_N2_Pa + " Pa");
                System.out.println("B = " + B + " Pa");
                System.out.println("C = " + C + " Pa²");
                System.out.println("B² = " + Math.pow(B,2));
                System.out.println("4C = " + (4.0*C));
                System.out.println("B² - 4C = " + (Math.pow(B,2) - 4.0*C));
                System.out.println("sqrt(B² - 4C) = " + Math.sqrt(Math.pow(B,2) - 4.0*C));
                System.out.println("New_Allowable_Grad_N2_Pascals: " + New_Allowable_Grad_N2_Pascals + " Pa");
                // System.out.println("Final Allowable_Gradient_N2[0]: " + Allowable_Gradient_N2[i] + " (diving units)");
            }
        }
        //===============================================================================
        //     END OF SUBROUTINE
        //===============================================================================
        //RETURN
        //END
    }
    
/*C===============================================================================
C     SUBROUTINE ONSET_OF_IMPERMEABILITY
C     Purpose:  This subroutine uses the Bisection Method to find the ambient
C     pressure and gas tension at the onset of impermeability for a given
C     compartment.  Source:  "Numerical Recipes in Fortran 77",
C     Cambridge University Press, 1992.
C===============================================================================*/
    private static void ONSET_OF_IMPERMEABILITY(double Starting_Ambient_Pressure, double Ending_Ambient_Pressure, double Rate, int compartmentIndex, double heliumCompartmentPressureBeforeDescent, double nitrogenCompartmentPressureBeforeDescent)
    {
        //Just make sure that Rate has the correct sign. negative on ascent and positive on descent.
        if((Ending_Ambient_Pressure < Starting_Ambient_Pressure && Rate > 0) || (Ending_Ambient_Pressure > Starting_Ambient_Pressure && Rate < 0))
        {
            Rate = -Rate;
        }
        //IMPLICIT NONE

//  C===============================================================================
//  C     LOCAL VARIABLES
//  C===============================================================================
        double Initial_Inspired_He_Pressure;
        double Initial_Inspired_N2_Pressure, Time;
        double Helium_Rate, Nitrogen_Rate;
        double Low_Bound, High_Bound, High_Bound_Helium_Pressure;
        double High_Bound_Nitrogen_Pressure, Mid_Range_Helium_Pressure;
        double Mid_Range_Nitrogen_Pressure, Last_Diff_Change;
        double Function_at_High_Bound, Function_at_Low_Bound;
        double Mid_Range_Time, Function_at_Mid_Range, Differential_Change;
        double Mid_Range_Ambient_Pressure = 0, Gas_Tension_at_Mid_Range = 0;
        double Gradient_Onset_of_Imperm;
        double Starting_Gas_Tension, Ending_Gas_Tension;

        //float SCHREINER_EQUATION                               //function subprogram
/*C===============================================================================
C     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
C===============================================================================
float Water_Vapor_Pressure
COMMON /Block_8/ Water_Vapor_Pressure

float Gradient_Onset_of_Imperm_Atm
COMMON /Block_14/ Gradient_Onset_of_Imperm_Atm

float Constant_Pressure_Other_Gases
COMMON /Block_17/ Constant_Pressure_Other_Gases
C===============================================================================
C     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
C===============================================================================
int Mix_Number
COMMON /Block_9/ Mix_Number

float Units_Factor
COMMON /Block_16/ Units_Factor
C===============================================================================
C     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
C===============================================================================
float Helium_Time_Constant(16)
COMMON /Block_1A/ Helium_Time_Constant

float Nitrogen_Time_Constant(16)
COMMON /Block_1B/ Nitrogen_Time_Constant

float Fraction_Helium(10), Fraction_Nitrogen(10)
COMMON /Block_5/ Fraction_Helium, Fraction_Nitrogen

float Amb_Pressure_Onset_of_Imperm(16)                              //output
float Gas_Tension_Onset_of_Imperm(16)
COMMON /Block_13/ Amb_Pressure_Onset_of_Imperm, Gas_Tension_Onset_of_Imperm

float Initial_Helium_Pressure(16), Initial_Nitrogen_Pressure(16)     //input
COMMON /Block_23/ Initial_Helium_Pressure, Initial_Nitrogen_Pressure

C===============================================================================
C     CALCULATIONS
C     First convert the Gradient for Onset of Impermeability to the diving
C     pressure units that are being used
C===============================================================================*/
        Gradient_Onset_of_Imperm = Settings.Gradient_Onset_of_Imperm_Atm * Settings.Depth_Per_ATM;
/*C===============================================================================
C     ESTABLISH THE BOUNDS FOR THE ROOT SEARCH USING THE BISECTION METHOD
C     In this case, we are solving for time - the time when the ambient pressure
C     minus the gas tension will be equal to the Gradient for Onset of
C     Impermeabliity.  The low bound for time is set at zero and the high
C     bound is set at the elapsed time (segment time) it took to go from the
C     starting ambient pressure to the ending ambient pressure.  The desired
C     ambient pressure and gas tension at the onset of impermeability will
C     be found somewhere between these endpoints.  The algorithm checks to
C     make sure that the solution lies in between these bounds by first
C     computing the low bound and high bound function values.
C===============================================================================*/
        Initial_Inspired_He_Pressure = (Starting_Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentHeliumFraction();

        Initial_Inspired_N2_Pressure = (Starting_Ambient_Pressure - Water_Vapor_Pressure)*currentDive.getCurrentNitrogenFraction();

        Helium_Rate = Rate*currentDive.getCurrentHeliumFraction();
        Nitrogen_Rate = Rate*currentDive.getCurrentNitrogenFraction();
        Low_Bound = 0.0;

        High_Bound = (Ending_Ambient_Pressure - Starting_Ambient_Pressure) / Rate;

        Starting_Gas_Tension = heliumCompartmentPressureBeforeDescent + nitrogenCompartmentPressureBeforeDescent + Constant_Pressure_Other_Gases;

        Function_at_Low_Bound = Starting_Ambient_Pressure - Starting_Gas_Tension - Gradient_Onset_of_Imperm;

        High_Bound_Helium_Pressure = SCHREINER_EQUATION(Initial_Inspired_He_Pressure, Helium_Rate, High_Bound, kHe[compartmentIndex], heliumCompartmentPressureBeforeDescent);

        High_Bound_Nitrogen_Pressure = SCHREINER_EQUATION(Initial_Inspired_N2_Pressure, Nitrogen_Rate, High_Bound, kN2[compartmentIndex], nitrogenCompartmentPressureBeforeDescent);

        Ending_Gas_Tension = High_Bound_Helium_Pressure + High_Bound_Nitrogen_Pressure + Constant_Pressure_Other_Gases;

        Function_at_High_Bound = Ending_Ambient_Pressure - Ending_Gas_Tension - Gradient_Onset_of_Imperm;

        if((Function_at_High_Bound*Function_at_Low_Bound) >= 0.0)
        {
            //PRINT *,'ERROR! ROOT IS NOT WITHIN BRACKETS'
            //PAUSE
            System.err.println("ERROR! ROOT IS NOT WITHIN BRACKETS (3455)");
        }
/*C===============================================================================
C     APPLY THE BISECTION METHOD IN SEVERAL ITERATIONS UNTIL A SOLUTION WITH
C     THE DESIRED ACCURACY IS FOUND
C     Note: the program allows for up to 100 iterations.  Normally an exit will
C     be made from the loop well before that number.  If, for some reason, the
C     program exceeds 100 iterations, there will be a pause to alert the user.
C===============================================================================*/
        if(Function_at_Low_Bound < 0.0)
        {
            Time = Low_Bound;
            Differential_Change = High_Bound - Low_Bound;
        }
        else
        {
            Time = High_Bound;
            Differential_Change = Low_Bound - High_Bound;
        }
        boolean solutionNotFound = true;
//	DO J = 1, 100
        for(int J=1; J<=100; J++)
        {
            Last_Diff_Change = Differential_Change;
            Differential_Change = Last_Diff_Change*0.5;
            Mid_Range_Time = Time + Differential_Change;

            Mid_Range_Ambient_Pressure = (Starting_Ambient_Pressure + Rate*Mid_Range_Time);

            Mid_Range_Helium_Pressure = SCHREINER_EQUATION(Initial_Inspired_He_Pressure, Helium_Rate,Mid_Range_Time, kHe[compartmentIndex],heliumCompartmentPressureBeforeDescent);

            Mid_Range_Nitrogen_Pressure = SCHREINER_EQUATION(Initial_Inspired_N2_Pressure, Nitrogen_Rate,Mid_Range_Time, kN2[compartmentIndex],nitrogenCompartmentPressureBeforeDescent);

            Gas_Tension_at_Mid_Range = Mid_Range_Helium_Pressure + Mid_Range_Nitrogen_Pressure + Constant_Pressure_Other_Gases;

            Function_at_Mid_Range = Mid_Range_Ambient_Pressure - Gas_Tension_at_Mid_Range - Gradient_Onset_of_Imperm;

            if(Function_at_Mid_Range <= 0.0)
            {
                Time = Mid_Range_Time;
            }

            if((Math.abs(Differential_Change) < 1.0E-3) || (Function_at_Mid_Range == 0.0))
            {
                //GOTO 100
                solutionNotFound = false;
                break;
            }
            else
            {
                solutionNotFound = true;
            }
        }
        if(solutionNotFound)
        {
            //PRINT *,'ERROR! ROOT SEARCH EXCEEDED MAXIMUM ITERATIONS'
            //PAUSE
            System.err.println("ERROR! ROOT SEARCH EXCEEDED MAXIMUM ITERATIONS");
        }
//  C===============================================================================
//  C     When a solution with the desired accuracy is found, the program jumps out
//  C     of the loop to Line 100 and assigns the solution values for ambient
//  C     pressure and gas tension at the onset of impermeability.
//  C===============================================================================
//  100
        Amb_Pressure_Onset_of_Imperm[compartmentIndex] = Mid_Range_Ambient_Pressure;
        Gas_Tension_Onset_of_Imperm[compartmentIndex] = Gas_Tension_at_Mid_Range;
//  C===============================================================================
//  C     END OF SUBROUTINE
//  C===============================================================================
        //RETURN
        //END
    }
    
    
 
/*C===============================================================================
C     SUBROUTINE RADIUS_ROOT_FINDER
C     Purpose: This subroutine is a "fail-safe" routine that combines the
C     Bisection Method and the Newton-Raphson Method to find the desired root.
C     This hybrid algorithm takes a bisection step whenever Newton-Raphson would
C     take the solution out of bounds, or whenever Newton-Raphson is not
C     converging fast enough.  Source:  "Numerical Recipes in Fortran 77",
C     Cambridge University Press, 1992.
C===============================================================================*/
    private static double RADIUS_ROOT_FINDER(double A, double B, double C, double Low_Bound, double High_Bound)
    {
        //IMPLICIT NONE
//  C===============================================================================
//  C     LOCAL VARIABLES
//  C===============================================================================
        double Ending_Radius;
        double Function, Derivative_of_Function, Differential_Change;
        double Last_Diff_Change, Last_Ending_Radius;
        double Radius_at_Low_Bound, Radius_at_High_Bound;
        double Function_at_Low_Bound, Function_at_High_Bound;
/*C===============================================================================
C     BEGIN CALCULATIONS BY MAKING SURE THAT THE ROOT LIES WITHIN BOUNDS
C     In this case we are solving for radius in a cubic equation of the form,
C     Ar^3 - Br^2 - C = 0.  The coefficients A, B, and C were passed to this
C     subroutine as arguments.
C===============================================================================*/
        Function_at_Low_Bound = Low_Bound*(Low_Bound*(A*Low_Bound - B)) - C;

        Function_at_High_Bound = High_Bound*(High_Bound*(A*High_Bound - B)) - C;

        if((Function_at_Low_Bound > 0.0) && (Function_at_High_Bound > 0.0))
        {
                //PRINT *,'ERROR! ROOT IS NOT WITHIN BRACKETS'
                //PAUSE
                System.err.println("ERROR! ROOT IS NOT WITHIN BRACKETS (3565)");
        }
//  C===============================================================================
//  C     Next the algorithm checks for special conditions and then prepares for
//  C     the first bisection.
//  C===============================================================================
        if((Function_at_Low_Bound < 0.0) && (Function_at_High_Bound < 0.0))
        {
            //PRINT *,'ERROR! ROOT IS NOT WITHIN BRACKETS'
            //PAUSE
            System.err.println("ERROR! ROOT IS NOT WITHIN BRACKETS (3575)");
        }
        if(Function_at_Low_Bound == 0.0)
        {
            Ending_Radius = Low_Bound;
            return Ending_Radius;
        }
        else if(Function_at_High_Bound == 0.0)
        {
            Ending_Radius = High_Bound;
            return Ending_Radius;
        }
        else if(Function_at_Low_Bound < 0.0)
        {
            Radius_at_Low_Bound = Low_Bound;
            Radius_at_High_Bound = High_Bound;
        }
        else
        {
            Radius_at_High_Bound = Low_Bound;
            Radius_at_Low_Bound = High_Bound;
        }
        Ending_Radius = 0.5*(Low_Bound + High_Bound);
        Last_Diff_Change = Math.abs(High_Bound-Low_Bound);
        Differential_Change = Last_Diff_Change;
/*C===============================================================================
C     At this point, the Newton-Raphson Method is applied which uses a function
C     and its first derivative to rapidly converge upon a solution.
C     Note: the program allows for up to 100 iterations.  Normally an exit will
C     be made from the loop well before that number.  If, for some reason, the
C     program exceeds 100 iterations, there will be a pause to alert the user.
C     When a solution with the desired accuracy is found, exit is made from the
C     loop by returning to the calling program.  The last value of ending
C     radius has been assigned as the solution.
C===============================================================================*/
        Function = Ending_Radius*(Ending_Radius*(A*Ending_Radius - B)) - C;

        Derivative_of_Function = Ending_Radius*(Ending_Radius*3.0*A - 2.0*B);

        for(int i=1; i<=100; i++)
        {
            if((((Ending_Radius-Radius_at_High_Bound)*Derivative_of_Function-Function)*((Ending_Radius-Radius_at_Low_Bound)*
                    Derivative_of_Function-Function)>=0.0) || (Math.abs(2.0*Function) > (Math.abs(Last_Diff_Change*Derivative_of_Function))))
            {
                Last_Diff_Change = Differential_Change;

                Differential_Change = 0.5*(Radius_at_High_Bound - Radius_at_Low_Bound);

                Ending_Radius = Radius_at_Low_Bound + Differential_Change;
                if(Radius_at_Low_Bound == Ending_Radius)
                    return Ending_Radius;
            }
            else
            {
                Last_Diff_Change = Differential_Change;
                Differential_Change = Function/Derivative_of_Function;
                Last_Ending_Radius = Ending_Radius;
                Ending_Radius = Ending_Radius - Differential_Change;
                if(Last_Ending_Radius == Ending_Radius)
                    return Ending_Radius;
            }
            if(Math.abs(Differential_Change) < 1.0E-12)
                return Ending_Radius;
            Function = Ending_Radius*(Ending_Radius*(A*Ending_Radius - B)) - C;

            Derivative_of_Function = Ending_Radius*(Ending_Radius*3.0*A - 2.0*B);

            if(Function < 0.0)
            {
                Radius_at_Low_Bound = Ending_Radius;
            }
            else
            {
                Radius_at_High_Bound = Ending_Radius;
            }
        }
        //PRINT *,'ERROR! ROOT SEARCH EXCEEDED MAXIMUM ITERATIONS'
        //PAUSE
        System.err.println("ERROR! ROOT SEARCH EXCEEDED MAXIMUM ITERATIONS");
        return Ending_Radius;
//  C===============================================================================
//  C     END OF SUBROUTINE
//  C===============================================================================
//	END
    } 
    
    /**
     * This function is only called if the user enters a planned ascent to a depth below the surface
     * @param startingDepth
     * @param endingDepth
     * @return 
     */
    private static boolean directAscentIsSafe(double startingDepth, double endingDepth)
    {
        //Save some values that may change in this function, and reset them before we leave this function.
        double[] tempHeliumCompartmentPressure = heliumCompartmentPressure.clone();
        double[] tempNitrogenCompartmentPressure = nitrogenCompartmentPressure.clone();
        double[] tempRegenerated_Radius_He = Regenerated_Radius_He.clone();
        double[] tempRegenerated_Radius_N2 = Regenerated_Radius_N2.clone();
        double[] tempAdjusted_Crushing_Pressure_He = Adjusted_Crushing_Pressure_He.clone();
        double[] tempAdjusted_Crushing_Pressure_N2 = Adjusted_Crushing_Pressure_N2.clone();
        
        
        
        NUCLEAR_REGENERATION(currentDive.currentRunTime);
        
        CALC_INITIAL_ALLOWABLE_GRADIENT();
        
        double Depth_Start_of_Deco_Zone = CALC_START_OF_DECO_ZONE(startingDepth);            
        
        if(Depth_Start_of_Deco_Zone < endingDepth)
        {
            //absolutely safe to ascend to endingDepth without deco stops
            return true;
        }
        
        //since we are still here it means that offgassing starts deeper than endingDepth
        if(currentDive.offgassingStartsAtDepth == 0.0)
        {
            currentDive.offgassingStartsAtDepth = Depth_Start_of_Deco_Zone;
        }


        //  C===============================================================================
        //  C     TEMPORARILY ASCEND PROFILE TO THE START OF THE DECOMPRESSION ZONE, SAVE
        //  C     VARIABLES AT THIS POINT, AND INITIALIZE VARIABLES FOR CRITICAL VOLUME LOOP
        //  C     The iterative process of the VPM Critical Volume Algorithm will operate
        //  C     only in the decompression zone since it deals with excess gas volume
        //  C     released as a result of supersaturation gradients (not possible below the
        //  C     decompression zone).
        //  C===============================================================================
        double durationOfAscent = GAS_LOADINGS_ASCENT_DESCENT(startingDepth, Depth_Start_of_Deco_Zone, Settings.ascentRate);
         
        
        //  C===============================================================================
        //  C     CALCULATE INITIAL ASCENT CEILING BASED ON ALLOWABLE SUPERSATURATION
        //  C     GRADIENTS AND SET FIRST DECO STOP.  CHECK TO MAKE SURE THAT SELECTED STEP
        //  C     SIZE WILL NOT ROUND UP FIRST STOP TO A DEPTH THAT IS BELOW THE DECO ZONE.
        //  C===============================================================================
        double Ascent_Ceiling_Depth = CALC_ASCENT_CEILING();
        
        //reset the values we may have changed in this function.
        heliumCompartmentPressure  = tempHeliumCompartmentPressure.clone();
        nitrogenCompartmentPressure = tempNitrogenCompartmentPressure.clone();
        Regenerated_Radius_He = tempRegenerated_Radius_He.clone();
        Regenerated_Radius_N2 = tempRegenerated_Radius_N2.clone();
        Adjusted_Crushing_Pressure_He = tempAdjusted_Crushing_Pressure_He.clone();
        Adjusted_Crushing_Pressure_N2 = tempAdjusted_Crushing_Pressure_N2.clone();
        
        return Ascent_Ceiling_Depth < endingDepth;
    }
    
    //===============================================================================
    //     SUBROUTINE GAS_LOADINGS_SURFACE_INTERVAL
    //     Purpose: This subprogram calculates the gas loading (off-gassing) during
    //     a surface interval.
    //===============================================================================
    private static void GAS_LOADINGS_SURFACE_INTERVAL(double Surface_Interval_Time)
    {
        //IMPLICIT NONE
        //===============================================================================
        //     LOCAL VARIABLES
        //===============================================================================
        double Inspired_Helium_Pressure, Inspired_Nitrogen_Pressure;
        double Initial_Helium_Pressure, Initial_Nitrogen_Pressure;

        //double HALDANE_EQUATION                                 //function subprogram
        /*===============================================================================
        //     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Water_Vapor_Pressure
        COMMON /Block_8/ Water_Vapor_Pressure
        //===============================================================================
        //     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        //===============================================================================
        double Barometric_Pressure
        COMMON /Block_18/ Barometric_Pressure
        //===============================================================================
        //     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Helium_Time_Constant(16)
        COMMON /Block_1A/ Helium_Time_Constant

        double Nitrogen_Time_Constant(16)
        COMMON /Block_1B/ Nitrogen_Time_Constant

        double Helium_Pressure(16), Nitrogen_Pressure(16)                //both input
        COMMON /Block_3/ Helium_Pressure, Nitrogen_Pressure            //and output
        //===============================================================================
        //     CALCULATIONS
        //===============================================================================*/        
        Inspired_Helium_Pressure = 0;
        Inspired_Nitrogen_Pressure = (currentDive.surfacePressure - Water_Vapor_Pressure)*0.79;
        for(int i=0; i<16; i++)
        {
            Initial_Helium_Pressure = heliumCompartmentPressure[i];
            Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[i];
            heliumCompartmentPressure[i] = haldaneEquation(Initial_Helium_Pressure, Inspired_Helium_Pressure, kHe[i], Surface_Interval_Time);
            nitrogenCompartmentPressure[i] = haldaneEquation(Initial_Nitrogen_Pressure, Inspired_Nitrogen_Pressure, kN2[i], Surface_Interval_Time);        
        }
        //===============================================================================
        //     END OF SUBROUTINE
        //===============================================================================
        //RETURN
        //END
    }


    //===============================================================================
    //     SUBROUTINE VPM_REPETITIVE_ALGORITHM
    //     Purpose: This subprogram implements the VPM Repetitive Algorithm that was
    //     envisioned by Professor David E. Yount only months before his passing.
    //===============================================================================
    private static void VPM_REPETITIVE_ALGORITHM(double Surface_Interval_Time)
    {
        //IMPLICIT NONE
        //===============================================================================
        //     LOCAL VARIABLES
        //===============================================================================
        double Max_Actual_Gradient_Pascals;
        double Adj_Crush_Pressure_He_Pascals, Adj_Crush_Pressure_N2_Pascals;
        double Initial_Allowable_Grad_He_Pa, Initial_Allowable_Grad_N2_Pa;
        double New_Critical_Radius_He, New_Critical_Radius_N2;
        /*===============================================================================
        //     GLOBAL CONSTANTS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Surface_Tension_Gamma, Skin_Compression_GammaC
        COMMON /Block_19/ Surface_Tension_Gamma, Skin_Compression_GammaC

        double Regeneration_Time_Constant
        COMMON /Block_22/ Regeneration_Time_Constant
        //===============================================================================
        //     GLOBAL VARIABLES IN NAMED COMMON BLOCKS
        //===============================================================================
        double Units_Factor
        COMMON /Block_16/ Units_Factor
        //===============================================================================
        //     GLOBAL ARRAYS IN NAMED COMMON BLOCKS
        //===============================================================================
        double Initial_Critical_Radius_He(16)                                 //input
        double Initial_Critical_Radius_N2(16)
        COMMON /Block_6/ Initial_Critical_Radius_He, Initial_Critical_Radius_N2

        double Adjusted_Critical_Radius_He(16)                               //output
        double Adjusted_Critical_Radius_N2(16)
        COMMON /Block_7/ Adjusted_Critical_Radius_He, Adjusted_Critical_Radius_N2

        double Max_Actual_Gradient(16)                                        //input
        COMMON /Block_12/ Max_Actual_Gradient

        double Adjusted_Crushing_Pressure_He(16)                              //input
        double Adjusted_Crushing_Pressure_N2(16)
        COMMON /Block_25/ Adjusted_Crushing_Pressure_He, Adjusted_Crushing_Pressure_N2

        double Initial_Allowable_Gradient_He(16)                              //input
        double Initial_Allowable_Gradient_N2(16)
        COMMON /Block_27/ Initial_Allowable_Gradient_He, Initial_Allowable_Gradient_N2
        //===============================================================================
        //     CALCULATIONS
        //===============================================================================*/
        double Units_Factor = Settings.Depth_Per_ATM;
        for(int i=1; i<=16; i++)
        {
            Max_Actual_Gradient_Pascals = (Max_Actual_Gradient[i]/Units_Factor) * 101325.0;

            Adj_Crush_Pressure_He_Pascals = (Adjusted_Crushing_Pressure_He[i]/Units_Factor) * 101325.0;

            Adj_Crush_Pressure_N2_Pascals = (Adjusted_Crushing_Pressure_N2[i]/Units_Factor) * 101325.0;

            Initial_Allowable_Grad_He_Pa = (Initial_Allowable_Gradient_He[i]/Units_Factor) * 101325.0;

            Initial_Allowable_Grad_N2_Pa = (Initial_Allowable_Gradient_N2[i]/Units_Factor) * 101325.0;

            if(Max_Actual_Gradient[i] > Initial_Allowable_Gradient_N2[i])
            {
                New_Critical_Radius_N2 = ((2.0*Settings.Surface_Tension_Gamma*(Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma))) / (Max_Actual_Gradient_Pascals*Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma*Adj_Crush_Pressure_N2_Pascals);

                Adjusted_Critical_Radius_N2[i] = Initial_Critical_Radius_N2[i] + (Initial_Critical_Radius_N2[i]-New_Critical_Radius_N2)*Math.exp(-Surface_Interval_Time/Settings.Regeneration_Time_Constant);
            }
            else
            {
                Adjusted_Critical_Radius_N2[i] = Initial_Critical_Radius_N2[i];
            }

            if(Max_Actual_Gradient[i] > Initial_Allowable_Gradient_He[i])
            {
                New_Critical_Radius_He = ((2.0*Settings.Surface_Tension_Gamma*(Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma))) / (Max_Actual_Gradient_Pascals*Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma*Adj_Crush_Pressure_He_Pascals);

                Adjusted_Critical_Radius_He[i] = Initial_Critical_Radius_He[i] + (Initial_Critical_Radius_He[i]-New_Critical_Radius_He)* Math.exp(-Surface_Interval_Time/Settings.Regeneration_Time_Constant);
            }
            else
            {
                Adjusted_Critical_Radius_He[i] = Initial_Critical_Radius_He[i];
            }
        }
        //===============================================================================
        //     END OF SUBROUTINE
        //===============================================================================
        //RETURN
        //END
    }
    
    
/*C===============================================================================
C     SUBROUTINE VPM_ALTITUDE_DIVE_ALGORITHM
C     Purpose:  This subprogram updates gas loadings and adjusts critical radii
C     (as required) based on whether or not diver is acclimatized at altitude or
C     makes an ascent to altitude before the dive.
C===============================================================================
    */
    private static void VPM_ALTITUDE_DIVE_ALGORITHM()
    {
        boolean Diver_Acclimatized = Settings.acclimatizedAtDiveAltitude;

        double Altitude_of_Dive = Settings.altitude;
        double Starting_Acclimatized_Altitude = Settings.altitudeAcclimatized;
        double Ascent_to_Altitude_Hours = Settings.hoursToAltitude;
        double Hours_at_Altitude_Before_Dive = Settings.hoursAtAltitudeBeforeDive;
        
        double Ascent_to_Altitude_Time, Time_at_Altitude_Before_Dive;
        double Starting_Ambient_Pressure, Ending_Ambient_Pressure;
        double Initial_Inspired_N2_Pressure, Rate, Nitrogen_Rate;
        double Inspired_Nitrogen_Pressure, Initial_Nitrogen_Pressure;
        double Compartment_Gradient, Compartment_Gradient_Pascals;
        double Gradient_He_Bubble_Formation, Gradient_N2_Bubble_Formation;
        double New_Critical_Radius_He, New_Critical_Radius_N2;
        double Ending_Radius_He, Ending_Radius_N2;
        double Regenerated_Radius_He_Tmp, Regenerated_Radius_N2_Tmp;

        double Barometric_Pressure;

        Ascent_to_Altitude_Time = Ascent_to_Altitude_Hours * 60.0;
        Time_at_Altitude_Before_Dive = Hours_at_Altitude_Before_Dive*60.0;

        if(Diver_Acclimatized) 
        {
            if(currentDive.missionDiveNumber == 1)
            {
                Barometric_Pressure = Util.calculateBarometricPressure(Altitude_of_Dive);
                for(int i=0;i<16;i++)
                {
                    Adjusted_Critical_Radius_N2[i] = Initial_Critical_Radius_N2[i];
                    Adjusted_Critical_Radius_He[i] = Initial_Critical_Radius_He[i];
                    heliumCompartmentPressure[i] = 0.0;
                    nitrogenCompartmentPressure[i] = (Barometric_Pressure - Water_Vapor_Pressure)*0.79;
                }
            }
        }
        else      
        {    
            Barometric_Pressure = Util.calculateBarometricPressure(Starting_Acclimatized_Altitude);
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
            Barometric_Pressure = Util.calculateBarometricPressure(Altitude_of_Dive);
            Ending_Ambient_Pressure = Barometric_Pressure;
            Initial_Inspired_N2_Pressure = (Starting_Ambient_Pressure - Water_Vapor_Pressure)*0.79;
            Rate = (Ending_Ambient_Pressure - Starting_Ambient_Pressure) / Ascent_to_Altitude_Time;
            Nitrogen_Rate = Rate*0.79;

            for(int i=0;i<16;i++)
            {
                Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[i];

                nitrogenCompartmentPressure[i] = SCHREINER_EQUATION(Initial_Inspired_N2_Pressure, Nitrogen_Rate,Ascent_to_Altitude_Time, kN2[i],Initial_Nitrogen_Pressure);

                Compartment_Gradient = (nitrogenCompartmentPressure[i] + Constant_Pressure_Other_Gases) - Ending_Ambient_Pressure;

                Compartment_Gradient_Pascals = (Compartment_Gradient / Settings.Depth_Per_ATM) * 101325.0;

                Gradient_He_Bubble_Formation = ((2.0*Settings.Surface_Tension_Gamma*(Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma)) / (Initial_Critical_Radius_He[i]*Settings.Skin_Compression_GammaC));

                if (Compartment_Gradient_Pascals > Gradient_He_Bubble_Formation) 
                {
                    New_Critical_Radius_He = ((2.0*Settings.Surface_Tension_Gamma*(Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma))) / (Compartment_Gradient_Pascals*Settings.Skin_Compression_GammaC);

                    Adjusted_Critical_Radius_He[i] = Initial_Critical_Radius_He[i] + (Initial_Critical_Radius_He[i]-New_Critical_Radius_He)*Math.exp(-Time_at_Altitude_Before_Dive/Settings.Regeneration_Time_Constant);

                    Initial_Critical_Radius_He[i] = Adjusted_Critical_Radius_He[i];
                }
                else
                {
                    Ending_Radius_He = 1.0/(Compartment_Gradient_Pascals/ (2.0*(Settings.Surface_Tension_Gamma-Settings.Skin_Compression_GammaC)) + 1.0/Initial_Critical_Radius_He[i]);

                    Regenerated_Radius_He_Tmp = Initial_Critical_Radius_He[i] + (Ending_Radius_He - Initial_Critical_Radius_He[i]) * Math.exp(-Time_at_Altitude_Before_Dive/Settings.Regeneration_Time_Constant);

                    Initial_Critical_Radius_He[i] = Regenerated_Radius_He_Tmp;

                    Adjusted_Critical_Radius_He[i] = Initial_Critical_Radius_He[i];
                }

                Gradient_N2_Bubble_Formation = ((2.0*Settings.Surface_Tension_Gamma*(Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma)) /(Initial_Critical_Radius_N2[i]*Settings.Skin_Compression_GammaC));

                if (Compartment_Gradient_Pascals > Gradient_N2_Bubble_Formation) 
                {
                    New_Critical_Radius_N2 = ((2.0*Settings.Surface_Tension_Gamma*(Settings.Skin_Compression_GammaC - Settings.Surface_Tension_Gamma))) /(Compartment_Gradient_Pascals*Settings.Skin_Compression_GammaC);

                    Adjusted_Critical_Radius_N2[i] =Initial_Critical_Radius_N2[i] + (Initial_Critical_Radius_N2[i]-New_Critical_Radius_N2) * Math.exp(-Time_at_Altitude_Before_Dive/Settings.Regeneration_Time_Constant);

                    Initial_Critical_Radius_N2[i] = Adjusted_Critical_Radius_N2[i];
                }
                else
                {
                    Ending_Radius_N2 = 1.0/(Compartment_Gradient_Pascals/(2.0*(Settings.Surface_Tension_Gamma-Settings.Skin_Compression_GammaC)) + 1.0/Initial_Critical_Radius_N2[i]);

                    Regenerated_Radius_N2_Tmp = Initial_Critical_Radius_N2[i] + (Ending_Radius_N2 - Initial_Critical_Radius_N2[i]) * Math.exp(-Time_at_Altitude_Before_Dive/Settings.Regeneration_Time_Constant);

                    Initial_Critical_Radius_N2[i] = Regenerated_Radius_N2_Tmp;

                    Adjusted_Critical_Radius_N2[i] = Initial_Critical_Radius_N2[i];
                }
            }
            Inspired_Nitrogen_Pressure = (Barometric_Pressure - Water_Vapor_Pressure)*0.79;
            for(int i=0;i<16;i++)
            {
                Initial_Nitrogen_Pressure = nitrogenCompartmentPressure[i];

                nitrogenCompartmentPressure[i] = HALDANE_EQUATION(Initial_Nitrogen_Pressure, Inspired_Nitrogen_Pressure,kN2[i], Time_at_Altitude_Before_Dive);
            }
        }
    }
}