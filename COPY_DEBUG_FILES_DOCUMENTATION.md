# DecoPlanner File Processing System Documentation

## Overview
The `copy_debug_files.py` script automates the post-processing of DecoPlanner dive plans by copying, renaming, enhancing, and organizing plan files and their corresponding debug files into the appropriate test case directories.

## Purpose
DecoPlanner generates two types of files after each dive plan calculation:
1. **Plan file** (e.g., `30m25min_EAN32_GF40_85.txt`) - Contains the dive schedule
2. **Debug file** (e.g., `decoplanner_debug_20250816_120929.txt`) - Contains detailed algorithm execution data

This script processes these files to create properly named, enhanced test cases for validation and analysis.

## Core Functionality

### 1. File Detection and Pairing
- **Plan File Detection**: Finds the most recent `.txt` file containing depth/time patterns (`\d+m\d+min`)
- **Exclusion Logic**: Filters out debug, log, analysis, and utility files using keyword blacklist
- **Debug File Pairing**: Locates the most recent `decoplanner_debug_*.txt` file
- **Timestamp Validation**: Warns if plan and debug files differ by more than 5 minutes

### 2. Algorithm Recognition and Routing
The script uses regex patterns to identify decompression algorithms and route files to appropriate directories:

```python
# Buhlmann ZH-L16C with Gradient Factors
if re.search(r'GF\d+_\d+', filename):
    target_dir = "test_cases/Open_Circuit/Buhlmann"

# VPM-B with Conservatism Levels  
elif re.search(r'VPM\d+', filename):
    target_dir = "test_cases/Open_Circuit/VPM"
```

**Supported Patterns:**
- **Buhlmann**: `GF20_85`, `GF30_70`, `GF40_85`, `GF100_100`, etc.
- **VPM**: `VPM0`, `VPM1`, `VPM2`, `VPM3`, `VPM4`

### 3. Last Stop Detection and Tagging
The script analyzes debug files to detect whether the 6m last stop setting was enabled:

#### Detection Methods:
1. **Direct Pattern Matching**: Searches for explicit last stop indicators
   ```
   "Last Stop 6m: true"    -> 6m enabled
   "Last Stop 6m: false"   -> 3m (default)
   "Last Stop: 6m"         -> 6m enabled
   "Last Stop: 3m"         -> 3m (default)
   ```

2. **Schedule Analysis**: Examines the decompression schedule for actual stop depths
   ```
   "6m for 12 min" -> 6m last stop detected
   "3m for 15 min" -> 3m last stop detected
   ```

#### 6mlast Tag Insertion:
When 6m last stop is detected, the script inserts a `6mlast` tag before the algorithm identifier:

```python
# Examples of tag insertion:
"30m25min_EAN32_GF40_85.txt" -> "30m25min_EAN32_6mlast_GF40_85.txt"
"100m25min_8-85_VPM2.txt"    -> "100m25min_8-85_6mlast_VPM2.txt"
```

### 4. File Enhancement
The script enhances plan files by adding missing information:

#### Gas Configuration Extraction:
Parses debug files to extract gas information:
```
Gas 0: 21/35 (O2=21.0%, He=35.0%, Type=1, Switch=100m)  -> Bottom Gas
Gas 1: EAN50 (O2=50.0%, He=0.0%, Type=2, Switch=21m)    -> Deco Gas
```

#### Content Enhancement:
- Adds standardized "Last Stop: 3m/6m" line after algorithm information
- Inserts comprehensive gas configuration section
- Includes debug file cross-reference for verification

### 5. File Organization and Cleanup
- **Target Structure**:
  ```
  test_cases/
  ├── Open_Circuit/
  │   ├── Buhlmann/          # All GF patterns
  │   └── VPM/               # All VPM patterns
  ```
- **Naming Convention**: `[original_name]_details.txt` for debug files
- **Cleanup**: Removes processed files from DecoPlanner directory

## Usage Instructions

### Prerequisites
1. DecoPlanner must be configured to generate debug files
2. Script must be run from project root directory
3. Both plan and debug files must exist and be recent

### Running the Script
```bash
# From project root directory
python3 copy_debug_files.py
```

