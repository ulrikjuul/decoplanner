import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Perfect alignment with DecoPlanner using exact iterative algorithm from debug output
 */
public class PerfectAlignmentCLI {
    
    // ZH-L16C coefficients (exact from DecoPlanner)
    private static final double[] N2_A = {11.696, 10.0, 8.618, 7.562, 6.2, 5.043, 4.41, 4.0, 3.75, 3.5, 3.295, 3.065, 2.835, 2.61, 2.48, 2.327};
    private static final double[] N2_B = {0.5578, 0.6514, 0.7222, 0.7825, 0.8126, 0.8434, 0.8693, 0.891, 0.9092, 0.9222, 0.9319, 0.9403, 0.9477, 0.9544, 0.9602, 0.9653};
    private static final double[] HE_A = {16.189, 13.83, 11.919, 10.458, 9.22, 8.205, 7.305, 6.502, 5.95, 5.545, 5.333, 5.189, 5.181, 5.176, 5.172, 5.119};
    private static final double[] HE_B = {0.477, 0.5747, 0.6527, 0.7223, 0.7582, 0.7957, 0.8279, 0.8553, 0.8757, 0.8903, 0.8997, 0.9073, 0.9122, 0.9171, 0.9217, 0.9267};
    private static final double[] N2_HALFTIME = {5.0, 8.0, 12.5, 18.5, 27.0, 38.3, 54.3, 77.0, 109.0, 146.0, 187.0, 239.0, 305.0, 390.0, 498.0, 635.0};
    private static final double[] HE_HALFTIME = {1.88, 3.02, 4.72, 6.99, 10.21, 14.48, 20.53, 29.11, 41.20, 55.19, 70.69, 90.34, 115.29, 147.42, 188.24, 240.03};
    
    // Settings (exact from DecoPlanner debug)
    private static final double SURFACE_PRESSURE = 10.1325; // msw
    private static final double WATER_VAPOR_PRESSURE = 0.627; // msw
    private static final int DESCENT_RATE = 10; // m/min
    private static final int ASCENT_RATE = 9; // m/min
    private static final int DECO_STOP_INTERVAL = 3; // m
    private static final double MIN_DECO_STOP_TIME = 1.0; // min
    
    // Tissue compartments
    private static double[] n2Pressure = new double[16];
    private static double[] hePressure = new double[16];
    
    // Gas management (matching DecoPlanner's exact structure)
    private static List<Gas> gases = new ArrayList<>();
    private static int activeGasID = 0;
    
    // EXACT DecoPlanner variable (line 97 in BuhlmannDeco.java)
    private static int currentMinimumDecoStopDuration;
    
