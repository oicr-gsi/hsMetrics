#!/bin/bash
cd $1

# HS file  - need o remove first few lines which may change between runs
# INS file - need o remove first few lines which may change between runs
# INS.pdf file, need to remove date info from the start of the file

echo "HS files:"
for f in *.recal.HS;do echo $f;tail -n+12 $f | md5sum;done

echo "INS files:"
for f in *.recal.INS;do echo $f;tail -n+12 $f | md5sum;done

echo "pdf files:"
for f in *.pdf;do echo $f;tail -n+11 $f | md5sum;done
