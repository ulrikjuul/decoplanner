#!/usr/bin/env node

/**
 * JavaScript version of PerfectAlignmentCLI.java
 * 100% aligned with Java implementation for Buhlmann ZH-L16C
 * 
 * Usage: node buhlmann-perfect-alignment-cli.js <depth_m> <time_min> <O2/He> <decoGases> <gfLow> <gfHigh>
 * Example: node buhlmann-perfect-alignment-cli.js 51 25 21/35 50@21 40 85
 */

// EXACT ZH-L16C coefficients from DecoPlanner (PerfectAlignmentCLI.java lines 11-16)
const N2_A = [11.696, 10.0, 8.618, 7.562, 6.2, 5.043, 4.41, 4.0, 3.75, 3.5, 3.295, 3.065, 2.835, 2.61, 2.48, 2.327];
const N2_B = [0.5578, 0.6514, 0.7222, 0.7825, 0.8126, 0.8434, 0.8693, 0.891, 0.9092, 0.9222, 0.9319, 0.9403, 0.9477, 0.9544, 0.9602, 0.9653];
const HE_A = [16.189, 13.83, 11.919, 10.458, 9.22, 8.205, 7.305, 6.502, 5.95, 5.545, 5.333, 5.189, 5.181, 5.176, 5.172, 5.119];
const HE_B = [0.477, 0.5747, 0.6527, 0.7223, 0.7582, 0.7957, 0.8279, 0.8553, 0.8757, 0.8903, 0.8997, 0.9073, 0.9122, 0.9171, 0.9217, 0.9267];
const N2_HALFTIME = [5.0, 8.0, 12.5, 18.5, 27.0, 38.3, 54.3, 77.0, 109.0, 146.0, 187.0, 239.0, 305.0, 390.0, 498.0, 635.0];
const HE_HALFTIME = [1.88, 3.02, 4.72, 6.99, 10.21, 14.48, 20.53, 29.11, 41.20, 55.19, 70.69, 90.34, 115.29, 147.42, 188.24, 240.03];

// Settings (exact from DecoPlanner debug) - lines 19-25
const SURFACE_PRESSURE = 10.1325; // msw
const WATER_VAPOR_PRESSURE = 0.627; // msw
const DESCENT_RATE = 10; // m/min
const ASCENT_RATE = 9; // m/min
const DECO_STOP_INTERVAL = 3; // m
const MIN_DECO_STOP_TIME = 1.0; // min

// Tissue compartments - lines 27-28
let n2Pressure = new Array(16);
let hePressure = new Array(16);

// Gas management (matching DecoPlanner's exact structure) - lines 31-32
let gases = [];
let activeGasID = 0;

// EXACT DecoPlanner variable (line 35)
let currentMinimumDecoStopDuration;

// Gas class - EXACT from lines 445-484
class Gas {
    static DIVE_GAS = 1;
    static DECO_GAS = 2;
    
    constructor(oxygenPercent, heliumPercent, gasType, switchDepth) {
        this.gasType = gasType;
        this.oxygenFraction = oxygenPercent / 100.0;
        this.heliumFraction = heliumPercent / 100.0;
        this.nitrogenFraction = 1.0 - this.oxygenFraction - this.heliumFraction;
        this.switchDepth = switchDepth;
        this.minimumDecoStopTime = 0;
    }
    
    getGasText() {
        if (this.heliumFraction > 0) {
            return `${Math.round(this.oxygenFraction * 100)}/${Math.round(this.heliumFraction * 100)}`;
        } else {
            return `EAN${Math.round(this.oxygenFraction * 100)}`;
        }
    }
}

// DecoStop class - EXACT from lines 474-484
class DecoStop {
    constructor(depth, time, gas) {
        this.depth = depth;
        this.time = time;
        this.gas = gas;
    }
}

// EXACT DecoPlanner rounding methods (lines 422-443)
function roundToTwoDecimals(value) {
    if (!isFinite(value)) {
        return value;
    }
    return Math.round(value * 100) / 100;
}

