#!/bin/bash
cd $1

# txt file  - need o remove first few lines which may change between runs

echo ".txt files:"
for f in *.txt;do echo $f;tail -n+12 $f | md5sum;done

