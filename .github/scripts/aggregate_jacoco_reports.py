import xml.etree.ElementTree as ET

# Parse both reports
tree1 = ET.parse('build_app_artifact/testCodeCoverageReport.xml')
tree2 = ET.parse('xts_artifact/testCodeCoverageReport.xml')

# Get the root elements
root1 = tree1.getroot()
root2 = tree2.getroot()

# Merge the `<package>` elements from the second report into the first
for package in root2.findall('package'):
    root1.append(package)

# Write the combined report to a new file
tree1.write('combinedCodeCoverageReport.xml')