    public static void main(String[] args) {
        if (args.length < 6) {
            System.out.println("Usage: java PerfectAlignmentCLI <depth_m> <time_min> <O2/He> <decoGases> <gfLow> <gfHigh>");
            System.out.println("Example: java PerfectAlignmentCLI 51 25 21/35 50@21 20 85");
            System.out.println("Example: java PerfectAlignmentCLI 100 25 8/85 18@21,99@6 20 85");
            System.exit(1);
        }
        
        // Parse arguments
        double depth = Double.parseDouble(args[0]);
        double time = Double.parseDouble(args[1]);
        String[] bottomGas = args[2].split("/");
        double bottomO2 = Double.parseDouble(bottomGas[0]);
        double bottomHe = bottomGas.length > 1 ? Double.parseDouble(bottomGas[1]) : 0;
        String decoGasesStr = args[3];
        double gfLow = Double.parseDouble(args[4]) / 100.0;
        double gfHigh = Double.parseDouble(args[5]) / 100.0;
        
        // Set up gas list exactly like DecoPlanner
        gases.clear();
        activeGasID = 0;
        
        // EXACT initialization from BuhlmannDeco.java line 625
        currentMinimumDecoStopDuration = (int)Math.round(MIN_DECO_STOP_TIME);
        
        // Add bottom gas (DIVE_GAS)
        gases.add(new Gas(bottomO2, bottomHe, Gas.DIVE_GAS, (int)depth));
        
        // Parse and add deco gases (format: "50@21,99@6" or just "50@21")
        if (!decoGasesStr.equals("0")) {
            String[] decoGasArray = decoGasesStr.split(",");
            for (String decoGasStr : decoGasArray) {
                String[] parts = decoGasStr.split("@");
                if (parts.length == 2) {
                    String[] gasParts = parts[0].split("/");
                    double decoO2 = Double.parseDouble(gasParts[0]);
                    double decoHe = gasParts.length > 1 ? Double.parseDouble(gasParts[1]) : 0;
                    int switchDepth = Integer.parseInt(parts[1]);
                    gases.add(new Gas(decoO2, decoHe, Gas.DECO_GAS, switchDepth));
                }
            }
        }
        
        System.out.println("=== DECOPLANNER DEBUG LOG ===");
        System.out.println("Generated: " + new Date());
        System.out.println();
        System.out.println("=== SETTINGS ===");
        System.out.println("ZHL-16C: true");
        System.out.println("Descent Rate: " + DESCENT_RATE + " m/min");
        System.out.println("Ascent Rate: " + ASCENT_RATE + " m/min");
        System.out.println("Gradient Factor Low: " + (int)(gfLow*100) + "%");
        System.out.println("Gradient Factor High: " + (int)(gfHigh*100) + "%");
        System.out.println("Water Vapor Pressure: " + WATER_VAPOR_PRESSURE + " msw");
        System.out.println();
        
        // Initialize tissues at surface
        double inspiredN2 = (SURFACE_PRESSURE - WATER_VAPOR_PRESSURE) * 0.79;
        for (int i = 0; i < 16; i++) {
            n2Pressure[i] = inspiredN2;
            hePressure[i] = 0;
        }
        
        double runtime = 0;
        double currentDepth = 0;
        
        // DESCENT
        double descentTime = depth / DESCENT_RATE;
        System.out.printf("Processing DESCENT: 0.0m to %.1fm at %d m/min\n", depth, DESCENT_RATE);
        
        // Calculate tissue loading during descent
        Gas currentGas = gases.get(activeGasID);
        loadTissuesDescent(0, depth, descentTime, currentGas.oxygenFraction, currentGas.heliumFraction);
        runtime += descentTime;
        currentDepth = depth;
        
        // BOTTOM TIME
        double bottomTime = time - descentTime; // Actual time at bottom (excluding descent)
        double totalTimeAtDepth = time; // Total time including descent (as per DecoPlanner convention)
        System.out.printf("Actual bottom time at %.1fm: %.2f min (total time: %.2f min)\n", depth, bottomTime, totalTimeAtDepth);
        
        // Calculate tissue loading at bottom
        loadTissuesConstant(depth, bottomTime, currentGas.oxygenFraction, currentGas.heliumFraction);
        runtime += bottomTime;
        
        // FIND FIRST DECO STOP
        double firstStopDepth = findFirstStop(gfLow);
        System.out.printf("\nFirst stop depth (using GF Low %.2f): %.1fm\n", gfLow, firstStopDepth);
        
        // Calculate gradient factor slope
        double gfSlope = (gfHigh - gfLow) / (0 - firstStopDepth);
        System.out.printf("GF Slope: %.4f\n\n", gfSlope);
        
        // ASCENT TO FIRST STOP
        double ascentTime = (currentDepth - firstStopDepth) / ASCENT_RATE;
        currentGas = gases.get(activeGasID);
        loadTissuesAscent(currentDepth, firstStopDepth, ascentTime, currentGas.oxygenFraction, currentGas.heliumFraction);
        runtime += ascentTime;
        currentDepth = firstStopDepth;
        
        // DECOMPRESSION STOPS
        System.out.println("=== DECOMPRESSION SCHEDULE ===");
        List<DecoStop> decoStops = new ArrayList<>();
        boolean firstStop = true;  // Track first stop for special handling
        
        for (double stopDepth = firstStopDepth; stopDepth > 0; stopDepth -= DECO_STOP_INTERVAL) {
            double nextStopDepth = Math.max(0, stopDepth - DECO_STOP_INTERVAL);
            
            // Calculate GF for NEXT stop (critical!)
            double gfForNextStop = gfLow + gfSlope * (nextStopDepth - firstStopDepth);
            
            System.out.printf("### Calculating stop at %.1fm ###\n", stopDepth);
            System.out.printf("Next Stop: %.1f, GF Slope: %.4f\n", nextStopDepth, gfSlope);
            System.out.printf("GF for next stop: %.3f (set as current GF)\n\n", gfForNextStop);
            
            // EXACT DECOPLANENR GAS SWITCHING LOGIC (lines 1735-1770)
            currentGas = gases.get(activeGasID);
            // Loop through all gases and see if we need to switch to any of them
            for (int gasID = 0; gasID < gases.size(); gasID++) {
                Gas decoGas = gases.get(gasID);
                // Check that:
                // 1) Current depth is less or equal to the switchdepth of the gas we're now checking
                // 2) The switchdepth of the gas we're now checking is less/shallower than the switchdepth of the gas we're currently using
                // 3) The gas we're checking is a deco gas
                // 4) The gas we're now checking is not the one we're already using
                if (stopDepth <= decoGas.switchDepth && decoGas.gasType == Gas.DECO_GAS && activeGasID != gasID) {
                    // Now we have to check that, IF we are on a deco gas already, make sure the switch depth of the new gas is less than the one we are currently on
                    if (currentGas.gasType == Gas.DIVE_GAS || (currentGas.gasType == Gas.DECO_GAS && decoGas.switchDepth < currentGas.switchDepth)) {
                        // Switch to this deco gas
                        activeGasID = gasID;
                        currentGas = gases.get(activeGasID);
                        System.out.printf("Gas switch: Switching to %s at %.1fm\n", currentGas.getGasText(), stopDepth);
                        
                        // EXACT DecoPlanner logic from lines 1802-1810
                        if(decoGas.minimumDecoStopTime > 0)
                        {
                            currentMinimumDecoStopDuration = decoGas.minimumDecoStopTime;
                        }
                        else
                        {
                            //If this deco gas has not defined a minimum stop time, we use the default minimum time, from Settings.
                            currentMinimumDecoStopDuration = (int)Math.round(MIN_DECO_STOP_TIME);
                        }
                    }
                }
            }
            
            // Use current active gas
            double gasO2Fraction = currentGas.oxygenFraction;
            double gasHeFraction = currentGas.heliumFraction;
            String gasName = currentGas.getGasText();
            
            // Calculate stop time using exact iterative method
            double stopTime = calculateStopTime(stopDepth, nextStopDepth, gfForNextStop, 
                                               gasO2Fraction, gasHeFraction, runtime);
            
            // EXACT DecoPlanner first stop special handling (lines 1867-1887)
            double displayStopTime;
            if(firstStop)
            {
                //We won't report the EXACT times for the first stop, since the DECOMPRESSION_STOP-function adjusted the stopDuration to end up on a whole 
                // multiplier of the Minimum_Deco_Stop_Time. 
                //So for the first deco stop we set endRunTime to follow the real schedule, but we adjust the startRunTime based on the Minimum_Deco_Stop_Time
                
                //Now adjust the stopDuration, just to make for a nice-looking (rounded values) table
                double adjustedStopDuration;
                //BUT, ONLY IF it's NOT already a whole number
                if(stopTime % 1 == 0)
                {
                    adjustedStopDuration = stopTime;
                }
                else
                {
                    adjustedStopDuration = Math.round((stopTime/currentMinimumDecoStopDuration) + 0.5) * currentMinimumDecoStopDuration;
                }
                displayStopTime = roundToOneDecimal(adjustedStopDuration);
                firstStop = false;  // No longer first stop
            }
            else
            {
                // For subsequent stops, include ascent time in display (exact from lines 1888-1893)
                double currentAscentTime = DECO_STOP_INTERVAL / (double)ASCENT_RATE;
                displayStopTime = roundToOneDecimal(stopTime + currentAscentTime);
            }
            
            runtime += stopTime;
            
            // Ascend to next stop
            if (nextStopDepth > 0) {
                ascentTime = DECO_STOP_INTERVAL / (double)ASCENT_RATE;
                loadTissuesAscent(stopDepth, nextStopDepth, ascentTime, gasO2Fraction, gasHeFraction);
                runtime += ascentTime;
            }
            
            decoStops.add(new DecoStop(stopDepth, displayStopTime, gasName));
        }
        
        // Final ascent to surface
        ascentTime = DECO_STOP_INTERVAL / (double)ASCENT_RATE;
        runtime += ascentTime;
        
        // Round runtime to match DecoPlanner's display
        double displayRuntime = Math.round(runtime);
        
        // Output results using DecoPlanner format
        System.out.println("\nDecompression Schedule:");
        System.out.println((int)depth + "m for " + (int)Math.round(totalTimeAtDepth) + " min");
        
        double totalDecoTime = 0;
        for (DecoStop stop : decoStops) {
            // Round to one decimal like DecoPlanner, then format like DecoPlanner
            double roundedTime = roundToOneDecimal(stop.time);
            String formattedTime = formatLikeDecoPlanner(roundedTime);
            System.out.println((int)stop.depth + "m for " + formattedTime + " min");
            totalDecoTime += stop.time;
        }
        
        System.out.println();
        System.out.printf("Final Runtime: %.1f min (rounded: %.0f min)\n", runtime, displayRuntime);
        System.out.printf("Total Deco Duration: %.1f min\n", totalDecoTime);
        System.out.printf("Total Ascent Duration: %.1f min\n", totalDecoTime + (depth - firstStopDepth) / ASCENT_RATE + decoStops.size() * DECO_STOP_INTERVAL / (double)ASCENT_RATE);
    }
    
