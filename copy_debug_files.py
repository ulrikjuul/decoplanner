#!/usr/bin/env python3
"""
Simple script to copy the most recent debug file and rename it to match the most recent plan file.
Run this after each DecoPlanner dive plan generation to create properly named debug files.
"""

import os
import re
import glob
import shutil
from datetime import datetime

def get_most_recent_file(pattern, directory="."):
    """Get the most recently modified file matching the pattern."""
    files = glob.glob(os.path.join(directory, pattern))
    if not files:
        return None
    return max(files, key=os.path.getmtime)

def copy_debug_to_plan_name():
    """Copy the most recent debug file to match the most recent plan file name."""
    
    decoplanner_dir = "NetBeans 13 project - JDK 17 - modular/DecoPlanner"
    
    # Find the most recent plan file (excluding debug and log files)
    plan_files = []
    all_txt_files = glob.glob(os.path.join(decoplanner_dir, "*.txt"))
    
    for file in all_txt_files:
        filename = os.path.basename(file)
        # Exclude debug, log, and other utility files
        if not any(x in filename.lower() for x in ['debug', 'log', 'analysis', 'capture', 'complete', 'final', 'last', 'all_', 'vmp_', 'vpm_', 'error', 'schedule_output', 'stops_', 'ccr_dive_plan', 'ccr_20', 'instructions']):
            # Only include files that look like dive plans (contain depth/time pattern)
            if re.search(r'\d+m\d+min', filename):
                plan_files.append(file)
    
    if not plan_files:
        print("No recent plan files found.")
        return False
    
    most_recent_plan = max(plan_files, key=os.path.getmtime)
    plan_filename = os.path.basename(most_recent_plan)
    
    # Find the most recent debug file
    debug_file = get_most_recent_file("decoplanner_debug_*.txt", decoplanner_dir)
    if not debug_file:
        print("ERROR: No debug file found!")
        print("Debug files are required for proper gas configuration and 6m detection.")
        print("Make sure DecoPlanner is configured to generate debug files.")
        print("Check: Settings > Debug mode or similar option in DecoPlanner.")
        return False
    
    # Check if debug file timestamp is close to plan file timestamp (within 5 minutes)
    plan_time = os.path.getmtime(most_recent_plan)
    debug_time = os.path.getmtime(debug_file)
    time_diff = abs(plan_time - debug_time)
    
    if time_diff > 300:  # 5 minutes
        print(f"Warning: Debug file and plan file timestamps differ by {time_diff/60:.1f} minutes")
        print("They may not be from the same calculation.")
    
    print(f"Found plan file: {plan_filename}")
    print(f"Found debug file: {os.path.basename(debug_file)}")
    
    # Check debug file for 6m last stop setting with multiple patterns
    last_stop_6m = False
    debug_6m_raw = "not found"
    
    try:
        with open(debug_file, 'r') as f:
            debug_content = f.read()
            
        # Look for various patterns that might indicate 6m last stop
        patterns_to_check = [
            "Last Stop 6m: true",
            "Last Stop 6m: false", 
            "Last Stop: 6m",
            "Last Stop: 3m",
            "6m last stop",
            "3m last stop"
        ]
        
        found_patterns = []
        for pattern in patterns_to_check:
            if pattern in debug_content:
                found_patterns.append(pattern)
                if pattern in ["Last Stop 6m: true", "Last Stop: 6m", "6m last stop"]:
                    last_stop_6m = True
                    debug_6m_raw = pattern
                elif pattern in ["Last Stop 6m: false", "Last Stop: 3m", "3m last stop"]:
                    debug_6m_raw = pattern
        
        print(f"Debug patterns found: {found_patterns}")
        print(f"Raw 6m setting in debug: {debug_6m_raw}")
        
        # Also check the actual dive schedule to see what depth the last stop is at
        if "=== DECOMPRESSION SCHEDULE ===" in debug_content:
            schedule_section = debug_content.split("=== DECOMPRESSION SCHEDULE ===")[1]
            if schedule_section:
                schedule_lines = schedule_section.split('\n')[:20]  # First 20 lines of schedule
                for line in schedule_lines:
                    if 'm for ' in line and ('6m for' in line or '3m for' in line):
                        print(f"Found stop in schedule: {line.strip()}")
                        if '6m for' in line:
                            last_stop_6m = True
                            print("Detected 6m last stop from schedule")
                        break
        
    except Exception as e:
        print(f"Warning: Could not read debug file to check 6m setting: {e}")
    
    print(f"Final 6m Last Stop Detection: {'YES' if last_stop_6m else 'NO'}")
    
    # Create debug filename, adding 6mlast tag if needed
    base_name = plan_filename.replace('.txt', '')
    if last_stop_6m and '6mlast' not in base_name:
        # Insert 6mlast before the algorithm part (GF40_85 or VPM2)
        if 'GF40_85' in base_name:
            base_name = base_name.replace('GF40_85', '6mlast_GF40_85')
        elif 'VPM2' in base_name:
            base_name = base_name.replace('VPM2', '6mlast_VPM2')
        else:
            # Fallback - add at the end
            base_name += '_6mlast'
    
    # Create updated plan filename to include 6mlast tag if needed
    updated_plan_filename = plan_filename
    if last_stop_6m and '6mlast' not in plan_filename:
        updated_plan_base = plan_filename.replace('.txt', '')
        if 'GF40_85' in updated_plan_base:
            updated_plan_base = updated_plan_base.replace('GF40_85', '6mlast_GF40_85')
        elif 'VPM2' in updated_plan_base:
            updated_plan_base = updated_plan_base.replace('VPM2', '6mlast_VPM2')
        else:
            updated_plan_base += '_6mlast'
        updated_plan_filename = updated_plan_base + '.txt'
    
    debug_filename = base_name + '_details.txt'
    debug_target_path = os.path.join(decoplanner_dir, debug_filename)
    
    # Determine which test case directory it should go to
    target_test_dir = None
    if 'GF40_85' in updated_plan_filename:
        target_test_dir = "test_cases/Open_Circuit/Buhlmann"
    elif 'VPM2' in updated_plan_filename:
        target_test_dir = "test_cases/Open_Circuit/VPM"
    
    if not target_test_dir or not os.path.exists(target_test_dir):
        print(f"Warning: Could not determine target directory for {plan_filename}")
        return False
    
    # Copy both files to test case directory and clean up
    try:
        
        # Copy and update the plan file content to include 6m setting
        plan_target_path = os.path.join(target_test_dir, updated_plan_filename)
        
        # Read the original plan file content
        with open(most_recent_plan, 'r') as f:
            plan_content = f.read()
        
        # Extract gas configuration from debug file
        gas_config_lines = []
        try:
            with open(debug_file, 'r') as f:
                debug_content_for_gas = f.read()
                
            debug_lines = debug_content_for_gas.split('\n')
            in_gases_section = False
            
            for line in debug_lines:
                if '=== GASES ===' in line:
                    in_gases_section = True
                    continue
                elif line.startswith('===') and in_gases_section:
                    break
                elif in_gases_section and line.strip() and not line.startswith('Gas'):
                    continue
                elif in_gases_section and line.startswith('Gas'):
                    # Parse gas line: "Gas 0: 8/85 (O2=8.0%, He=85.0%, Type=1, Switch=100m)"
                    if 'Type=1' in line:  # Bottom gas
                        gas_info = line.split(':')[1].strip()
                        if '(' in gas_info:
                            gas_name = gas_info.split('(')[0].strip()
                            details = gas_info.split('(')[1].split(')')[0]
                            o2_match = re.search(r'O2=([0-9.]+)%', details)
                            he_match = re.search(r'He=([0-9.]+)%', details)
                            if o2_match and he_match:
                                o2 = float(o2_match.group(1))
                                he = float(he_match.group(1))
                                n2 = 100 - o2 - he
                                gas_config_lines.append(f"Bottom Gas: {gas_name} ({o2:.0f}% O2, {he:.0f}% He, {n2:.0f}% N2)")
                    elif 'Type=2' in line:  # Deco gas
                        gas_info = line.split(':')[1].strip()
                        if '(' in gas_info:
                            gas_name = gas_info.split('(')[0].strip()
                            details = gas_info.split('(')[1].split(')')[0]
                            o2_match = re.search(r'O2=([0-9.]+)%', details)
                            he_match = re.search(r'He=([0-9.]+)%', details)
                            switch_match = re.search(r'Switch=([0-9]+)m', details)
                            if o2_match and he_match and switch_match:
                                o2 = float(o2_match.group(1))
                                he = float(he_match.group(1))
                                n2 = 100 - o2 - he
                                switch_depth = switch_match.group(1)
                                gas_config_lines.append(f"Deco Gas: {gas_name} ({o2:.0f}% O2, {he:.0f}% He, {n2:.0f}% N2) @ {switch_depth}m switch depth")
        except Exception as e:
            print(f"Warning: Could not extract gas configuration: {e}")
            gas_config_lines = ["# Gas configuration could not be extracted"]
        
        # Standardize format and add missing information
        lines = plan_content.split('\n')
        new_lines = []
        gas_config_added = False
        
        for i, line in enumerate(lines):
            new_lines.append(line)
            
            # Add Last Stop setting after algorithm info  
            if ('Gradient Factors:' in line or 'VPM Conservatism:' in line) and not any('Last Stop:' in l for l in lines):
                # Note: The debug field interpretation may be inverted from GUI setting
                # When GUI shows "6m last stop enabled", debug may show "Last Stop 6m: false"
                # For now, let's use the debug file value directly and add a note
                last_stop_line = f"Last Stop: {'6m' if last_stop_6m else '3m'}"
                new_lines.append(last_stop_line)
                new_lines.append(f"# Debug file shows: Last Stop 6m: {last_stop_6m}")
            
            # Add Gas Configuration section if missing
            if 'Total Deco Duration:' in line and not gas_config_added and not any('GAS CONFIGURATION' in l for l in lines):
                new_lines.extend([
                    "",
                    "=== GAS CONFIGURATION ==="
                ])
                new_lines.extend(gas_config_lines)
                gas_config_added = True
        
        plan_content = '\n'.join(new_lines)
        
        # Write the updated content to the target file
        with open(plan_target_path, 'w') as f:
            f.write(plan_content)
        print(f"✓ Copied plan file:")
        print(f"  From: {plan_filename}")
        print(f"  To:   {plan_target_path}")
        if last_stop_6m:
            print(f"  Added: Last Stop: 6m to dive schedule")
        
        # Copy the debug file to test cases
        debug_test_path = os.path.join(target_test_dir, debug_filename)
        shutil.copy2(debug_file, debug_test_path)
        print(f"✓ Copied debug file:")
        print(f"  From: {os.path.basename(debug_file)}")
        print(f"  To:   {debug_test_path}")
        
        # Clean up the original files from DecoPlanner directory
        try:
            os.remove(most_recent_plan)
            print(f"✓ Cleaned up: {plan_filename}")
        except Exception as e:
            print(f"Warning: Could not remove {plan_filename}: {e}")
        
        try:
            os.remove(debug_file)
            print(f"✓ Cleaned up: {os.path.basename(debug_file)}")
        except Exception as e:
            print(f"Warning: Could not remove {os.path.basename(debug_file)}: {e}")
        
        return True
        
    except Exception as e:
        print(f"Error copying debug file: {e}")
        return False

def main():
    """Main function."""
    print("=== COPY PLAN AND DEBUG FILES TO TEST CASES ===")
    print("This script copies the most recent plan and debug files to the test case")
    print("directory with proper naming, then cleans up the DecoPlanner directory.")
    print()
    
    success = copy_debug_to_plan_name()
    
    if success:
        print()
        print("Files copied and cleaned up successfully!")
        print("Both plan file and debug file are now in the test case directory.")
    else:
        print()
        print("Failed to copy files. Make sure:")
        print("1. You've run a dive plan in DecoPlanner recently")
        print("2. Both plan (.txt) and debug files exist")
        print("3. You're running this script from the project root directory")
        print("4. The plan file contains 'GF40_85' or 'VPM2' for proper directory detection")

if __name__ == "__main__":
    main()