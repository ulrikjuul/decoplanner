#!/usr/bin/env node

/**
 * Validation script to compare JavaScript vs Java CLI implementations
 * Tests multiple dive profiles to ensure 100% alignment
 */

const { execSync } = require('child_process');
const fs = require('fs');

console.log("🧪 JAVASCRIPT vs JAVA CLI VALIDATION");
console.log("=====================================\n");

// Test cases from comprehensive_test_library.csv
const testCases = [
    {
        name: "Buhlmann 51m/25min Perfect Alignment Test",
        algorithm: "buhlmann", 
        args: "51 25 21/35 50@21 40 85",
        expectedRuntime: 177.3, // From Java validation
        description: "Critical test case - should match Java exactly"
    },
    {
        name: "Buhlmann 100m/25min Reference Case",
        algorithm: "buhlmann",
        args: "100 25 8/85 18@21,99@6 40 85", 
        expectedRuntime: 178.0,
        description: "Deep technical dive with multiple deco gases"
    },
    {
        name: "VPM-B 51m/25min Conservatism +2",
        algorithm: "vpm",
        args: "51 25 21/35 50@21 2 85",
        expectedRuntime: 54.7, // From DecoPlanner captures
        description: "VPM-B test case matching DecoPlanner"
    }
];

const results = [];

console.log("🎯 Running validation tests...\n");

for (const testCase of testCases) {
    console.log(`Testing: ${testCase.name}`);
    console.log(`Args: ${testCase.args}`);
    console.log(`Expected: ${testCase.expectedRuntime} min\n`);
    
    let jsRuntime = null;
    let javaRuntime = null;
    let jsError = null;
    let javaError = null;
    
    try {
        // Run JavaScript version
        const jsCommand = testCase.algorithm === "buhlmann" 
            ? `node buhlmann-perfect-alignment-cli.js ${testCase.args}`
            : `node vpm-alignment-cli.js ${testCase.args}`;
            
        const jsOutput = execSync(jsCommand, { encoding: 'utf8', timeout: 30000 });
        const jsMatch = jsOutput.match(/Final Runtime: ([\d.]+) min/);
        if (jsMatch) {
            jsRuntime = parseFloat(jsMatch[1]);
        }
        
        console.log(`✅ JavaScript: ${jsRuntime} min`);
        
    } catch (error) {
        jsError = error.message;
        console.log(`❌ JavaScript Error: ${jsError}`);
    }
    
    try {
        // Run Java version
        const javaCommand = testCase.algorithm === "buhlmann"
            ? `/opt/homebrew/opt/openjdk@17/bin/java PerfectAlignmentCLI ${testCase.args}`
            : `/opt/homebrew/opt/openjdk@17/bin/java VPMAlignmentCLI ${testCase.args}`;
            
        const javaOutput = execSync(javaCommand, { encoding: 'utf8', timeout: 30000 });
        const javaMatch = javaOutput.match(/Final Runtime: ([\d.]+) min/);
        if (javaMatch) {
            javaRuntime = parseFloat(javaMatch[1]);
        }
        
        console.log(`✅ Java: ${javaRuntime} min`);
        
    } catch (error) {
        javaError = error.message;
        console.log(`❌ Java Error: ${javaError}`);
    }
    
    // Calculate accuracy
    let accuracy = null;
    let status = "ERROR";
    let difference = null;
    
    if (jsRuntime !== null && javaRuntime !== null) {
        difference = Math.abs(jsRuntime - javaRuntime);
        accuracy = difference < 0.1 ? 100.0 : (1 - difference / javaRuntime) * 100;
        
        if (difference < 0.1) {
            status = "PERFECT_MATCH";
            console.log(`🎉 PERFECT MATCH! Difference: ${difference.toFixed(3)} min`);
        } else if (difference < 1.0) {
            status = "CLOSE_MATCH"; 
            console.log(`✅ Close match. Difference: ${difference.toFixed(3)} min (${accuracy.toFixed(1)}% accuracy)`);
        } else {
            status = "NEEDS_INVESTIGATION";
            console.log(`⚠️  Significant difference: ${difference.toFixed(3)} min (${accuracy.toFixed(1)}% accuracy)`);
        }
    }
    
    results.push({
        name: testCase.name,
        algorithm: testCase.algorithm,
        args: testCase.args,
        expectedRuntime: testCase.expectedRuntime,
        jsRuntime,
        javaRuntime, 
        difference,
        accuracy,
        status,
        jsError,
        javaError,
        description: testCase.description
    });
    
    console.log("-".repeat(60) + "\n");
}

// Generate validation report
console.log("📊 VALIDATION SUMMARY");
console.log("====================\n");

let perfectMatches = 0;
let closeMatches = 0;
let needsInvestigation = 0;
let errors = 0;

for (const result of results) {
    console.log(`${result.name}:`);
    console.log(`  Status: ${result.status}`);
    
    if (result.status === "PERFECT_MATCH") {
        perfectMatches++;
        console.log(`  ✅ JavaScript and Java runtimes identical`);
    } else if (result.status === "CLOSE_MATCH") {
        closeMatches++;
        console.log(`  ✅ Runtimes very close (${result.accuracy.toFixed(1)}% accuracy)`);
    } else if (result.status === "NEEDS_INVESTIGATION") {
        needsInvestigation++;
        console.log(`  ⚠️  Significant runtime difference detected`);
    } else {
        errors++;
        console.log(`  ❌ Execution errors occurred`);
    }
    
    if (result.jsRuntime !== null && result.javaRuntime !== null) {
        console.log(`  JS Runtime: ${result.jsRuntime} min | Java Runtime: ${result.javaRuntime} min`);
    }
    
    console.log("");
}

console.log("📈 FINAL RESULTS:");
console.log(`Perfect Matches: ${perfectMatches}/${results.length}`);
console.log(`Close Matches: ${closeMatches}/${results.length}`);
console.log(`Need Investigation: ${needsInvestigation}/${results.length}`);
console.log(`Errors: ${errors}/${results.length}`);

const overallSuccess = perfectMatches + closeMatches;
const successRate = (overallSuccess / results.length) * 100;

console.log(`\n🎯 Overall Success Rate: ${overallSuccess}/${results.length} (${successRate.toFixed(1)}%)`);

if (successRate >= 90) {
    console.log("🎉 EXCELLENT: JavaScript implementations are production ready!");
} else if (successRate >= 75) {
    console.log("✅ GOOD: JavaScript implementations mostly aligned, minor tweaks needed");
} else {
    console.log("⚠️  NEEDS WORK: JavaScript implementations require significant alignment fixes");
}

// Save detailed results to JSON
const reportData = {
    timestamp: new Date().toISOString(),
    summary: {
        totalTests: results.length,
        perfectMatches,
        closeMatches, 
        needsInvestigation,
        errors,
        successRate
    },
    testResults: results
};

fs.writeFileSync('js-vs-java-validation-results.json', JSON.stringify(reportData, null, 2));
console.log("\n💾 Detailed results saved to: js-vs-java-validation-results.json");