    private static double calculateStopTime(double stopDepth, double nextStopDepth, 
                                           double gradientFactor, double gasO2, double gasHe,
                                           double currentRuntime) {
        System.out.println("=== DECOMPRESSION_STOP START ===");
        System.out.printf("Stop Depth: %.1f, Step Size: %.1f\n", stopDepth, (double)DECO_STOP_INTERVAL);
        System.out.printf("Current GF: %.3f\n", gradientFactor);
        System.out.printf("Current Runtime: %.2f\n", currentRuntime);
        System.out.printf("Gas: O2=%.1f%% He=%.1f%%\n", gasO2*100, gasHe*100);
        
        double segmentTime = 0;
        double tempSegmentTime = 0;
        int iteration = 0;
        
        // Make a copy of current tissue state
        double[] n2Original = Arrays.copyOf(n2Pressure, 16);
        double[] heOriginal = Arrays.copyOf(hePressure, 16);
        
        // EXACT DecoPlanner algorithm from BuhlmannDeco.java lines 2670-2677
        double Last_Run_Time = currentRuntime;
        double Round_Up_Operation = Math.floor(Last_Run_Time) + currentMinimumDecoStopDuration;
        segmentTime = Round_Up_Operation - Last_Run_Time;
        tempSegmentTime = segmentTime;
        
        boolean doLoop;
        do {
            doLoop = false;
            iteration++;
            
            // Load tissue compartments for iteration using TOTAL time (tempSegmentTime)
            for (int i = 0; i < 16; i++) {
                n2Pressure[i] = haldaneEquation(n2Original[i], 
                    (SURFACE_PRESSURE + stopDepth - WATER_VAPOR_PRESSURE) * (1 - gasO2 - gasHe), 
                    Math.log(2) / N2_HALFTIME[i], tempSegmentTime);
                hePressure[i] = haldaneEquation(heOriginal[i], 
                    (SURFACE_PRESSURE + stopDepth - WATER_VAPOR_PRESSURE) * gasHe, 
                    Math.log(2) / HE_HALFTIME[i], tempSegmentTime);
            }
            
            // Calculate ceiling
            double ceiling = calculateCeiling(gradientFactor);
            double roundedCeiling = roundToTwoDecimals(ceiling);
            
            System.out.printf("  Iteration %d: Segment_Time=%.2f, Temp_Segment_Time=%.2f\n", 
                            iteration, segmentTime, tempSegmentTime);
            System.out.printf("    Ceiling=%.3f, Rounded=%.2f, Next_Stop=%.1f\n", 
                            ceiling, roundedCeiling, nextStopDepth);
            
            // Show leading compartments
            for (int i = 0; i < 3; i++) {
                System.out.printf("    Comp %d: N2=%.3f, He=%.3f, Total=%.3f\n", 
                                i+1, n2Pressure[i], hePressure[i], n2Pressure[i] + hePressure[i]);
            }
            
            // EXACT DecoPlanner ceiling check logic from line 2743
            if (roundedCeiling > nextStopDepth) {
                // EXACT DecoPlanner lines 2746-2749
                segmentTime = currentMinimumDecoStopDuration;  // currentMinimumDecoStopDuration
                double Time_Counter = tempSegmentTime;
                tempSegmentTime = Time_Counter + currentMinimumDecoStopDuration;  // currentMinimumDecoStopDuration
                doLoop = true;
                System.out.println("    Need more time - continuing iterations");
            }
            
            // DecoPlanner has no iteration limit - removed to match exactly
        } while (doLoop);
        
        System.out.println("=== DECOMPRESSION_STOP END ===");
        System.out.printf("Total iterations: %d\n", iteration);
        System.out.printf("Returning stop time: %.2f min\n\n", tempSegmentTime);
        
        // Restore final tissue state exactly like DecoPlanner
        n2Pressure = Arrays.copyOf(n2Original, 16);
        hePressure = Arrays.copyOf(heOriginal, 16);
        loadTissuesConstant(stopDepth, tempSegmentTime, gasO2, gasHe);
        
        return tempSegmentTime;
    }
    
