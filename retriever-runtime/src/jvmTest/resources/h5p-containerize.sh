#!/bin/bash

#
# Quick bash script that will simulate a container with a lot of files
# 

FCOUNT=0
OUTDIR=$1
for FILE in $(find . -type f); do
   echo $FILE
   cp $FILE $1/$FCOUNT
   gzip $1/$FCOUNT
   FCOUNT=$((FCOUNT+1))
done

