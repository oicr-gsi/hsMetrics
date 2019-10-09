version 1.0

workflow hsMetrics {
input {
   File    inputBam
   File    baitBed
   File    targetBed
   String? outputFileNamePrefix = ""
}

String? outputPrefix = if outputFileNamePrefix=="" then basename(inputBam, '.bam') else outputFileNamePrefix

call makeRefDictionary {}
call bedToIntervals as bedToTargetIntervals { input: inputBed = targetBed, refDict = makeRefDictionary.refDict }
call bedToIntervals as bedToBaitIntervals { input: inputBed = baitBed, refDict = makeRefDictionary.refDict }

call collectHSmetrics{ input: inputBam = inputBam, baitIntervals = bedToBaitIntervals.outputIntervals, targetIntervals = bedToTargetIntervals.outputIntervals, outputPrefix = outputPrefix }
call collectInsertMetrics{ input: inputBam = inputBam, outputPrefix = outputPrefix }

meta {
 author: "Peter Ruzanov"
 email: "peter.ruzanov@oicr.on.ca"
 description: "HSMetrics 2.0"
}

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
   Int?   jobMemory = 16
   String? modules   = "java/8 picard/2.19.2 hg19/p13"
}

parameter_meta {
 refFasta: "Path to fasta reference file"
 jobMemory: "Memory allocated to Job"
 modules: "Names and versions of modules needed"
}

command <<<
 java -Xmx~{jobMemory-6}G -jar $PICARD_ROOT/picard.jar CreateSequenceDictionary \
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
   Int?   jobMemory = 16
   String? modules   = "java/8 picard/2.19.2" 
}

command <<<
 java -Xmx~{jobMemory-6}G -jar $PICARD_ROOT/picard.jar BedToIntervalList \
                              INPUT=~{inputBed} \
                              OUTPUT="~{basename(inputBed, '.bed')}.interval_list" \
                              SD="~{refDict}"
>>>

parameter_meta {
 inputBed: "Input bed file"
 refDict: "Path to index of fasta reference file"
 jobMemory: "Memory allocated to job"
 modules: "Names and versions of modules needed"
}

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
   String? refFasta   = "$HG19_ROOT/hg19_random.fa"
   String? metricTag  = "HS"
   String? filter     = "LENIENT"
   String? outputPrefix = "OUTPUT"
   Int?   jobMemory   = 18
   Int?   coverageCap = 500
   String? modules    = "java/8 picard/2.19.2 hg19/p13"
}

command <<<
 java -Xmx~{jobMemory-6}G -jar $PICARD_ROOT/picard.jar CollectHsMetrics \
                              TMP_DIR=picardTmp \
                              BAIT_INTERVALS=~{baitIntervals} \
                              TARGET_INTERVALS=~{targetIntervals} \
                              R=~{refFasta} \
                              COVERAGE_CAP=~{coverageCap} \
                              INPUT=~{inputBam} \
                              OUTPUT="~{outputPrefix}.~{metricTag}.txt" \
                              VALIDATION_STRINGENCY=~{filter} 
>>>

parameter_meta {
 inputBam: "Input bam file"
 baitIntervals: "bed file with bait intervals"
 targetIntervals: "bed file with target intervals"
 refFasta: "Path to fasta reference file"
 metricTag: "Extension for metrics file"
 filter: "Settings for picard filter"
 outputPrefix: "prefix to build a name for output file"
 coverageCap: "Parameter to set a max coverage limit for Theoretical Sensitivity calculations"
 jobMemory: "Memory allocated to job"
 modules: "Names and versions of modules needed"
}

runtime {
  memory:  "~{jobMemory} GB"
  modules: "~{modules}"
}

output {
  File outputHSMetrics = "~{outputPrefix}.~{metricTag}.txt"
}
}


# ==========================================
#  TASK 4 of 4: collect Insert metrics
# ==========================================
task collectInsertMetrics {
input {
   File    inputBam
   String? metricTag  = "INS"
   Int?    jobMemory  = 18
   Float?  minPct     = 0.5
   String? outputPrefix = "OUTPUT"
   String? modules    = "java/8 rstats/3.6 picard/2.19.2"
}

command <<<
 java -Xmx~{jobMemory-6}G -jar $PICARD_ROOT/picard.jar CollectInsertSizeMetrics \
                              INPUT=~{inputBam} \
                              OUTPUT="~{outputPrefix}.~{metricTag}.txt" \
                              H="~{outputPrefix}.~{metricTag}.PDF" \
                              M=~{minPct}
>>>

parameter_meta {
 inputBam: "Input bam file"
 metricTag: "Extension for metrics file"
 minPct: "Discard any data categories (out of FR, TANDEM, RF) that have fewer than this percentage of overall reads"
 outputPrefix: "prefix to build a name for output file"
 jobMemory: "Memory allocated to job"
 modules: "Names and versions of modules needed"
}

runtime {
  memory:  "~{jobMemory} GB"
  modules: "~{modules}"
}

output {
  File outputINSMetrics = "~{outputPrefix}.~{metricTag}.txt"
  File outputINSPDF     = "~{outputPrefix}.~{metricTag}.PDF"
}
}