    private static double findFirstStop(double gfLow) {
        // Find the ceiling depth where we must stop
        double ceiling = calculateCeiling(gfLow);
        
        // Round UP to next stop interval
        if (ceiling > 0) {
            return Math.ceil(ceiling / DECO_STOP_INTERVAL) * DECO_STOP_INTERVAL;
        }
        return 0;
    }
    
    private static double calculateCeiling(double gradientFactor) {
        double maxCeiling = 0;
        
        for (int i = 0; i < 16; i++) {
            double totalPressure = n2Pressure[i] + hePressure[i];
            
            // Calculate tolerated ambient pressure
            double a = (n2Pressure[i] * N2_A[i] + hePressure[i] * HE_A[i]) / totalPressure;
            double b = (n2Pressure[i] * N2_B[i] + hePressure[i] * HE_B[i]) / totalPressure;
            
            // Apply gradient factor
            double toleratedPressure = (totalPressure - a * gradientFactor) / (gradientFactor / b - gradientFactor + 1);
            double ceiling = toleratedPressure - SURFACE_PRESSURE;
            
            if (ceiling > maxCeiling) {
                maxCeiling = ceiling;
            }
        }
        
        return maxCeiling;
    }
    
    private static void loadTissuesConstant(double depth, double time, double gasO2, double gasHe) {
        double ambientPressure = SURFACE_PRESSURE + depth;
        double inspiredN2 = (ambientPressure - WATER_VAPOR_PRESSURE) * (1 - gasO2 - gasHe);
        double inspiredHe = (ambientPressure - WATER_VAPOR_PRESSURE) * gasHe;
        
        for (int i = 0; i < 16; i++) {
            // Haldane equation
            n2Pressure[i] = inspiredN2 + (n2Pressure[i] - inspiredN2) * 
                           Math.exp(-Math.log(2) * time / N2_HALFTIME[i]);
            hePressure[i] = inspiredHe + (hePressure[i] - inspiredHe) * 
                           Math.exp(-Math.log(2) * time / HE_HALFTIME[i]);
        }
    }
    
