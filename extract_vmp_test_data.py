#!/usr/bin/env python3
"""
Extract VPM test case data from DecoPlanner captured files
Creates structured test data for validation
"""

import os
import re
import json
import csv
from pathlib import Path

def parse_vpm_file(filepath):
    """Parse a VPM test file and extract structured data"""
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Extract basic info from filename
    filename = Path(filepath).name
    pattern = r'(\d+)m(\d+)min_([^_]+)_([^_]+)_VPM(\d+)\.txt'
    match = re.match(pattern, filename)
    
    if not match:
        return None
        
    depth, time, bottom_gas, deco_gas, conservatism = match.groups()
    
    # Extract runtime and deco duration from content
    runtime_match = re.search(r'Total Runtime: (\d+\.?\d*) min', content)
    deco_match = re.search(r'Total Deco Duration: (\d+\.?\d*) min', content)
    
    if not runtime_match:
        return None
        
    runtime = float(runtime_match.group(1))
    deco_duration = float(deco_match.group(1)) if deco_match else 0.0
    
    # Parse decompression schedule
    schedule = []
    schedule_section = False
    for line in content.split('\n'):
        line = line.strip()
        if line == "Decompression Schedule:":
            schedule_section = True
            continue
        elif schedule_section and line and not line.startswith('='):
            # Parse lines like "45m for 25 min" or "24m for 2 min"
            stop_match = re.match(r'(\d+)m for (\d+\.?\d*) min', line)
            if stop_match:
                stop_depth = int(stop_match.group(1))
                stop_time = float(stop_match.group(2))
                schedule.append({'depth': stop_depth, 'time': stop_time})
    
    # Convert gas notation
    if bottom_gas == "EAN32":
        bottom_gas_parsed = "32/0"
    elif "-" in bottom_gas:
        bottom_gas_parsed = bottom_gas.replace("-", "/")
    else:
        bottom_gas_parsed = bottom_gas
    
    # Handle deco gas notation
    if deco_gas == "EAN50":
        deco_gas_parsed = "50@21"
    elif deco_gas.startswith("O2"):
        deco_gas_parsed = "99@6"  # Oxygen at 6m
    else:
        deco_gas_parsed = deco_gas
    
    return {
        'depth': int(depth),
        'time': int(time),
        'bottom_gas': bottom_gas_parsed,
        'deco_gas': deco_gas_parsed,
        'conservatism': int(conservatism),
        'expected_runtime': runtime,
        'expected_deco_time': deco_duration,
        'schedule': schedule,
        'filename': filename
    }

def main():
    # Find all VPM test files
    base_dir = "/Users/ulrikjuulchristensen/vibecoder/decoplanner/NetBeans 13 project - JDK 17 - modular/DecoPlanner"
    vpm_files = []
    
    for filename in os.listdir(base_dir):
        if filename.endswith('_VPM.txt') or (filename.endswith('.txt') and 'VPM' in filename and filename != 'LAST_VPM_CAPTURED.txt'):
            filepath = os.path.join(base_dir, filename)
            vpm_files.append(filepath)
    
    # Parse all files
    test_cases = []
    for filepath in vpm_files:
        try:
            data = parse_vpm_file(filepath)
            if data:
                test_cases.append(data)
                print(f"✅ Parsed: {data['filename']} - {data['depth']}m/{data['time']}min - {data['expected_runtime']} min")
        except Exception as e:
            print(f"❌ Error parsing {filepath}: {e}")
    
    # Sort by depth, time, conservatism
    test_cases.sort(key=lambda x: (x['depth'], x['time'], x['conservatism']))
    
    # Save as JSON
    output_file = '/Users/ulrikjuulchristensen/vibecoder/decoplanner/vpm_test_cases.json'
    with open(output_file, 'w') as f:
        json.dump(test_cases, f, indent=2)
    
    print(f"\n📊 EXTRACTED {len(test_cases)} VPM test cases")
    print(f"💾 Saved to: {output_file}")
    
    # Create CSV for easy analysis
    csv_file = '/Users/ulrikjuulchristensen/vibecoder/decoplanner/vpm_test_cases.csv'
    with open(csv_file, 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=[
            'depth', 'time', 'bottom_gas', 'deco_gas', 'conservatism', 
            'expected_runtime', 'expected_deco_time', 'filename'
        ])
        writer.writeheader()
        for case in test_cases:
            writer.writerow({k: v for k, v in case.items() if k != 'schedule'})
    
    print(f"📋 CSV created: {csv_file}")
    
    return test_cases

if __name__ == "__main__":
    main()