function roundToOneDecimal(val) {
    return Math.round(val * 10) / 10;
}

function formatLikeDecoPlanner(d) {
    if (d === Math.floor(d)) {
        return Math.floor(d).toString();
    } else {
        return d.toString();
    }
}

// Haldane equation - EXACT from line 417
function haldaneEquation(initialPressure, inspiredPressure, k, time) {
    return inspiredPressure + (initialPressure - inspiredPressure) * Math.exp(-k * time);
}

// Tissue loading methods - EXACT from lines 370-414
function loadTissuesConstant(depth, time, gasO2, gasHe) {
    const ambientPressure = SURFACE_PRESSURE + depth;
    const inspiredN2 = (ambientPressure - WATER_VAPOR_PRESSURE) * (1 - gasO2 - gasHe);
    const inspiredHe = (ambientPressure - WATER_VAPOR_PRESSURE) * gasHe;
    
    for (let i = 0; i < 16; i++) {
        // Haldane equation
        n2Pressure[i] = inspiredN2 + (n2Pressure[i] - inspiredN2) * 
                       Math.exp(-Math.log(2) * time / N2_HALFTIME[i]);
        hePressure[i] = inspiredHe + (hePressure[i] - inspiredHe) * 
                       Math.exp(-Math.log(2) * time / HE_HALFTIME[i]);
    }
}

function loadTissuesDescent(startDepth, endDepth, time, gasO2, gasHe) {
    for (let i = 0; i < 16; i++) {
        const n2k = Math.log(2) / N2_HALFTIME[i];
        const hek = Math.log(2) / HE_HALFTIME[i];
        
        const startPressure = SURFACE_PRESSURE + startDepth;
        const endPressure = SURFACE_PRESSURE + endDepth;
        
        const inspiredN2Start = (startPressure - WATER_VAPOR_PRESSURE) * (1 - gasO2 - gasHe);
        const inspiredN2End = (endPressure - WATER_VAPOR_PRESSURE) * (1 - gasO2 - gasHe);
        const inspiredHeStart = (startPressure - WATER_VAPOR_PRESSURE) * gasHe;
        const inspiredHeEnd = (endPressure - WATER_VAPOR_PRESSURE) * gasHe;
        
        // Schreiner equation
        const n2Rate = (inspiredN2End - inspiredN2Start) / time;
        n2Pressure[i] = inspiredN2Start + n2Rate * (time - 1/n2k) - 
                      (inspiredN2Start - n2Pressure[i] - n2Rate/n2k) * Math.exp(-n2k * time);
        
        const heRate = (inspiredHeEnd - inspiredHeStart) / time;
        hePressure[i] = inspiredHeStart + heRate * (time - 1/hek) - 
                      (inspiredHeStart - hePressure[i] - heRate/hek) * Math.exp(-hek * time);
    }
}

function loadTissuesAscent(startDepth, endDepth, time, gasO2, gasHe) {
    loadTissuesDescent(startDepth, endDepth, time, gasO2, gasHe); // Same math, opposite direction
}

// Calculate ceiling - EXACT from lines 348-368
function calculateCeiling(gradientFactor) {
    let maxCeiling = 0;
    
    for (let i = 0; i < 16; i++) {
        const totalPressure = n2Pressure[i] + hePressure[i];
        
        // Calculate tolerated ambient pressure
        const a = (n2Pressure[i] * N2_A[i] + hePressure[i] * HE_A[i]) / totalPressure;
        const b = (n2Pressure[i] * N2_B[i] + hePressure[i] * HE_B[i]) / totalPressure;
        
        // Apply gradient factor
        const toleratedPressure = (totalPressure - a * gradientFactor) / (gradientFactor / b - gradientFactor + 1);
        const ceiling = toleratedPressure - SURFACE_PRESSURE;
        
        if (ceiling > maxCeiling) {
            maxCeiling = ceiling;
        }
    }
    
    return maxCeiling;
}

