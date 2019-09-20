version 1.0

workflow hsmetricsWorkflow {
input {
    File    inputBam
    File    baitBed
    File    targetBed
    String  refFasta
    String? outputPrefix = ""
    String? stringencyFilter = "LENIENT"
    Float?  minPct      = 0.5
    Int?    coverageCap = 500
}

call makeRefDictionary { input: refFasta = refFasta }
call bedToIntervals as bedToTargetIntervals { input: inputBed = targetBed, refDict = makeRefDictionary.refDict }
call bedToIntervals as bedToBaitIntervals { input: inputBed = baitBed, refDict = makeRefDictionary.refDict }

call collectHSmetrics{ input: inputBam = inputBam, baitIntervals = bedToBaitIntervals.outputIntervals, targetIntervals = bedToTargetIntervals.outputIntervals, refFasta = refFasta, filter = stringencyFilter, coverageCap = coverageCap, outputPrefix = outputPrefix }
call collectInsertMetrics{ input: inputBam = inputBam, minPct = minPct, outputPrefix = outputPrefix }

output {
  File outputHSMetrics  = collectHSmetrics.outputHSMetrics
  File outputINSMetrics = collectInsertMetrics.outputINSMetrics
  File outputINSPDF     = collectInsertMetrics.outputINSPDF
}

}

# ==========================================
#  TASK 1 of 4: make reference dictionary
# ==========================================
task makeRefDictionary {
input {
        String refFasta
        Int?   jobMemory  = 12
        Int?   javaMemory = 10
        String? modules   = "java/8 picard/2.19.2 hg19/p13"
}

command <<<
 java -Xmx~{javaMemory}G -jar $PICARD_ROOT/picard.jar CreateSequenceDictionary \
                              REFERENCE=~{refFasta} \
                              OUTPUT="~{basename(refFasta, '.fa')}.dict" 
>>>

runtime {
  memory:  "~{jobMemory} GB"
  modules: "~{modules}"
}

output {
  File refDict = "~{basename(refFasta, '.fa')}.dict"
}
}

# ==========================================
#  TASK 2 of 4: convert bed to intervals
# ==========================================
task bedToIntervals {
input {
	File   inputBed
        File   refDict       
        Int?   jobMemory  = 16
        Int?   javaMemory = 12
        String? modules   = "java/8 picard/2.19.2" 
}

command <<<
 java -Xmx~{javaMemory}G -jar $PICARD_ROOT/picard.jar BedToIntervalList \
                              INPUT=~{inputBed} \
                              OUTPUT="~{basename(inputBed, '.bed')}.interval_list" \
                              SD="~{refDict}"
>>>

runtime {
  memory:  "~{jobMemory} GB"
  modules: "~{modules}"
}

output {
  File outputIntervals = "~{basename(inputBed, '.bed')}.interval_list"
}
}


# ==========================================
#  TASK 3 of 4: collect HS metric
# ==========================================
task collectHSmetrics {
input { 
        File   inputBam
        File   baitIntervals
        File   targetIntervals
        String refFasta
        String? metricTag  = "HS"
        String? filter     = "LENIENT"
        String? outputPrefix = ""
        Int?   jobMemory   = 16
        Int?   javaMemory  = 12
        Int?   coverageCap = 500
        String? modules    = "java/8 picard/2.19.2 hg19/p13 rstats/3.6"
}

command <<<
 java -Xmx~{javaMemory}G -jar $PICARD_ROOT/picard.jar CollectHsMetrics \
                              TMP_DIR=picardTmp \
                              BAIT_INTERVALS=~{baitIntervals} \
                              TARGET_INTERVALS=~{targetIntervals} \
                              R=~{refFasta} \
                              COVERAGE_CAP=~{coverageCap} \
                              INPUT=~{inputBam} \
                              OUTPUT="~{basename(inputBam, '.bam')}~{outputPrefix}.~{metricTag}" \
                              VALIDATION_STRINGENCY=~{filter} 
>>>

runtime {
  memory:  "~{jobMemory} GB"
  modules: "~{modules}"
}

output {
  File outputHSMetrics = "~{basename(inputBam, '.bam')}~{outputPrefix}.~{metricTag}"
}
}


# ==========================================
#  TASK 4 of 4: collect Insert metrics
# ==========================================
task collectInsertMetrics {
input {
        File    inputBam
        String? metricTag  = "INS"
        Int?    jobMemory  = 16
        Int?    javaMemory = 12
        Float?  minPct     = 0.5
        String? outputPrefix = ""
        String? modules    = "java/8 picard/2.19.2"
}

command <<<
 java -Xmx~{javaMemory}G -jar $PICARD_ROOT/picard.jar CollectInsertSizeMetrics \
                              INPUT=~{inputBam} \
                              OUTPUT="~{basename(inputBam, '.bam')}~{outputPrefix}.~{metricTag}" \
                              H="~{basename(inputBam, '.bam')}~{outputPrefix}.~{metricTag}.PDF" \
                              M=~{minPct}
>>>

runtime {
  memory:  "~{jobMemory} GB"
  modules: "~{modules}"
}

output {
  File outputINSMetrics = "~{basename(inputBam, '.bam')}~{outputPrefix}.~{metricTag}"
  File outputINSPDF     = "~{basename(inputBam, '.bam')}~{outputPrefix}.~{metricTag}.PDF"
}
}