### Expected Output
```
=== COPY PLAN AND DEBUG FILES TO TEST CASES ===
Found plan file: 30m25min_EAN32_GF40_85.txt
Found debug file: decoplanner_debug_20250816_120929.txt
Debug patterns found: ['Last Stop 6m: false']
Final 6m Last Stop Detection: NO
✓ Copied plan file:
  From: 30m25min_EAN32_GF40_85.txt
  To:   test_cases/Open_Circuit/Buhlmann/30m25min_EAN32_GF40_85.txt
✓ Copied debug file:
  From: decoplanner_debug_20250816_120929.txt
  To:   test_cases/Open_Circuit/Buhlmann/30m25min_EAN32_GF40_85_details.txt
```

## Error Handling

### Critical Errors (Script Fails):
- **No plan files found**: Indicates no recent dive plans
- **No debug files found**: Debug logging may be disabled
- **Target directory missing**: Test case structure may be corrupted

### Warnings (Script Continues):
- **Timestamp mismatch**: Plan and debug files may not be from same calculation
- **Gas extraction failure**: Debug file format may be unexpected
- **Cleanup failure**: Original files may remain in DecoPlanner directory

## Algorithm Integration

### Debug File Generation
The script relies on debug logging added to DecoPlanner algorithms:

#### Buhlmann (BuhlmannDeco.java):
```java
// Debug file creation
String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
String logFileName = "decoplanner_debug_" + timestamp + ".txt";
debugLog = new PrintWriter(new FileWriter(logFileName));

// Last stop logging
debugLog.println("Last Stop 6m: " + Settings.lastStopDoubleInterval);
```

#### VPM (VPMDeco.java):
```java
// Same pattern as Buhlmann
debugLog.println("Last Stop 6m: " + Settings.lastStopDoubleInterval);
```

### File Creation Behavior
- **New file per calculation**: Each dive plan generates a fresh debug file
- **Timestamp-based naming**: Ensures unique filenames for concurrent calculations
- **Comprehensive logging**: All algorithm steps logged for validation

## Testing and Validation

### Supported Test Cases
The script handles all algorithm configurations:
- **Gradient Factors**: Any combination (GF20_85, GF30_70, GF40_85, GF100_100, etc.)
- **VPM Conservatism**: All levels (VPM0, VPM1, VPM2, VPM3, VPM4)
- **Gas Configurations**: Single gas, multi-gas, trimix, nitrox
- **Last Stop Settings**: Both 3m and 6m configurations

### Validation Points
1. **Algorithm detection accuracy**: Verify files route to correct directories
2. **6mlast tag logic**: Confirm tag insertion matches actual last stop depths
3. **Gas configuration parsing**: Validate extracted gas information matches debug data
4. **File pairing**: Ensure plan and debug files are from same calculation

## Maintenance Notes

### Extending Algorithm Support
To add new algorithm support:
1. Add regex pattern to algorithm detection logic
2. Create corresponding test case directory
3. Update help text and documentation

### Debug Format Changes
If DecoPlanner debug format changes:
1. Update gas configuration parsing patterns
2. Modify last stop detection logic
3. Test with representative dive plans

### Directory Structure Changes
If test case organization changes:
1. Update target directory paths
2. Modify routing logic
3. Update cleanup procedures

## Common Issues and Solutions

### Issue: "No debug file found!"
**Cause**: DecoPlanner debug logging disabled or failed
**Solution**: 
1. Check DecoPlanner debug mode settings
2. Verify algorithm includes debug logging code
3. Ensure write permissions in DecoPlanner directory

### Issue: "Could not determine target directory"
**Cause**: Plan filename doesn't match expected algorithm patterns
**Solution**:
1. Verify filename contains GF or VPM pattern
2. Check for typos in algorithm identifiers
3. Update regex patterns if new naming convention used

### Issue: Gas configuration extraction fails
**Cause**: Debug file format changed or corrupted
**Solution**:
1. Examine debug file manually for gas section format
2. Update parsing logic to match current format
3. Add fallback handling for missing gas data

## Future Enhancements

### Potential Improvements
1. **Batch processing**: Handle multiple plan files simultaneously
2. **Configuration validation**: Cross-check plan vs debug settings
3. **Historical tracking**: Maintain database of processed files
4. **Format conversion**: Export to additional formats (JSON, CSV)
5. **Automated testing**: Generate test reports for algorithm validation

### Integration Opportunities
1. **CI/CD pipeline**: Automate file processing in build systems
2. **Web interface**: Provide GUI for file management
3. **API integration**: Connect with dive planning databases
4. **Validation frameworks**: Integrate with test suites