// Find first stop - EXACT from lines 337-346
function findFirstStop(gfLow) {
    // Find the ceiling depth where we must stop
    const ceiling = calculateCeiling(gfLow);
    
    // Round UP to next stop interval
    if (ceiling > 0) {
        return Math.ceil(ceiling / DECO_STOP_INTERVAL) * DECO_STOP_INTERVAL;
    }
    return 0;
}

// EXACT calculateStopTime function - lines 259-335
function calculateStopTime(stopDepth, nextStopDepth, gradientFactor, gasO2, gasHe, currentRuntime) {
    console.log("=== DECOMPRESSION_STOP START ===");
    console.log(`Stop Depth: ${stopDepth.toFixed(1)}, Step Size: ${DECO_STOP_INTERVAL.toFixed(1)}`);
    console.log(`Current GF: ${gradientFactor.toFixed(3)}`);
    console.log(`Current Runtime: ${currentRuntime.toFixed(2)}`);
    console.log(`Gas: O2=${(gasO2*100).toFixed(1)}% He=${(gasHe*100).toFixed(1)}%`);
    
    let segmentTime = 0;
    let tempSegmentTime = 0;
    let iteration = 0;
    
    // Make a copy of current tissue state
    const n2Original = [...n2Pressure];
    const heOriginal = [...hePressure];
    
    // EXACT DecoPlanner algorithm from BuhlmannDeco.java lines 2670-2677
    const Last_Run_Time = currentRuntime;
    const Round_Up_Operation = Math.floor(Last_Run_Time) + currentMinimumDecoStopDuration;
    segmentTime = Round_Up_Operation - Last_Run_Time;
    tempSegmentTime = segmentTime;
    
    let doLoop;
    do {
        doLoop = false;
        iteration++;
        
        // Load tissue compartments for iteration using TOTAL time (tempSegmentTime)
        for (let i = 0; i < 16; i++) {
            n2Pressure[i] = haldaneEquation(n2Original[i], 
                (SURFACE_PRESSURE + stopDepth - WATER_VAPOR_PRESSURE) * (1 - gasO2 - gasHe), 
                Math.log(2) / N2_HALFTIME[i], tempSegmentTime);
            hePressure[i] = haldaneEquation(heOriginal[i], 
                (SURFACE_PRESSURE + stopDepth - WATER_VAPOR_PRESSURE) * gasHe, 
                Math.log(2) / HE_HALFTIME[i], tempSegmentTime);
        }
        
        // Calculate ceiling
        const ceiling = calculateCeiling(gradientFactor);
        const roundedCeiling = roundToTwoDecimals(ceiling);
        
        console.log(`  Iteration ${iteration}: Segment_Time=${segmentTime.toFixed(2)}, Temp_Segment_Time=${tempSegmentTime.toFixed(2)}`);
        console.log(`    Ceiling=${ceiling.toFixed(3)}, Rounded=${roundedCeiling.toFixed(2)}, Next_Stop=${nextStopDepth.toFixed(1)}`);
        
        // Show leading compartments
        for (let i = 0; i < 3; i++) {
            console.log(`    Comp ${i+1}: N2=${n2Pressure[i].toFixed(3)}, He=${hePressure[i].toFixed(3)}, Total=${(n2Pressure[i] + hePressure[i]).toFixed(3)}`);
        }
        
        // EXACT DecoPlanner ceiling check logic from line 2743
        if (roundedCeiling > nextStopDepth) {
            // EXACT DecoPlanner lines 2746-2749
            segmentTime = currentMinimumDecoStopDuration;  // currentMinimumDecoStopDuration
            const Time_Counter = tempSegmentTime;
            tempSegmentTime = Time_Counter + currentMinimumDecoStopDuration;  // currentMinimumDecoStopDuration
            doLoop = true;
            console.log("    Need more time - continuing iterations");
        }
        
        // DecoPlanner has no iteration limit - removed to match exactly
    } while (doLoop);
    
    console.log("=== DECOMPRESSION_STOP END ===");
    console.log(`Total iterations: ${iteration}`);
    console.log(`Returning stop time: ${tempSegmentTime.toFixed(2)} min\n`);
    
    // Restore final tissue state exactly like DecoPlanner
    n2Pressure = [...n2Original];
    hePressure = [...heOriginal];
    loadTissuesConstant(stopDepth, tempSegmentTime, gasO2, gasHe);
    
    return tempSegmentTime;
}