    private static void loadTissuesDescent(double startDepth, double endDepth, double time, 
                                          double gasO2, double gasHe) {
        double rate = (endDepth - startDepth) / time;
        
        for (int i = 0; i < 16; i++) {
            double n2k = Math.log(2) / N2_HALFTIME[i];
            double hek = Math.log(2) / HE_HALFTIME[i];
            
            double startPressure = SURFACE_PRESSURE + startDepth;
            double endPressure = SURFACE_PRESSURE + endDepth;
            
            double inspiredN2Start = (startPressure - WATER_VAPOR_PRESSURE) * (1 - gasO2 - gasHe);
            double inspiredN2End = (endPressure - WATER_VAPOR_PRESSURE) * (1 - gasO2 - gasHe);
            double inspiredHeStart = (startPressure - WATER_VAPOR_PRESSURE) * gasHe;
            double inspiredHeEnd = (endPressure - WATER_VAPOR_PRESSURE) * gasHe;
            
            // Schreiner equation
            double n2Rate = (inspiredN2End - inspiredN2Start) / time;
            n2Pressure[i] = inspiredN2Start + n2Rate * (time - 1/n2k) - 
                          (inspiredN2Start - n2Pressure[i] - n2Rate/n2k) * Math.exp(-n2k * time);
            
            double heRate = (inspiredHeEnd - inspiredHeStart) / time;
            hePressure[i] = inspiredHeStart + heRate * (time - 1/hek) - 
                          (inspiredHeStart - hePressure[i] - heRate/hek) * Math.exp(-hek * time);
        }
    }
    