// MAIN FUNCTION - EXACT from lines 37-257
function main() {
    const args = process.argv.slice(2);
    
    if (args.length < 6) {
        console.log("Usage: node buhlmann-perfect-alignment-cli.js <depth_m> <time_min> <O2/He> <decoGases> <gfLow> <gfHigh>");
        console.log("Example: node buhlmann-perfect-alignment-cli.js 51 25 21/35 50@21 40 85");
        console.log("Example: node buhlmann-perfect-alignment-cli.js 100 25 8/85 18@21,99@6 40 85");
        process.exit(1);
    }
    
    // Parse arguments
    const depth = parseFloat(args[0]);
    const time = parseFloat(args[1]);
    const bottomGas = args[2].split('/');
    const bottomO2 = parseFloat(bottomGas[0]);
    const bottomHe = bottomGas.length > 1 ? parseFloat(bottomGas[1]) : 0;
    const decoGasesStr = args[3];
    const gfLow = parseFloat(args[4]) / 100.0;
    const gfHigh = parseFloat(args[5]) / 100.0;
    
    // Set up gas list exactly like DecoPlanner
    gases = [];
    activeGasID = 0;
    
    // EXACT initialization from BuhlmannDeco.java line 625
    currentMinimumDecoStopDuration = Math.round(MIN_DECO_STOP_TIME);
    
    // Add bottom gas (DIVE_GAS)
    gases.push(new Gas(bottomO2, bottomHe, Gas.DIVE_GAS, Math.round(depth)));
    
    // Parse and add deco gases (format: "50@21,99@6" or just "50@21")
    if (decoGasesStr !== "0") {
        const decoGasArray = decoGasesStr.split(",");
        for (const decoGasStr of decoGasArray) {
            const parts = decoGasStr.split("@");
            if (parts.length === 2) {
                const gasParts = parts[0].split("/");
                const decoO2 = parseFloat(gasParts[0]);
                const decoHe = gasParts.length > 1 ? parseFloat(gasParts[1]) : 0;
                const switchDepth = parseInt(parts[1]);
                gases.push(new Gas(decoO2, decoHe, Gas.DECO_GAS, switchDepth));
            }
        }
    }
    
    console.log("=== DECOPLANNER DEBUG LOG ===");
    console.log("Generated: " + new Date().toISOString());
    console.log("");
    console.log("=== SETTINGS ===");
    console.log("ZHL-16C: true");
    console.log(`Descent Rate: ${DESCENT_RATE} m/min`);
    console.log(`Ascent Rate: ${ASCENT_RATE} m/min`);
    console.log(`Gradient Factor Low: ${Math.round(gfLow*100)}%`);
    console.log(`Gradient Factor High: ${Math.round(gfHigh*100)}%`);
    console.log(`Water Vapor Pressure: ${WATER_VAPOR_PRESSURE} msw`);
    console.log("");
    
    // Initialize tissues at surface
    const inspiredN2 = (SURFACE_PRESSURE - WATER_VAPOR_PRESSURE) * 0.79;
    for (let i = 0; i < 16; i++) {
        n2Pressure[i] = inspiredN2;
        hePressure[i] = 0;
    }
    
    let runtime = 0;
    let currentDepth = 0;
    
    // DESCENT
    const descentTime = depth / DESCENT_RATE;
    console.log(`Processing DESCENT: 0.0m to ${depth.toFixed(1)}m at ${DESCENT_RATE} m/min`);
    
    // Calculate tissue loading during descent
    let currentGas = gases[activeGasID];
    loadTissuesDescent(0, depth, descentTime, currentGas.oxygenFraction, currentGas.heliumFraction);
    runtime += descentTime;
    currentDepth = depth;
    
    // BOTTOM TIME
    const bottomTime = time - descentTime; // Actual time at bottom (excluding descent)
    const totalTimeAtDepth = time; // Total time including descent (as per DecoPlanner convention)
    console.log(`Actual bottom time at ${depth.toFixed(1)}m: ${bottomTime.toFixed(2)} min (total time: ${totalTimeAtDepth.toFixed(2)} min)`);
    
    // Calculate tissue loading at bottom
    loadTissuesConstant(depth, bottomTime, currentGas.oxygenFraction, currentGas.heliumFraction);
    runtime += bottomTime;
    
    // FIND FIRST DECO STOP
    const firstStopDepth = findFirstStop(gfLow);
    console.log(`\nFirst stop depth (using GF Low ${gfLow.toFixed(2)}): ${firstStopDepth.toFixed(1)}m`);
    
    // Calculate gradient factor slope
    const gfSlope = (gfHigh - gfLow) / (0 - firstStopDepth);
    console.log(`GF Slope: ${gfSlope.toFixed(4)}\n`);
    
    // ASCENT TO FIRST STOP
    let ascentTime = (currentDepth - firstStopDepth) / ASCENT_RATE;
    currentGas = gases[activeGasID];
    loadTissuesAscent(currentDepth, firstStopDepth, ascentTime, currentGas.oxygenFraction, currentGas.heliumFraction);
    runtime += ascentTime;
    currentDepth = firstStopDepth;
    
    // DECOMPRESSION STOPS
    console.log("=== DECOMPRESSION SCHEDULE ===");
    const decoStops = [];
    let firstStop = true;  // Track first stop for special handling
    
    for (let stopDepth = firstStopDepth; stopDepth > 0; stopDepth -= DECO_STOP_INTERVAL) {
        const nextStopDepth = Math.max(0, stopDepth - DECO_STOP_INTERVAL);
        
        // Calculate GF for NEXT stop (critical!)
        const gfForNextStop = gfLow + gfSlope * (nextStopDepth - firstStopDepth);
        
        console.log(`### Calculating stop at ${stopDepth.toFixed(1)}m ###`);
        console.log(`Next Stop: ${nextStopDepth.toFixed(1)}, GF Slope: ${gfSlope.toFixed(4)}`);
        console.log(`GF for next stop: ${gfForNextStop.toFixed(3)} (set as current GF)\n`);
        
        // EXACT DECOPLANENR GAS SWITCHING LOGIC (lines 1735-1770)
        currentGas = gases[activeGasID];
        // Loop through all gases and see if we need to switch to any of them
        for (let gasID = 0; gasID < gases.length; gasID++) {
            const decoGas = gases[gasID];
            // Check that:
            // 1) Current depth is less or equal to the switchdepth of the gas we're now checking
            // 2) The switchdepth of the gas we're now checking is less/shallower than the switchdepth of the gas we're currently using
            // 3) The gas we're checking is a deco gas
            // 4) The gas we're now checking is not the one we're already using
            if (stopDepth <= decoGas.switchDepth && decoGas.gasType === Gas.DECO_GAS && activeGasID !== gasID) {
                // Now we have to check that, IF we are on a deco gas already, make sure the switch depth of the new gas is less than the one we are currently on
                if (currentGas.gasType === Gas.DIVE_GAS || (currentGas.gasType === Gas.DECO_GAS && decoGas.switchDepth < currentGas.switchDepth)) {
                    // Switch to this deco gas
                    activeGasID = gasID;
                    currentGas = gases[activeGasID];
                    console.log(`Gas switch: Switching to ${currentGas.getGasText()} at ${stopDepth.toFixed(1)}m`);
                    
                    // EXACT DecoPlanner logic from lines 1802-1810
                    if(decoGas.minimumDecoStopTime > 0) {
                        currentMinimumDecoStopDuration = decoGas.minimumDecoStopTime;
                    } else {
                        //If this deco gas has not defined a minimum stop time, we use the default minimum time, from Settings.
                        currentMinimumDecoStopDuration = Math.round(MIN_DECO_STOP_TIME);
                    }
                }
            }
        }
        
        // Use current active gas
        const gasO2Fraction = currentGas.oxygenFraction;
        const gasHeFraction = currentGas.heliumFraction;
        const gasName = currentGas.getGasText();
        
        // Calculate stop time using exact iterative method
        const stopTime = calculateStopTime(stopDepth, nextStopDepth, gfForNextStop, 
                                           gasO2Fraction, gasHeFraction, runtime);
        
        // EXACT DecoPlanner first stop special handling (lines 1867-1887)
        let displayStopTime;
        if(firstStop) {
            //We won't report the EXACT times for the first stop, since the DECOMPRESSION_STOP-function adjusted the stopDuration to end up on a whole 
            // multiplier of the Minimum_Deco_Stop_Time. 
            //So for the first deco stop we set endRunTime to follow the real schedule, but we adjust the startRunTime based on the Minimum_Deco_Stop_Time
            
            //Now adjust the stopDuration, just to make for a nice-looking (rounded values) table
            let adjustedStopDuration;
            //BUT, ONLY IF it's NOT already a whole number
            if(stopTime % 1 === 0) {
                adjustedStopDuration = stopTime;
            } else {
                adjustedStopDuration = Math.round((stopTime/currentMinimumDecoStopDuration) + 0.5) * currentMinimumDecoStopDuration;
            }
            displayStopTime = roundToOneDecimal(adjustedStopDuration);
            firstStop = false;  // No longer first stop
        } else {
            // For subsequent stops, include ascent time in display (exact from lines 1888-1893)
            const currentAscentTime = DECO_STOP_INTERVAL / ASCENT_RATE;
            displayStopTime = roundToOneDecimal(stopTime + currentAscentTime);
        }
        
        runtime += stopTime;
        
        // Ascend to next stop
        if (nextStopDepth > 0) {
            ascentTime = DECO_STOP_INTERVAL / ASCENT_RATE;
            loadTissuesAscent(stopDepth, nextStopDepth, ascentTime, gasO2Fraction, gasHeFraction);
            runtime += ascentTime;
        }
        
        decoStops.push(new DecoStop(stopDepth, displayStopTime, gasName));
    }
    
    // Final ascent to surface
    ascentTime = DECO_STOP_INTERVAL / ASCENT_RATE;
    runtime += ascentTime;
    
    // Round runtime to match DecoPlanner's display
    const displayRuntime = Math.round(runtime);
    
    // Output results using DecoPlanner format
    console.log("\nDecompression Schedule:");
    console.log(`${Math.round(depth)}m for ${Math.round(totalTimeAtDepth)} min`);
    
    let totalDecoTime = 0;
    for (const stop of decoStops) {
        // Round to one decimal like DecoPlanner, then format like DecoPlanner
        const roundedTime = roundToOneDecimal(stop.time);
        const formattedTime = formatLikeDecoPlanner(roundedTime);
        console.log(`${Math.round(stop.depth)}m for ${formattedTime} min`);
        totalDecoTime += stop.time;
    }
    
    console.log("");
    console.log(`Final Runtime: ${runtime.toFixed(1)} min (rounded: ${displayRuntime} min)`);
    console.log(`Total Deco Duration: ${totalDecoTime.toFixed(1)} min`);
    console.log(`Total Ascent Duration: ${(totalDecoTime + (depth - firstStopDepth) / ASCENT_RATE + decoStops.length * DECO_STOP_INTERVAL / ASCENT_RATE).toFixed(1)} min`);
}

// Run if called directly
if (require.main === module) {
    main();
}

module.exports = {
    main,
    Gas,
    DecoStop,
    calculateStopTime,
    calculateCeiling,
    loadTissuesConstant,
    loadTissuesDescent,
    loadTissuesAscent
};