    private static void loadTissuesAscent(double startDepth, double endDepth, double time, 
                                         double gasO2, double gasHe) {
        loadTissuesDescent(startDepth, endDepth, time, gasO2, gasHe); // Same math, opposite direction
    }
    
    // Haldane equation for tissue loading (matches DecoPlanner)
    private static double haldaneEquation(double initialPressure, double inspiredPressure, double k, double time) {
        return inspiredPressure + (initialPressure - inspiredPressure) * Math.exp(-k * time);
    }
    
    // EXACT DecoPlanner rounding method (Util.roundToTwoDecimals)
    private static double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
    
    // EXACT DecoPlanner rounding method (Util.roundToOneDecimal)
    private static double roundToOneDecimal(double val) {
        double temp = Math.rint(val * 10);
        return temp / 10;
    }
    
    // EXACT DecoPlanner formatting method (Util.format)
    private static String formatLikeDecoPlanner(double d) {
        if(d == (long) d)
            return String.format("%d",(long)d);
        else
            return String.format("%s",d);
    }
    
    static class Gas {
        public static final int DIVE_GAS = 1;
        public static final int DECO_GAS = 2;
        
        public int gasType;
        public double oxygenFraction;
        public double heliumFraction;
        public double nitrogenFraction;
        public int switchDepth;
        public int minimumDecoStopTime;
        
        public Gas(double oxygenPercent, double heliumPercent, int gasType, int switchDepth) {
            this.gasType = gasType;
            this.oxygenFraction = oxygenPercent / 100.0;
            this.heliumFraction = heliumPercent / 100.0;
            this.nitrogenFraction = 1.0 - this.oxygenFraction - this.heliumFraction;
            this.switchDepth = switchDepth;
            this.minimumDecoStopTime = 0;
        }
        
        public String getGasText() {
            if (heliumFraction > 0) {
                return String.format("%d/%d", (int)(oxygenFraction*100), (int)(heliumFraction*100));
            } else {
                return String.format("EAN%d", (int)(oxygenFraction*100));
            }
        }
    }
    
    static class DecoStop {
        double depth;
        double time;
        String gas;
        
        DecoStop(double depth, double time, String gas) {
            this.depth = depth;
            this.time = time;
            this.gas = gas;
        }
    }
}